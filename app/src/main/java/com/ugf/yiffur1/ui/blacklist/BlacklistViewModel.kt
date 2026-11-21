package com.ugf.yiffur1.ui.blacklist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugf.yiffur1.data.api.ApiErrorParser
import com.ugf.yiffur1.data.local.BlacklistRuleEntity
import com.ugf.yiffur1.di.AppContainer
import com.ugf.yiffur1.domain.blacklist.BlacklistEngine
import com.ugf.yiffur1.domain.blacklist.BlacklistTerm
import com.ugf.yiffur1.domain.blacklist.CompoundOperator
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BlacklistUiState(
    val selectedOperator: CompoundOperator = CompoundOperator.AND,
    val editingRuleId: Long? = null,
    val compoundSelectMode: Boolean = false,
    val selectedRuleIds: Set<Long> = emptySet(),
    val suggestions: List<TagSuggestion> = emptyList(),
    val rules: List<BlacklistRuleEntity> = emptyList(),
    val isDisabledGlobally: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isSyncingAccount: Boolean = false
)

class BlacklistViewModel(
    private val container: AppContainer
) : ViewModel() {

    private data class EditorDraft(
        val selectedOperator: CompoundOperator = CompoundOperator.AND,
        val editingRuleId: Long? = null,
        val compoundSelectMode: Boolean = false,
        val selectedRuleIds: Set<Long> = emptySet()
    )

    /**
     * Synchronous Compose Snapshot input state preventing async StateFlow cursor displacement.
     */
    var inputText by mutableStateOf("")
        private set

    private val dao = container.database.blacklistDao()
    private val _draft = MutableStateFlow(EditorDraft())
    private val _suggestions = MutableStateFlow<List<TagSuggestion>>(emptyList())
    private val _isSyncing = MutableStateFlow(false)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private var acJob: Job? = null

    val uiState: StateFlow<BlacklistUiState> = combine(
        _draft,
        _suggestions,
        dao.observeAll(),
        container.settingsRepository.settingsFlow,
        combine(container.credentialStore.credentialsFlow, _isSyncing) { creds, syncing -> (creds != null) to syncing }
    ) { draft, suggestions, rules, settings, (loggedIn, syncing) ->
        BlacklistUiState(
            selectedOperator = draft.selectedOperator,
            editingRuleId = draft.editingRuleId,
            compoundSelectMode = draft.compoundSelectMode,
            selectedRuleIds = draft.selectedRuleIds,
            suggestions = suggestions,
            rules = rules,
            isDisabledGlobally = settings.disableBlacklist,
            isLoggedIn = loggedIn,
            isSyncingAccount = syncing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlacklistUiState())

    fun onInputChange(newText: String) {
        inputText = newText
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

    fun selectOperator(op: CompoundOperator) {
        _draft.update { it.copy(selectedOperator = op) }
    }

    /**
     * Appends a tag or metatag to the active compound rule builder with the chosen operator.
     */
    fun appendToken(rawToken: String, overrideOperator: CompoundOperator? = null) {
        val cleaned = rawToken.trim()
        if (cleaned.isEmpty()) return

        val currentOp = _draft.value.selectedOperator
        val terms = BlacklistEngine.parseTerms(inputText).toMutableList()
        val explicitOp = when {
            cleaned.startsWith("-") && cleaned.length > 1 -> CompoundOperator.NOT
            cleaned.startsWith("~") && cleaned.length > 1 -> CompoundOperator.OR
            else -> overrideOperator ?: currentOp
        }
        val bare = cleaned.removePrefix("-").removePrefix("~")
        val newTerm = BlacklistTerm(explicitOp, bare)
        val existingIdx = terms.indexOfFirst { it.term.equals(bare, ignoreCase = true) }
        if (existingIdx >= 0) {
            terms[existingIdx] = newTerm
        } else {
            terms.add(newTerm)
        }
        inputText = BlacklistEngine.formatTerms(terms) + " "
        _suggestions.value = emptyList()
    }

    /**
     * Replaces the trailing partial word with an autocompleted tag using the selected operator.
     */
    fun applyAutocompleteSuggestion(tagName: String) {
        val raw = inputText
        val lastSpace = raw.lastIndexOf(' ')
        val prefix = if (lastSpace >= 0) raw.substring(0, lastSpace + 1) else ""
        val trailing = if (lastSpace >= 0) raw.substring(lastSpace + 1) else raw
        val opPrefix = when {
            trailing.startsWith("-") -> "-"
            trailing.startsWith("~") -> "~"
            else -> _draft.value.selectedOperator.prefix
        }
        inputText = "$prefix$opPrefix$tagName "
        _suggestions.value = emptyList()
    }

    fun cycleTermOperatorAt(index: Int) {
        val terms = BlacklistEngine.parseTerms(inputText).toMutableList()
        if (index !in terms.indices) return
        val nextOp = when (terms[index].operator) {
            CompoundOperator.AND -> CompoundOperator.NOT
            CompoundOperator.NOT -> CompoundOperator.OR
            CompoundOperator.OR -> CompoundOperator.AND
        }
        terms[index] = terms[index].copy(operator = nextOp)
        inputText = BlacklistEngine.formatTerms(terms)
    }

    fun removeTermAt(index: Int) {
        val terms = BlacklistEngine.parseTerms(inputText).toMutableList()
        if (index !in terms.indices) return
        terms.removeAt(index)
        inputText = BlacklistEngine.formatTerms(terms)
    }

    fun startEditingRule(rule: BlacklistRuleEntity) {
        inputText = rule.rule
        _draft.update {
            it.copy(
                editingRuleId = rule.id,
                compoundSelectMode = false,
                selectedRuleIds = emptySet()
            )
        }
        _suggestions.value = emptyList()
    }

    fun cancelEditing() {
        inputText = ""
        _draft.update { it.copy(editingRuleId = null) }
        _suggestions.value = emptyList()
    }

    fun saveRule() {
        val draft = _draft.value
        val lines = BlacklistEngine.parseLines(inputText)
        if (lines.isEmpty()) {
            _events.tryEmit("Enter a tag or compound rule (e.g. gore or young -rating:s)")
            return
        }

        viewModelScope.launch {
            val editId = draft.editingRuleId
            if (editId != null) {
                val formatted = BlacklistEngine.formatTerms(BlacklistEngine.parseTerms(lines.first()))
                if (formatted.isNotBlank()) {
                    dao.updateRuleText(editId, formatted)
                    inputText = ""
                    _draft.update { it.copy(editingRuleId = null) }
                    _suggestions.value = emptyList()
                    _events.tryEmit("Updated blacklist rule")
                }
            } else {
                val existing = dao.getAllSnapshot().map { it.rule.lowercase() }.toSet()
                val normalized = lines
                    .map { BlacklistEngine.formatTerms(BlacklistEngine.parseTerms(it)) }
                    .filter { it.isNotBlank() && it.lowercase() !in existing }
                    .distinctBy { it.lowercase() }

                if (normalized.isEmpty()) {
                    _events.tryEmit("Rule already in blacklist")
                } else {
                    dao.insertAll(normalized.map { BlacklistRuleEntity(rule = it) })
                    inputText = ""
                    _draft.update { it.copy(editingRuleId = null) }
                    _suggestions.value = emptyList()
                    val kind = if (normalized.size == 1 && BlacklistEngine.isCompoundRule(normalized.first())) {
                        "Added compound blacklist rule"
                    } else {
                        "Added ${normalized.size} blacklist rule(s)"
                    }
                    _events.tryEmit(kind)
                }
            }
        }
    }

    // ==================== Multi-Rule Compounding ====================

    fun toggleCompoundSelectMode() {
        _draft.update { cur ->
            cur.copy(
                compoundSelectMode = !cur.compoundSelectMode,
                selectedRuleIds = emptySet(),
                editingRuleId = null
            )
        }
    }

    fun toggleRuleSelected(ruleId: Long) {
        _draft.update { cur ->
            val next = if (ruleId in cur.selectedRuleIds) {
                cur.selectedRuleIds - ruleId
            } else {
                cur.selectedRuleIds + ruleId
            }
            cur.copy(selectedRuleIds = next)
        }
    }

    fun compoundSelectedRules(useOr: Boolean, replaceOriginals: Boolean) {
        val selectedIds = _draft.value.selectedRuleIds
        if (selectedIds.size < 2) {
            _events.tryEmit("Select at least 2 rules to compound together")
            return
        }

        viewModelScope.launch {
            val allRules = dao.getAllSnapshot()
            val chosen = allRules.filter { it.id in selectedIds }
            if (chosen.size < 2) return@launch

            val compounded = BlacklistEngine.compoundRules(chosen.map { it.rule }, useOr = useOr)
            if (compounded.isBlank()) return@launch

            val existing = allRules.map { it.rule.lowercase() }.toSet()
            if (replaceOriginals) {
                dao.deleteByIds(chosen.map { it.id })
            }
            if (compounded.lowercase() !in existing || replaceOriginals) {
                dao.insert(BlacklistRuleEntity(rule = compounded, enabled = true))
            }

            _draft.update { it.copy(compoundSelectMode = false, selectedRuleIds = emptySet()) }
            val modeLabel = if (useOr) "OR (~)" else "AND"
            _events.tryEmit("Compounded ${chosen.size} rules into $modeLabel rule: $compounded")
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
            if (_draft.value.editingRuleId == rule.id) {
                inputText = ""
                _draft.update { it.copy(editingRuleId = null) }
            }
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
            inputText = ""
            _draft.update { it.copy(editingRuleId = null, compoundSelectMode = false, selectedRuleIds = emptySet()) }
            _events.tryEmit("Blacklist cleared")
        }
    }
}
