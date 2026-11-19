package com.ugf.yiffur1.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ugf.yiffur1.domain.model.ApiHost
import com.ugf.yiffur1.domain.model.AppSettings
import com.ugf.yiffur1.domain.model.FeedLayoutMode
import com.ugf.yiffur1.domain.model.ThemeMode
import com.ugf.yiffur1.domain.model.VideoQualityPreference
import com.ugf.yiffur1.domain.perf.PerformanceMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicReference

private val Context.yiffurDataStore: DataStore<Preferences> by preferencesDataStore(name = "yiffur_settings_v2")

class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.yiffurDataStore
    private val cachedHost = AtomicReference(ApiHost.E926)

    val currentHost: ApiHost
        get() = cachedHost.get()

    val settingsFlow: Flow<AppSettings> = dataStore.data.map { prefs ->
        val settings = readAll(prefs)
        cachedHost.set(settings.apiHost)
        settings
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val current = readAll(prefs)
            val updated = transform(current)
            cachedHost.set(updated.apiHost)
            writeAll(prefs, updated)
        }
    }

    suspend fun replaceAll(settings: AppSettings) {
        cachedHost.set(settings.apiHost)
        dataStore.edit { prefs -> writeAll(prefs, settings) }
    }

    private fun readAll(prefs: Preferences): AppSettings = AppSettings(
        apiHost = ApiHost.fromDomain(prefs[Keys.API_HOST]),
        hasAccepted18PlusUgc = prefs[Keys.ACCEPTED_UGC] ?: false,
        showSafe = prefs[Keys.SHOW_SAFE] ?: true,
        showQuestionable = prefs[Keys.SHOW_QUESTIONABLE] ?: true,
        showExplicit = prefs[Keys.SHOW_EXPLICIT] ?: true,
        layoutMode = prefs[Keys.LAYOUT_MODE]?.let { runCatching { FeedLayoutMode.valueOf(it) }.getOrNull() }
            ?: FeedLayoutMode.STAGGERED_GRID,
        gridColumns = (prefs[Keys.GRID_COLUMNS] ?: 3).coerceIn(1, 5),
        cropThumbnails = prefs[Keys.CROP_THUMBNAILS] ?: true,
        hdThumbnails = prefs[Keys.HD_THUMBNAILS] ?: true,
        hdFullscreen = prefs[Keys.HD_FULLSCREEN] ?: false,
        hdDownloads = prefs[Keys.HD_DOWNLOADS] ?: true,
        videoQuality = prefs[Keys.VIDEO_QUALITY]?.let { runCatching { VideoQualityPreference.valueOf(it) }.getOrNull() }
            ?: VideoQualityPreference.MP4_COMPATIBLE,
        muteVideosByDefault = prefs[Keys.MUTE_VIDEOS] ?: false,
        showVotesOverlay = prefs[Keys.SHOW_VOTES] ?: true,
        showRatingBadge = prefs[Keys.SHOW_RATING_BADGE] ?: true,
        showVideoIndicator = prefs[Keys.SHOW_VIDEO_INDICATOR] ?: true,
        showSearchTabs = prefs[Keys.SHOW_SEARCH_TABS] ?: true,
        showQuickBrowseButton = prefs[Keys.SHOW_QUICK_BROWSE] ?: true,
        syncFavoritesToE621 = prefs[Keys.SYNC_FAVS_E621] ?: true,
        disableLocalFavorites = prefs[Keys.DISABLE_LOCAL_FAVS] ?: false,
        disableBlacklist = prefs[Keys.DISABLE_BLACKLIST] ?: false,
        themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.YIFFUR_MIDNIGHT,
        hideFromRecents = prefs[Keys.HIDE_FROM_RECENTS] ?: false,
        postsPerPage = (prefs[Keys.POSTS_PER_PAGE] ?: 75).coerceIn(20, 150),
        performanceMode = prefs[Keys.PERFORMANCE_MODE]?.let { runCatching { PerformanceMode.valueOf(it) }.getOrNull() }
            ?: PerformanceMode.ADAPTIVE_AUTO,
        dataSaverOnMetered = prefs[Keys.DATA_SAVER_METERED] ?: true,
        autoCheckUpdates = prefs[Keys.AUTO_CHECK_UPDATES] ?: true,
        pushAnnouncementsEnabled = prefs[Keys.PUSH_ANNOUNCEMENTS] ?: true,
        watchedTagAlertsEnabled = prefs[Keys.WATCHED_TAG_ALERTS] ?: false,
        skippedUpdateTag = prefs[Keys.SKIPPED_UPDATE_TAG] ?: ""
    )

    private fun writeAll(prefs: MutablePreferences, s: AppSettings) {
        prefs[Keys.API_HOST] = s.apiHost.domain
        prefs[Keys.ACCEPTED_UGC] = s.hasAccepted18PlusUgc
        prefs[Keys.SHOW_SAFE] = s.showSafe
        prefs[Keys.SHOW_QUESTIONABLE] = s.showQuestionable
        prefs[Keys.SHOW_EXPLICIT] = s.showExplicit
        prefs[Keys.LAYOUT_MODE] = s.layoutMode.name
        prefs[Keys.GRID_COLUMNS] = s.gridColumns.coerceIn(1, 5)
        prefs[Keys.CROP_THUMBNAILS] = s.cropThumbnails
        prefs[Keys.HD_THUMBNAILS] = s.hdThumbnails
        prefs[Keys.HD_FULLSCREEN] = s.hdFullscreen
        prefs[Keys.HD_DOWNLOADS] = s.hdDownloads
        prefs[Keys.VIDEO_QUALITY] = s.videoQuality.name
        prefs[Keys.MUTE_VIDEOS] = s.muteVideosByDefault
        prefs[Keys.SHOW_VOTES] = s.showVotesOverlay
        prefs[Keys.SHOW_RATING_BADGE] = s.showRatingBadge
        prefs[Keys.SHOW_VIDEO_INDICATOR] = s.showVideoIndicator
        prefs[Keys.SHOW_SEARCH_TABS] = s.showSearchTabs
        prefs[Keys.SHOW_QUICK_BROWSE] = s.showQuickBrowseButton
        prefs[Keys.SYNC_FAVS_E621] = s.syncFavoritesToE621
        prefs[Keys.DISABLE_LOCAL_FAVS] = s.disableLocalFavorites
        prefs[Keys.DISABLE_BLACKLIST] = s.disableBlacklist
        prefs[Keys.THEME_MODE] = s.themeMode.name
        prefs[Keys.HIDE_FROM_RECENTS] = s.hideFromRecents
        prefs[Keys.POSTS_PER_PAGE] = s.postsPerPage.coerceIn(20, 150)
        prefs[Keys.PERFORMANCE_MODE] = s.performanceMode.name
        prefs[Keys.DATA_SAVER_METERED] = s.dataSaverOnMetered
        prefs[Keys.AUTO_CHECK_UPDATES] = s.autoCheckUpdates
        prefs[Keys.PUSH_ANNOUNCEMENTS] = s.pushAnnouncementsEnabled
        prefs[Keys.WATCHED_TAG_ALERTS] = s.watchedTagAlertsEnabled
        prefs[Keys.SKIPPED_UPDATE_TAG] = s.skippedUpdateTag
    }

    private object Keys {
        val API_HOST = stringPreferencesKey("api_host")
        val ACCEPTED_UGC = booleanPreferencesKey("accepted_ugc")
        val SHOW_SAFE = booleanPreferencesKey("show_safe")
        val SHOW_QUESTIONABLE = booleanPreferencesKey("show_questionable")
        val SHOW_EXPLICIT = booleanPreferencesKey("show_explicit")
        val LAYOUT_MODE = stringPreferencesKey("layout_mode")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val CROP_THUMBNAILS = booleanPreferencesKey("crop_thumbnails")
        val HD_THUMBNAILS = booleanPreferencesKey("hd_thumbnails")
        val HD_FULLSCREEN = booleanPreferencesKey("hd_fullscreen")
        val HD_DOWNLOADS = booleanPreferencesKey("hd_downloads")
        val VIDEO_QUALITY = stringPreferencesKey("video_quality")
        val MUTE_VIDEOS = booleanPreferencesKey("mute_videos")
        val SHOW_VOTES = booleanPreferencesKey("show_votes")
        val SHOW_RATING_BADGE = booleanPreferencesKey("show_rating_badge")
        val SHOW_VIDEO_INDICATOR = booleanPreferencesKey("show_video_indicator")
        val SHOW_SEARCH_TABS = booleanPreferencesKey("show_search_tabs")
        val SHOW_QUICK_BROWSE = booleanPreferencesKey("show_quick_browse")
        val SYNC_FAVS_E621 = booleanPreferencesKey("sync_favs_e621")
        val DISABLE_LOCAL_FAVS = booleanPreferencesKey("disable_local_favs")
        val DISABLE_BLACKLIST = booleanPreferencesKey("disable_blacklist")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HIDE_FROM_RECENTS = booleanPreferencesKey("hide_from_recents")
        val POSTS_PER_PAGE = intPreferencesKey("posts_per_page")
        val PERFORMANCE_MODE = stringPreferencesKey("performance_mode")
        val DATA_SAVER_METERED = booleanPreferencesKey("data_saver_metered")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val PUSH_ANNOUNCEMENTS = booleanPreferencesKey("push_announcements")
        val WATCHED_TAG_ALERTS = booleanPreferencesKey("watched_tag_alerts")
        val SKIPPED_UPDATE_TAG = stringPreferencesKey("skipped_update_tag")
    }
}
