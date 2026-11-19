package com.ugf.yiffur1.ui.blacklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugf.yiffur1.data.api.ApiErrorParser
import com.ugf.yiffur1.data.local.BlacklistRuleEntity
import com.ugf.yiffur1.di.AppContainer
import com.ugf.yiffur1.domain.blacklist.BlacklistEngine
import com.ugf.yiffur1.domain.model.TagSuggestion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BlacklistUiState(
    val input: String = "",
    val suggestions: List<TagSuggestion> = emptyList(),
    val rules: List<BlacklistRuleEntity> = emptyList(),
    val isDisabledGlobally: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isSyncingAccount: Boolean = false
)

class BlacklistViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val dao = container.database.blacklistDao()
    private val _input = MutableStateFlow("")
    private val _suggestions = MutableStateFlow<List<TagSuggestion>>(emptyList())
    private val _isSyncing = MutableStateFlow(false)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private var acJob: Job? = null

    val uiState: StateFlow<BlacklistUiState> = combine(
        _input,
        _suggestions,
        dao.observeAll(),
        container.settingsRepository.settingsFlow,
        combine(container.credentialStore.credentialsFlow, _isSyncing) { creds, syncing -> (creds != null) to syncing }
    ) { input, suggestions, rules, settings, (loggedIn, syncing) ->
        BlacklistUiState(
            input = input,
            suggestions = suggestions,
            rules = rules,
            isDisabledGlobally = settings.disableBlacklist,
            isLoggedIn = loggedIn,
            isSyncingAccount = syncing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlacklistUiState())

    fun onInputChange(newText: String) {
        _input.value = newText
        acJob?.cancel()
        val lastToken = if (newText.endsWith(" ")) "" else newText.substringAfterLast(' ').trim()
        val clean = lastToken.removePrefix("-").removePrefix("~")
        if (clean.length < 2 || clean.contains(':')) {
            _suggestions.value = emptyList()
            return
        }
        acJob = viewModelScope.launch {
            delay(280L)
            _suggestions.value = runCatching { container.postRepository.autocompleteTags(clean) }.getOrDefault(emptyList())
        }
    }

    fun clearSuggestions() {
        _suggestions.value = emptyList()
    }

    fun addRule(rawRule: String = _input.value) {
        val lines = BlacklistEngine.parseLines(rawRule)
        if (lines.isEmpty()) {
            _events.tryEmit("Enter a tag or compound rule (e.g. gore or young -rating:s)")
            return
        }
        viewModelScope.launch {
            val existing = dao.getAllSnapshot().map { it.rule.lowercase() }.toSet()
            val toAdd = lines.filter { it.lowercase() !in existing }
            if (toAdd.isEmpty()) {
                _events.tryEmit("Already in blacklist")
            } else {
                dao.insertAll(toAdd.map { BlacklistRuleEntity(rule = it) })
                _input.value = ""
                _suggestions.value = emptyList()
                _events.tryEmit("Added ${toAdd.size} blacklist rule(s)")
            }
        }
    }

    fun toggleRule(rule: BlacklistRuleEntity) {
        viewModelScope.launch {
            dao.setEnabled(rule.id, !rule.enabled)
        }
    }

    fun deleteRule(rule: BlacklistRuleEntity) {
        viewModelScope.launch {
            dao.deleteById(rule.id)
        }
    }

    fun toggleGlobalDisable(disabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.update { it.copy(disableBlacklist = disabled) }
        }
    }

    fun syncFromE621Account() {
        viewModelScope.launch {
            _isSyncing.value = true
            runCatching { container.postRepository.getCurrentUser() }
                .onSuccess { profile ->
                    val parsed = BlacklistEngine.parseLines(profile.blacklistedTagsRaw)
                    if (parsed.isEmpty()) {
                        _events.tryEmit("No blacklist rules found on account.")
                    } else {
                        val existing = dao.getAllSnapshot().map { it.rule.lowercase() }.toSet()
                        val newRules = parsed
                            .filter { it.lowercase() !in existing }
                            .map { BlacklistRuleEntity(rule = it, enabled = true, fromAccountSync = true) }
                        if (newRules.isNotEmpty()) {
                            dao.insertAll(newRules)
                        }
                        val label = if (profile.isAuthenticated) "${profile.name}'s account" else "e621 defaults"
                        _events.tryEmit("Synced ${newRules.size} new rule(s) from $label (${parsed.size} total)")
                    }
                    _isSyncing.value = false
                }
                .onFailure { err ->
                    _isSyncing.value = false
                    _events.tryEmit(ApiErrorParser.toUserMessage(err))
                }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            dao.clearAll()
            _events.tryEmit("Blacklist cleared")
        }
    }
}
