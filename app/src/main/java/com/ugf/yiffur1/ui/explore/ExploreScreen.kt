package com.ugf.yiffur1.ui.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ugf.yiffur1.domain.model.Pool
import com.ugf.yiffur1.domain.model.PopularScale
import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.domain.model.PostSet
import com.ugf.yiffur1.domain.recommend.DiscoveryMixMode
import com.ugf.yiffur1.ui.components.PostFeedGrid
import com.ugf.yiffur1.ui.theme.color
import com.ugf.yiffur1.util.Formatters
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onOpenPosts: (List<Post>, Int) -> Unit,
    onOpenPool: (Pool) -> Unit,
    onOpenPostSet: (PostSet) -> Unit,
    onCopyLink: (Post) -> Unit,
    onShareLink: (Post) -> Unit,
    onOpenInBrowser: (Post) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val perfState by viewModel.performanceState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    var showTasteSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!state.isFetchingRandom) {
                        viewModel.fetchRandomPost { randomPost ->
                            onOpenPosts(listOf(randomPost), 0)
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                icon = {
                    if (state.isFetchingRandom) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Casino, contentDescription = null)
                    }
                },
                text = { Text("Random") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // Section Switcher: For You | Popular | Pools | Sets
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                ExploreSection.entries.forEachIndexed { idx, section ->
                    SegmentedButton(
                        selected = state.section == section,
                        onClick = { viewModel.selectSection(section) },
                        shape = SegmentedButtonDefaults.itemShape(idx, ExploreSection.entries.size),
                        icon = {}
                    ) {
                        Text(
                            text = section.label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            when (state.section) {
                ExploreSection.FOR_YOU -> {
                    // Mix Mode Chips + Taste Tuner + Shuffle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(DiscoveryMixMode.entries) { mode ->
                                FilterChip(
                                    selected = state.mixMode == mode,
                                    onClick = { viewModel.selectMixMode(mode) },
                                    label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showTasteSheet = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Tune,
                                    contentDescription = "Your Taste Profile",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = viewModel::shuffleForYou,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Shuffle,
                                    contentDescription = "Shuffle Feed",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    // Dynamic Taste Profile Chips Strip (tap a chip to focus For You on that preference!)
                    if (state.tasteProfile.isPersonalized) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (state.focusedTasteTag != null) {
                                item(key = "clear_focus") {
                                    InputChip(
                                        selected = true,
                                        onClick = { viewModel.focusTasteTag(null) },
                                        label = {
                                            Text(
                                                "All Taste",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        trailingIcon = {
                                            Icon(Icons.Rounded.Close, null, modifier = Modifier.size(14.dp))
                                        }
                                    )
                                }
                            }
                            items(state.tasteProfile.topTags.take(10), key = { it.tag }) { tasteTag ->
                                val isFocused = state.focusedTasteTag.equals(tasteTag.tag, ignoreCase = true)
                                FilterChip(
                                    selected = isFocused,
                                    onClick = { viewModel.focusTasteTag(tasteTag.tag) },
                                    label = {
                                        Text(
                                            text = tasteTag.tag.replace('_', ' '),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isFocused) {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            } else {
                                                tasteTag.categoryHint?.color() ?: MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Search, browse, or favorite posts to automatically personalize this discovery feed.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Personalized Discovery Grid
                    PullToRefreshBox(
                        isRefreshing = state.isLoadingForYou && state.forYouPosts.isNotEmpty(),
                        onRefresh = viewModel::shuffleForYou,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when {
                            state.isLoadingForYou && state.forYouPosts.isEmpty() -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator()
                                        Text(
                                            text = if (state.tasteProfile.isPersonalized) {
                                                "Curating discoveries from your tastes…"
                                            } else {
                                                "Finding fresh artwork on ${state.settings.apiHost.domain}…"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            state.forYouError != null && state.forYouPosts.isEmpty() -> {
                                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            state.forYouError.orEmpty(),
                                            color = MaterialTheme.colorScheme.error,
                                            textAlign = TextAlign.Center
                                        )
                                        Button(onClick = viewModel::shuffleForYou) { Text("Retry") }
                                    }
                                }
                            }

                            state.forYouPosts.isEmpty() -> {
                                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text("No fresh matches in this batch", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = if (state.mixMode == DiscoveryMixMode.UNSEEN_ONLY) {
                                                "You've seen everything in this slice—shuffle again or switch to Balanced Mix."
                                            } else {
                                                "Try shuffling for another discovery blend or check your rating/blacklist filters."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Button(onClick = viewModel::shuffleForYou) {
                                            Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Shuffle Again")
                                        }
                                    }
                                }
                            }

                            else -> {
                                PostFeedGrid(
                                    posts = state.forYouPosts,
                                    settings = state.settings,
                                    budget = perfState.budget,
                                    localFavoriteIds = state.localFavoriteIds,
                                    isLoadingMore = state.isLoadingMoreForYou,
                                    hasMore = true,
                                    onLoadMore = viewModel::loadMoreForYou,
                                    onPostClick = { idx -> onOpenPosts(state.forYouPosts, idx) },
                                    onQuickFavorite = viewModel::quickToggleFavorite,
                                    onDownload = viewModel::downloadPost,
                                    onCopyLink = onCopyLink,
                                    onShareLink = onShareLink,
                                    onOpenInBrowser = onOpenInBrowser,
                                    onNotInterested = viewModel::markNotInterested,
                                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 92.dp)
                                )
                            }
                        }
                    }
                }

                ExploreSection.POPULAR -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PopularScale.entries.forEach { scale ->
                                FilterChip(
                                    selected = state.popularScale == scale,
                                    onClick = { viewModel.selectPopularScale(scale) },
                                    label = { Text(scale.label) }
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.stepPopularDate(-1) }) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous")
                            }
                            Text(
                                text = state.popularDate.format(DateTimeFormatter.ofPattern("MMM d")),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(
                                onClick = { viewModel.stepPopularDate(1) },
                                enabled = state.popularDate.isBefore(LocalDate.now())
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next")
                            }
                        }
                    }

                    when {
                        state.isLoadingPopular && state.popularPosts.isEmpty() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        state.popularError != null && state.popularPosts.isEmpty() -> {
                            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(state.popularError.orEmpty(), color = MaterialTheme.colorScheme.error)
                                    Button(onClick = viewModel::loadPopular) { Text("Retry") }
                                }
                            }
                        }
                        else -> {
                            PostFeedGrid(
                                posts = state.popularPosts,
                                settings = state.settings,
                                budget = perfState.budget,
                                localFavoriteIds = state.localFavoriteIds,
                                isLoadingMore = false,
                                hasMore = false,
                                onLoadMore = {},
                                onPostClick = { idx -> onOpenPosts(state.popularPosts, idx) },
                                onQuickFavorite = viewModel::quickToggleFavorite,
                                onDownload = viewModel::downloadPost,
                                onCopyLink = onCopyLink,
                                onShareLink = onShareLink,
                                onOpenInBrowser = onOpenInBrowser,
                                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 92.dp)
                            )
                        }
                    }
                }

                ExploreSection.POOLS -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = state.poolQuery,
                            onValueChange = viewModel::onPoolQueryChange,
                            placeholder = { Text("Search comics & pools…") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                viewModel.loadPools(reset = true)
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        )
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = state.poolCategoryFilter == null,
                                onClick = { viewModel.selectPoolCategory(null) },
                                label = { Text("All") }
                            )
                            FilterChip(
                                selected = state.poolCategoryFilter == "series",
                                onClick = { viewModel.selectPoolCategory("series") },
                                label = { Text("Series / Comics") }
                            )
                            FilterChip(
                                selected = state.poolCategoryFilter == "collection",
                                onClick = { viewModel.selectPoolCategory("collection") },
                                label = { Text("Collections") }
                            )
                        }

                        if (state.isLoadingPools && state.pools.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (state.poolsError != null && state.pools.isEmpty()) {
                            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(state.poolsError.orEmpty(), color = MaterialTheme.colorScheme.error)
                                    Button(onClick = { viewModel.loadPools(reset = true) }) { Text("Retry") }
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 92.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.pools, key = { it.id }) { pool ->
                                    Surface(
                                        onClick = { onOpenPool(pool) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.CollectionsBookmark,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(
                                                        text = pool.displayName,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = "${pool.postCount} posts",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "${pool.category.replaceFirstChar { it.uppercase() }} · by ${pool.creatorName} · updated ${Formatters.formatRelativeTime(pool.updatedAt)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (pool.description.isNotBlank()) {
                                                Text(
                                                    text = pool.description.replace(Regex("""\[/?[^\]]+\]"""), "").trim(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                                item {
                                    if (state.pools.isNotEmpty()) {
                                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                            if (state.isLoadingPools) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                            } else {
                                                Button(onClick = { viewModel.loadPools(reset = false) }) {
                                                    Text("Load More Pools")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ExploreSection.SETS -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = state.setQuery,
                            onValueChange = viewModel::onSetQueryChange,
                            placeholder = { Text("Search community sets…") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                viewModel.loadPostSets(reset = true)
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        Spacer(Modifier.height(4.dp))

                        if (state.isLoadingSets && state.postSets.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (state.setsError != null && state.postSets.isEmpty()) {
                            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(state.setsError.orEmpty(), color = MaterialTheme.colorScheme.error)
                                    Button(onClick = { viewModel.loadPostSets(reset = true) }) { Text("Retry") }
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 92.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.postSets, key = { it.id }) { postSet ->
                                    Surface(
                                        onClick = { onOpenPostSet(postSet) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.FolderSpecial,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(
                                                        text = postSet.name,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        text = "${postSet.postCount} posts",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "set:${postSet.shortname} · updated ${Formatters.formatRelativeTime(postSet.updatedAt)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (postSet.description.isNotBlank()) {
                                                Text(
                                                    text = postSet.description.replace(Regex("""\[/?[^\]]+\]"""), "").trim(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                                item {
                                    if (state.postSets.isNotEmpty()) {
                                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                            if (state.isLoadingSets) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                            } else {
                                                Button(onClick = { viewModel.loadPostSets(reset = false) }) {
                                                    Text("Load More Sets")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // On-Device Taste Profile & Tuning Bottom Sheet
    if (showTasteSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTasteSheet = false },
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Your On-Device Taste Profile", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Built 100% locally from ${state.tasteProfile.signalCount} favorites & search signals · ${state.seenPostsCount} viewed posts tracked",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = "Discovery Mode",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = state.mixMode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Top Detected Preferences (tap × to mute from For You)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (state.tasteProfile.topTags.isEmpty()) {
                    Text(
                        text = "No signals yet—favorite a few posts or search tags and your taste profile will populate here automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.tasteProfile.topTags.take(20).forEach { tag ->
                            InputChip(
                                selected = state.focusedTasteTag.equals(tag.tag, ignoreCase = true),
                                onClick = {
                                    viewModel.focusTasteTag(tag.tag)
                                    showTasteSheet = false
                                },
                                label = {
                                    Text(
                                        text = "${tag.tag.replace('_', ' ')} · ${tag.sourceLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tag.categoryHint?.color() ?: MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Rounded.VisibilityOff,
                                        contentDescription = "Mute from For You",
                                        modifier = Modifier
                                            .size(15.dp)
                                            .clickable { viewModel.muteTasteTag(tag.tag) }
                                    )
                                }
                            )
                        }
                    }
                }

                if (state.mutedTasteTags.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "Muted from For You (tap to restore)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.mutedTasteTags.forEach { muted ->
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.unmuteTasteTag(muted) },
                                label = { Text(muted.replace('_', ' '), style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.clearSeenHistory()
                            showTasteSheet = false
                        },
                        enabled = state.seenPostsCount > 0,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.History, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset Seen")
                    }
                    Button(
                        onClick = {
                            viewModel.shuffleForYou()
                            showTasteSheet = false
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle Feed")
                    }
                }
            }
        }
    }
}
