package com.ugf.yiffur1.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.size.Precision
import com.ugf.yiffur1.domain.model.AppSettings
import com.ugf.yiffur1.domain.model.FeedLayoutMode
import com.ugf.yiffur1.domain.model.MediaType
import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.domain.perf.AdaptiveBudget
import com.ugf.yiffur1.domain.perf.PerformanceTier
import com.ugf.yiffur1.ui.theme.FavoritePink
import com.ugf.yiffur1.ui.theme.VoteDownRed
import com.ugf.yiffur1.ui.theme.VoteUpGreen
import com.ugf.yiffur1.ui.theme.color
import com.ugf.yiffur1.util.Formatters

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PostFeedGrid(
    posts: List<Post>,
    settings: AppSettings,
    localFavoriteIds: Set<Int>,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onPostClick: (Int) -> Unit,
    onQuickFavorite: (Post) -> Unit,
    onDownload: (Post) -> Unit,
    onCopyLink: (Post) -> Unit,
    onShareLink: (Post) -> Unit,
    onOpenInBrowser: (Post) -> Unit,
    modifier: Modifier = Modifier,
    budget: AdaptiveBudget = AdaptiveBudget.forTier(PerformanceTier.BALANCED, false),
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    gridState: LazyGridState = rememberLazyGridState(),
    staggeredState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    listState: LazyListState = rememberLazyListState()
) {
    var quickActionPost by remember { mutableStateOf<Post?>(null) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    fun isFav(post: Post): Boolean = post.isFavorited || post.id in localFavoriteIds

    // Scroll-idle viewport prefetcher: warms the next `budget.prefetchCount` tiles ONLY when the user stops scrolling
    val prefetchAnchor by remember(settings.layoutMode, posts.size) {
        derivedStateOf {
            when (settings.layoutMode) {
                FeedLayoutMode.STAGGERED_GRID ->
                    (staggeredState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to staggeredState.isScrollInProgress
                FeedLayoutMode.UNIFORM_GRID ->
                    (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to gridState.isScrollInProgress
                FeedLayoutMode.VERTICAL_LIST ->
                    (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to listState.isScrollInProgress
            }
        }
    }

    LaunchedEffect(prefetchAnchor, budget.prefetchCount, budget.maxThumbnailPx, settings.hdThumbnails, settings.layoutMode) {
        val (lastVisible, isScrolling) = prefetchAnchor
        if (isScrolling || budget.prefetchCount <= 0 || lastVisible < 0 || posts.isEmpty()) return@LaunchedEffect

        val loader = SingletonImageLoader.get(context)
        val start = (lastVisible + 1).coerceAtMost(posts.size)
        val end = (start + budget.prefetchCount).coerceAtMost(posts.size)
        val isLarge = settings.layoutMode == FeedLayoutMode.VERTICAL_LIST
        val cfg = if (budget.useRgb565Thumbnails) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888

        for (i in start until end) {
            val url = posts[i].bestAdaptiveThumbnailUrl(settings.hdThumbnails, budget, isLarge) ?: continue
            val req = ImageRequest.Builder(context)
                .data(url)
                .size(budget.maxThumbnailPx)
                .precision(Precision.INEXACT)
                .bitmapConfig(cfg)
                .build()
            loader.enqueue(req)
        }
    }

    when (settings.layoutMode) {
        FeedLayoutMode.STAGGERED_GRID -> {
            val shouldLoadMore by remember(posts.size, hasMore, isLoadingMore) {
                derivedStateOf {
                    val lastVisible = staggeredState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    hasMore && !isLoadingMore && posts.isNotEmpty() && lastVisible >= posts.size - 12
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) onLoadMore()
            }

            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(settings.gridColumns.coerceIn(1, 5)),
                state = staggeredState,
                modifier = modifier.fillMaxSize(),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalItemSpacing = 6.dp
            ) {
                itemsIndexed(posts, key = { _, p -> p.id }, contentType = { _, _ -> "post_card" }) { index, post ->
                    PostCard(
                        post = post,
                        settings = settings,
                        budget = budget,
                        isFavorited = isFav(post),
                        useNaturalAspectRatio = true,
                        onClick = { onPostClick(index) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            quickActionPost = post
                        }
                    )
                }
                if (isLoadingMore) {
                    item(span = StaggeredGridItemSpan.FullLine, contentType = "footer") {
                        LoadingMoreFooter()
                    }
                }
            }
        }

        FeedLayoutMode.UNIFORM_GRID -> {
            val shouldLoadMore by remember(posts.size, hasMore, isLoadingMore) {
                derivedStateOf {
                    val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    hasMore && !isLoadingMore && posts.isNotEmpty() && lastVisible >= posts.size - 12
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) onLoadMore()
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.gridColumns.coerceIn(1, 5)),
                state = gridState,
                modifier = modifier.fillMaxSize(),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(posts, key = { _, p -> p.id }, contentType = { _, _ -> "post_card" }) { index, post ->
                    PostCard(
                        post = post,
                        settings = settings,
                        budget = budget,
                        isFavorited = isFav(post),
                        useNaturalAspectRatio = false,
                        onClick = { onPostClick(index) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            quickActionPost = post
                        }
                    )
                }
                if (isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }, contentType = "footer") {
                        LoadingMoreFooter()
                    }
                }
            }
        }

        FeedLayoutMode.VERTICAL_LIST -> {
            val shouldLoadMore by remember(posts.size, hasMore, isLoadingMore) {
                derivedStateOf {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    hasMore && !isLoadingMore && posts.isNotEmpty() && lastVisible >= posts.size - 6
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) onLoadMore()
            }

            LazyColumn(
                state = listState,
                modifier = modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(posts, key = { _, p -> p.id }, contentType = { _, _ -> "post_card_lg" }) { index, post ->
                    PostCard(
                        post = post,
                        settings = settings,
                        budget = budget,
                        isFavorited = isFav(post),
                        useNaturalAspectRatio = true,
                        isLargeCard = true,
                        onClick = { onPostClick(index) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            quickActionPost = post
                        }
                    )
                }
                if (isLoadingMore) {
                    item(contentType = "footer") { LoadingMoreFooter() }
                }
            }
        }
    }

    quickActionPost?.let { selected ->
        val fav = isFav(selected)
        ModalBottomSheet(
            onDismissRequest = { quickActionPost = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                val artistsLabel = selected.tags.artist.take(2).joinToString(", ") { it.replace('_', ' ') }
                    .ifBlank { "Unknown Artist" }
                Text(
                    text = "Post #${selected.id} · $artistsLabel",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ListItem(
                    headlineContent = { Text(if (fav) "Remove from Favorites" else "Add to Favorites") },
                    leadingContent = {
                        Icon(
                            imageVector = if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            tint = if (fav) FavoritePink else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    modifier = Modifier.combinedClickable(onClick = {
                        onQuickFavorite(selected)
                        quickActionPost = null
                    })
                )
                ListItem(
                    headlineContent = { Text("Download (${selected.ext.uppercase()})") },
                    leadingContent = { Icon(Icons.Rounded.Download, contentDescription = null) },
                    modifier = Modifier.combinedClickable(onClick = {
                        onDownload(selected)
                        quickActionPost = null
                    })
                )
                ListItem(
                    headlineContent = { Text("Copy Post Link") },
                    leadingContent = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                    modifier = Modifier.combinedClickable(onClick = {
                        onCopyLink(selected)
                        quickActionPost = null
                    })
                )
                ListItem(
                    headlineContent = { Text("Share Link") },
                    leadingContent = { Icon(Icons.Rounded.Share, contentDescription = null) },
                    modifier = Modifier.combinedClickable(onClick = {
                        onShareLink(selected)
                        quickActionPost = null
                    })
                )
                ListItem(
                    headlineContent = { Text("Open on ${settings.apiHost.domain}") },
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                    modifier = Modifier.combinedClickable(onClick = {
                        onOpenInBrowser(selected)
                        quickActionPost = null
                    })
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostCard(
    post: Post,
    settings: AppSettings,
    budget: AdaptiveBudget,
    isFavorited: Boolean,
    useNaturalAspectRatio: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLargeCard: Boolean = false
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    val thumbUrl = remember(post.id, settings.hdThumbnails, budget.tier, budget.preferSampleThumbnails, isLargeCard) {
        post.bestAdaptiveThumbnailUrl(
            hdSetting = settings.hdThumbnails,
            budget = budget,
            isLargeCard = isLargeCard
        )
    }
    val ratio = if (useNaturalAspectRatio) post.aspectRatio else 0.78f
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val placeholderPainter = remember(placeholderColor) { ColorPainter(placeholderColor) }

    val imageRequest = remember(thumbUrl, budget.maxThumbnailPx, budget.useRgb565Thumbnails, budget.enableCrossfade) {
        ImageRequest.Builder(context)
            .data(thumbUrl)
            .size(budget.maxThumbnailPx)
            .precision(Precision.INEXACT)
            .bitmapConfig(if (budget.useRgb565Thumbnails) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888)
            .crossfade(budget.enableCrossfade)
            .build()
    }

    val scoreText = remember(post.scoreTotal) { Formatters.formatCompactCount(post.scoreTotal) }
    val favText = remember(post.favCount) { Formatters.formatCompactCount(post.favCount) }
    val durText = remember(post.duration) { Formatters.formatDuration(post.duration) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
        ) {
            // Zero-subcomposition AsyncImage: ~3x lower CPU layout cost per grid tile than SubcomposeAsyncImage
            AsyncImage(
                model = imageRequest,
                contentDescription = "Post #${post.id}",
                placeholder = placeholderPainter,
                error = placeholderPainter,
                contentScale = if (settings.cropThumbnails || useNaturalAspectRatio) {
                    ContentScale.Crop
                } else {
                    ContentScale.Fit
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top badges row: Rating + Pool/Children + Media type (Video/GIF/Sound)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .padding(5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (settings.showRatingBadge) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.68f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = post.rating.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = post.rating.color()
                            )
                        }
                    }
                    if (post.pools.isNotEmpty() || post.hasChildren || post.parentId != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.68f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Collections,
                                contentDescription = "Has pool or related posts",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                if (settings.showVideoIndicator && (post.mediaType == MediaType.VIDEO || post.mediaType == MediaType.GIF)) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = if (post.mediaType == MediaType.GIF) Icons.Rounded.Gif else Icons.Rounded.PlayArrow,
                            contentDescription = "Animated",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        if (durText.isNotEmpty()) {
                            Text(
                                text = durText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                        if (post.hasSound) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = "Sound",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }

            // Bottom gradient stats overlay
            if (settings.showVotesOverlay || isFavorited) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                            )
                        )
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (settings.showVotesOverlay) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = null,
                                    tint = when {
                                        post.scoreTotal > 0 -> VoteUpGreen
                                        post.scoreTotal < 0 -> VoteDownRed
                                        else -> Color.White.copy(alpha = 0.8f)
                                    },
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = scoreText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Favorite,
                                    contentDescription = null,
                                    tint = if (isFavorited) FavoritePink else Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = favText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    if (isFavorited) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(FavoritePink.copy(alpha = 0.9f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = "Favorited",
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingMoreFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            strokeWidth = 2.5.dp
        )
    }
}
