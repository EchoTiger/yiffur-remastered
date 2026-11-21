package com.ugf.yiffur1.ui.favorites

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ugf.yiffur1.R
import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.ui.components.PostFeedGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    scrollSyncTargets: Map<String, Int>,
    onConsumeScrollSync: (String) -> Unit,
    onOpenPostViewer: (List<Post>, Int, String) -> Unit,
    onGoToSettingsLogin: () -> Unit,
    onCopyLink: (Post) -> Unit,
    onShareLink: (Post) -> Unit,
    onOpenInBrowser: (Post) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val perfBudget by viewModel.performanceBudget.collectAsStateWithLifecycle()
    val filterQuery = viewModel.filterQuery
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // Source Switcher: Local Yiffur Favorites vs Remote e621 Account Favorites
            if (!state.settings.disableLocalFavorites) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    SegmentedButton(
                        selected = state.source == FavoritesSource.LOCAL,
                        onClick = { viewModel.selectSource(FavoritesSource.LOCAL) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_yiffur_favbutton),
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color.Unspecified,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    ) {
                        Text("Local (${state.localCount})")
                    }
                    SegmentedButton(
                        selected = state.source == FavoritesSource.REMOTE_E621,
                        onClick = { viewModel.selectSource(FavoritesSource.REMOTE_E621) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_e6logo_favbutton),
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color.Unspecified,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    ) {
                        Text("e621 Account")
                    }
                }
            }

            // Filter / Search Box
            if (state.source == FavoritesSource.LOCAL || state.isLoggedIn) {
                OutlinedTextField(
                    value = filterQuery,
                    onValueChange = viewModel::onFilterQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    placeholder = {
                        Text(
                            if (state.source == FavoritesSource.LOCAL) {
                                "Filter local favorites by tags, artist, ID…"
                            } else {
                                "Search ${state.username}'s e621 favorites…"
                            }
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        Row {
                            if (filterQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    viewModel.onFilterQueryChange("")
                                    if (state.source == FavoritesSource.REMOTE_E621) viewModel.submitRemoteFilter()
                                }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                }
                            }
                            if (state.source == FavoritesSource.REMOTE_E621) {
                                IconButton(onClick = viewModel::refreshRemote) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                                }
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        viewModel.submitRemoteFilter()
                    })
                )
            }

            // Content Area
            when {
                state.source == FavoritesSource.REMOTE_E621 && !state.isLoggedIn -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Login,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text("Sign in to sync e621 favorites", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Connect your e621 username and API key in Settings to browse, search, and sync your account favorites.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(onClick = onGoToSettingsLogin) {
                                Text("Open Account Settings")
                            }
                        }
                    }
                }

                state.isLoading && state.posts.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.error != null && state.posts.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                            Button(onClick = viewModel::refreshRemote) { Text("Retry") }
                        }
                    }
                }

                state.posts.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FavoriteBorder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = when {
                                    filterQuery.isNotBlank() -> "No matching favorites"
                                    state.source == FavoritesSource.REMOTE_E621 -> "No e621 favorites found"
                                    else -> "No local favorites yet"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = when {
                                    filterQuery.isNotBlank() -> "Try clearing or simplifying your filter."
                                    state.source == FavoritesSource.REMOTE_E621 ->
                                        "No visible e621 favorites matched your active host (${state.settings.apiHost.domain}), rating filters, or blacklist."
                                    else -> "Long-press any post in Browse or tap the heart in the viewer to save it here."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            if (state.source == FavoritesSource.REMOTE_E621) {
                                Button(onClick = viewModel::refreshRemote) {
                                    Text("Refresh e621 Favorites")
                                }
                            }
                        }
                    }
                }

                else -> {
                    val feedKey = "favorites:${state.source.name}"
                    PullToRefreshBox(
                        isRefreshing = state.isLoading && state.posts.isNotEmpty(),
                        onRefresh = {
                            if (state.source == FavoritesSource.REMOTE_E621) viewModel.refreshRemote()
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        PostFeedGrid(
                            posts = state.posts,
                            settings = state.settings,
                            budget = perfBudget.budget,
                            localFavoriteIds = state.localFavoriteIds,
                            isLoadingMore = state.isLoadingMore,
                            hasMore = state.hasMoreRemote,
                            onLoadMore = viewModel::loadNextRemotePage,
                            onPostClick = { idx -> onOpenPostViewer(state.posts, idx, feedKey) },
                            onQuickFavorite = viewModel::quickToggleFavorite,
                            onDownload = viewModel::downloadPost,
                            onCopyLink = onCopyLink,
                            onShareLink = onShareLink,
                            onOpenInBrowser = onOpenInBrowser,
                            scrollToPostId = scrollSyncTargets[feedKey],
                            onScrollToPostConsumed = { onConsumeScrollSync(feedKey) },
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 24.dp)
                        )
                    }
                }
            }
        }
    }
}
