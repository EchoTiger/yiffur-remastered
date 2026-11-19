package com.ugf.yiffur1.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugf.yiffur1.data.api.ApiErrorParser
import com.ugf.yiffur1.data.local.StoredCredentials
import com.ugf.yiffur1.data.update.UpdateCheckResult
import com.ugf.yiffur1.di.AppContainer
import com.ugf.yiffur1.domain.model.ApiHost
import com.ugf.yiffur1.domain.model.AppSettings
import com.ugf.yiffur1.domain.model.FeedLayoutMode
import com.ugf.yiffur1.domain.model.ThemeMode
import com.ugf.yiffur1.domain.model.UserProfile
import com.ugf.yiffur1.domain.model.VideoQualityPreference
import com.ugf.yiffur1.domain.perf.PerformanceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val credentials: StoredCredentials? = null,
    val userProfile: UserProfile? = null,
    val isVerifyingLogin: Boolean = false,
    val loginError: String? = null,
    val blacklistCount: Int = 0,
    val localFavoritesCount: Int = 0,
    val cacheSizeBytes: Long = 0L
)

class SettingsViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    private val _isVerifying = MutableStateFlow(false)
    private val _loginError = MutableStateFlow<String?>(null)
    private val _cacheSizeBytes = MutableStateFlow(0L)
    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates: StateFlow<Boolean> = _isCheckingUpdates.asStateFlow()

    val performanceState = container.performanceEngine.state
    val availableUpdate = container.appUpdateManager.availableUpdate
    val updateDownloadState = container.appUpdateManager.downloadState
    val lastUpdateCheckMs = container.appUpdateManager.lastCheckedMs
    val fcmToken = container.pushNotificationManager.fcmToken
    val isFirebaseConfigured: Boolean
        get() = container.pushNotificationManager.isFirebaseConfigured

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        container.settingsRepository.settingsFlow,
        container.credentialStore.credentialsFlow,
        combine(_userProfile, _isVerifying, _loginError) { p, v, e -> Triple(p, v, e) },
        combine(
            container.database.blacklistDao().observeAll(),
            container.database.localFavoriteDao().observeAllIds(),
            _cacheSizeBytes
        ) { bl, favs, cache -> Triple(bl.size, favs.size, cache) }
    ) { settings, creds, (profile, verifying, loginErr), (blCount, favCount, cacheBytes) ->
        SettingsUiState(
            settings = settings,
            credentials = creds,
            userProfile = profile,
            isVerifyingLogin = verifying,
            loginError = loginErr,
            blacklistCount = blCount,
            localFavoritesCount = favCount,
            cacheSizeBytes = cacheBytes
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        refreshCacheSize()
        refreshAccountProfile()
    }

    fun refreshAccountProfile() {
        if (!container.credentialStore.isLoggedIn) {
            _userProfile.value = null
            return
        }
        viewModelScope.launch {
            runCatching { container.postRepository.getCurrentUser() }
                .onSuccess { profile ->
                    if (profile.isAuthenticated) _userProfile.value = profile
                }
        }
    }

    fun login(username: String, apiKey: String, onSuccess: () -> Unit) {
        val cleanUser = username.trim()
        val cleanKey = apiKey.trim()
        if (cleanUser.isEmpty() || cleanKey.isEmpty()) {
            _loginError.value = "Enter both your e621 username and API key."
            return
        }
        viewModelScope.launch {
            _isVerifying.value = true
            _loginError.value = null
            runCatching { container.postRepository.verifyCredentials(cleanUser, cleanKey) }
                .onSuccess { profile ->
                    container.credentialStore.save(cleanUser, cleanKey)
                    _userProfile.value = profile
                    _isVerifying.value = false
                    _events.tryEmit("Signed in as ${profile.name} (${profile.levelString})")
                    onSuccess()
                }
                .onFailure { err ->
                    _isVerifying.value = false
                    _loginError.value = ApiErrorParser.toUserMessage(err)
                }
        }
    }

    fun clearLoginError() {
        _loginError.value = null
    }

    fun logout() {
        container.credentialStore.clear()
        _userProfile.value = null
        _events.tryEmit("Signed out")
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            container.settingsRepository.update(transform)
        }
    }

    fun setApiHost(host: ApiHost, acceptedUgc: Boolean = false) {
        viewModelScope.launch {
            container.settingsRepository.update { cur ->
                cur.copy(
                    apiHost = host,
                    hasAccepted18PlusUgc = cur.hasAccepted18PlusUgc || acceptedUgc
                )
            }
            _events.tryEmit("Image source switched to ${host.domain}")
        }
    }

    fun setLayoutMode(mode: FeedLayoutMode) = updateSettings { it.copy(layoutMode = mode) }
    fun setGridColumns(cols: Int) = updateSettings { it.copy(gridColumns = cols.coerceIn(1, 5)) }
    fun setThemeMode(mode: ThemeMode) = updateSettings { it.copy(themeMode = mode) }
    fun setVideoQuality(pref: VideoQualityPreference) = updateSettings { it.copy(videoQuality = pref) }
    fun setPerformanceMode(mode: PerformanceMode) = updateSettings { it.copy(performanceMode = mode) }

    fun checkForUpdatesNow(onUpdateFound: () -> Unit) {
        if (_isCheckingUpdates.value) return
        viewModelScope.launch {
            _isCheckingUpdates.value = true
            when (val res = container.appUpdateManager.checkForUpdate(force = true, skippedTag = "")) {
                is UpdateCheckResult.UpdateAvailable -> {
                    _isCheckingUpdates.value = false
                    onUpdateFound()
                }
                is UpdateCheckResult.UpToDate -> {
                    _isCheckingUpdates.value = false
                    _events.tryEmit("Yiffur is up to date!")
                }
                is UpdateCheckResult.Error -> {
                    _isCheckingUpdates.value = false
                    _events.tryEmit("Update check failed: ${res.message}")
                }
            }
        }
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                dirSize(container.imageCacheDir) +
                    dirSize(container.videoCacheDir) +
                    dirSize(File(container.appContext.cacheDir, "shared")) +
                    dirSize(container.appUpdateManager.updatesCacheDir)
            }
            _cacheSizeBytes.value = size
        }
    }

    fun clearImageCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                container.clearAllMediaCaches()
            }
            refreshCacheSize()
            _events.tryEmit("Image, video & update caches cleared")
        }
    }

    fun exportBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = container.backupRepository.exportToJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Could not open file for writing")
                }
            }.onSuccess {
                _events.tryEmit("Backup exported successfully!")
            }.onFailure { err ->
                _events.tryEmit("Export failed: ${err.message}")
            }
        }
    }

    fun importBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val raw = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: error("Could not read selected file")
                }
                container.backupRepository.importFromJson(raw).getOrThrow()
            }.onSuccess { summary ->
                _events.tryEmit(summary)
            }.onFailure { err ->
                _events.tryEmit("Import failed: ${err.message}")
            }
        }
    }

    private fun dirSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { dirSize(it) } ?: 0L
    }
}
