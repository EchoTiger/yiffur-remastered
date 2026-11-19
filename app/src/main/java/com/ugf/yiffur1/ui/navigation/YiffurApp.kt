package com.ugf.yiffur1.ui.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ugf.yiffur1.data.update.UpdateCheckResult
import com.ugf.yiffur1.di.AppContainer
import com.ugf.yiffur1.domain.model.AppSettings
import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.ui.blacklist.BlacklistScreen
import com.ugf.yiffur1.ui.blacklist.BlacklistViewModel
import com.ugf.yiffur1.ui.browse.BrowseScreen
import com.ugf.yiffur1.ui.browse.BrowseViewModel
import com.ugf.yiffur1.ui.components.AppUpdateDialog
import com.ugf.yiffur1.ui.explore.ExploreScreen
import com.ugf.yiffur1.ui.explore.ExploreViewModel
import com.ugf.yiffur1.ui.favorites.FavoritesScreen
import com.ugf.yiffur1.ui.favorites.FavoritesViewModel
import com.ugf.yiffur1.ui.settings.SettingsScreen
import com.ugf.yiffur1.ui.settings.SettingsViewModel
import com.ugf.yiffur1.ui.theme.YiffurTheme
import com.ugf.yiffur1.ui.viewer.CollectionMode
import com.ugf.yiffur1.ui.viewer.CollectionScreen
import com.ugf.yiffur1.ui.viewer.PostViewerScreen
import com.ugf.yiffur1.ui.viewer.ViewerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private object Routes {
    const val BROWSE = "browse"
    const val EXPLORE = "explore"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val VIEWER = "viewer"
    const val COLLECTION = "collection"
    const val BLACKLIST = "blacklist"
}

