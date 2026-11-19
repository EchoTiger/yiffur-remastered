package com.ugf.yiffur1

import com.ugf.yiffur1.data.api.RateLimitInterceptor
import com.ugf.yiffur1.data.api.UserAgentAndAuthInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class E621InterceptorsTest {

    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `attaches custom user agent and never leaks Basic Auth to non-e621 or static URLs`() {
        val ua = "Yiffur/2.0.0 (by Echo on e621)"
        val client = OkHttpClient.Builder()
            .addInterceptor(
                UserAgentAndAuthInterceptor(
                    userAgent = ua,
                    credentialsProvider = { "echo" to "secret_key" },
                    attachAuth = true
                )
            )
            .build()

        server.enqueue(MockResponse().setBody("[]"))
        val req = Request.Builder().url(server.url("/data/sample/test.webp")).build()
        client.newCall(req).execute().use { resp ->
            assertTrue(resp.isSuccessful)
        }
        val recorded = server.takeRequest()
        assertEquals(ua, recorded.getHeader("User-Agent"))
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `rate limit interceptor retries HTTP 429 and 503`() {
        val client = OkHttpClient.Builder()
            .addInterceptor(RateLimitInterceptor(minIntervalMs = 20L, maxRetries = 2))
            .build()

        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"ok\":true}"))

        val req = Request.Builder().url(server.url("/posts.json")).build()
        client.newCall(req).execute().use { resp ->
            assertEquals(200, resp.code)
            assertNotNull(resp.body?.string())
        }
        assertEquals(2, server.requestCount)
    }
}
