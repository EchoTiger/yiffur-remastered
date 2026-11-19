package com.ugf.yiffur1.data.api

import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ApiErrorParser {

    private val lenientJson = Json { ignoreUnknownKeys = true }

    fun toUserMessage(error: Throwable): String {
        return when (error) {
            is HttpException -> parseHttpException(error)
            is UnknownHostException -> "No internet connection. Check your Wi-Fi or mobile data."
            is SocketTimeoutException -> "e621 took too long to respond. Try again in a moment."
            is IOException -> error.message?.takeIf { it.isNotBlank() } ?: "Network error while contacting e621."
            else -> error.message?.takeIf { it.isNotBlank() } ?: "Something went wrong."
        }
    }

    private fun parseHttpException(e: HttpException): String {
        val rawBody = try {
            e.response()?.errorBody()?.string().orEmpty()
        } catch (_: Exception) {
            ""
        }

        val parsed = if (rawBody.trimStart().startsWith("{")) {
            runCatching { lenientJson.decodeFromString<ApiErrorDto>(rawBody) }.getOrNull()
        } else null

        val apiMsg = parsed?.message?.takeIf { it.isNotBlank() }
            ?: parsed?.reason?.takeIf { it.isNotBlank() }

        return when (e.code()) {
            401 -> apiMsg ?: "Authentication failed. Check your e621 username and API key."
            403 -> apiMsg ?: "Access denied. You may need to sign in or check permissions."
            404 -> apiMsg ?: "Not found on e621."
            410 -> apiMsg ?: "Page limit reached (max page 750). Narrow your search tags."
            422 -> apiMsg ?: "Search invalid, too many tags (limit 40), or query timed out."
            429 -> "Rate limit hit — slowing down. Please retry in a second."
            500 -> "e621 server error or search timeout. Try again shortly."
            502, 503, 520, 522, 524, 525 -> "e621 is temporarily unavailable or busy (HTTP ${e.code()})."
            else -> apiMsg ?: "Request failed (HTTP ${e.code()})."
        }
    }
}
