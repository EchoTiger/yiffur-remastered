package com.ugf.yiffur1.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ugf.yiffur1.YiffurApplication
import com.ugf.yiffur1.data.update.UpdateCheckResult
import kotlinx.coroutines.flow.first

/**
 * Periodic WorkManager task that checks GitHub Releases for newer Yiffur versions and posts
 * an actionable notification when an update is ready to install.
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? YiffurApplication ?: return Result.success()
        val container = app.container
        val settings = container.settingsRepository.settingsFlow.first()
        if (!settings.autoCheckUpdates) return Result.success()

        return when (
            val check = container.appUpdateManager.checkForUpdate(
                force = true,
                skippedTag = settings.skippedUpdateTag
            )
        ) {
            is UpdateCheckResult.UpdateAvailable -> {
                val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastNotifiedTag = prefs.getString(KEY_LAST_NOTIFIED_TAG, "")
                if (lastNotifiedTag != check.info.tagName) {
                    NotificationHelper.showUpdateNotification(applicationContext, check.info)
                    prefs.edit().putString(KEY_LAST_NOTIFIED_TAG, check.info.tagName).apply()
                }
                Result.success()
            }

            is UpdateCheckResult.UpToDate -> Result.success()
            is UpdateCheckResult.Error -> Result.retry()
        }
    }

    companion object {
        private const val PREFS_NAME = "yiffur_update_worker_prefs"
        private const val KEY_LAST_NOTIFIED_TAG = "last_notified_tag"
    }
}
