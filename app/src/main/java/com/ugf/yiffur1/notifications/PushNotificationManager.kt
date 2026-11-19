package com.ugf.yiffur1.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.ugf.yiffur1.BuildConfig
import com.ugf.yiffur1.data.local.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Coordinates BOTH:
 * 1. Remote Push Notifications via Firebase Cloud Messaging (FCM topics + token management), safely
 *    guarded when `google-services.json` is absent (e.g. open-source CI builds).
 * 2. Local Sovereign Background Workers via AndroidX WorkManager (GitHub Releases Auto-Update checks
 *    and Saved-Search Tag Watch alerts — zero Google dependency required).
 */
class PushNotificationManager(
    context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _fcmToken = MutableStateFlow(prefs.getString(KEY_FCM_TOKEN, null))
    val fcmToken: StateFlow<String?> = _fcmToken.asStateFlow()

    val isFirebaseConfigured: Boolean
        get() = BuildConfig.FIREBASE_ENABLED && runCatching { FirebaseApp.getApps(appContext).isNotEmpty() }.getOrDefault(false)

    init {
        NotificationHelper.ensureChannels(appContext)
        refreshFcmTokenIfAvailable()

        scope.launch {
            settingsRepository.settingsFlow
                .map { Triple(it.autoCheckUpdates, it.pushAnnouncementsEnabled, it.watchedTagAlertsEnabled) }
                .distinctUntilChanged()
                .collect { (autoUpdates, pushEnabled, tagAlerts) ->
                    syncWorkManagerSchedules(autoUpdates, tagAlerts)
                    syncFcmTopics(pushEnabled, autoUpdates)
                }
        }
    }

    fun onNewFcmToken(token: String) {
        if (token.isBlank()) return
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
        _fcmToken.value = token
    }

    fun refreshFcmTokenIfAvailable() {
        if (!isFirebaseConfigured) return
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (!token.isNullOrBlank()) onNewFcmToken(token)
            }
        }
    }

    private fun syncFcmTopics(announcementsEnabled: Boolean, updatesEnabled: Boolean) {
        if (!isFirebaseConfigured) return
        runCatching {
            val fcm = FirebaseMessaging.getInstance()
            if (announcementsEnabled) {
                fcm.subscribeToTopic(TOPIC_ANNOUNCEMENTS)
            } else {
                fcm.unsubscribeFromTopic(TOPIC_ANNOUNCEMENTS)
            }
            if (updatesEnabled) {
                fcm.subscribeToTopic(TOPIC_UPDATES)
            } else {
                fcm.unsubscribeFromTopic(TOPIC_UPDATES)
            }
        }
    }

    private fun syncWorkManagerSchedules(autoUpdates: Boolean, tagAlerts: Boolean) {
        runCatching {
            val wm = WorkManager.getInstance(appContext)

            if (autoUpdates) {
                val updateReq = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                wm.enqueueUniquePeriodicWork(
                    WORK_AUTO_UPDATES,
                    ExistingPeriodicWorkPolicy.KEEP,
                    updateReq
                )
            } else {
                wm.cancelUniqueWork(WORK_AUTO_UPDATES)
            }

            if (tagAlerts) {
                val watchReq = PeriodicWorkRequestBuilder<TagWatchWorker>(6, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .setRequiresBatteryNotLow(true)
                            .build()
                    )
                    .build()
                wm.enqueueUniquePeriodicWork(
                    WORK_TAG_WATCH,
                    ExistingPeriodicWorkPolicy.KEEP,
                    watchReq
                )
            } else {
                wm.cancelUniqueWork(WORK_TAG_WATCH)
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "yiffur_push_prefs"
        private const val KEY_FCM_TOKEN = "fcm_token"

        const val TOPIC_ANNOUNCEMENTS = "yiffur_announcements"
        const val TOPIC_UPDATES = "yiffur_updates"

        const val WORK_AUTO_UPDATES = "yiffur_periodic_update_check"
        const val WORK_TAG_WATCH = "yiffur_periodic_tag_watch"
    }
}
