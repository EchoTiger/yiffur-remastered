package com.ugf.yiffur1.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ugf.yiffur1.MainActivity
import com.ugf.yiffur1.R
import com.ugf.yiffur1.data.update.AppUpdateInfo

object NotificationChannels {
    const val UPDATES = "yiffur_updates"
    const val ANNOUNCEMENTS = "yiffur_announcements"
    const val TAG_ALERTS = "yiffur_tag_alerts"
}

object NotificationExtras {
    const val OPEN_POST_ID = "yiffur_extra_post_id"
    const val OPEN_POOL_ID = "yiffur_extra_pool_id"
    const val OPEN_SEARCH_QUERY = "yiffur_extra_search_query"
    const val OPEN_UPDATE_DIALOG = "yiffur_extra_open_update"
    const val OPEN_EXTERNAL_URL = "yiffur_extra_external_url"
}

/**
 * Manages Android 8+ Notification Channels, Android 13+ permission checks, and deep-linked notifications
 * for App Updates, FCM Push Announcements, and Sovereign Local Tag Alerts.
 */
object NotificationHelper {

    private const val UPDATE_NOTIFICATION_ID = 42001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val channels = listOf(
            NotificationChannel(
                NotificationChannels.UPDATES,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a new Yiffur release is available to download and install"
            },
            NotificationChannel(
                NotificationChannels.ANNOUNCEMENTS,
                "Push Announcements",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Important Yiffur news, hotfixes, and community announcements"
            },
            NotificationChannel(
                NotificationChannels.TAG_ALERTS,
                "Saved Tag & Artist Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when new posts appear for your pinned saved searches"
            }
        )
        channels.forEach { nm.createNotificationChannel(it) }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun showUpdateNotification(context: Context, info: AppUpdateInfo) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationExtras.OPEN_UPDATE_DIALOG, true)
        }
        val pi = PendingIntent.getActivity(
            context,
            UPDATE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summary = info.releaseNotes.lines()
            .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            ?.replace("**", "")
            ?.take(120)
            ?: "Tap to view changelog and install Yiffur ${info.tagName}."

        val notification = NotificationCompat.Builder(context, NotificationChannels.UPDATES)
            .setSmallIcon(R.drawable.ic_yiffuricon)
            .setContentTitle("Yiffur ${info.tagName} Available")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(UPDATE_NOTIFICATION_ID, notification)
        }
    }

    fun showAnnouncementNotification(
        context: Context,
        title: String,
        body: String,
        postId: Int? = null,
        poolId: Int? = null,
        searchQuery: String? = null,
        externalUrl: String? = null,
        openUpdateDialog: Boolean = false
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (postId != null) putExtra(NotificationExtras.OPEN_POST_ID, postId)
            if (poolId != null) putExtra(NotificationExtras.OPEN_POOL_ID, poolId)
            if (!searchQuery.isNullOrBlank()) putExtra(NotificationExtras.OPEN_SEARCH_QUERY, searchQuery)
            if (!externalUrl.isNullOrBlank()) putExtra(NotificationExtras.OPEN_EXTERNAL_URL, externalUrl)
            if (openUpdateDialog) putExtra(NotificationExtras.OPEN_UPDATE_DIALOG, true)
        }
        val notifId = (System.currentTimeMillis() % 100_000L).toInt() + 10_000
        val pi = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channel = if (openUpdateDialog) NotificationChannels.UPDATES else NotificationChannels.ANNOUNCEMENTS
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_yiffuricon)
            .setContentTitle(title.ifBlank { "Yiffur" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }
    }

    fun showWatchedTagNotification(
        context: Context,
        shortcutId: Long,
        shortcutTitle: String,
        query: String,
        newCount: Int
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationExtras.OPEN_SEARCH_QUERY, query)
        }
        val notifId = 50_000 + (shortcutId % 10_000L).toInt()
        val pi = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val countLabel = if (newCount == 1) "1 new post" else "$newCount+ new posts"
        val notification = NotificationCompat.Builder(context, NotificationChannels.TAG_ALERTS)
            .setSmallIcon(R.drawable.ic_yiffuricon)
            .setContentTitle("New in \"$shortcutTitle\"")
            .setContentText("$countLabel matching $query")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }
    }
}
