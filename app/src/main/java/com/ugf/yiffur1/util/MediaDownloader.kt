package com.ugf.yiffur1.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.ugf.yiffur1.domain.model.MediaType
import com.ugf.yiffur1.domain.model.Post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Modern Android 10-15 Scoped Storage downloader & share helper using OkHttp (with e621 User-Agent).
 * Requires zero storage permissions on API 29+.
 */
class MediaDownloader(
    private val context: Context,
    private val mediaClient: OkHttpClient
) {

    suspend fun downloadPost(post: Post, highQuality: Boolean): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = post.bestDownloadUrl(highQuality)
                ?: throw IOException("This post has no downloadable file URL.")

            val ext = url.substringAfterLast('.', post.ext).substringBefore('?').lowercase()
                .ifBlank { post.ext.ifBlank { "jpg" } }
            val artistsPrefix = post.tags.artist.take(2).joinToString("_")
                .replace(Regex("[^a-zA-Z0-9_-]"), "")
                .take(40)
            val fileName = if (artistsPrefix.isNotEmpty()) {
                "e621_${post.id}_${artistsPrefix}.$ext"
            } else {
                "e621_${post.id}.$ext"
            }

            val mimeType = mimeTypeForExtension(ext)
            val isVideo = post.mediaType == MediaType.VIDEO || ext == "mp4" || ext == "webm"

            val request = Request.Builder().url(url).build()
            mediaClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Download failed (HTTP ${response.code})")
                }
                val body = response.body ?: throw IOException("Empty response body")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val collection = if (isVideo) {
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    }
                    val relativePath = if (isVideo) {
                        "${Environment.DIRECTORY_MOVIES}/Yiffur"
                    } else {
                        "${Environment.DIRECTORY_PICTURES}/Yiffur"
                    }
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }

                    val resolver = context.contentResolver
                    val uri = resolver.insert(collection, values)
                        ?: throw IOException("Could not create MediaStore entry")

                    try {
                        resolver.openOutputStream(uri)?.use { out ->
                            body.byteStream().use { input -> input.copyTo(out) }
                        } ?: throw IOException("Could not open output stream")

                        val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                        resolver.update(uri, done, null, null)
                    } catch (t: Throwable) {
                        resolver.delete(uri, null, null)
                        throw t
                    }
                    "Saved to $relativePath/$fileName"
                } else {
                    @Suppress("DEPRECATION")
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "Yiffur"
                    )
                    if (!dir.exists()) dir.mkdirs()
                    val dest = File(dir, fileName)
                    dest.outputStream().use { out ->
                        body.byteStream().use { input -> input.copyTo(out) }
                    }
                    "Saved to Downloads/Yiffur/$fileName"
                }
            }
        }
    }

    suspend fun prepareShareMediaIntent(post: Post, highQuality: Boolean): Result<Intent> = withContext(Dispatchers.IO) {
        runCatching {
            val url = post.bestDownloadUrl(highQuality)
                ?: throw IOException("No shareable media URL for this post.")
            val ext = url.substringAfterLast('.', post.ext).substringBefore('?').lowercase()
                .ifBlank { post.ext.ifBlank { "jpg" } }
            val mimeType = mimeTypeForExtension(ext)

            val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
            // Keep cache tidy by deleting older shared files
            shareDir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 3_600_000L }?.forEach { it.delete() }

            val file = File(shareDir, "yiffur_${post.id}.$ext")
            val request = Request.Builder().url(url).build()
            mediaClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Failed to fetch media (HTTP ${response.code})")
                val body = response.body ?: throw IOException("Empty media body")
                file.outputStream().use { out ->
                    body.byteStream().use { input -> input.copyTo(out) }
                }
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private fun mimeTypeForExtension(ext: String): String = when (ext.lowercase()) {
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        else -> "image/jpeg"
    }
}
