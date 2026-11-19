package com.ugf.yiffur1

import android.app.Application
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
}
