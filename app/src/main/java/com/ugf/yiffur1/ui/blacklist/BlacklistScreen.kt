package com.ugf.yiffur1.ui.blacklist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ugf.yiffur1.domain.blacklist.BlacklistEngine
import com.ugf.yiffur1.domain.blacklist.CompoundOperator
import com.ugf.yiffur1.ui.theme.RatingExplicitColor
import com.ugf.yiffur1.ui.theme.RatingQuestionableColor
import com.ugf.yiffur1.ui.theme.color
import com.ugf.yiffur1.util.Formatters

private val QuickMetaTokens = listOf(
    "rating:e",
    "rating:q",
    "-rating:s",
    "type:webm",
    "type:gif",
    "score:<0"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BlacklistScreen(
    viewModel: BlacklistViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showCompoundConfirm by remember { mutableStateOf<Boolean?>(null) } // Boolean = useOr

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.compoundSelectMode) {
                            "Compound Rules (${state.selectedRuleIds.size} selected)"
                        } else {
                            "Blacklist (${state.rules.count { it.enabled }} active)"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.compoundSelectMode) {
                                viewModel.toggleCompoundSelectMode()
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (state.compoundSelectMode) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (state.rules.size >= 2) {
                        IconButton(onClick = viewModel::toggleCompoundSelectMode) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.CallMerge,
                                contentDescription = "Compound existing rules",
                                tint = if (state.compoundSelectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (state.rules.isNotEmpty() && !state.compoundSelectMode) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear all")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = state.compoundSelectMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { showCompoundConfirm = false },
                            enabled = state.selectedRuleIds.size >= 2,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.CallMerge, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Compound (AND)")
                        }
                        OutlinedButton(
                            onClick = { showCompoundConfirm = true },
                            enabled = state.selectedRuleIds.size >= 2,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Compound (~OR)")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Interactive Compound Rule Builder Card (hidden while multi-selecting existing rules)
            AnimatedVisibility(visible = !state.compoundSelectMode) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = if (state.editingRuleId != null) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (state.editingRuleId != null) "Editing Rule (Tap chips to compound)" else "Rule & Compound Builder",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (state.editingRuleId != null) {
                                TextButton(
                                    onClick = viewModel::cancelEditing,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }

                        // Operator selector for next appended tag
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CompoundOperator.entries.forEach { op ->
                                FilterChip(
                                    selected = state.selectedOperator == op,
                                    onClick = { viewModel.selectOperator(op) },
                                    label = {
                                        Text(
                                            when (op) {
                                                CompoundOperator.AND -> "Require (AND)"
                                                CompoundOperator.NOT -> "Except (-)"
                                                CompoundOperator.OR -> "Any (~OR)"
                                            },
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = state.input,
                                onValueChange = viewModel::onInputChange,
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Add tags to compound (e.g. young -rating:s)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { viewModel.saveRule() })
                            )
                            Button(
                                onClick = viewModel::saveRule,
                                enabled = state.input.isNotBlank(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (state.editingRuleId != null) Icons.Rounded.Check else Icons.Rounded.Add,
                                    contentDescription = "Save rule"
                                )
                            }
                        }

                        // Autocomplete Dropdown
                        if (state.suggestions.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)
                            ) {
                                LazyColumn {
                                    items(state.suggestions, key = { it.id }) { sug ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.applyAutocompleteSuggestion(sug.name) }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${state.selectedOperator.prefix}${sug.name}",
                                                color = sug.category.color(),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = Formatters.formatCompactCount(sug.postCount),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    }
                                }
                            }
                        }

                        // Quick Compound Metatags Strip
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(QuickMetaTokens) { meta ->
                                AssistChip(
                                    onClick = { viewModel.appendToken(meta) },
                                    label = { Text(meta, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        // Interactive Compound Token Chips + Live Plain-English Preview
                        val terms = state.parsedTerms
                        if (terms.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                terms.forEachIndexed { idx, term ->
                                    val chipTint = when (term.operator) {
                                        CompoundOperator.AND -> MaterialTheme.colorScheme.primary
                                        CompoundOperator.NOT -> RatingExplicitColor
                                        CompoundOperator.OR -> RatingQuestionableColor
                                    }
                                    InputChip(
                                        selected = true,
                                        onClick = { viewModel.cycleTermOperatorAt(idx) },
                                        label = {
                                            Text(
                                                text = "${term.operator.chipLabel}: ${term.term}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = chipTint,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "Remove token",
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clickable { viewModel.removeTermAt(idx) }
                                            )
                                        }
                                    )
                                }
                            }

                            if (state.liveDescription.isNotBlank()) {
                                Text(
                                    text = "${state.liveDescription} · Tap a chip to cycle AND / EXCEPT / ANY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Sync from e621 Account / Default Blacklist + Global Pause Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = viewModel::syncFromE621Account,
                    enabled = !state.isSyncingAccount,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isSyncingAccount) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.CloudDownload, null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (state.isLoggedIn) "Sync e621 Account" else "Import e621 Defaults",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Active", style = MaterialTheme.typography.labelMedium)
                        Switch(
                            checked = !state.isDisabledGlobally,
                            onCheckedChange = { enabled -> viewModel.toggleGlobalDisable(!enabled) }
                        )
                    }
                }
            }

            // Rules List
            if (state.rules.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No blacklist rules yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Build single or compound rules above, or tap \"Import e621 Defaults\" to load e621's standard filter list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.rules, key = { it.id }) { rule ->
                        val isCompound = remember(rule.rule) { BlacklistEngine.isCompoundRule(rule.rule) }
                        val explanation = remember(rule.rule) { BlacklistEngine.describeRule(rule.rule) }
                        val isSelectedForMerge = rule.id in state.selectedRuleIds
                        val isBeingEdited = state.editingRuleId == rule.id

                        Surface(
                            onClick = {
                                if (state.compoundSelectMode) {
                                    viewModel.toggleRuleSelected(rule.id)
                                } else {
                                    viewModel.startEditingRule(rule)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                isSelectedForMerge -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                isBeingEdited -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            border = if (isSelectedForMerge || isBeingEdited) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (state.compoundSelectMode) {
                                    Checkbox(
                                        checked = isSelectedForMerge,
                                        onCheckedChange = { viewModel.toggleRuleSelected(rule.id) }
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = rule.rule,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                            fontWeight = FontWeight.Medium,
                                            textDecoration = if (rule.enabled) TextDecoration.None else TextDecoration.LineThrough,
                                            color = if (rule.enabled) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isCompound) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                            ) {
                                                Text(
                                                    text = "COMPOUND",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (isCompound || rule.fromAccountSync) {
                                        Text(
                                            text = buildString {
                                                if (isCompound) append(explanation)
                                                if (isCompound && rule.fromAccountSync) append(" · ")
                                                if (rule.fromAccountSync) append("Synced from e621")
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (!state.compoundSelectMode) {
                                    IconButton(
                                        onClick = { viewModel.startEditingRule(rule) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Edit,
                                            contentDescription = "Edit or compound rule",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Switch(
                                        checked = rule.enabled,
                                        onCheckedChange = { viewModel.toggleRule(rule) }
                                    )
                                    IconButton(onClick = { viewModel.deleteRule(rule) }) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Delete rule",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirm Compounding Selected Rules Dialog
    showCompoundConfirm?.let { useOr ->
        val selectedRules = state.rules.filter { it.id in state.selectedRuleIds }.map { it.rule }
        val preview = remember(selectedRules, useOr) { BlacklistEngine.compoundRules(selectedRules, useOr = useOr) }
        val desc = remember(preview) { BlacklistEngine.describeRule(preview) }

        AlertDialog(
            onDismissRequest = { showCompoundConfirm = null },
            icon = { Icon(Icons.AutoMirrored.Rounded.CallMerge, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(if (useOr) "Compound into ~OR Rule?" else "Compound into AND Rule?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Replace the ${selectedRules.size} separate rules with this single compound rule, or keep both?",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.compoundSelectedRules(useOr = useOr, replaceOriginals = true)
                        showCompoundConfirm = null
                    }
                ) { Text("Replace Originals") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel.compoundSelectedRules(useOr = useOr, replaceOriginals = false)
                            showCompoundConfirm = null
                        }
                    ) { Text("Keep Both") }
                    TextButton(onClick = { showCompoundConfirm = null }) { Text("Cancel") }
                }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear entire blacklist?") },
            text = { Text("All ${state.rules.size} blacklist rules will be removed.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearAll()
                    showClearConfirm = false
                }) { Text("Clear All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
