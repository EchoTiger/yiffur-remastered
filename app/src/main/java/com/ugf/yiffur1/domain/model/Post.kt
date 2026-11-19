package com.ugf.yiffur1.domain.model

import com.ugf.yiffur1.domain.perf.AdaptiveBudget
import com.ugf.yiffur1.domain.perf.DeviceHardwareProfile
import com.ugf.yiffur1.domain.perf.PerformanceTier
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

enum class Rating(val code: String, val label: String, val shortLabel: String) {
    SAFE("s", "Safe", "S"),
    QUESTIONABLE("q", "Questionable", "Q"),
    EXPLICIT("e", "Explicit", "E");

    companion object {
        fun fromCode(code: String?): Rating = when (code?.lowercase()) {
            "s" -> SAFE
            "q" -> QUESTIONABLE
            "e" -> EXPLICIT
            else -> EXPLICIT
        }
    }
}

enum class TagCategory(val id: Int, val label: String) {
    ARTIST(1, "Artist"),
    CONTRIBUTOR(2, "Contributor"),
    COPYRIGHT(3, "Copyright"),
    CHARACTER(4, "Character"),
    SPECIES(5, "Species"),
    GENERAL(0, "General"),
    META(7, "Meta"),
    LORE(8, "Lore"),
    INVALID(6, "Invalid");

    companion object {
        fun fromId(id: Int): TagCategory = entries.firstOrNull { it.id == id } ?: GENERAL
    }
}

enum class MediaType {
    IMAGE,
    GIF,
    VIDEO,
    FLASH,
    UNKNOWN
}

@Serializable
data class VideoVariant(
    val key: String,
    val label: String,
    val url: String,
    val width: Int,
    val height: Int,
    val size: Long,
    val fps: Double?,
    val codec: String?
) {
    /**
     * Checks whether this video stream's codec has dedicated silicon decoding on [hardware].
     * Prevents CPU pegging from software AV1/VP9 decoding on older or mid-range Android phones.
     */
    fun isHardwareAcceleratedOn(hardware: DeviceHardwareProfile): Boolean {
        val c = codec?.lowercase().orEmpty()
        val u = url.lowercase()
        return when {
            c.startsWith("av01") || c.startsWith("av1") -> hardware.hasHwAv1
            c.startsWith("vp09") || c.startsWith("vp9") -> hardware.hasHwVp9
            c.startsWith("avc") || c.startsWith("h264") -> hardware.hasHwH264
            u.endsWith(".webm") -> hardware.hasHwVp9
            u.endsWith(".mp4") -> hardware.hasHwH264
            else -> hardware.hasHwH264
        }
    }

    fun codecBadge(hardware: DeviceHardwareProfile): String {
        val c = codec?.lowercase().orEmpty()
        val u = url.lowercase()
        val name = when {
            c.startsWith("av01") || c.startsWith("av1") -> "AV1"
            c.startsWith("vp09") || c.startsWith("vp9") -> "VP9"
            c.startsWith("avc") || c.startsWith("h264") -> "H.264"
            u.endsWith(".webm") -> "WebM"
            u.endsWith(".mp4") -> "MP4"
            else -> "Video"
        }
        return if (isHardwareAcceleratedOn(hardware)) "$name HW" else "$name SW"
    }

    fun effectiveHeight(): Int = when {
        height > 0 -> height
        key.equals("480p", ignoreCase = true) -> 480
        key.equals("720p", ignoreCase = true) -> 720
        else -> 1080
    }
}

