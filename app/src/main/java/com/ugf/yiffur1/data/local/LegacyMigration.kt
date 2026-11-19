package com.ugf.yiffur1.data.local

import android.content.Context
import com.ugf.yiffur1.domain.model.ApiHost
import com.ugf.yiffur1.domain.model.FeedLayoutMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One-time migration from Yiffur 1.x (`YiffurUserSettings` SharedPreferences) into
 * the Yiffur 2.0 Room database, DataStore preferences, and Android Keystore vault.
 */
object LegacyMigration {

    private const val LEGACY_PREFS = "YiffurUserSettings"
    private const val MIGRATION_META = "yiffur_v2_migration"
    private const val KEY_DONE = "migrated_from_v1"

    suspend fun runIfNeeded(
        context: Context,
        settingsRepository: SettingsRepository,
        credentialStore: SecureCredentialStore,
        database: YiffurDatabase
    ) {
        val meta = context.getSharedPreferences(MIGRATION_META, Context.MODE_PRIVATE)
        if (meta.getBoolean(KEY_DONE, false)) return

        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        if (legacy.all.isEmpty()) {
            meta.edit().putBoolean(KEY_DONE, true).apply()
            return
        }

        // 1. Migrate credentials into hardware-backed Keystore vault and wipe plaintext key
        val oldUser = legacy.getString("e6Username", "").orEmpty().trim()
        val oldApiKey = legacy.getString("e6PassHash", "").orEmpty().trim()
        if (oldUser.isNotEmpty() && oldApiKey.isNotEmpty() && !credentialStore.isLoggedIn) {
            runCatching { credentialStore.save(oldUser, oldApiKey) }
            legacy.edit().remove("e6PassHash").remove("e6Pass").apply()
        }

        // 2. Migrate settings
        val imageSource = legacy.getString("ImageSources", "e926.net")
        val layoutFlow = legacy.getBoolean("LayoutMode", true)
        val rowCount = legacy.getInt("FlowRowCount", 3).coerceIn(1, 5)

        settingsRepository.update { current ->
            current.copy(
                apiHost = ApiHost.fromDomain(imageSource),
                hasAccepted18PlusUgc = legacy.getBoolean("HasAcceptedUGC", false),
                showSafe = legacy.getBoolean("ShowSafeContent", true),
                showQuestionable = legacy.getBoolean("ShowQuestionableContent", true),
                showExplicit = legacy.getBoolean("ShowNSFWContent", true),
                layoutMode = if (layoutFlow) FeedLayoutMode.STAGGERED_GRID else FeedLayoutMode.VERTICAL_LIST,
                gridColumns = rowCount,
                cropThumbnails = legacy.getBoolean("CropThumbnails", true),
                hdThumbnails = legacy.getBoolean("HQChecked", true),
                hdFullscreen = legacy.getBoolean("HQFSChecked", false),
                hdDownloads = legacy.getBoolean("HQDownloadsChecked", true),
                showVotesOverlay = legacy.getBoolean("VotesChecked", true),
                showRatingBadge = legacy.getBoolean("ShowImageRating", true),
                showVideoIndicator = legacy.getBoolean("ShowVideoIndicator", true),
                showSearchTabs = legacy.getBoolean("ShowSearchHistory", true),
                showQuickBrowseButton = legacy.getBoolean("ShowAutoSearchButtons", true),
                syncFavoritesToE621 = legacy.getBoolean("SyncFavsToE6", true),
                disableLocalFavorites = legacy.getBoolean("DisableYiffurFavorites", false),
                disableBlacklist = legacy.getBoolean("DisableYiffurBlacklist", false)
            )
        }

        // 3. Migrate blacklisted tags
        val oldBlacklist = legacy.getStringSet("BlacklistList", null).orEmpty()
        if (oldBlacklist.isNotEmpty()) {
            database.blacklistDao().insertAll(
                oldBlacklist.mapNotNull { tag ->
                    val cleaned = tag.trim()
                    if (cleaned.isNotEmpty()) BlacklistRuleEntity(rule = cleaned) else null
                }
            )
        }

        // 4. Migrate custom saved browse buttons
        val buttonsJson = legacy.getString("BrowseButtonsList", null)
        if (!buttonsJson.isNullOrBlank()) {
            runCatching {
                val arr = Json.parseToJsonElement(buttonsJson).jsonArray
                val entities = arr.mapIndexedNotNull { idx, elem ->
                    val obj = elem.jsonObject
                    val isPermanent = obj["isPermanent"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    if (isPermanent) return@mapIndexedNotNull null
                    val title = obj["title"]?.jsonPrimitive?.content?.trim().orEmpty()
                    val tags = obj["tags"]?.jsonPrimitive?.content?.trim().orEmpty()
                    if (title.isNotEmpty() && tags.isNotEmpty()) {
                        SavedSearchEntity(title = title, query = tags, sortOrder = idx)
                    } else null
                }
                if (entities.isNotEmpty()) {
                    database.savedSearchDao().insertAll(entities)
                }
            }
        }

        meta.edit().putBoolean(KEY_DONE, true).apply()
    }
}
