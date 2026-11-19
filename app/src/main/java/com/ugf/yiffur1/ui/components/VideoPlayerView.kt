package com.ugf.yiffur1.ui.components

import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.domain.model.VideoQualityPreference
import com.ugf.yiffur1.domain.model.VideoVariant
import com.ugf.yiffur1.domain.perf.AdaptivePerformanceState
import com.ugf.yiffur1.util.Formatters
import kotlinx.coroutines.delay

/**
 * High-efficiency AndroidX Media3 ExoPlayer:
 * - Streams through persistent LRU `SimpleCache` so looped & re-visited videos incur zero extra network CPU.
 * - Hardware-codec-aware stream selection: automatically skips software-only AV1/VP9 on older/mid-range phones
 *   and picks silicon-accelerated H.264/VP9/AV1 matched to live thermal/memory/jank budget.
 * - Adaptive `DefaultLoadControl` buffer sizing saves RAM on constrained devices.
 * - Survival mode defers ExoPlayer initialization until the user taps Play over the lightweight WebP poster.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    post: Post,
    videoDataSourceFactory: DataSource.Factory,
    perfState: AdaptivePerformanceState,
    defaultQuality: VideoQualityPreference,
    defaultMuted: Boolean,
    isActivePage: Boolean,
    showControls: Boolean,
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val budget = perfState.budget
    val hardware = perfState.hardware

    var userTappedPlay by remember(post.id) { mutableStateOf(false) }

    // In Survival mode, avoid allocating ExoPlayer / hardware codec instances until user taps Play
    if (budget.tapToPlayAnimatedMedia && !userTappedPlay) {
        val posterUrl = post.sampleUrl ?: post.previewUrl ?: post.previewFallbackUrl
        val recommended = remember(post.id, defaultQuality, budget.recommendedVideoMaxHeight) {
            post.selectOptimalVideoVariant(defaultQuality, hardware, budget)
        }
        Box(
            modifier = modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleChrome
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = "Video preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Surface(
                onClick = { userTappedPlay = true },
                shape = RoundedCornerShape(26.dp),
                color = Color.Black.copy(alpha = 0.80f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            text = "Tap to Play Video",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        val infoLine = buildString {
                            append(recommended?.key?.uppercase() ?: post.ext.uppercase())
                            recommended?.let {
                                append(" · ")
                                append(it.codecBadge(hardware))
                                if (it.size > 0L) {
                                    append(" · ")
                                    append(Formatters.formatFileSize(it.size))
                                }
                            }
                        }
                        Text(
                            text = infoLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }
                }
            }
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val availableVariants = remember(post.id) {
        if (post.videoVariants.isNotEmpty()) {
            post.videoVariants
        } else {
            listOfNotNull(
                post.originalUrl?.let {
                    VideoVariant("original", "Original (${post.ext.uppercase()})", it, post.width, post.height, post.fileSize, null, null)
                }
            )
        }
    }

    var manuallyPinnedVariant by remember(post.id) { mutableStateOf(false) }
    var selectedVariant by remember(post.id, defaultQuality) {
        mutableStateOf(
            post.selectOptimalVideoVariant(defaultQuality, hardware, budget)
                ?: availableVariants.firstOrNull()
        )
    }

    // Automatically adapt downward if the device heats up / struggles while watching (unless user pinned manually)
    LaunchedEffect(budget.recommendedVideoMaxHeight, budget.tier) {
        if (!manuallyPinnedVariant) {
            val recommended = post.selectOptimalVideoVariant(defaultQuality, hardware, budget)
            val current = selectedVariant
            if (recommended != null && current != null && recommended.url != current.url) {
                if (recommended.effectiveHeight() < current.effectiveHeight()) {
                    selectedVariant = recommended
                }
            }
        }
    }

    var isMuted by remember(post.id) { mutableStateOf(defaultMuted) }
    var isLooping by remember(post.id) { mutableStateOf(true) }
    var playbackSpeed by remember(post.id) { mutableFloatStateOf(1f) }
    var isPlaying by remember(post.id) { mutableStateOf(isActivePage) }
    var isBuffering by remember(post.id) { mutableStateOf(true) }
    var positionMs by remember(post.id) { mutableLongStateOf(0L) }
    var durationMs by remember(post.id) { mutableLongStateOf(((post.duration ?: 0.0) * 1000).toLong()) }
    var isSeeking by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    val exoPlayer = remember(post.id) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                budget.exoPlayerMinBufferMs,
                budget.exoPlayerMaxBufferMs,
                budget.exoPlayerBufferForPlaybackMs,
                budget.exoPlayerBufferForPlaybackMs * 2
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(videoDataSourceFactory))
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = if (defaultMuted) 0f else 1f
            }
    }

    // Load or seamlessly switch stream variant while keeping current playback position
    LaunchedEffect(selectedVariant?.url) {
        val url = selectedVariant?.url ?: return@LaunchedEffect
        val resumePos = exoPlayer.currentPosition.coerceAtLeast(0L)
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        if (resumePos > 0L) exoPlayer.seekTo(resumePos)
        exoPlayer.playWhenReady = isActivePage
    }

    LaunchedEffect(isActivePage) {
        exoPlayer.playWhenReady = isActivePage
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(isLooping) {
        exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    LaunchedEffect(playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (exoPlayer.duration > 0L) durationMs = exoPlayer.duration
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> if (isActivePage) exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Poll progress ONLY when the controls HUD is actually visible so playback incurs zero idle recomposition!
    LaunchedEffect(isActivePage, showControls, exoPlayer) {
        while (isActivePage && showControls) {
            if (!isSeeking) {
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                if (exoPlayer.duration > 0L) durationMs = exoPlayer.duration
            }
            delay(250L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleChrome
            ),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setKeepContentOnPlayerReset(true)
                }
            },
            update = { playerView ->
                if (playerView.player !== exoPlayer) {
                    playerView.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (showControls) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 74.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.72f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = Formatters.formatDuration(positionMs / 1000.0).ifEmpty { "0:00" },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                        val maxDur = durationMs.coerceAtLeast(1L).toFloat()
                        Slider(
                            value = positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L)).toFloat(),
                            onValueChange = { newVal ->
                                isSeeking = true
                                positionMs = newVal.toLong()
                            },
                            onValueChangeFinished = {
                                exoPlayer.seekTo(positionMs)
                                isSeeking = false
                            },
                            valueRange = 0f..maxDur,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        Text(
                            text = Formatters.formatDuration(durationMs / 1000.0).ifEmpty { "0:00" },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = { isMuted = !isMuted }) {
                                Icon(
                                    imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                    tint = if (isMuted) Color.White.copy(alpha = 0.6f) else Color.White
                                )
                            }
                            IconButton(onClick = { isLooping = !isLooping }) {
                                Icon(
                                    imageVector = if (isLooping) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                    contentDescription = "Loop",
                                    tint = if (isLooping) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                Surface(
                                    onClick = { showSpeedMenu = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.12f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Rounded.Speed, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Text("${playbackSpeed}x", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                    }
                                }
                                DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { spd ->
                                        DropdownMenuItem(
                                            text = { Text("${spd}x") },
                                            onClick = {
                                                playbackSpeed = spd
                                                showSpeedMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            if (availableVariants.isNotEmpty()) {
                                Spacer(Modifier.size(6.dp))
                                Box {
                                    Surface(
                                        onClick = { if (availableVariants.size > 1) showQualityMenu = true },
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Rounded.HighQuality, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                            val badge = selectedVariant?.let {
                                                "${it.key.uppercase()} · ${it.codecBadge(hardware)}"
                                            } ?: "AUTO"
                                            Text(
                                                text = badge,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    DropdownMenu(expanded = showQualityMenu, onDismissRequest = { showQualityMenu = false }) {
                                        availableVariants.forEach { variant ->
                                            val sizeStr = Formatters.formatFileSize(variant.size)
                                            val codecTag = variant.codecBadge(hardware)
                                            DropdownMenuItem(
                                                text = {
                                                    Text("${variant.label} · $codecTag · $sizeStr")
                                                },
                                                onClick = {
                                                    manuallyPinnedVariant = true
                                                    selectedVariant = variant
                                                    showQualityMenu = false
                                                }
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
    }
}