@Serializable
data class PostTags(
    val artist: List<String> = emptyList(),
    val contributor: List<String> = emptyList(),
    val copyright: List<String> = emptyList(),
    val character: List<String> = emptyList(),
    val species: List<String> = emptyList(),
    val general: List<String> = emptyList(),
    val meta: List<String> = emptyList(),
    val lore: List<String> = emptyList(),
    val invalid: List<String> = emptyList()
) {
    @Transient
    private var cachedAllTags: List<String>? = null

    @Transient
    private var cachedTagSet: Set<String>? = null

    val allTags: List<String>
        get() = cachedAllTags ?: (artist + contributor + copyright + character + species + general + meta + lore + invalid)
            .also { cachedAllTags = it }

    val allTagSet: Set<String>
        get() = cachedTagSet ?: buildSet(
            artist.size + contributor.size + copyright.size + character.size +
                species.size + general.size + meta.size + lore.size + invalid.size
        ) {
            artist.forEach { add(it.lowercase()) }
            contributor.forEach { add(it.lowercase()) }
            copyright.forEach { add(it.lowercase()) }
            character.forEach { add(it.lowercase()) }
            species.forEach { add(it.lowercase()) }
            general.forEach { add(it.lowercase()) }
            meta.forEach { add(it.lowercase()) }
            lore.forEach { add(it.lowercase()) }
            invalid.forEach { add(it.lowercase()) }
        }.also { cachedTagSet = it }

    fun byCategory(): List<Pair<TagCategory, List<String>>> = listOf(
        TagCategory.ARTIST to artist,
        TagCategory.CONTRIBUTOR to contributor,
        TagCategory.COPYRIGHT to copyright,
        TagCategory.CHARACTER to character,
        TagCategory.SPECIES to species,
        TagCategory.LORE to lore,
        TagCategory.META to meta,
        TagCategory.GENERAL to general,
        TagCategory.INVALID to invalid
    ).filter { it.second.isNotEmpty() }
}

