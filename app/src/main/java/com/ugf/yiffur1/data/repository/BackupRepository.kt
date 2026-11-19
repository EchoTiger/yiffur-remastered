package com.ugf.yiffur1.data.repository

import com.ugf.yiffur1.data.local.BlacklistRuleEntity
import com.ugf.yiffur1.data.local.LocalFavoriteEntity
import com.ugf.yiffur1.data.local.SavedSearchEntity
import com.ugf.yiffur1.data.local.SettingsRepository
import com.ugf.yiffur1.data.local.YiffurDatabase
import com.ugf.yiffur1.domain.model.ApiHost
import com.ugf.yiffur1.domain.model.AppSettings
import com.ugf.yiffur1.domain.model.FeedLayoutMode
import com.ugf.yiffur1.domain.model.Post
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
 * Supports importing BOTH Yiffur v2 JSON backups AND legacy Yiffur v1 (`YiffurUserSettings`) exports!
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
        val root = json.parseToJsonElement(rawJson).jsonObject

        // Auto-detect legacy Yiffur v1 SharedPreferences export (flat map without `formatVersion`/`settings`)
        if ("formatVersion" !in root && "settings" !in root && looksLikeLegacyV1Export(root)) {
            return@runCatching importLegacyV1Export(root)
        }

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

    private fun looksLikeLegacyV1Export(obj: JsonObject): Boolean {
        val legacyKeys = setOf(
            "ImageSources", "BlacklistList", "BrowseButtonsList", "FlowRowCount",
            "HQChecked", "ShowSafeContent", "SyncFavsToE6", "DisableYiffurBlacklist"
        )
        return legacyKeys.any { it in obj }
    }

    private suspend fun importLegacyV1Export(obj: JsonObject): String {
        fun bool(key: String, default: Boolean): Boolean =
            (obj[key] as? JsonPrimitive)?.booleanOrNull ?: default

        fun str(key: String, default: String): String =
            (obj[key] as? JsonPrimitive)?.contentOrNull ?: default

        fun intVal(key: String, default: Int): Int =
            (obj[key] as? JsonPrimitive)?.intOrNull ?: default

        settingsRepository.update { current ->
            current.copy(
                apiHost = ApiHost.fromDomain(str("ImageSources", current.apiHost.domain)),
                hasAccepted18PlusUgc = bool("HasAcceptedUGC", current.hasAccepted18PlusUgc),
                showSafe = bool("ShowSafeContent", current.showSafe),
                showQuestionable = bool("ShowQuestionableContent", current.showQuestionable),
                showExplicit = bool("ShowNSFWContent", current.showExplicit),
                layoutMode = if (bool("LayoutMode", true)) FeedLayoutMode.STAGGERED_GRID else FeedLayoutMode.VERTICAL_LIST,
                gridColumns = intVal("FlowRowCount", current.gridColumns).coerceIn(1, 5),
                cropThumbnails = bool("CropThumbnails", current.cropThumbnails),
                hdThumbnails = bool("HQChecked", current.hdThumbnails),
                hdFullscreen = bool("HQFSChecked", current.hdFullscreen),
                hdDownloads = bool("HQDownloadsChecked", current.hdDownloads),
                showVotesOverlay = bool("VotesChecked", current.showVotesOverlay),
                showRatingBadge = bool("ShowImageRating", current.showRatingBadge),
                showVideoIndicator = bool("ShowVideoIndicator", current.showVideoIndicator),
                showSearchTabs = bool("ShowSearchHistory", current.showSearchTabs),
                showQuickBrowseButton = bool("ShowAutoSearchButtons", current.showQuickBrowseButton),
                syncFavoritesToE621 = bool("SyncFavsToE6", current.syncFavoritesToE621),
                disableLocalFavorites = bool("DisableYiffurFavorites", current.disableLocalFavorites),
                disableBlacklist = bool("DisableYiffurBlacklist", current.disableBlacklist)
            )
        }

        var importedRules = 0
        val blElem = obj["BlacklistList"]
        if (blElem is JsonArray) {
            val existing = database.blacklistDao().getAllSnapshot().map { it.rule.lowercase() }.toSet()
            val rules = blElem.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                .filter { it.isNotEmpty() && it.lowercase() !in existing }
                .map { BlacklistRuleEntity(rule = it) }
            if (rules.isNotEmpty()) {
                database.blacklistDao().insertAll(rules)
                importedRules = rules.size
            }
        }

        var importedShortcuts = 0
        val buttonsRaw = (obj["BrowseButtonsList"] as? JsonPrimitive)?.contentOrNull
        if (!buttonsRaw.isNullOrBlank()) {
            runCatching {
                val arr = json.parseToJsonElement(buttonsRaw).jsonArray
                val existing = database.savedSearchDao().getAllSnapshot().map { "${it.title}|${it.query}" }.toSet()
                val items = arr.mapIndexedNotNull { idx, elem ->
                    val btn = elem.jsonObject
                    val isPerm = btn["isPermanent"]?.jsonPrimitive?.booleanOrNull ?: false
                    if (isPerm) return@mapIndexedNotNull null
                    val title = btn["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val tags = btn["tags"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (title.isNotEmpty() && tags.isNotEmpty() && "$title|$tags" !in existing) {
                        SavedSearchEntity(title = title, query = tags, sortOrder = idx)
                    } else null
                }
                if (items.isNotEmpty()) {
                    database.savedSearchDao().insertAll(items)
                    importedShortcuts = items.size
                }
            }
        }

        return "Imported legacy Yiffur v1 settings, $importedRules blacklist rules, and $importedShortcuts shortcuts!"
    }
}
