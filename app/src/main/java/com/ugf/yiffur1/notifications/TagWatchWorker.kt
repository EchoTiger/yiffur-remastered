package com.ugf.yiffur1.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ugf.yiffur1.YiffurApplication
import com.ugf.yiffur1.domain.blacklist.BlacklistEngine
import kotlinx.coroutines.flow.first

/**
 * Sovereign background worker that polls up to 5 of the user's saved tag shortcuts on e926/e621
 * (respecting API rate limits, active rating filters, and blacklist) and sends local push alerts
 * whenever favorite artists or tags receive brand-new posts.
 */
class TagWatchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? YiffurApplication ?: return Result.success()
        val container = app.container
        val settings = container.settingsRepository.settingsFlow.first()
        if (!settings.watchedTagAlertsEnabled) return Result.success()
        if (!NotificationHelper.hasNotificationPermission(applicationContext)) return Result.success()

        val shortcuts = container.database.savedSearchDao().getAllSnapshot()
            .filter { it.query.isNotBlank() && !it.query.contains("order:random", ignoreCase = true) }
            .take(MAX_WATCHED_SHORTCUTS)
        if (shortcuts.isEmpty()) return Result.success()

        val compiledBlacklist = if (settings.disableBlacklist) {
            emptyList()
        } else {
            BlacklistEngine.compile(container.database.blacklistDao().getAllSnapshot().filter { it.enabled }.map { it.rule })
        }

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        for (shortcut in shortcuts) {
            runCatching {
                val posts = container.postRepository.getPosts(
                    tags = shortcut.query,
                    limit = 10,
                    page = "1"
                ).filter { post ->
                    !post.isHiddenOrDeleted &&
                        settings.allowsRating(post.rating) &&
                        (settings.disableBlacklist || !BlacklistEngine.isBlacklisted(post, compiledBlacklist))
                }

                val latestId = posts.maxOfOrNull { it.id } ?: return@runCatching
                val prefKey = "seen_${settings.apiHost.domain}_${shortcut.id}"
                val lastSeenId = prefs.getInt(prefKey, 0)

                if (lastSeenId == 0) {
                    // First baseline sync: record current newest ID without spamming old posts
                    editor.putInt(prefKey, latestId)
                } else if (latestId > lastSeenId) {
                    val newCount = posts.count { it.id > lastSeenId }
                    if (newCount > 0) {
                        NotificationHelper.showWatchedTagNotification(
                            context = applicationContext,
                            shortcutId = shortcut.id,
                            shortcutTitle = shortcut.title,
                            query = shortcut.query,
                            newCount = newCount
                        )
                    }
                    editor.putInt(prefKey, latestId)
                }
            }
        }

        editor.apply()
        return Result.success()
    }

    companion object {
        private const val PREFS_NAME = "yiffur_tag_watch_prefs"
        private const val MAX_WATCHED_SHORTCUTS = 5
    }
}
