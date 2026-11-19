package com.ugf.yiffur1.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.ugf.yiffur1.domain.model.PostNote
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Fullscreen zoomable image & animated GIF viewer with edge pass-through for HorizontalPager
 * and optional interactive e621 Note annotation boxes.
 */
@Composable
fun ZoomablePostImage(
    imageUrl: String?,
    placeholderUrl: String?,
    imageWidth: Int,
    imageHeight: Int,
    notes: List<PostNote>,
    showNotes: Boolean,
    onSingleTap: () -> Unit,
    onZoomStateChanged: (Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenPost: (Int) -> Unit,
    onOpenPool: (Int) -> Unit,
    onOpenWiki: (String) -> Unit,
    onSearchTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember(imageUrl) { mutableFloatStateOf(1f) }
    var offset by remember(imageUrl) { mutableStateOf(Offset.Zero) }
    var activeNote by remember(imageUrl) { mutableStateOf<PostNote?>(null) }

    LaunchedEffect(scale) {
        onZoomStateChanged(scale > 1.02f)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(imageUrl) {
                detectTapGestures(
                    onTap = {
                        if (activeNote != null) {
                            activeNote = null
                        } else {
                            onSingleTap()
                        }
                    },
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.15f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val targetScale = 2.6f
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            val maxX = (size.width * (targetScale - 1f)) / 2f
                            val maxY = (size.height * (targetScale - 1f)) / 2f
                            val dx = ((centerX - tapOffset.x) * (targetScale - 1f)).coerceIn(-maxX, maxX)
                            val dy = ((centerY - tapOffset.y) * (targetScale - 1f)).coerceIn(-maxY, maxY)
                            scale = targetScale
                            offset = Offset(dx, dy)
                        }
                    }
                )
            }
            .pointerInput(imageUrl) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (pointerCount >= 2 || scale > 1.02f) {
                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            val maxX = (size.width * (newScale - 1f)) / 2f
                            val maxY = (size.height * (newScale - 1f)) / 2f
                            val newOffset = if (newScale <= 1.02f) {
                                Offset.Zero
                            } else {
                                Offset(
                                    x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                    y = (offset.y + panChange.y).coerceIn(-maxY, maxY)
                                )
                            }

                            val atHorizontalEdge = newScale > 1.02f &&
                                ((offset.x >= maxX - 1f && panChange.x > 0f) ||
                                    (offset.x <= -maxX + 1f && panChange.x < 0f))

                            scale = newScale
                            offset = newOffset

                            if (pointerCount >= 2 || (newScale > 1.02f && !atHorizontalEdge)) {
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()
        val density = LocalDensity.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (!placeholderUrl.isNullOrBlank() && placeholderUrl != imageUrl) {
                            AsyncImage(
                                model = placeholderUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                error = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Unable to load image",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            // Interactive e621 Notes Overlay
            if (showNotes && notes.isNotEmpty() && imageWidth > 0 && imageHeight > 0 && containerWidthPx > 0f && containerHeightPx > 0f) {
                val fitScale = min(containerWidthPx / imageWidth.toFloat(), containerHeightPx / imageHeight.toFloat())
                val renderedW = imageWidth * fitScale
                val renderedH = imageHeight * fitScale
                val leftMargin = (containerWidthPx - renderedW) / 2f
                val topMargin = (containerHeightPx - renderedH) / 2f

                Box(modifier = Modifier.fillMaxSize()) {
                    notes.forEach { note ->
                        val nx = leftMargin + note.x * fitScale
                        val ny = topMargin + note.y * fitScale
                        val nw = (note.width * fitScale).coerceAtLeast(18f)
                        val nh = (note.height * fitScale).coerceAtLeast(18f)
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
        }

        // Expanded active note card pinned cleanly near the top/center
        AnimatedVisibility(
            visible = showNotes && activeNote != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 88.dp)
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
