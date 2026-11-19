package com.ugf.yiffur1.ui.explore

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ugf.yiffur1.domain.model.Pool
import com.ugf.yiffur1.domain.model.PopularScale
import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.domain.model.PostSet
import com.ugf.yiffur1.ui.components.PostFeedGrid
import com.ugf.yiffur1.util.Formatters
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

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
            // Section Switcher
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                ExploreSection.entries.forEachIndexed { idx, section ->
                    SegmentedButton(
                        selected = state.section == section,
                        onClick = { viewModel.selectSection(section) },
                        shape = SegmentedButtonDefaults.itemShape(idx, ExploreSection.entries.size)
                    ) {
                        Text(section.label)
                    }
                }
            }

            when (state.section) {
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
}