private data class BottomNavDest(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val PrimaryDestinations = listOf(
    BottomNavDest(Routes.BROWSE, "Browse", Icons.Rounded.Search),
    BottomNavDest(Routes.EXPLORE, "Explore", Icons.Rounded.Explore),
    BottomNavDest(Routes.FAVORITES, "Favorites", Icons.Rounded.Favorite),
    BottomNavDest(Routes.SETTINGS, "Settings", Icons.Rounded.Settings)
)

sealed interface DeepLinkTarget {
    data class OpenPost(val postId: Int) : DeepLinkTarget
    data class OpenPool(val poolId: Int) : DeepLinkTarget
    data class OpenSearch(val query: String) : DeepLinkTarget
    data class OpenExternalUrl(val url: String) : DeepLinkTarget
    data object OpenUpdatePrompt : DeepLinkTarget
}

@Composable
fun YiffurApp(
    container: AppContainer,
    pendingDeepLink: DeepLinkTarget?,
    onDeepLinkConsumed: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()

    val settings by container.settingsRepository.settingsFlow
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val availableUpdate by container.appUpdateManager.availableUpdate.collectAsStateWithLifecycle()
    val updateDownloadState by container.appUpdateManager.downloadState.collectAsStateWithLifecycle()

    var showUpdateDialog by remember { mutableStateOf(false) }
    var canInstallPackages by remember { mutableStateOf(container.appUpdateManager.canRequestPackageInstalls()) }

    val browseVm: BrowseViewModel = viewModel(factory = simpleFactory { BrowseViewModel(container) })
    val exploreVm: ExploreViewModel = viewModel(factory = simpleFactory { ExploreViewModel(container) })
    val favoritesVm: FavoritesViewModel = viewModel(factory = simpleFactory { FavoritesViewModel(container) })
    val blacklistVm: BlacklistViewModel = viewModel(factory = simpleFactory { BlacklistViewModel(container) })
    val settingsVm: SettingsViewModel = viewModel(factory = simpleFactory { SettingsViewModel(container) })
    val viewerVm: ViewerViewModel = viewModel(
        factory = simpleFactory {
            ViewerViewModel(container) { updatedPost ->
                browseVm.updatePostInPlace(updatedPost)
                exploreVm.updatePostInPlace(updatedPost)
                favoritesVm.updatePostInPlace(updatedPost)
            }
        }
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in PrimaryDestinations.map { it.route }

    // Refresh "Install unknown apps" permission status whenever user returns from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canInstallPackages = container.appUpdateManager.canRequestPackageInstalls()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Automatic startup update check (throttled to every 6 hours unless user checks manually)
    LaunchedEffect(Unit) {
        val currentSettings = container.settingsRepository.settingsFlow.first()
        if (currentSettings.autoCheckUpdates) {
            val result = container.appUpdateManager.checkForUpdate(
                force = false,
                skippedTag = currentSettings.skippedUpdateTag
            )
            if (result is UpdateCheckResult.UpdateAvailable) {
                showUpdateDialog = true
            }
        }
    }

    fun openExternalUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(context, "Unable to open URL", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyPostLink(post: Post) {
        val url = "https://${settings.apiHost.domain}/posts/${post.id}"
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("e621 Post", url))
        Toast.makeText(context, "Link copied: $url", Toast.LENGTH_SHORT).show()
    }

    fun sharePostLink(post: Post) {
        val url = "https://${settings.apiHost.domain}/posts/${post.id}"
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(Intent.createChooser(share, "Share Post Link"))
    }

    fun navigateToPrimary(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun searchTagInBrowse(query: String) {
        browseVm.openNewSearchTab(query = query, title = query.ifBlank { "Recent" })
        navController.popBackStack(Routes.BROWSE, inclusive = false)
        if (navController.currentDestination?.route != Routes.BROWSE) {
            navigateToPrimary(Routes.BROWSE)
        }
    }

    LaunchedEffect(pendingDeepLink) {
        when (pendingDeepLink) {
            is DeepLinkTarget.OpenPost -> {
                viewerVm.openPostById(pendingDeepLink.postId)
                navController.navigate(Routes.VIEWER) { launchSingleTop = true }
                onDeepLinkConsumed()
            }
            is DeepLinkTarget.OpenPool -> {
                viewerVm.loadCollection(CollectionMode.PoolCollection(pendingDeepLink.poolId))
                navController.navigate(Routes.COLLECTION) { launchSingleTop = true }
                onDeepLinkConsumed()
            }
            is DeepLinkTarget.OpenSearch -> {
                searchTagInBrowse(pendingDeepLink.query)
                onDeepLinkConsumed()
            }
            is DeepLinkTarget.OpenExternalUrl -> {
                openExternalUrl(pendingDeepLink.url)
                onDeepLinkConsumed()
            }
            is DeepLinkTarget.OpenUpdatePrompt -> {
                onDeepLinkConsumed()
                if (container.appUpdateManager.availableUpdate.value != null) {
                    showUpdateDialog = true
                } else {
                    val res = container.appUpdateManager.checkForUpdate(force = true, skippedTag = "")
                    if (res is UpdateCheckResult.UpdateAvailable) {
                        showUpdateDialog = true
                    } else {
                        Toast.makeText(context, "Yiffur is already up to date!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            null -> Unit
        }
    }

    YiffurTheme(themeMode = settings.themeMode) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        PrimaryDestinations.forEach { dest ->
                            NavigationBarItem(
                                selected = currentRoute == dest.route,
                                onClick = { navigateToPrimary(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) }
                            )
                        }
                    }
                }
            }
        ) { outerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (showBottomBar) outerPadding.calculateBottomPadding() else androidx.compose.ui.unit.Dp(0f))
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.BROWSE
                ) {
                    composable(Routes.BROWSE) {
                        BrowseScreen(
                            viewModel = browseVm,
                            onOpenPostViewer = { posts, idx ->
                                viewerVm.openPosts(posts, idx)
                                navController.navigate(Routes.VIEWER)
                            },
                            onOpenBlacklist = { navController.navigate(Routes.BLACKLIST) },
                            onCopyLink = ::copyPostLink,
                            onShareLink = ::sharePostLink,
                            onOpenInBrowser = { post ->
                                openExternalUrl("https://${settings.apiHost.domain}/posts/${post.id}")
                            }
                        )
                    }

                    composable(Routes.EXPLORE) {
                        ExploreScreen(
                            viewModel = exploreVm,
                            onOpenPosts = { posts, idx ->
                                viewerVm.openPosts(posts, idx)
                                navController.navigate(Routes.VIEWER)
                            },
                            onOpenPool = { pool ->
                                viewerVm.loadCollection(CollectionMode.PoolCollection(pool.id, pool))
                                navController.navigate(Routes.COLLECTION)
                            },
                            onOpenPostSet = { postSet ->
                                viewerVm.loadCollection(CollectionMode.PostSetCollection(postSet))
                                navController.navigate(Routes.COLLECTION)
                            },
                            onCopyLink = ::copyPostLink,
                            onShareLink = ::sharePostLink,
                            onOpenInBrowser = { post ->
                                openExternalUrl("https://${settings.apiHost.domain}/posts/${post.id}")
                            }
                        )
                    }

                    composable(Routes.FAVORITES) {
                        FavoritesScreen(
                            viewModel = favoritesVm,
                            onOpenPostViewer = { posts, idx ->
                                viewerVm.openPosts(posts, idx)
                                navController.navigate(Routes.VIEWER)
                            },
                            onGoToSettingsLogin = { navigateToPrimary(Routes.SETTINGS) },
                            onCopyLink = ::copyPostLink,
                            onShareLink = ::sharePostLink,
                            onOpenInBrowser = { post ->
                                openExternalUrl("https://${settings.apiHost.domain}/posts/${post.id}")
                            }
                        )
                    }

                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            viewModel = settingsVm,
                            onOpenBlacklist = { navController.navigate(Routes.BLACKLIST) },
                            onOpenUpdateDialog = {
                                container.appUpdateManager.resetDownloadState()
                                showUpdateDialog = true
                            },
                            onOpenUrl = ::openExternalUrl
                        )
                    }

                    composable(Routes.VIEWER) {
                        PostViewerScreen(
                            viewModel = viewerVm,
                            videoDataSourceFactory = container.videoDataSourceFactory,
                            onBack = { navController.popBackStack() },
                            onSearchTag = ::searchTagInBrowse,
                            onOpenPool = { poolId, pool ->
                                viewerVm.loadCollection(CollectionMode.PoolCollection(poolId, pool))
                                navController.navigate(Routes.COLLECTION)
                            },
                            onOpenFamily = { rootParentId ->
                                viewerVm.loadCollection(CollectionMode.FamilyCollection(rootParentId))
                                navController.navigate(Routes.COLLECTION)
                            },
                            onOpenPostId = { postId ->
                                viewerVm.openPostById(postId)
                            },
                            onCopyLink = ::copyPostLink,
                            onShareLink = ::sharePostLink,
                            onOpenUrl = ::openExternalUrl
                        )
                    }

                    composable(Routes.COLLECTION) {
                        CollectionScreen(
                            viewModel = viewerVm,
                            onBack = { navController.popBackStack() },
                            onOpenPostViewer = { posts, idx ->
                                viewerVm.openPosts(posts, idx)
                                navController.navigate(Routes.VIEWER)
                            },
                            onAddToBrowse = ::searchTagInBrowse,
                            onCopyLink = ::copyPostLink,
                            onShareLink = ::sharePostLink,
                            onOpenUrl = ::openExternalUrl
                        )
                    }

                    composable(Routes.BLACKLIST) {
                        BlacklistScreen(
                            viewModel = blacklistVm,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }

        // Global In-App Auto-Updater Dialog
        val updateInfo = availableUpdate
        if (showUpdateDialog && updateInfo != null) {
            AppUpdateDialog(
                updateInfo = updateInfo,
                downloadState = updateDownloadState,
                canInstallPackages = canInstallPackages,
                onStartDownload = {
                    coroutineScope.launch {
                        container.appUpdateManager.downloadUpdateApk(updateInfo)
                    }
                },
                onInstallApk = { apkFile ->
                    runCatching {
                        context.startActivity(container.appUpdateManager.createInstallApkIntent(apkFile))
                    }.onFailure { err ->
                        Toast.makeText(context, "Unable to launch installer: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                },
                onOpenInstallPermissionSettings = {
                    runCatching {
                        context.startActivity(container.appUpdateManager.createUnknownSourcesPermissionIntent())
                    }.onFailure {
                        Toast.makeText(context, "Enable 'Install unknown apps' in Android Settings", Toast.LENGTH_LONG).show()
                    }
                },
                onOpenGitHubRelease = {
                    openExternalUrl(updateInfo.htmlUrl)
                },
                onSkipVersion = {
                    showUpdateDialog = false
                    coroutineScope.launch {
                        container.settingsRepository.update { it.copy(skippedUpdateTag = updateInfo.tagName) }
                        container.appUpdateManager.dismissBanner()
                    }
                },
                onDismiss = {
                    showUpdateDialog = false
                }
            )
        }
    }
}

private inline fun <reified T : ViewModel> simpleFactory(crossinline creator: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
    }
