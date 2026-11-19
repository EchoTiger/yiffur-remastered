package com.ugf.yiffur1.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
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
import com.ugf.yiffur1.util.MediaDownloader
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

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
     * Dedicated OkHttp client for static media CDN downloads (images, GIFs, MP4/WebM streams).
     * Sends compliant User-Agent and NEVER sends Basic Auth headers (per Nov 2026 API rules).
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

    val imageCacheDir: File = File(appContext.cacheDir, "image_cache")

    val imageLoader: ImageLoader = ImageLoader.Builder(appContext)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { mediaOkHttpClient }))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                add(AnimatedImageDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(appContext, 0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(imageCacheDir)
                .maxSizeBytes(300L * 1024L * 1024L) // 300 MB disk cache
                .build()
        }
        .crossfade(true)
        .build()
}
