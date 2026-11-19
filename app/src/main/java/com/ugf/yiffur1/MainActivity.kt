package com.ugf.yiffur1

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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
import com.ugf.yiffur1.ui.navigation.DeepLinkTarget
import com.ugf.yiffur1.ui.navigation.YiffurApp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var pendingDeepLink by mutableStateOf<DeepLinkTarget?>(null)

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
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val uri = intent?.data ?: return
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
