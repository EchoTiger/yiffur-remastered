package com.ugf.yiffur1.ui.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.domain.perf.PerformanceTier
import com.ugf.yiffur1.ui.components.DTextView
import com.ugf.yiffur1.ui.components.PostFeedGrid

/**
 * Remastered Pool / Comic Reader, Parent-Children Family Viewer, and Post Set Viewer
 * (replaces legacy ChildPostViewer.kt).
 * Supports both Grid browsing and continuous vertical Comic Strip reading mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    viewModel: ViewerViewModel,
    onBack: () -> Unit,
    onOpenPostViewer: (List<Post>, Int) -> Unit,
    onAddToBrowse: (String) -> Unit,
    onCopyLink: (Post) -> Unit,
    onShareLink: (Post) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val state by viewModel.collectionState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val perfState by viewModel.performanceState.collectAsStateWithLifecycle()
    val localFavIds by viewModel.localFavoriteIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val placeholderPainter = remember(placeholderColor) { ColorPainter(placeholderColor) }

    var comicStripMode by remember(state.title, state.isComicSeries) {
        mutableStateOf(state.isComicSeries)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.title.ifBlank { "Collection" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.subtitle.isNotBlank()) {
                            Text(
                                text = state.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.posts.isNotEmpty()) {
                        IconButton(onClick = { comicStripMode = !comicStripMode }) {
                            Icon(
                                imageVector = if (comicStripMode) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = if (comicStripMode) "Switch to Grid" else "Switch to Comic Strip Reader",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = viewModel::reverseCollectionOrder) {
                            Icon(Icons.Rounded.SwapVert, contentDescription = "Reverse order")
                        }
                    }
                    state.browseQuery?.let { query ->
                        IconButton(onClick = { onAddToBrowse(query) }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Open in Browse tab")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.error != null -> {
                    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                }

                state.posts.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                        Text("No visible posts in this collection.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                comicStripMode -> {
                    // Seamless Vertical Comic / Series Reader
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (state.description.isNotBlank()) {
                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    DTextView(
                                        rawText = state.description,
                                        onOpenUrl = onOpenUrl,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                        itemsIndexed(state.posts, key = { _, p -> p.id }) { idx, post ->
                            val allowHd = settings.hdFullscreen && perfState.budget.allowImmediateHdFullscreen
                            val imageUrl = post.bestFullscreenImageUrl(highQuality = allowHd)
                            val maxDecodePx = when (perfState.effectiveTier) {
                                PerformanceTier.ULTRA -> 1600
                                PerformanceTier.BALANCED -> 1200
                                PerformanceTier.POWER_SAVER -> 900
                                PerformanceTier.SURVIVAL -> 720
                            }
                            val req = remember(imageUrl, maxDecodePx, perfState.budget.enableCrossfade) {
                                ImageRequest.Builder(context)
                                    .data(imageUrl)
                                    .size(maxDecodePx)
                                    .precision(Precision.INEXACT)
                                    .crossfade(perfState.budget.enableCrossfade)
                                    .build()
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenPostViewer(state.posts, idx) }
                            ) {
                                AsyncImage(
                                    model = req,
                                    contentDescription = "Page ${idx + 1}",
                                    placeholder = placeholderPainter,
                                    error = placeholderPainter,
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(post.aspectRatio.coerceIn(0.35f, 2.5f))
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "${idx + 1} / ${state.posts.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (state.description.isNotBlank()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                DTextView(
                                    rawText = state.description,
                                    onOpenUrl = onOpenUrl,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        PostFeedGrid(
                            posts = state.posts,
                            settings = settings,
                            budget = perfState.budget,
                            localFavoriteIds = localFavIds,
                            isLoadingMore = false,
                            hasMore = false,
                            onLoadMore = {},
                            onPostClick = { idx -> onOpenPostViewer(state.posts, idx) },
                            onQuickFavorite = viewModel::toggleFavorite,
                            onDownload = viewModel::downloadPost,
                            onCopyLink = onCopyLink,
                            onShareLink = onShareLink,
                            onOpenInBrowser = { p -> onOpenUrl("https://${settings.apiHost.domain}/posts/${p.id}") }
                        )
                    }
                }
            }
        }
    }
}
