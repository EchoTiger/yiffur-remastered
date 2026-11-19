package com.ugf.yiffur1.ui.viewer

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.SpeakerNotes
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SpeakerNotesOff
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ugf.yiffur1.R
import com.ugf.yiffur1.domain.model.Comment
import com.ugf.yiffur1.domain.model.MediaType
import com.ugf.yiffur1.domain.model.Pool
import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.domain.model.TagCategory
import com.ugf.yiffur1.ui.components.ArtistInfoSheet
import com.ugf.yiffur1.ui.components.DTextView
import com.ugf.yiffur1.ui.components.PostInfoSheet
import com.ugf.yiffur1.ui.components.TagBuilderSheet
import com.ugf.yiffur1.ui.components.TagChip
import com.ugf.yiffur1.ui.components.VideoPlayerView
import com.ugf.yiffur1.ui.components.WikiViewerSheet
import com.ugf.yiffur1.ui.components.ZoomablePostImage
import com.ugf.yiffur1.ui.theme.FavoritePink
import com.ugf.yiffur1.ui.theme.VoteDownRed
import com.ugf.yiffur1.ui.theme.VoteUpGreen
import com.ugf.yiffur1.ui.theme.color
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import com.ugf.yiffur1.util.Formatters

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostViewerScreen(
    viewModel: ViewerViewModel,
    videoDataSourceFactory: DataSource.Factory,
    onBack: () -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenPool: (Int, Pool?) -> Unit,
    onOpenFamily: (Int) -> Unit,
    onOpenPostId: (Int) -> Unit,
    onCopyLink: (Post) -> Unit,
    onShareLink: (Post) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val state by viewModel.viewerState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val perfState by viewModel.performanceState.collectAsStateWithLifecycle()
    val localFavIds by viewModel.localFavoriteIds.collectAsStateWithLifecycle()
    val credentials by viewModel.credentials.collectAsStateWithLifecycle()
    val blacklistedTags by viewModel.blacklistedTagStrings.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val view = LocalView.current

    var chromeVisible by remember { mutableStateOf(true) }
    var privacyCurtainOn by remember { mutableStateOf(false) }
    var isCurrentZoomed by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(true) }
    var forceFullRes by remember(settings.hdFullscreen, perfState.budget.allowImmediateHdFullscreen) {
        mutableStateOf(settings.hdFullscreen && perfState.budget.allowImmediateHdFullscreen)
    }

    var showDetailsSheet by remember { mutableStateOf(false) }
    var showTagBuilder by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showShareMenu by remember { mutableStateOf(false) }
    var showRelationsMenu by remember { mutableStateOf(false) }
    var editingComment by remember { mutableStateOf<Comment?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.shareIntentEvents.collect { shareIntent ->
            runCatching {
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share media"))
            }
        }
    }

    // Immersive system bar control synchronized with chromeVisible
    DisposableEffect(chromeVisible, view) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (chromeVisible) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val w = (context as? Activity)?.window
            if (w != null) {
                WindowCompat.getInsetsController(w, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            state.isLoadingSinglePost -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            state.error != null && state.posts.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        Button(onClick = onBack) { Text("Back") }
                    }
                }
            }

            state.posts.isNotEmpty() -> {
                val pagerState = rememberPagerState(
                    initialPage = state.initialIndex.coerceIn(0, state.posts.lastIndex),
                    pageCount = { state.posts.size }
                )
                val currentPost = state.posts[pagerState.currentPage]
                val isFav = currentPost.isFavorited || currentPost.id in localFavIds

                LaunchedEffect(currentPost.id) {
                    isCurrentZoomed = false
                    viewModel.loadNotesIfNeeded(currentPost)
                    viewModel.loadPostPoolsIfNeeded(currentPost)
                }

                LaunchedEffect(showDetailsSheet, currentPost.id) {
                    if (showDetailsSheet) {
                        viewModel.loadCommentsIfNeeded(currentPost.id)
                    }
                }

                // Media Pager
                if (!privacyCurtainOn) {
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !isCurrentZoomed,
                        beyondViewportPageCount = perfState.budget.pagerOffscreenPages,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val post = state.posts[page]
                        val isActive = page == pagerState.currentPage

                        when (post.mediaType) {
                            MediaType.VIDEO -> {
                                VideoPlayerView(
                                    post = post,
                                    videoDataSourceFactory = videoDataSourceFactory,
                                    perfState = perfState,
                                    defaultQuality = settings.videoQuality,
                                    defaultMuted = settings.muteVideosByDefault,
                                    isActivePage = isActive && !privacyCurtainOn,
                                    showControls = chromeVisible,
                                    onToggleChrome = { chromeVisible = !chromeVisible }
                                )
                            }

                            MediaType.FLASH -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { chromeVisible = !chromeVisible },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        Text("Flash (SWF) Animation", style = MaterialTheme.typography.titleLarge, color = Color.White)
                                        Text(
                                            "Android does not play legacy Adobe Flash files natively.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                        Button(onClick = { onOpenUrl("https://${settings.apiHost.domain}/posts/${post.id}") }) {
                                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Open in Browser")
                                        }
                                    }
                                }
                            }

                            else -> {
                                val targetUrl = post.bestFullscreenImageUrl(highQuality = forceFullRes)
                                val placeholderUrl = post.bestAdaptiveThumbnailUrl(
                                    hdSetting = settings.hdThumbnails,
                                    budget = perfState.budget,
                                    isLargeCard = false
                                )
                                ZoomablePostImage(
                                    imageUrl = targetUrl,
                                    originalUrl = post.originalUrl,
                                    placeholderUrl = placeholderUrl,
                                    isGif = post.mediaType == MediaType.GIF,
                                    fileSizeBytes = post.fileSize,
                                    imageWidth = post.width,
                                    imageHeight = post.height,
                                    notes = state.notesByPost[post.id].orEmpty(),
                                    showNotes = showNotes,
                                    budget = perfState.budget,
                                    onSingleTap = { chromeVisible = !chromeVisible },
                                    onZoomStateChanged = { zoomed ->
                                        if (isActive) isCurrentZoomed = zoomed
                                    },
                                    onOpenUrl = onOpenUrl,
                                    onOpenPost = onOpenPostId,
                                    onOpenPool = { poolId -> onOpenPool(poolId, null) },
                                    onOpenWiki = viewModel::openWiki,
                                    onSearchTag = onSearchTag
                                )
                            }
                        }
                    }
                } else {
                    // Privacy Curtain / Boss-Key Overlay (honoring original Yiffur!)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_yiffuricon),
                                contentDescription = "Privacy curtain",
                                modifier = Modifier.size(140.dp)
                            )
                            Text(
                                text = "Privacy Shield Active",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            OutlinedButton(onClick = { privacyCurtainOn = false }) {
                                Icon(Icons.Rounded.Visibility, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Resume Viewing")
                            }
                        }
                    }
                }

                // Top Floating Overlay Chrome
                AnimatedVisibility(
                    visible = chromeVisible || privacyCurtainOn,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.80f), Color.Transparent)
                                )
                            )
                            .statusBarsPadding()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            if (!privacyCurtainOn) {
                                Column {
                                    Text(
                                        text = "#${currentPost.id}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "${pagerState.currentPage + 1} / ${state.posts.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.78f)
                                        )
                                        Text(
                                            text = currentPost.rating.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = currentPost.rating.color()
                                        )
                                    }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!privacyCurtainOn) {
                                if (currentPost.hasNotes) {
                                    IconButton(onClick = { showNotes = !showNotes }) {
                                        Icon(
                                            imageVector = if (showNotes) Icons.AutoMirrored.Rounded.SpeakerNotes else Icons.Rounded.SpeakerNotesOff,
                                            contentDescription = "Toggle Notes",
                                            tint = if (showNotes) MaterialTheme.colorScheme.secondary else Color.White.copy(alpha = 0.65f)
                                        )
                                    }
                                }
                                if (currentPost.mediaType == MediaType.IMAGE && currentPost.hasSample) {
                                    IconButton(onClick = { forceFullRes = !forceFullRes }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Hd,
                                            contentDescription = "Toggle Original Quality",
                                            tint = if (forceFullRes) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                IconButton(onClick = { showInfoSheet = true }) {
                                    Icon(Icons.Rounded.Info, contentDescription = "Post Info", tint = Color.White)
                                }
                            }
                            IconButton(onClick = { privacyCurtainOn = !privacyCurtainOn }) {
                                Icon(
                                    imageVector = if (privacyCurtainOn) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                    contentDescription = "Privacy Shield",
                                    tint = if (privacyCurtainOn) MaterialTheme.colorScheme.primary else Color.White
                                )
                            }
                        }
                    }
                }

                // Bottom Floating Action Bar
                AnimatedVisibility(
                    visible = chromeVisible && !privacyCurtainOn,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                                )
                            )
                            .navigationBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Vote Pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.12f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.votePost(currentPost, 1) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ThumbUp,
                                        contentDescription = "Upvote",
                                        tint = if (currentPost.userVote == 1) VoteUpGreen else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = Formatters.formatCompactCount(currentPost.scoreTotal),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        currentPost.scoreTotal > 0 -> VoteUpGreen
                                        currentPost.scoreTotal < 0 -> VoteDownRed
                                        else -> Color.White
                                    }
                                )
                                IconButton(
                                    onClick = { viewModel.votePost(currentPost, -1) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ThumbDown,
                                        contentDescription = "Downvote",
                                        tint = if (currentPost.userVote == -1) VoteDownRed else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Favorite
                            IconButton(onClick = { viewModel.toggleFavorite(currentPost) }) {
                                Icon(
                                    imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isFav) FavoritePink else Color.White
                                )
                            }

                            // Download
                            IconButton(onClick = { viewModel.downloadPost(currentPost) }) {
                                Icon(Icons.Rounded.Download, contentDescription = "Download", tint = Color.White)
                            }

                            // Share / Copy
                            Box {
                                IconButton(onClick = { showShareMenu = true }) {
                                    Icon(Icons.Rounded.Share, contentDescription = "Share", tint = Color.White)
                                }
                                DropdownMenu(expanded = showShareMenu, onDismissRequest = { showShareMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Copy Post Link") },
                                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                        onClick = {
                                            showShareMenu = false
                                            onCopyLink(currentPost)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share Link") },
                                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                        onClick = {
                                            showShareMenu = false
                                            onShareLink(currentPost)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share Media File") },
                                        leadingIcon = { Icon(Icons.Rounded.Download, null) },
                                        onClick = {
                                            showShareMenu = false
                                            viewModel.sharePostMedia(currentPost)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Open on ${settings.apiHost.domain}") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                                        onClick = {
                                            showShareMenu = false
                                            onOpenUrl("https://${settings.apiHost.domain}/posts/${currentPost.id}")
                                        }
                                    )
                                }
                            }

                            // Pools / Parent-Child Relations
                            val hasFamily = currentPost.parentId != null || currentPost.hasChildren
                            val hasPools = currentPost.pools.isNotEmpty()
                            if (hasFamily || hasPools) {
                                Box {
                                    IconButton(onClick = { showRelationsMenu = true }) {
                                        Icon(
                                            imageVector = if (hasPools) Icons.Rounded.CollectionsBookmark else Icons.Rounded.AccountTree,
                                            contentDescription = "Pools and Related Posts",
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    DropdownMenu(expanded = showRelationsMenu, onDismissRequest = { showRelationsMenu = false }) {
                                        if (hasFamily) {
                                            DropdownMenuItem(
                                                text = { Text("View Parent / Children") },
                                                leadingIcon = { Icon(Icons.Rounded.AccountTree, null) },
                                                onClick = {
                                                    showRelationsMenu = false
                                                    val rootId = currentPost.parentId ?: currentPost.id
                                                    onOpenFamily(rootId)
                                                }
                                            )
                                        }
                                        val loadedPools = state.poolsByPost[currentPost.id].orEmpty()
                                        if (loadedPools.isNotEmpty()) {
                                            loadedPools.forEach { pool ->
                                                DropdownMenuItem(
                                                    text = { Text("Pool: ${pool.displayName} (${pool.postCount})") },
                                                    leadingIcon = { Icon(Icons.Rounded.CollectionsBookmark, null) },
                                                    onClick = {
                                                        showRelationsMenu = false
                                                        onOpenPool(pool.id, pool)
                                                    }
                                                )
                                            }
                                        } else {
                                            currentPost.pools.forEach { poolId ->
                                                DropdownMenuItem(
                                                    text = { Text("Open Pool #$poolId") },
                                                    leadingIcon = { Icon(Icons.Rounded.CollectionsBookmark, null) },
                                                    onClick = {
                                                        showRelationsMenu = false
                                                        onOpenPool(poolId, null)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Details, Tags & Comments Sheet Trigger
                            IconButton(onClick = { showDetailsSheet = true }) {
                                BadgedBox(
                                    badge = {
                                        if (currentPost.commentCount > 0) {
                                            Badge { Text(Formatters.formatCompactCount(currentPost.commentCount)) }
                                        }
                                    }
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Comment, contentDescription = "Details & Comments", tint = Color.White)
                                }
                            }
                        }
                    }
                }

                // Details, Tags & Comments Bottom Sheet
                if (showDetailsSheet) {
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
                    val comments = state.commentsByPost[currentPost.id].orEmpty()
                    val loadingComments = currentPost.id in state.loadingCommentsFor
                    var commentDraft by remember(currentPost.id) { mutableStateOf("") }
                    var doNotBump by remember(currentPost.id) { mutableStateOf(false) }

                    ModalBottomSheet(
                        onDismissRequest = { showDetailsSheet = false },
                        sheetState = sheetState,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 18.dp)
                                .padding(bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Artists + Uploader + Favorites Summary Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val artistStr = currentPost.tags.artist.joinToString(", ") { it.replace('_', ' ') }
                                        .ifBlank { "Unknown Artist" }
                                    Text(
                                        text = artistStr,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = TagCategory.ARTIST.color(),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Post #${currentPost.id} · ❤ ${currentPost.favCount} · ${currentPost.width}×${currentPost.height} ${currentPost.ext.uppercase()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { showTagBuilder = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Label, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Tag Builder")
                                }
                            }

                            // Pools & Family Quick Pills
                            val loadedPools = state.poolsByPost[currentPost.id].orEmpty()
                            if (loadedPools.isNotEmpty() || currentPost.pools.isNotEmpty() || currentPost.parentId != null || currentPost.hasChildren) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (currentPost.parentId != null || currentPost.hasChildren) {
                                        Surface(
                                            onClick = {
                                                showDetailsSheet = false
                                                onOpenFamily(currentPost.parentId ?: currentPost.id)
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Rounded.AccountTree, null, modifier = Modifier.size(16.dp))
                                                Text(
                                                    text = if (currentPost.parentId != null) "Parent #${currentPost.parentId}" else "${currentPost.childrenIds.size} Children",
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        }
                                    }
                                    loadedPools.forEach { pool ->
                                        Surface(
                                            onClick = {
                                                showDetailsSheet = false
                                                onOpenPool(pool.id, pool)
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Rounded.CollectionsBookmark, null, modifier = Modifier.size(16.dp))
                                                Text(
                                                    text = "${pool.displayName} (${pool.postCount})",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Description (DText)
                            if (currentPost.description.isNotBlank()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Description", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        DTextView(
                                            rawText = currentPost.description,
                                            onOpenUrl = onOpenUrl,
                                            onOpenPost = {
                                                showDetailsSheet = false
                                                onOpenPostId(it)
                                            },
                                            onOpenPool = {
                                                showDetailsSheet = false
                                                onOpenPool(it, null)
                                            },
                                            onOpenWiki = viewModel::openWiki,
                                            onSearchTag = {
                                                showDetailsSheet = false
                                                onSearchTag(it)
                                            },
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }

                            // Categorized Tags Section
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Tags (tap to search · hold for wiki/artist)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                currentPost.tags.byCategory().forEach { (category, tagList) ->
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = category.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = category.color(),
                                            fontWeight = FontWeight.Bold
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            tagList.forEach { tag ->
                                                TagChip(
                                                    tag = tag,
                                                    category = category,
                                                    onClick = {
                                                        showDetailsSheet = false
                                                        onSearchTag(tag)
                                                    },
                                                    onLongClick = {
                                                        if (category == TagCategory.ARTIST) {
                                                            viewModel.openArtist(tag)
                                                        } else {
                                                            viewModel.openWiki(tag)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            // Comments Section
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Comments (${comments.size.coerceAtLeast(currentPost.commentCount)})",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    TextButton(onClick = { viewModel.loadCommentsIfNeeded(currentPost.id, force = true) }) {
                                        Text("Refresh")
                                    }
                                }

                                // Reply Composer (authenticated)
                                if (credentials != null) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedTextField(
                                            value = commentDraft,
                                            onValueChange = { commentDraft = it },
                                            placeholder = { Text("Write a comment (DText supported)…") },
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 2,
                                            maxLines = 5,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.clickable { doNotBump = !doNotBump }
                                            ) {
                                                Checkbox(checked = doNotBump, onCheckedChange = { doNotBump = it })
                                                Text("Do not bump", style = MaterialTheme.typography.labelMedium)
                                            }
                                            Button(
                                                onClick = {
                                                    val body = commentDraft
                                                    viewModel.postComment(currentPost, body, doNotBump)
                                                    commentDraft = ""
                                                },
                                                enabled = commentDraft.isNotBlank() && !state.isPostingComment,
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                if (state.isPostingComment) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                } else {
                                                    Icon(Icons.AutoMirrored.Rounded.Send, null, modifier = Modifier.size(16.dp))
                                                }
                                                Spacer(Modifier.width(6.dp))
                                                Text("Post")
                                            }
                                        }
                                    }
                                }

                                if (loadingComments) {
                                    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(26.dp))
                                    }
                                } else if (comments.isEmpty()) {
                                    Text(
                                        text = "No comments yet.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    comments.forEach { comment ->
                                        val isOwn = credentials?.username?.equals(comment.creatorName, ignoreCase = true) == true
                                        CommentCard(
                                            comment = comment,
                                            isOwnComment = isOwn,
                                            isLoggedIn = credentials != null,
                                            onVote = { delta -> viewModel.voteComment(currentPost.id, comment, delta) },
                                            onQuoteReply = {
                                                val quote = "[quote]\"${comment.creatorName}\":/users/${comment.creatorId} said:\n${comment.body}\n[/quote]\n"
                                                commentDraft = if (commentDraft.isBlank()) quote else "$commentDraft\n$quote"
                                            },
                                            onEdit = { editingComment = comment },
                                            onOpenUrl = onOpenUrl,
                                            onOpenPost = {
                                                showDetailsSheet = false
                                                onOpenPostId(it)
                                            },
                                            onOpenPool = {
                                                showDetailsSheet = false
                                                onOpenPool(it, null)
                                            },
                                            onOpenWiki = viewModel::openWiki,
                                            onSearchTag = {
                                                showDetailsSheet = false
                                                onSearchTag(it)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Tag Builder Sheet
                if (showTagBuilder) {
                    TagBuilderSheet(
                        post = currentPost,
                        blacklistedTagStrings = blacklistedTags,
                        onSearchQuery = { query ->
                            showDetailsSheet = false
                            onSearchTag(query)
                        },
                        onToggleBlacklistTag = viewModel::toggleBlacklistTag,
                        onOpenWiki = viewModel::openWiki,
                        onOpenArtist = viewModel::openArtist,
                        onDismiss = { showTagBuilder = false }
                    )
                }

                // Post Technical Info Sheet
                if (showInfoSheet) {
                    PostInfoSheet(
                        post = currentPost,
                        apiHost = settings.apiHost,
                        onOpenUrl = onOpenUrl,
                        onSearchUploader = { query ->
                            showDetailsSheet = false
                            onSearchTag(query)
                        },
                        onDismiss = { showInfoSheet = false }
                    )
                }

                // Edit Own Comment Dialog
                editingComment?.let { target ->
                    var editedText by remember(target.id) { mutableStateOf(target.body) }
                    AlertDialog(
                        onDismissRequest = { editingComment = null },
                        title = { Text("Edit Comment") },
                        text = {
                            OutlinedTextField(
                                value = editedText,
                                onValueChange = { editedText = it },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 260.dp)
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.editComment(currentPost.id, target.id, editedText)
                                    editingComment = null
                                },
                                enabled = editedText.isNotBlank()
                            ) { Text("Save") }
                        },
                        dismissButton = {
                            TextButton(onClick = { editingComment = null }) { Text("Cancel") }
                        }
                    )
                }
            }
        }

        // In-App Wiki Sheet
        state.activeWikiTitle?.let { wikiTitle ->
            WikiViewerSheet(
                title = wikiTitle,
                wikiPage = state.activeWikiPage,
                isLoading = state.isLoadingWiki,
                apiHost = settings.apiHost,
                onSearchTag = {
                    showDetailsSheet = false
                    onSearchTag(it)
                },
                onOpenUrl = onOpenUrl,
                onOpenPost = {
                    showDetailsSheet = false
                    onOpenPostId(it)
                },
                onOpenPool = {
                    showDetailsSheet = false
                    onOpenPool(it, null)
                },
                onOpenWiki = viewModel::openWiki,
                onDismiss = viewModel::dismissWiki
            )
        }

        // In-App Artist Profile Sheet
        state.activeArtistName?.let { artistName ->
            ArtistInfoSheet(
                artistName = artistName,
                artistInfo = state.activeArtistInfo,
                isLoading = state.isLoadingArtist,
                onSearchArtist = {
                    showDetailsSheet = false
                    onSearchTag(it)
                },
                onOpenUrl = onOpenUrl,
                onDismiss = viewModel::dismissArtist
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 70.dp)
        )
    }
}

@Composable
private fun CommentCard(
    comment: Comment,
    isOwnComment: Boolean,
    isLoggedIn: Boolean,
    onVote: (Int) -> Unit,
    onQuoteReply: () -> Unit,
    onEdit: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenPost: (Int) -> Unit,
    onOpenPool: (Int) -> Unit,
    onOpenWiki: (String) -> Unit,
    onSearchTag: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isOwnComment) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (comment.isSticky) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (comment.isSticky) {
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = "Sticky",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = comment.creatorName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isOwnComment) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "· ${Formatters.formatRelativeTime(comment.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Comment Score & Vote Controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onVote(1) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Rounded.KeyboardArrowUp,
                            contentDescription = "Upvote comment",
                            tint = if (comment.userVote == 1) VoteUpGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = comment.score.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            comment.score > 0 -> VoteUpGreen
                            comment.score < 0 -> VoteDownRed
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    IconButton(onClick = { onVote(-1) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Downvote comment",
                            tint = if (comment.userVote == -1) VoteDownRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            DTextView(
                rawText = comment.body,
                onOpenUrl = onOpenUrl,
                onOpenPost = onOpenPost,
                onOpenPool = onOpenPool,
                onOpenWiki = onOpenWiki,
                onSearchTag = onSearchTag
            )

            if (isLoggedIn) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isOwnComment) {
                        TextButton(onClick = onEdit) {
                            Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    TextButton(onClick = onQuoteReply) {
                        Icon(Icons.AutoMirrored.Rounded.Reply, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Quote", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
