package com.ugf.yiffur1.ui.browse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ugf.yiffur1.data.local.SavedSearchEntity
import com.ugf.yiffur1.domain.model.MediaFilter
import com.ugf.yiffur1.domain.model.PopularScale
import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.domain.model.SearchOrder
import com.ugf.yiffur1.ui.components.PostFeedGrid
import com.ugf.yiffur1.ui.components.SearchAutocompleteBar
import com.ugf.yiffur1.ui.components.SearchSortFilterSheet
import com.ugf.yiffur1.ui.theme.RatingExplicitColor
import com.ugf.yiffur1.ui.theme.RatingSafeColor
import com.ugf.yiffur1.util.Formatters
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    scrollSyncTargets: Map<String, Int>,
    onConsumeScrollSync: (String) -> Unit,
    onOpenPostViewer: (posts: List<Post>, startIndex: Int, feedKey: String) -> Unit,
    onOpenBlacklist: () -> Unit,
    onCopyLink: (Post) -> Unit,
    onShareLink: (Post) -> Unit,
    onOpenInBrowser: (Post) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val perfBudget by viewModel.performanceBudget.collectAsStateWithLifecycle()
    val searchInput = viewModel.searchInput
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showFilterSheet by remember { mutableStateOf(false) }
    var showQuickBrowseSheet by remember { mutableStateOf(false) }
    var showPageJumpDialog by remember { mutableStateOf(false) }
    var showClearTabsConfirm by remember { mutableStateOf(false) }
    var editingSavedSearch by remember { mutableStateOf<SavedSearchEntity?>(null) }
    var showAddSavedSearchDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val activeFilterCount = (if (state.selectedOrder != SearchOrder.RECENT) 1 else 0) +
        (if (state.selectedMediaFilter != MediaFilter.ALL) 1 else 0)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.settings.showQuickBrowseButton) {
                FloatingActionButton(
                    onClick = { showQuickBrowseSheet = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Rounded.Bookmarks, contentDescription = "Quick Browse Shortcuts")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // Search Bar Header
            SearchAutocompleteBar(
                query = searchInput,
                onQueryChange = viewModel::onSearchInputChange,
                onSubmit = viewModel::submitSearch,
                suggestions = suggestions,
                recentQueries = state.recentQueries,
                onClearSuggestions = viewModel::clearSuggestions,
                onOpenFilterSheet = { showFilterSheet = true },
                activeFilterCount = activeFilterCount,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )

            // Persistent Multi-Tab Strip
            if (state.settings.showSearchTabs && state.tabs.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(state.tabs, key = { it.id }) { tab ->
                            val isActive = tab.id == state.activeTab?.id
                            Surface(
                                onClick = { viewModel.selectTab(tab) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                border = if (isActive) {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                } else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = tab.title.ifBlank { "Recent" },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isActive) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isActive) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = "Refresh tab",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { viewModel.refreshActiveTab() }
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Close tab",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { viewModel.closeTab(tab) }
                                    )
                                }
                            }
                        }
                    }

                    if (state.tabs.size > 1) {
                        IconButton(
                            onClick = { showClearTabsConfirm = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ClearAll,
                                contentDescription = "Close all tabs",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Compact Status & Page Jump Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isSafeHost = state.settings.apiHost.isSafeOnly
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = (if (isSafeHost) RatingSafeColor else RatingExplicitColor).copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = if (isSafeHost) RatingSafeColor else RatingExplicitColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = state.settings.apiHost.domain,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSafeHost) RatingSafeColor else RatingExplicitColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (perfBudget.isAutoThrottled) {
                        Surface(
                            onClick = {
                                val reason = perfBudget.activeReason ?: "Optimizing for device load"
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Adaptive Engine: ${perfBudget.effectiveTier.label} ($reason)")
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = "Adaptive performance active",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = perfBudget.effectiveTier.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    val countText = state.totalCount?.let { count ->
                        val suffix = if (state.totalCountCapped) "+" else ""
                        "${Formatters.formatCompactCount(count)}$suffix results"
                    } ?: if (state.posts.isNotEmpty()) "${state.posts.size} loaded" else ""

                    if (countText.isNotEmpty()) {
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (state.activeTab?.isPopular == false) {
                    Surface(
                        onClick = { showPageJumpDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "Page ${state.currentPage}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Main Feed Content
            PullToRefreshBox(
                isRefreshing = state.isLoading && state.posts.isNotEmpty(),
                onRefresh = viewModel::refreshActiveTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    state.isLoading && state.posts.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Fetching posts from ${state.settings.apiHost.domain}…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    state.error != null && state.posts.isEmpty() -> {
                        Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = state.error.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(onClick = viewModel::refreshActiveTab) {
                                    Text("Retry")
                                }
                            }
                        }
                    }

                    state.allFilteredByBlacklist -> {
                        Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VisibilityOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "Results found, but all posts matched your blacklist or rating filters.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(onClick = onOpenBlacklist) {
                                    Text("Manage Blacklist")
                                }
                            }
                        }
                    }

                    state.posts.isEmpty() -> {
                        Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "No posts found",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Try different tags, check spelling, or clear active filters.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    else -> {
                        val feedKey = "browse:${state.activeTab?.id ?: 0L}"
                        PostFeedGrid(
                            posts = state.posts,
                            settings = state.settings,
                            budget = perfBudget.budget,
                            localFavoriteIds = state.localFavoriteIds,
                            isLoadingMore = state.isLoadingMore,
                            hasMore = state.hasMore,
                            onLoadMore = viewModel::loadNextPage,
                            onPostClick = { idx -> onOpenPostViewer(state.posts, idx, feedKey) },
                            onQuickFavorite = viewModel::quickToggleFavorite,
                            onDownload = viewModel::downloadPost,
                            onCopyLink = onCopyLink,
                            onShareLink = onShareLink,
                            onOpenInBrowser = onOpenInBrowser,
                            scrollToPostId = scrollSyncTargets[feedKey],
                            onScrollToPostConsumed = { onConsumeScrollSync(feedKey) },
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 88.dp)
                        )
                    }
                }
            }
        }
    }

    // Sort & Filter Bottom Sheet
    if (showFilterSheet) {
        SearchSortFilterSheet(
            selectedOrder = state.selectedOrder,
            selectedMediaFilter = state.selectedMediaFilter,
            onSelectOrder = viewModel::selectOrder,
            onSelectMediaFilter = viewModel::selectMediaFilter,
            onReset = viewModel::resetFilters,
            onApplyAndSearch = { viewModel.submitSearch(searchInput) },
            onDismiss = { showFilterSheet = false }
        )
    }

    // Quick Browse / Saved Searches Drawer Sheet
    if (showQuickBrowseSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQuickBrowseSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Quick Browse", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { showAddSavedSearchDialog = true }) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Shortcut")
                    }
                }

                // Permanent built-in shortcuts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickShortcutCard(
                        title = "Recent",
                        icon = Icons.Rounded.Schedule,
                        onClick = {
                            showQuickBrowseSheet = false
                            viewModel.openNewSearchTab(query = "", title = "Recent")
                        },
                        modifier = Modifier.weight(1f)
                    )
                    QuickShortcutCard(
                        title = "Trending",
                        icon = Icons.Rounded.Whatshot,
                        onClick = {
                            showQuickBrowseSheet = false
                            viewModel.openPopularTab(PopularScale.DAY)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    QuickShortcutCard(
                        title = "Random",
                        icon = Icons.Rounded.Casino,
                        onClick = {
                            showQuickBrowseSheet = false
                            viewModel.openNewSearchTab(query = "order:random", title = "Random")
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (searchInput.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            viewModel.addSavedSearch(
                                title = searchInput.take(28),
                                query = searchInput
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Bookmark, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save current search: \"${searchInput.take(24)}\"")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Saved Tag Shortcuts", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                if (state.savedSearches.isEmpty()) {
                    Text(
                        text = "No custom shortcuts yet. Tap \"New Shortcut\" to pin your favorite tag searches!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.savedSearches.forEach { saved ->
                        Surface(
                            onClick = {
                                showQuickBrowseSheet = false
                                viewModel.openNewSearchTab(query = saved.query, title = saved.title)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(saved.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        saved.query,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { editingSavedSearch = saved }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { viewModel.deleteSavedSearch(saved) }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Saved Search Dialog
    if (showAddSavedSearchDialog || editingSavedSearch != null) {
        val target = editingSavedSearch
        var titleText by remember(target) { mutableStateOf(target?.title ?: "") }
        var tagsText by remember(target) { mutableStateOf(target?.query ?: searchInput) }

        AlertDialog(
            onDismissRequest = {
                showAddSavedSearchDialog = false
                editingSavedSearch = null
            },
            title = { Text(if (target == null) "New Search Shortcut" else "Edit Shortcut") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        label = { Text("Button Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tagsText,
                        onValueChange = { tagsText = it },
                        label = { Text("Tags to Search") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (target == null) {
                            viewModel.addSavedSearch(titleText, tagsText)
                        } else {
                            viewModel.updateSavedSearch(target, titleText, tagsText)
                        }
                        showAddSavedSearchDialog = false
                        editingSavedSearch = null
                    },
                    enabled = titleText.isNotBlank() && tagsText.isNotBlank()
                ) {
                    Text(if (target == null) "Add" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddSavedSearchDialog = false
                    editingSavedSearch = null
                }) { Text("Cancel") }
            }
        )
    }

    // Jump to Page Dialog
    if (showPageJumpDialog) {
        var pageInput by remember { mutableStateOf(state.currentPage.toString()) }
        AlertDialog(
            onDismissRequest = { showPageJumpDialog = false },
            title = { Text("Jump to Page") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter a page number between 1 and 750.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pageInput,
                        onValueChange = { pageInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Page (1 – 750)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val page = pageInput.toIntOrNull()?.coerceIn(1, 750) ?: 1
                        viewModel.jumpToPage(page)
                        showPageJumpDialog = false
                    }
                ) { Text("Go") }
            },
            dismissButton = {
                TextButton(onClick = { showPageJumpDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearTabsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearTabsConfirm = false },
            title = { Text("Close all search tabs?") },
            text = { Text("This clears your current tab strip and returns to Recent posts.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.closeAllTabs()
                    showClearTabsConfirm = false
                }) { Text("Close All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearTabsConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun QuickShortcutCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
