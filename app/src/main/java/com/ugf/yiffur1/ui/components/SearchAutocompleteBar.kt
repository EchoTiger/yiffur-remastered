package com.ugf.yiffur1.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ugf.yiffur1.domain.model.MediaFilter
import com.ugf.yiffur1.domain.model.SearchOrder
import com.ugf.yiffur1.domain.model.TagSuggestion
import com.ugf.yiffur1.ui.theme.color
import com.ugf.yiffur1.util.Formatters

/**
 * Live e621 tag search bar with real-time `/tags/autocomplete.json` dropdown,
 * recent query history, and Sort/Filter sheet trigger.
 */
@Composable
fun SearchAutocompleteBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    suggestions: List<TagSuggestion>,
    recentQueries: List<String>,
    onClearSuggestions: () -> Unit,
    onOpenFilterSheet: () -> Unit,
    activeFilterCount: Int,
    modifier: Modifier = Modifier,
    placeholder: String = "Search tags (e.g. fox order:score)…"
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    // Keep TextFieldValue synchronously in Compose state so fast IME typing never loses cursor position,
    // while programmatic query updates (autocomplete taps, tab switches, filter chips) anchor cursor cleanly at the end.
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = query, selection = TextRange(query.length)))
    }
    if (textFieldValue.text != query) {
        textFieldValue = TextFieldValue(text = query, selection = TextRange(query.length))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { updated ->
                textFieldValue = updated
                if (updated.text != query) {
                    onQueryChange(updated.text)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    isFocused = state.isFocused
                    if (!state.isFocused) onClearSuggestions()
                },
            placeholder = {
                Text(
                    text = placeholder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (textFieldValue.text.isNotEmpty()) {
                        IconButton(onClick = {
                            textFieldValue = TextFieldValue(text = "", selection = TextRange.Zero)
                            onQueryChange("")
                        }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(onClick = onOpenFilterSheet) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Sort and Filter",
                            tint = if (activeFilterCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                    onClearSuggestions()
                    onSubmit(textFieldValue.text)
                }
            )
        )

        // Autocomplete suggestions dropdown
        if (isFocused && suggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .heightIn(max = 260.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                LazyColumn {
                    items(suggestions, key = { it.id }) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val updated = applyAutocompleteToken(textFieldValue.text, suggestion.name)
                                    textFieldValue = TextFieldValue(text = updated, selection = TextRange(updated.length))
                                    onQueryChange(updated)
                                    onClearSuggestions()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = suggestion.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = suggestion.category.color(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!suggestion.antecedentName.isNullOrBlank()) {
                                        Text(
                                            text = "← ${suggestion.antecedentName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            Text(
                                text = Formatters.formatCompactCount(suggestion.postCount),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        } else if (isFocused && textFieldValue.text.isBlank() && recentQueries.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .heightIn(max = 200.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                LazyColumn {
                    items(recentQueries.take(6)) { recent ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    focusManager.clearFocus()
                                    textFieldValue = TextFieldValue(text = recent, selection = TextRange(recent.length))
                                    onQueryChange(recent)
                                    onSubmit(recent)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = recent,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

fun applyAutocompleteToken(currentQuery: String, selectedTag: String): String {
    if (currentQuery.isBlank()) return "$selectedTag "
    val endsWithSpace = currentQuery.endsWith(" ")
    if (endsWithSpace) return "$currentQuery$selectedTag "

    val lastSpace = currentQuery.lastIndexOf(' ')
    val prefixPart = if (lastSpace >= 0) currentQuery.substring(0, lastSpace + 1) else ""
    val activeToken = if (lastSpace >= 0) currentQuery.substring(lastSpace + 1) else currentQuery
    val modifierPrefix = when {
        activeToken.startsWith("-") -> "-"
        activeToken.startsWith("~") -> "~"
        else -> ""
    }
    return "$prefixPart$modifierPrefix$selectedTag "
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchSortFilterSheet(
    selectedOrder: SearchOrder,
    selectedMediaFilter: MediaFilter,
    onSelectOrder: (SearchOrder) -> Unit,
    onSelectMediaFilter: (MediaFilter) -> Unit,
    onReset: () -> Unit,
    onApplyAndSearch: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sort & Media Filter", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onReset) { Text("Reset") }
            }

            Text("Order By", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SearchOrder.entries.forEach { order ->
                    FilterChip(
                        selected = selectedOrder == order,
                        onClick = { onSelectOrder(order) },
                        label = { Text(order.label) }
                    )
                }
            }

            Text("Media Type", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MediaFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedMediaFilter == filter,
                        onClick = { onSelectMediaFilter(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    onApplyAndSearch()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Apply & Search")
            }
        }
    }
}
