package com.ugf.yiffur1.domain.model

import kotlinx.serialization.Serializable

enum class ApiHost(val domain: String, val baseUrl: String, val isSafeOnly: Boolean) {
    E926("e926.net", "https://e926.net/", true),
    E621("e621.net", "https://e621.net/", false);

    companion object {
        fun fromDomain(domain: String?): ApiHost =
            if (domain?.contains("e621", ignoreCase = true) == true) E621 else E926
    }
}

enum class FeedLayoutMode(val label: String) {
    UNIFORM_GRID("Uniform Grid"),
    STAGGERED_GRID("Masonry Flow"),
    VERTICAL_LIST("Single Column")
}

enum class ThemeMode(val label: String) {
    YIFFUR_MIDNIGHT("Yiffur Midnight"),
    OLED_BLACK("OLED Pitch Black"),
    DYNAMIC_DARK("Dynamic Dark")
}

enum class VideoQualityPreference(val label: String) {
    MP4_COMPATIBLE("Auto (MP4 Compatible)"),
    ORIGINAL("Original Quality"),
    SD_720P("720p Sample"),
    LOW_480P("480p Data Saver")
}

@Serializable
data class AppSettings(
    val apiHost: ApiHost = ApiHost.E926,
    val hasAccepted18PlusUgc: Boolean = false,
    val showSafe: Boolean = true,
    val showQuestionable: Boolean = true,
    val showExplicit: Boolean = true,
    val layoutMode: FeedLayoutMode = FeedLayoutMode.STAGGERED_GRID,
    val gridColumns: Int = 3,
    val cropThumbnails: Boolean = true,
    val hdThumbnails: Boolean = true,
    val hdFullscreen: Boolean = false,
    val hdDownloads: Boolean = true,
    val videoQuality: VideoQualityPreference = VideoQualityPreference.MP4_COMPATIBLE,
    val muteVideosByDefault: Boolean = false,
    val showVotesOverlay: Boolean = true,
    val showRatingBadge: Boolean = true,
    val showVideoIndicator: Boolean = true,
    val showSearchTabs: Boolean = true,
    val showQuickBrowseButton: Boolean = true,
    val syncFavoritesToE621: Boolean = true,
    val disableLocalFavorites: Boolean = false,
    val disableBlacklist: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.YIFFUR_MIDNIGHT,
    val hideFromRecents: Boolean = false,
    val postsPerPage: Int = 75
) {
    fun allowsRating(rating: Rating): Boolean {
        if (apiHost.isSafeOnly && rating != Rating.SAFE) return false
        return when (rating) {
            Rating.SAFE -> showSafe
            Rating.QUESTIONABLE -> showQuestionable
            Rating.EXPLICIT -> showExplicit
        }
    }
}
