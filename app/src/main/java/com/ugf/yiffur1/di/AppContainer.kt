package com.ugf.yiffur1.di

import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowHardware
import coil3.request.crossfade
import com.ugf.yiffur1.BuildConfig
import com.ugf.yiffur1.data.api.DynamicHostInterceptor
import com.ugf.yiffur1.data.api.E621ApiService
import com.ugf.yiffur1.data.api.RateLimitInterceptor
import com.ugf.yiffur1.data.api.UserAgentAndAuthInterceptor
import com.ugf.yiffur1.data.local.SecureCredentialStore
import com.ugf.yiffur1.data.local.SettingsRepository
import com.ugf.yiffur1.data.local.YiffurDatabase
import com.ugf.yiffur1.data.repository.BackupRepository
import com.ugf.yiffur1.data.repository.FavoritesRepository
import com.ugf.yiffur1.data.repository.PostRepository
import com.ugf.yiffur1.data.update.AppUpdateManager
import com.ugf.yiffur1.domain.perf.AdaptivePerformanceEngine
import com.ugf.yiffur1.notifications.PushNotificationManager
import com.ugf.yiffur1.util.MediaDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class, ExperimentalCoroutinesApi::class)
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    val settingsRepository = SettingsRepository(appContext)
    val credentialStore = SecureCredentialStore(appContext)
    val database = YiffurDatabase.create(appContext)

    /**
     * Real-time hardware probe + thermal/memory/Battery Saver/FrameMetrics adaptive performance governor.
     */
    val performanceEngine = AdaptivePerformanceEngine(appContext, settingsRepository)

    /**
     * OkHttp client for e621/e926 JSON API endpoints:
     * - Dynamic host switching (e926.net <-> e621.net)
     * - Mandatory custom User-Agent + HTTP Basic Auth ONLY on .json API endpoints
     * - 2 req/sec rate limiter + polite 429/503 exponential backoff
     */
    val apiOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .addInterceptor(DynamicHostInterceptor { settingsRepository.currentHost })
        .addInterceptor(
            UserAgentAndAuthInterceptor(
                userAgent = BuildConfig.USER_AGENT,
                credentialsProvider = {
                    credentialStore.currentCredentials?.let { it.username to it.apiKey }
                },
                attachAuth = true
            )
        )
        .addInterceptor(RateLimitInterceptor())
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    redactHeader("Authorization")
                })
            }
        }
        .build()

    /**
     * Dedicated OkHttp client for static media CDN downloads (images, GIFs, MP4/WebM streams, GitHub Releases).
     * Sends compliant User-Agent and NEVER sends e621 Basic Auth headers (per Nov 2026 API rules).
     */
    val mediaOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .addInterceptor(
            UserAgentAndAuthInterceptor(
                userAgent = BuildConfig.USER_AGENT,
                credentialsProvider = { null },
                attachAuth = false
            )
        )
        .build()

    val apiService: E621ApiService = Retrofit.Builder()
        .baseUrl("https://e926.net/")
        .client(apiOkHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json; charset=UTF8".toMediaType()))
        .build()
        .create(E621ApiService::class.java)

    val postRepository = PostRepository(apiService)

    val favoritesRepository = FavoritesRepository(
        api = apiService,
        localFavoriteDao = database.localFavoriteDao(),
        settingsRepository = settingsRepository,
        credentialStore = credentialStore,
        json = json
    )

    val backupRepository = BackupRepository(
        settingsRepository = settingsRepository,
        database = database,
        json = json
    )

    val mediaDownloader = MediaDownloader(appContext, mediaOkHttpClient)

    val appUpdateManager = AppUpdateManager(appContext, mediaOkHttpClient, json)

    val pushNotificationManager = PushNotificationManager(appContext, settingsRepository)

    val imageCacheDir: File = File(appContext.cacheDir, "image_cache")
    val videoCacheDir: File = File(appContext.cacheDir, "video_cache")

    /**
     * Coil 3.1 ImageLoader tuned for low CPU contention & legacy-to-flagship scaling:
     * - Bounded decode thread pool prevents multi-megapixel PNG bursts from starving the UI thread
     * - Dynamic RAM & disk budgets scaled to device hardware profile
     * - Hardware bitmaps enabled on API 28+ (Android 9+) and disabled on API 24-27 for driver stability
     */
    val imageLoader: ImageLoader = run {
        val hw = performanceEngine.hardwareProfile
        val decodeDispatcher = Dispatchers.IO.limitedParallelism(hw.initialDecodeThreads)
        val fetchDispatcher = Dispatchers.IO.limitedParallelism(hw.initialDecodeThreads + 2)

        ImageLoader.Builder(appContext)
            .fetcherCoroutineContext(fetchDispatcher)
            .decoderCoroutineContext(decodeDispatcher)
            .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { mediaOkHttpClient }))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(appContext, hw.memoryCachePercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(imageCacheDir)
                    .maxSizeBytes(hw.imageDiskCacheBytes)
                    .build()
            }
            .crossfade(false) // Per-request crossfade controlled dynamically by AdaptiveBudget
            .build()
    }

    /**
     * Persistent Media3 LRU disk cache so looping & re-visited MP4/WebM videos stream from local
     * storage with zero redundant network/TLS CPU overhead.
     */
    val videoCache: SimpleCache by lazy {
        if (!videoCacheDir.exists()) videoCacheDir.mkdirs()
        SimpleCache(
            videoCacheDir,
            LeastRecentlyUsedCacheEvictor(performanceEngine.hardwareProfile.videoDiskCacheBytes),
            StandaloneDatabaseProvider(appContext)
        )
    }

    val videoDataSourceFactory: DataSource.Factory by lazy {
        val upstream = OkHttpDataSource.Factory(mediaOkHttpClient)
        CacheDataSource.Factory()
            .setCache(videoCache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Clears Coil RAM + disk caches, Media3 video disk entries, shared temp files, and old update APKs.
     */
    fun clearAllMediaCaches() {
        runCatching { imageLoader.memoryCache?.clear() }
        runCatching { imageLoader.diskCache?.clear() }
        runCatching {
            val cache = videoCache
            cache.keys.toList().forEach { key -> cache.removeResource(key) }
        }
        runCatching { File(appContext.cacheDir, "shared").deleteRecursively() }
        runCatching { appUpdateManager.updatesCacheDir.deleteRecursively() }
    }
}