@Serializable
data class Post(
    val id: Int,
    val createdAt: String,
    val updatedAt: String?,
    val md5: String?,
    val ext: String,
    val fileSize: Long,
    val duration: Double?,
    val width: Int,
    val height: Int,
    val originalUrl: String?,
    val previewUrl: String?,
    val previewFallbackUrl: String?,
    val previewWidth: Int,
    val previewHeight: Int,
    val sampleUrl: String?,
    val sampleFallbackUrl: String?,
    val sampleWidth: Int,
    val sampleHeight: Int,
    val hasSample: Boolean,
    val videoVariants: List<VideoVariant> = emptyList(),
    val uploaderId: Int,
    val uploaderName: String,
    val approverId: Int?,
    val scoreUp: Int,
    val scoreDown: Int,
    val scoreTotal: Int,
    val favCount: Int,
    val isFavorited: Boolean,
    val userVote: Int, // -1, 0, 1
    val commentCount: Int,
    val rating: Rating,
    val isPending: Boolean,
    val isFlagged: Boolean,
    val isDeleted: Boolean,
    val hasNotes: Boolean,
    val parentId: Int?,
    val childrenIds: List<Int>,
    val hasChildren: Boolean,
    val pools: List<Int>,
    val sources: List<String>,
    val description: String,
    val tags: PostTags
) {
    val mediaType: MediaType
        get() = when (ext.lowercase()) {
            "mp4", "webm" -> MediaType.VIDEO
            "gif" -> MediaType.GIF
            "swf" -> MediaType.FLASH
            "jpg", "jpeg", "png", "webp" -> MediaType.IMAGE
            else -> MediaType.UNKNOWN
        }

    val isAnimated: Boolean
        get() = mediaType == MediaType.VIDEO || mediaType == MediaType.GIF

    val hasSound: Boolean
        get() = tags.meta.any { it.equals("sound", ignoreCase = true) }

    val aspectRatio: Float
        get() = if (width > 0 && height > 0) {
            (width.toFloat() / height.toFloat()).coerceIn(0.45f, 2.2f)
        } else {
            1f
        }

    fun bestThumbnailUrl(highQuality: Boolean): String? {
        return if (highQuality && hasSample && !sampleUrl.isNullOrBlank()) {
            sampleUrl
        } else {
            previewUrl ?: previewFallbackUrl ?: sampleUrl ?: originalUrl
        }
    }

    /**
     * Dynamically picks the lightest crisp thumbnail URL allowed by the active [AdaptiveBudget].
     * Prevents multi-megabyte sample/original downloads in dense grids or under thermal/RAM pressure.
     */
    fun bestAdaptiveThumbnailUrl(
        hdSetting: Boolean,
        budget: AdaptiveBudget,
        isLargeCard: Boolean
    ): String? {
        val allowSample = when (budget.tier) {
            PerformanceTier.ULTRA -> hdSetting || isLargeCard
            PerformanceTier.BALANCED -> isLargeCard || (hdSetting && budget.preferSampleThumbnails)
            PerformanceTier.POWER_SAVER -> isLargeCard
            PerformanceTier.SURVIVAL -> false
        }
        return if (allowSample && hasSample && !sampleUrl.isNullOrBlank()) {
            sampleUrl
        } else {
            previewUrl ?: previewFallbackUrl ?: sampleUrl ?: originalUrl
        }
    }

    fun bestFullscreenImageUrl(highQuality: Boolean): String? {
        if (mediaType == MediaType.GIF) {
            return originalUrl ?: sampleUrl ?: previewUrl
        }
        return if (highQuality) {
            originalUrl ?: sampleUrl ?: previewUrl
        } else {
            if (hasSample && !sampleUrl.isNullOrBlank()) sampleUrl else originalUrl ?: previewUrl
        }
    }

    fun bestDownloadUrl(highQuality: Boolean): String? {
        if (mediaType == MediaType.VIDEO) {
            return bestVideoVariant(VideoQualityPreference.MP4_COMPATIBLE)?.url ?: originalUrl
        }
        return if (highQuality) {
            originalUrl ?: sampleUrl
        } else {
            sampleFallbackUrl ?: sampleUrl ?: originalUrl
        }
    }

    /**
     * Hardware-codec-aware, adaptive-budget-aware video stream selector.
     * - Strongly prefers hardware-accelerated streams (never falls into software AV1 when HW H.264 exists).
     * - Steps resolution down smoothly (Original/1080p -> 720p -> 480p) under thermal, RAM, jank, or metered constraints.
     */
    fun selectOptimalVideoVariant(
        preference: VideoQualityPreference,
        hardware: DeviceHardwareProfile,
        budget: AdaptiveBudget
    ): VideoVariant? {
        val candidates = if (videoVariants.isNotEmpty()) {
            videoVariants
        } else {
            listOfNotNull(
                originalUrl?.let {
                    VideoVariant("original", "Original", it, width, height, fileSize, null, null)
                }
            )
        }
        if (candidates.isEmpty()) return null

        val hwCandidates = candidates.filter { it.isHardwareAcceleratedOn(hardware) }.ifEmpty { candidates }

        val preferenceCap = when (preference) {
            VideoQualityPreference.LOW_480P -> 480
            VideoQualityPreference.SD_720P -> 720
            VideoQualityPreference.MP4_COMPATIBLE -> 2160
            VideoQualityPreference.ORIGINAL -> Int.MAX_VALUE
        }
        val maxHeight = minOf(preferenceCap, budget.recommendedVideoMaxHeight)

        // Filter candidates that fit inside our active resolution budget
        val withinCap = hwCandidates.filter { it.effectiveHeight() <= maxHeight }
        if (withinCap.isNotEmpty()) {
            // If user explicitly asked for Original and our tier is Ultra, pick hardware-accelerated original first
            if (preference == VideoQualityPreference.ORIGINAL && budget.tier == PerformanceTier.ULTRA) {
                withinCap.firstOrNull { it.key == "original" }?.let { return it }
            }
            // Otherwise prefer highest resolution within budget; prefer MP4/H.264 on ties, then smaller size
            return withinCap.sortedWith(
                compareByDescending<VideoVariant> { it.effectiveHeight() }
                    .thenByDescending { it.key == "mp4" || it.url.endsWith(".mp4", ignoreCase = true) }
                    .thenBy { if (it.size > 0L) it.size else Long.MAX_VALUE }
            ).first()
        }

        // Everything exceeded maxHeight (e.g. video only has a single 1080p stream): pick lightest HW variant
        return hwCandidates.sortedWith(
            compareBy<VideoVariant> { it.effectiveHeight() }
                .thenBy { if (it.size > 0L) it.size else Long.MAX_VALUE }
        ).first()
    }

    fun bestVideoVariant(preference: VideoQualityPreference): VideoVariant? {
        if (videoVariants.isEmpty()) {
            return originalUrl?.let {
                VideoVariant("original", "Original", it, width, height, fileSize, null, null)
            }
        }
        return when (preference) {
            VideoQualityPreference.ORIGINAL ->
                videoVariants.firstOrNull { it.key == "original" } ?: videoVariants.first()
            VideoQualityPreference.MP4_COMPATIBLE ->
                videoVariants.firstOrNull { it.key == "mp4" }
                    ?: videoVariants.firstOrNull { it.key == "original" && it.url.endsWith(".mp4") && it.codec?.startsWith("avc") == true }
                    ?: videoVariants.firstOrNull { it.key == "720p" }
                    ?: videoVariants.firstOrNull { it.key == "480p" }
                    ?: videoVariants.first()
            VideoQualityPreference.SD_720P ->
                videoVariants.firstOrNull { it.key == "720p" }
                    ?: videoVariants.firstOrNull { it.key == "mp4" }
                    ?: videoVariants.firstOrNull { it.key == "480p" }
                    ?: videoVariants.first()
            VideoQualityPreference.LOW_480P ->
                videoVariants.firstOrNull { it.key == "480p" }
                    ?: videoVariants.firstOrNull { it.key == "720p" }
                    ?: videoVariants.firstOrNull { it.key == "mp4" }
                    ?: videoVariants.first()
        }
    }

    val isHiddenOrDeleted: Boolean
        get() = isDeleted || (originalUrl.isNullOrBlank() && previewUrl.isNullOrBlank() && sampleUrl.isNullOrBlank())
}

