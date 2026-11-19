package com.ugf.yiffur1.data.repository

import com.ugf.yiffur1.data.local.BlacklistRuleEntity
import com.ugf.yiffur1.data.local.LocalFavoriteEntity
import com.ugf.yiffur1.data.local.SavedSearchEntity
import com.ugf.yiffur1.data.local.SettingsRepository
import com.ugf.yiffur1.data.local.YiffurDatabase
import com.ugf.yiffur1.domain.model.AppSettings
import com.ugf.yiffur1.domain.model.Post
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class YiffurBackupPayload(
    val formatVersion: Int = 2,
    val exportedAtMs: Long = System.currentTimeMillis(),
    val settings: AppSettings,
    val blacklistRules: List<BackupBlacklistRule> = emptyList(),
    val savedSearches: List<BackupSavedSearch> = emptyList(),
    val localFavorites: List<Post> = emptyList()
)

@Serializable
data class BackupBlacklistRule(
    val rule: String,
    val enabled: Boolean = true,
    val fromAccountSync: Boolean = false
)

@Serializable
data class BackupSavedSearch(
    val title: String,
    val query: String,
    val isPinned: Boolean = false
)

/**
 * Local JSON Backup & Restore (replaces the obsolete 2020 external pastebin upload).
 * Credentials/API keys are intentionally never exported for security.
 */
class BackupRepository(
    private val settingsRepository: SettingsRepository,
    private val database: YiffurDatabase,
    private val json: Json
) {

    private val prettyJson = Json(json) {
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun exportToJson(): String {
        val settings = settingsRepository.settingsFlow.first()
        val rules = database.blacklistDao().getAllSnapshot().map {
            BackupBlacklistRule(it.rule, it.enabled, it.fromAccountSync)
        }
        val searches = database.savedSearchDao().getAllSnapshot().map {
            BackupSavedSearch(it.title, it.query, it.isPinned)
        }
        val favs = database.localFavoriteDao().getAllSnapshot().mapNotNull {
            runCatching { json.decodeFromString<Post>(it.postJson) }.getOrNull()
        }
        return prettyJson.encodeToString(
            YiffurBackupPayload(
                settings = settings,
                blacklistRules = rules,
                savedSearches = searches,
                localFavorites = favs
            )
        )
    }

    suspend fun importFromJson(rawJson: String): Result<String> = runCatching {
        val payload = json.decodeFromString<YiffurBackupPayload>(rawJson)
        settingsRepository.replaceAll(payload.settings)

        if (payload.blacklistRules.isNotEmpty()) {
            val existing = database.blacklistDao().getAllSnapshot().map { it.rule.lowercase() }.toSet()
            val newRules = payload.blacklistRules
                .filter { it.rule.isNotBlank() && it.rule.lowercase() !in existing }
                .map { BlacklistRuleEntity(rule = it.rule.trim(), enabled = it.enabled, fromAccountSync = it.fromAccountSync) }
            if (newRules.isNotEmpty()) database.blacklistDao().insertAll(newRules)
        }

        if (payload.savedSearches.isNotEmpty()) {
            val existing = database.savedSearchDao().getAllSnapshot().map { "${it.title}|${it.query}" }.toSet()
            val newSearches = payload.savedSearches
                .filter { "${it.title}|${it.query}" !in existing }
                .mapIndexed { idx, s -> SavedSearchEntity(title = s.title, query = s.query, isPinned = s.isPinned, sortOrder = idx) }
            if (newSearches.isNotEmpty()) database.savedSearchDao().insertAll(newSearches)
        }

        if (payload.localFavorites.isNotEmpty()) {
            val entities = payload.localFavorites.map { post ->
                LocalFavoriteEntity(
                    postId = post.id,
                    postJson = json.encodeToString(post),
                    searchableTags = (post.tags.allTags + listOf(post.uploaderName, post.id.toString())).joinToString(" ").lowercase(),
                    rating = post.rating.code
                )
            }
            database.localFavoriteDao().upsertAll(entities)
        }

        "Restored settings, ${payload.blacklistRules.size} blacklist rules, ${payload.savedSearches.size} saved searches, and ${payload.localFavorites.size} favorites."
    }
}
