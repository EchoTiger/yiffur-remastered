package com.ugf.yiffur1

import android.app.Application
import android.content.ComponentCallbacks2
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.ugf.yiffur1.data.local.LegacyMigration
import com.ugf.yiffur1.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class YiffurApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        appScope.launch {
            runCatching {
                LegacyMigration.runIfNeeded(
                    context = this@YiffurApplication,
                    settingsRepository = container.settingsRepository,
                    credentialStore = container.credentialStore,
                    database = container.database
                )
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = container.imageLoader

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!::container.isInitialized) return
        container.performanceEngine.onMemoryTrim(level)

        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        ) {
            runCatching { container.imageLoader.memoryCache?.clear() }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (!::container.isInitialized) return
        container.performanceEngine.onLowMemory()
        runCatching { container.imageLoader.memoryCache?.clear() }
    }
}