data class Comment(
    val id: Int,
    val postId: Int,
    val createdAt: String,
    val updatedAt: String?,
    val creatorId: Int,
    val creatorName: String,
    val body: String,
    val score: Int,
    val userVote: Int, // -1, 0, 1
    val isSticky: Boolean,
    val isHidden: Boolean,
    val warningType: String?
)

data class Pool(
    val id: Int,
    val name: String,
    val displayName: String,
    val description: String,
    val category: String, // "series" or "collection"
    val isActive: Boolean,
    val creatorId: Int,
    val creatorName: String,
    val postIds: List<Int>,
    val postCount: Int,
    val updatedAt: String
)

data class PostSet(
    val id: Int,
    val name: String,
    val shortname: String,
    val description: String,
    val creatorId: Int,
    val isPublic: Boolean,
    val postIds: List<Int>,
    val postCount: Int,
    val updatedAt: String
)

data class PostNote(
    val id: Int,
    val postId: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val body: String,
    val isActive: Boolean,
    val creatorName: String
)

data class TagSuggestion(
    val id: Int,
    val name: String,
    val postCount: Int,
    val category: TagCategory,
    val antecedentName: String?
)

data class WikiPage(
    val id: Int,
    val title: String,
    val body: String,
    val otherNames: List<String>,
    val category: TagCategory,
    val updatedAt: String?
)

data class ArtistInfo(
    val id: Int,
    val name: String,
    val otherNames: List<String>,
    val groupName: String,
    val isActive: Boolean,
    val notes: String?,
    val urls: List<ArtistUrl>
)

data class ArtistUrl(
    val url: String,
    val isActive: Boolean
)

data class UserProfile(
    val id: Int?,
    val name: String,
    val level: Int,
    val levelString: String,
    val favoriteCount: Int,
    val postUploadCount: Int,
    val commentCount: Int,
    val avatarId: Int?,
    val blacklistedTagsRaw: String,
    val favoriteTagsRaw: String,
    val tagQueryLimit: Int,
    val perPage: Int,
    val remainingApiLimit: Int,
    val apiBurstLimit: Int,
    val isAuthenticated: Boolean
)

enum class SearchOrder(val metatag: String, val label: String) {
    RECENT("", "Newest"),
    SCORE("order:score", "Highest Score"),
    FAVORITES("order:favcount", "Most Favorites"),
    HOT("order:hot", "Trending / Hot"),
    RANDOM("order:random", "Random"),
    COMMENTS("order:comment_count", "Most Discussed"),
    SCORE_ASC("order:score_asc", "Lowest Score"),
    FAVORITES_ASC("order:favcount_asc", "Least Favorites");

    companion object {
        fun fromMetatag(tag: String): SearchOrder =
            entries.firstOrNull { it.metatag.equals(tag, ignoreCase = true) } ?: RECENT
    }
}

enum class MediaFilter(val metatag: String, val label: String) {
    ALL("", "All Media"),
    IMAGES("-animated", "Images"),
    VIDEOS("type:mp4", "MP4 Videos"),
    WEBM("type:webm", "WebM Videos"),
    ANIMATED("animated", "All Animated"),
    GIF("type:gif", "GIFs"),
    SOUND("sound", "With Sound");

    companion object {
        fun fromMetatag(tag: String): MediaFilter =
            entries.firstOrNull { it.metatag.equals(tag, ignoreCase = true) } ?: ALL
    }
}

enum class PopularScale(val apiValue: String, val label: String) {
    DAY("day", "Day"),
    WEEK("week", "Week"),
    MONTH("month", "Month")
}
