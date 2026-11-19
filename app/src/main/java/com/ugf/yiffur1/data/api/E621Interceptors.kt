package com.ugf.yiffur1.data.api

import com.ugf.yiffur1.domain.model.ApiHost
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * Dynamically rewrites the request host between `e926.net` and `e621.net` so switching
 * image sources takes effect immediately without rebuilding Retrofit/OkHttp instances.
 */
class DynamicHostInterceptor(
    private val hostProvider: () -> ApiHost
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val host = original.url.host
        if (host == "e621.net" || host == "e926.net") {
            val targetHost = hostProvider().domain
            if (host != targetHost) {
                val updatedUrl = original.url.newBuilder()
                    .host(targetHost)
                    .build()
                return chain.proceed(original.newBuilder().url(updatedUrl).build())
            }
        }
        return chain.proceed(original)
    }
}

/**
 * Enforces e621's mandatory descriptive User-Agent header and attaches HTTP Basic Auth
 * ONLY to `.json` API requests on e621.net/e926.net (per Nov 2026 API rules, sending API
 * keys to non-API or static CDN requests is rejected with 401).
 */
class UserAgentAndAuthInterceptor(
    private val userAgent: String,
    private val credentialsProvider: () -> Pair<String, String>?,
    private val attachAuth: Boolean
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
            .header("User-Agent", userAgent)

        if (attachAuth && original.header("Authorization") == null) {
            val host = original.url.host
            val path = original.url.encodedPath
            val isApiJsonRequest = (host == "e621.net" || host == "e926.net") && path.endsWith(".json")
            if (isApiJsonRequest) {
                credentialsProvider()?.let { (username, apiKey) ->
                    if (username.isNotBlank() && apiKey.isNotBlank()) {
                        builder.header("Authorization", Credentials.basic(username, apiKey))
                    }
                }
            }
        }
        return chain.proceed(builder.build())
    }
}

/**
 * Respects e621's 2 req/sec API rate limit (520ms minimum gap between API requests)
 * and backs off politely on HTTP 429 or 503 responses.
 */
class RateLimitInterceptor(
    private val minIntervalMs: Long = 520L,
    private val maxRetries: Int = 2
) : Interceptor {

    private val lock = Any()
    private val lastRequestTimeMs = AtomicLong(0L)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var attempt = 0
        var lastResponse: Response? = null

        while (attempt <= maxRetries) {
            throttle()
            lastResponse?.close()
            val response = chain.proceed(request)

            if (response.code != 429 && response.code != 503) {
                return response
            }

            if (attempt == maxRetries) {
                return response
            }

            lastResponse = response
            attempt++
            val backoffMs = 800L * attempt
            try {
                Thread.sleep(backoffMs)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted during rate-limit backoff", e)
            }
        }

        return lastResponse ?: throw IOException("Request failed after retries")
    }

    private fun throttle() {
        var sleepMs = 0L
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTimeMs.get()
            if (elapsed < minIntervalMs) {
                sleepMs = minIntervalMs - elapsed
                lastRequestTimeMs.set(now + sleepMs)
            } else {
                lastRequestTimeMs.set(now)
            }
        }
        if (sleepMs > 0L) {
            try {
                Thread.sleep(sleepMs)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted during rate limiting", e)
            }
        }
    }
}
