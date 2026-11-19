package com.ugf.yiffur1

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.ugf.yiffur1.notifications.NotificationExtras
import com.ugf.yiffur1.ui.navigation.DeepLinkTarget
import com.ugf.yiffur1.ui.navigation.YiffurApp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var pendingDeepLink by mutableStateOf<DeepLinkTarget?>(null)
    private var frameMetricsThread: HandlerThread? = null
    private var frameMetricsListener: Window.OnFrameMetricsAvailableListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        val container = (application as YiffurApplication).container

        // Observe privacy flag (hide screen in Android Recents / block screenshots)
        lifecycleScope.launch {
            container.settingsRepository.settingsFlow
                .map { it.hideFromRecents }
                .distinctUntilChanged()
                .collect { secure ->
                    if (secure) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }

        // Attach lightweight off-main-thread FrameMetrics monitor for live jank adaptation (API 24+)
        attachFrameMetricsMonitor(container)

        handleDeepLinkIntent(intent)

        setContent {
            YiffurApp(
                container = container,
                pendingDeepLink = pendingDeepLink,
                onDeepLinkConsumed = { pendingDeepLink = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    override fun onDestroy() {
        detachFrameMetricsMonitor()
        super.onDestroy()
    }

    private fun attachFrameMetricsMonitor(container: com.ugf.yiffur1.di.AppContainer) {
        runCatching {
            val thread = HandlerThread("yiffur-frame-metrics", android.os.Process.THREAD_PRIORITY_BACKGROUND)
            thread.start()
            val handler = Handler(thread.looper)

            val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                val totalDurationNs = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
                val refreshRate = currentRefreshRateHz()
                container.performanceEngine.recordFrameDuration(totalDurationNs, refreshRate)
            }

            window.addOnFrameMetricsAvailableListener(listener, handler)
            frameMetricsThread = thread
            frameMetricsListener = listener
        }
    }

    private fun detachFrameMetricsMonitor() {
        runCatching {
            frameMetricsListener?.let { window.removeOnFrameMetricsAvailableListener(it) }
            frameMetricsListener = null
            frameMetricsThread?.quitSafely()
            frameMetricsThread = null
        }
    }

    @Suppress("DEPRECATION")
    private fun currentRefreshRateHz(): Float {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display?.refreshRate ?: 60f
            } else {
                windowManager.defaultDisplay?.refreshRate ?: 60f
            }
        }.getOrDefault(60f)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        if (intent == null) return

        // 1. Check notification extras first
        if (intent.getBooleanExtra(NotificationExtras.OPEN_UPDATE_DIALOG, false)) {
            pendingDeepLink = DeepLinkTarget.OpenUpdatePrompt
            return
        }
        val notifPostId = intent.getIntExtra(NotificationExtras.OPEN_POST_ID, -1)
        if (notifPostId > 0) {
            pendingDeepLink = DeepLinkTarget.OpenPost(notifPostId)
            return
        }
        val notifPoolId = intent.getIntExtra(NotificationExtras.OPEN_POOL_ID, -1)
        if (notifPoolId > 0) {
            pendingDeepLink = DeepLinkTarget.OpenPool(notifPoolId)
            return
        }
        val notifQuery = intent.getStringExtra(NotificationExtras.OPEN_SEARCH_QUERY)
        if (!notifQuery.isNullOrBlank()) {
            pendingDeepLink = DeepLinkTarget.OpenSearch(notifQuery)
            return
        }
        val notifUrl = intent.getStringExtra(NotificationExtras.OPEN_EXTERNAL_URL)
        if (!notifUrl.isNullOrBlank()) {
            pendingDeepLink = DeepLinkTarget.OpenExternalUrl(notifUrl)
            return
        }

        // 2. Check standard e621 / e926 web deep links
        val uri = intent.data ?: return
        val segments = uri.pathSegments
        if (segments.size >= 2) {
            val id = segments[1].toIntOrNull() ?: return
            when (segments[0].lowercase()) {
                "posts" -> pendingDeepLink = DeepLinkTarget.OpenPost(id)
                "pools" -> pendingDeepLink = DeepLinkTarget.OpenPool(id)
            }
        }
    }
}
