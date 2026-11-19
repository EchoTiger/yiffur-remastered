package com.ugf.yiffur1

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ugf.yiffur1.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging receiver preserving the exact legacy service class name
 * `com.ugf.yiffur1.MyFirebase` for seamless upgrades from Yiffur v1.x.
 *
 * Supports both Firebase Console Notification messages and rich `data` deep links:
 * - `post_id` -> opens Post Viewer on that post
 * - `pool_id` -> opens Pool / Comic reader
 * - `query` -> opens a Browse tab searching those tags
 * - `type = "update"` -> opens Yiffur's in-app Auto-Updater dialog
 * - `url` -> opens an announcement link
 */
class MyFirebase : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val notif = message.notification

        val title = notif?.title ?: data["title"] ?: "Yiffur"
        val body = notif?.body ?: data["body"] ?: data["message"] ?: return

        val postId = data["post_id"]?.toIntOrNull()
        val poolId = data["pool_id"]?.toIntOrNull()
        val searchQuery = data["query"]?.trim()?.takeIf { it.isNotEmpty() }
        val externalUrl = data["url"]?.trim()?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        val isUpdatePush = data["type"].equals("update", ignoreCase = true) ||
            data["open_update"].equals("true", ignoreCase = true)

        val app = application as? YiffurApplication
        serviceScope.launch {
            val settings = runCatching { app?.container?.settingsRepository?.settingsFlow?.first() }.getOrNull()
            if (isUpdatePush && settings?.autoCheckUpdates == false) return@launch
            if (!isUpdatePush && settings?.pushAnnouncementsEnabled == false) return@launch

            NotificationHelper.showAnnouncementNotification(
                context = applicationContext,
                title = title,
                body = body,
                postId = postId,
                poolId = poolId,
                searchQuery = searchQuery,
                externalUrl = externalUrl,
                openUpdateDialog = isUpdatePush
            )
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val app = application as? YiffurApplication ?: return
        app.container.pushNotificationManager.onNewFcmToken(token)
    }
}
