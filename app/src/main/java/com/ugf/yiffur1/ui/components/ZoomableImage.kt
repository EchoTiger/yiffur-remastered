package com.ugf.yiffur1.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.ugf.yiffur1.domain.model.PostNote
import com.ugf.yiffur1.domain.perf.AdaptiveBudget
import com.ugf.yiffur1.domain.perf.PerformanceTier
import com.ugf.yiffur1.util.Formatters
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import kotlin.math.roundToInt

/**
 * Gold-standard fullscreen image & GIF viewer:
 * - Static images use Telephoto `ZoomableAsyncImage` backed by Android `BitmapRegionDecoder`
 *   tiled subsampling so multi-megapixel 4K–10K desktop artwork zooms smoothly with tiny RAM.
 * - Smart Progressive Zoom Upgrade: starts on fast WebP sample, and automatically promotes to
 *   tiled full-resolution original when the user zooms past 1.6x (when device budget permits).
 * - Animated GIFs use dimension-capped Coil 3 decoding (`budget.maxGifDecodePx`) and switch to
 *   tap-to-play poster protection in Survival mode so huge desktop GIFs never stall older phones.
 * - Interactive e621 Notes track live pan & zoom pixel-accurately via `transformedContentBounds`.
 */
@Composable
fun ZoomablePostImage(
    imageUrl: String?,
    originalUrl: String?,
    placeholderUrl: String?,
    isGif: Boolean,
    fileSizeBytes: Long,
    imageWidth: Int,
    imageHeight: Int,
    notes: List<PostNote>,
    showNotes: Boolean,
    isActivePage: Boolean = true,
    budget: AdaptiveBudget = AdaptiveBudget.forTier(PerformanceTier.BALANCED, false),
    onSingleTap: () -> Unit,
    onZoomStateChanged: (Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenPost: (Int) -> Unit,
    onOpenPool: (Int) -> Unit,
    onOpenWiki: (String) -> Unit,
    onSearchTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Keep offscreen pager neighbors ultra-lightweight: avoid mounting Telephoto BitmapRegionDecoder
    // tile engines or running animated GIF decoders until the user actually lands on the page.
    if (!isActivePage) {
        val previewModel = remember(placeholderUrl, imageUrl, budget.maxThumbnailPx) {
            val preview = placeholderUrl ?: imageUrl
            ImageRequest.Builder(context)
                .data(preview)
                .size(budget.maxThumbnailPx.coerceAtLeast(512))
                .precision(Precision.INEXACT)
                .crossfade(false)
                .build()
        }
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = previewModel,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    var activeNote by remember(imageUrl, originalUrl) { mutableStateOf<PostNote?>(null) }
    var promotedToOriginal by remember(imageUrl, originalUrl) { mutableStateOf(false) }
    var gifUserStarted by remember(imageUrl) { mutableStateOf(false) }

    val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 5f)
    )
    val zoomableImageState = rememberZoomableImageState(zoomableState)

    val zoomFraction = zoomableState.zoomFraction ?: 0f
    LaunchedEffect(zoomFraction) {
        onZoomStateChanged(zoomFraction > 0.02f)
        if (!isGif &&
            !promotedToOriginal &&
            zoomFraction > 0.16f &&
            budget.autoUpgradeZoomToOriginal &&
            !originalUrl.isNullOrBlank() &&
            originalUrl != imageUrl
        ) {
            promotedToOriginal = true
        }
    }

    val effectiveUrl = if (!isGif && promotedToOriginal && !originalUrl.isNullOrBlank()) {
        originalUrl
    } else {
        imageUrl
    }

    val handleTap: () -> Unit = {
        if (activeNote != null) {
            activeNote = null
        } else {
            onSingleTap()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isGif) {
            val holdForTap = budget.tapToPlayAnimatedMedia && !gifUserStarted && !placeholderUrl.isNullOrBlank()
            val gifModel = remember(effectiveUrl, placeholderUrl, holdForTap, budget.maxGifDecodePx) {
                val target = if (holdForTap) placeholderUrl else effectiveUrl
                ImageRequest.Builder(context)
                    .data(target)
                    .size(budget.maxGifDecodePx)
                    .precision(Precision.INEXACT)
                    // Keep animated GIF frames out of long-lived RAM cache (disk cache still active)
                    // so multi-megabyte AnimatedImageDrawable buffers are freed immediately on swipe.
                    .memoryCachePolicy(if (holdForTap) CachePolicy.ENABLED else CachePolicy.DISABLED)
                    .crossfade(false)
                    .build()
            }

            AsyncImage(
                model = gifModel,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .zoomable(
                        state = zoomableState,
                        onClick = { handleTap() }
                    )
            )

            if (holdForTap) {
                Surface(
                    onClick = { gifUserStarted = true },
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.78f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                        Icon(Icons.Rounded.Gif, null, tint = Color.White)
                        Text(
                            text = "Tap to play GIF · ${Formatters.formatFileSize(fileSizeBytes)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else {
            // Static high-resolution image: Telephoto tiled subsampling + smooth placeholder
            val request = remember(effectiveUrl, placeholderUrl, budget.enableCrossfade) {
                ImageRequest.Builder(context)
                    .data(effectiveUrl)
                    .crossfade(budget.enableCrossfade)
                    .build()
            }

            // Show cached lightweight preview immediately behind Telephoto while full tiles decode
            if (!zoomableImageState.isImageDisplayed && !placeholderUrl.isNullOrBlank() && placeholderUrl != effectiveUrl) {
                AsyncImage(
                    model = placeholderUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            ZoomableAsyncImage(
                model = request,
                contentDescription = null,
                state = zoomableImageState,
                contentScale = ContentScale.Fit,
                onClick = { handleTap() },
                modifier = Modifier.fillMaxSize()
            )

            if (!zoomableImageState.isImageDisplayed) {
                CircularProgressIndicator(
                    modifier = Modifier.size(34.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Subtle transient badge when smart-zoom upgraded to full-res tiled original
            if (promotedToOriginal && !zoomableImageState.isImageDisplayed) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 86.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.72f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Hd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "Loading HD tiles…",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Interactive e621 Notes Overlay — tracks pan & zoom cleanly via Telephoto's transformedContentBounds
        val bounds = zoomableState.transformedContentBounds
        if (showNotes && notes.isNotEmpty() && imageWidth > 0 && imageHeight > 0 &&
            bounds.width > 1f && bounds.height > 1f
        ) {
            val scaleX = bounds.width / imageWidth.toFloat()
            val scaleY = bounds.height / imageHeight.toFloat()

            Box(modifier = Modifier.fillMaxSize()) {
                notes.forEach { note ->
                    val nx = bounds.left + note.x * scaleX
                    val ny = bounds.top + note.y * scaleY
                    val nw = (note.width * scaleX).coerceAtLeast(20f)
                    val nh = (note.height * scaleY).coerceAtLeast(20f)
                    val isSelected = activeNote?.id == note.id

                    Surface(
                        modifier = Modifier
                            .offset { IntOffset(nx.roundToInt(), ny.roundToInt()) }
                            .size(
                                width = with(density) { nw.toDp() },
                                height = with(density) { nh.toDp() }
                            )
                            .clickable { activeNote = if (isSelected) null else note },
                        color = Color(0xFFFFF59D).copy(alpha = if (isSelected) 0.50f else 0.28f),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = Color(0xFFFFD54F).copy(alpha = 0.9f)
                        ),
                        shape = RoundedCornerShape(3.dp)
                    ) {}
                }
            }
        }

        // Expanded active note card pinned cleanly above bottom chrome (respecting 3-button & gesture nav bars)
        AnimatedVisibility(
            visible = showNotes && activeNote != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 78.dp)
        ) {
            activeNote?.let { note ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                    tonalElevation = 8.dp,
                    modifier = Modifier.widthIn(max = 360.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        DTextView(
                            rawText = note.body,
                            onOpenUrl = onOpenUrl,
                            onOpenPost = onOpenPost,
                            onOpenPool = onOpenPool,
                            onOpenWiki = onOpenWiki,
                            onSearchTag = onSearchTag
                        )
                    }
                }
            }
        }
    }
}
