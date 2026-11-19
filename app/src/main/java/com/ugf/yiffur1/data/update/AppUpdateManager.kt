package com.ugf.yiffur1.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ugf.yiffur1.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubReleaseAssetDto> = emptyList()
)

@Serializable
data class GitHubReleaseAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0L,
    @SerialName("content_type") val contentType: String? = null
)

data class AppUpdateInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkUrl: String?,
    val apkName: String?,
    val apkSizeBytes: Long,
    val expectedSha256: String?
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(
        val percent: Int,
        val bytesRead: Long,
        val totalBytes: Long
    ) : UpdateDownloadState

    data class ReadyToInstall(val apkFile: File, val info: AppUpdateInfo) : UpdateDownloadState
    data class Error(val message: String) : UpdateDownloadState
}

/**
 * Pure Kotlin semantic version comparator & release asset selector (unit-tested without Android deps).
 */
object AppVersionComparator {

    private data class ParsedVersion(
        val parts: List<Int>,
        val isPreRelease: Boolean,
        val preReleaseSuffix: String
    )

    fun normalizeTag(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V")

    fun isNewer(remoteTag: String, currentVersion: String): Boolean {
        val remote = parse(remoteTag) ?: return false
        val current = parse(currentVersion) ?: return false

        val maxLen = maxOf(remote.parts.size, current.parts.size)
        for (i in 0 until maxLen) {
            val r = remote.parts.getOrElse(i) { 0 }
            val c = current.parts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }

        // Same numeric parts: stable release > prerelease (e.g. 2.2.0 > 2.2.0-beta.1)
        if (remote.isPreRelease != current.isPreRelease) {
            return !remote.isPreRelease && current.isPreRelease
        }
        return remote.preReleaseSuffix > current.preReleaseSuffix
    }

    private fun parse(raw: String): ParsedVersion? {
        val cleaned = normalizeTag(raw)
        if (cleaned.isEmpty()) return null
        val dashIdx = cleaned.indexOf('-')
        val numericPart = if (dashIdx >= 0) cleaned.substring(0, dashIdx) else cleaned
        val suffix = if (dashIdx >= 0) cleaned.substring(dashIdx + 1).lowercase() else ""
        val nums = numericPart.split('.').mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        if (nums.isEmpty()) return null
        return ParsedVersion(nums, dashIdx >= 0 && suffix.isNotEmpty(), suffix)
    }

    /**
     * Picks the best sideloadable `.apk` asset from a GitHub Release matching the installed package ID
     * (distinguishing legacy `com.ugf0.yiffur` vs standard `com.ugf.yiffur1`) and device CPU ABI.
     */
    fun pickBestApkAsset(
        assets: List<GitHubReleaseAssetDto>,
        packageName: String,
        supportedAbis: List<String> = emptyList()
    ): GitHubReleaseAssetDto? {
        val apks = assets.filter {
            it.name.endsWith(".apk", ignoreCase = true) && it.browserDownloadUrl.isNotBlank()
        }
        if (apks.isEmpty()) return null

        val isLegacyPkg = packageName == "com.ugf0.yiffur"
        val matchingPkg = apks.filter { asset ->
            val lower = asset.name.lowercase()
            val hasLegacyMarker = lower.contains("legacy") || lower.contains("ugf0")
            if (isLegacyPkg) hasLegacyMarker else !hasLegacyMarker
        }.ifEmpty { apks }

        return matchingPkg.maxByOrNull { asset ->
            val lower = asset.name.lowercase()
            var score = 0
            if (lower.contains("release")) score += 50
            if (!lower.contains("debug")) score += 30
            if (!lower.contains("unsigned")) score += 20
            if (lower.contains("universal")) score += 15
            val matchedAbi = supportedAbis.firstOrNull { abi -> lower.contains(abi.lowercase()) }
            if (matchedAbi != null) score += 25
            // Prefer universal/non-ABI-specific if none of our ABIs matched another ABI-specific apk
            val looksAbiSpecific = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86").any { lower.contains(it) }
            if (looksAbiSpecific && matchedAbi == null) score -= 100
            score
        }
    }

    /**
     * Extracts an optional 64-hex SHA-256 digest from GitHub release notes (e.g. `SHA-256: <hex>`).
     */
    fun extractSha256FromNotes(releaseNotes: String, assetName: String? = null): String? {
        if (releaseNotes.isBlank()) return null
        val lines = releaseNotes.lines()
        val hexRegex = Regex("""\b([a-fA-F0-9]{64})\b""")

        if (!assetName.isNullOrBlank()) {
            lines.firstOrNull { it.contains(assetName, ignoreCase = true) }?.let { line ->
                hexRegex.find(line)?.groupValues?.getOrNull(1)?.lowercase()?.let { return it }
            }
        }
        lines.firstOrNull { it.contains("sha256", ignoreCase = true) || it.contains("sha-256", ignoreCase = true) }
            ?.let { line ->
                hexRegex.find(line)?.groupValues?.getOrNull(1)?.lowercase()?.let { return it }
            }
        return null
    }
}

/**
 * Sovereign GitHub Releases self-updater:
 * - Polls GitHub Releases API (no third-party closed update SDKs)
 * - Streams APK with progress tracking & optional SHA-256 verification
 * - Launches Android's native Package Installer via FileProvider (API 24–35)
 */
class AppUpdateManager(
    context: Context,
    private val httpClient: OkHttpClient,
    private val json: Json
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val availableUpdate: StateFlow<AppUpdateInfo?> = _availableUpdate.asStateFlow()

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private val _lastCheckedMs = MutableStateFlow(prefs.getLong(KEY_LAST_CHECK_MS, 0L))
    val lastCheckedMs: StateFlow<Long> = _lastCheckedMs.asStateFlow()

    val updatesCacheDir: File
        get() = File(appContext.cacheDir, "updates")

    suspend fun checkForUpdate(
        force: Boolean = false,
        skippedTag: String = ""
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
        if (!force && lastCheck > 0L && (now - lastCheck) < AUTO_CHECK_THROTTLE_MS) {
            val cached = _availableUpdate.value
            return@withContext if (cached != null && cached.tagName != skippedTag) {
                UpdateCheckResult.UpdateAvailable(cached)
            } else {
                UpdateCheckResult.UpToDate
            }
        }

        val url = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("GitHub API returned HTTP ${response.code}")
                }
                val raw = response.body?.string().orEmpty()
                if (raw.isBlank()) throw IOException("Empty release response")
                json.decodeFromString<GitHubReleaseDto>(raw)
            }
        }.fold(
            onSuccess = { release ->
                prefs.edit().putLong(KEY_LAST_CHECK_MS, now).apply()
                _lastCheckedMs.value = now

                if (release.draft || release.tagName.isBlank()) {
                    _availableUpdate.value = null
                    return@fold UpdateCheckResult.UpToDate
                }

                val isNewer = AppVersionComparator.isNewer(release.tagName, BuildConfig.VERSION_NAME)
                if (!isNewer) {
                    _availableUpdate.value = null
                    return@fold UpdateCheckResult.UpToDate
                }

                val bestAsset = AppVersionComparator.pickBestApkAsset(
                    assets = release.assets,
                    packageName = appContext.packageName,
                    supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty()
                )
                val notes = release.body.orEmpty().trim()
                val info = AppUpdateInfo(
                    tagName = release.tagName,
                    versionName = AppVersionComparator.normalizeTag(release.tagName),
                    title = release.name?.ifBlank { null } ?: "Yiffur ${release.tagName}",
                    releaseNotes = notes,
                    htmlUrl = release.htmlUrl.ifBlank {
                        "https://github.com/${BuildConfig.GITHUB_REPO}/releases/tag/${release.tagName}"
                    },
                    apkUrl = bestAsset?.browserDownloadUrl,
                    apkName = bestAsset?.name,
                    apkSizeBytes = bestAsset?.size ?: 0L,
                    expectedSha256 = AppVersionComparator.extractSha256FromNotes(notes, bestAsset?.name)
                )

                if (!force && skippedTag.isNotBlank() && skippedTag.equals(info.tagName, ignoreCase = true)) {
                    return@fold UpdateCheckResult.UpToDate
                }

                _availableUpdate.value = info
                UpdateCheckResult.UpdateAvailable(info)
            },
            onFailure = { err ->
                UpdateCheckResult.Error(err.message ?: "Unable to check for updates")
            }
        )
    }

    suspend fun downloadUpdateApk(info: AppUpdateInfo): Result<File> = withContext(Dispatchers.IO) {
        val apkUrl = info.apkUrl
            ?: return@withContext Result.failure(IOException("This release does not attach an APK directly. Open GitHub to download."))

        _downloadState.value = UpdateDownloadState.Downloading(0, 0L, info.apkSizeBytes)

        runCatching {
            val dir = updatesCacheDir.apply { mkdirs() }
            // Prune older downloaded update APKs
            dir.listFiles()?.forEach { it.delete() }

            val safeName = (info.apkName ?: "Yiffur-${info.versionName}.apk")
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(dir, safeName)

            val request = Request.Builder().url(apkUrl).build()
            val digest = MessageDigest.getInstance("SHA-256")

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Download failed (HTTP ${response.code})")
                }
                val body = response.body ?: throw IOException("Empty download response")
                val totalBytes = body.contentLength().takeIf { it > 0L } ?: info.apkSizeBytes

                var downloaded = 0L
                var lastReportedPercent = -1
                val buffer = ByteArray(16 * 1024)

                body.byteStream().use { input ->
                    targetFile.outputStream().use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read

                            val pct = if (totalBytes > 0L) {
                                ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                -1
                            }
                            if (pct != lastReportedPercent) {
                                lastReportedPercent = pct
                                _downloadState.value = UpdateDownloadState.Downloading(pct, downloaded, totalBytes)
                            }
                        }
                    }
                }
            }

            if (!info.expectedSha256.isNullOrBlank()) {
                val actualHex = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualHex.equals(info.expectedSha256, ignoreCase = true)) {
                    targetFile.delete()
                    throw IOException("APK checksum verification failed! Download aborted for safety.")
                }
            }

            _downloadState.value = UpdateDownloadState.ReadyToInstall(targetFile, info)
            targetFile
        }.onFailure { err ->
            _downloadState.value = UpdateDownloadState.Error(err.message ?: "APK download failed")
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { appContext.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        } else {
            true
        }
    }

    fun createUnknownSourcesPermissionIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createInstallApkIntent(apkFile: File): Intent {
        val contentUri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun dismissBanner() {
        _availableUpdate.value = null
    }

    fun resetDownloadState() {
        _downloadState.value = UpdateDownloadState.Idle
    }

    companion object {
        private const val PREFS_NAME = "yiffur_update_prefs"
        private const val KEY_LAST_CHECK_MS = "last_check_ms"
        private const val AUTO_CHECK_THROTTLE_MS = 6L * 60L * 60L * 1000L // 6 hours
    }
}
