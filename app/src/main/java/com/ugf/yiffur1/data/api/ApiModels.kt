package com.ugf.yiffur1.data.api

import com.ugf.yiffur1.domain.model.ArtistInfo
import com.ugf.yiffur1.domain.model.ArtistUrl
import com.ugf.yiffur1.domain.model.Comment
import com.ugf.yiffur1.domain.model.Pool
import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.domain.model.PostNote
import com.ugf.yiffur1.domain.model.PostSet
import com.ugf.yiffur1.domain.model.PostTags
import com.ugf.yiffur1.domain.model.Rating
import com.ugf.yiffur1.domain.model.TagCategory
import com.ugf.yiffur1.domain.model.TagSuggestion
import com.ugf.yiffur1.domain.model.UserProfile
import com.ugf.yiffur1.domain.model.VideoVariant
import com.ugf.yiffur1.domain.model.WikiPage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Exact November 2026 e621/e926 API v2 ExtendedPost schema (`mode=extended`).
 */
@Serializable
data class ExtendedPostDto(
    val id: Int,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("change_seq") val changeSeq: Long? = null,
    val files: PostFilesDto = PostFilesDto(),
    @SerialName("uploader_id") val uploaderId: Int = 0,
    @SerialName("uploader_name") val uploaderName: String = "",
    @SerialName("approver_id") val approverId: Int? = null,
    val stats: PostStatsDto = PostStatsDto(),
    val flags: PostFlagsDto = PostFlagsDto(),
    val has: PostHasDto = PostHasDto(),
    val relationships: PostRelationshipsDto = PostRelationshipsDto(),
    val pools: List<Int> = emptyList(),
    val rating: String = "s",
    @SerialName("locked_tags") val lockedTags: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
    val description: String = "",
    val tags: PostTagsDto = PostTagsDto()
)

@Serializable
data class PostFilesDto(
    val meta: PostFileMetaDto = PostFileMetaDto(),
    val original: PostOriginalFileDto = PostOriginalFileDto(),
    val preview: PostImageVariantDto? = null,
    val sample: PostImageVariantDto? = null,
    val video: PostVideoFilesDto? = null
)

@Serializable
data class PostFileMetaDto(
    val md5: String? = null,
    val ext: String = "",
    val size: Long = 0L,
    val duration: Double? = null,
    @SerialName("has_sample") val hasSample: Boolean = false
)

@Serializable
data class PostOriginalFileDto(
    val width: Int = 0,
    val height: Int = 0,
    val url: String? = null
)

@Serializable
data class PostImageVariantDto(
    val width: Int = 0,
    val height: Int = 0,
    val jpg: String? = null,
    val webp: String? = null
)

@Serializable
data class PostVideoFilesDto(
    val has: Boolean = false,
    val original: VideoStreamDto? = null,
    val variants: Map<String, VideoStreamDto?> = emptyMap(),
    val samples: Map<String, VideoStreamDto?> = emptyMap()
)

@Serializable
data class VideoStreamDto(
    val url: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val size: Long = 0L,
    val fps: Double? = null,
    val codec: String? = null
)

@Serializable
data class PostStatsDto(
    val score: PostScoreDto = PostScoreDto(),
    @SerialName("fav_count") val favCount: Int = 0,
    @SerialName("is_favorited") val isFavorited: Boolean = false,
    val vote: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    val hotness: Double? = null
)

@Serializable
data class PostScoreDto(
    val up: Int = 0,
    val down: Int = 0,
    val total: Int = 0
)

@Serializable
data class PostFlagsDto(
    val pending: Boolean = false,
    val flagged: Boolean = false,
    @SerialName("note_locked") val noteLocked: Boolean = false,
    @SerialName("status_locked") val statusLocked: Boolean = false,
    @SerialName("rating_locked") val ratingLocked: Boolean = false,
    val deleted: Boolean = false
)

@Serializable
data class PostHasDto(
    val parent: Boolean = false,
    val children: Boolean = false,
    @SerialName("active_children") val activeChildren: Boolean = false,
    val notes: Boolean = false,
    val sample: Boolean = false
)

@Serializable
data class PostRelationshipsDto(
    @SerialName("parent_id") val parentId: Int? = null,
    val children: List<Int> = emptyList()
)

@Serializable
data class PostTagsDto(
    val general: List<String> = emptyList(),
    val artist: List<String> = emptyList(),
    val contributor: List<String> = emptyList(),
    val copyright: List<String> = emptyList(),
    val character: List<String> = emptyList(),
    val species: List<String> = emptyList(),
    val invalid: List<String> = emptyList(),
    val meta: List<String> = emptyList(),
    val lore: List<String> = emptyList()
)

@Serializable
data class CommentDto(
    val id: Int,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("post_id") val postId: Int = 0,
    @SerialName("creator_id") val creatorId: Int = 0,
    @SerialName("creator_name") val creatorName: String = "",
    val body: String = "",
    val score: Int = 0,
    val vote: Int = 0,
    @SerialName("do_not_bump_post") val doNotBumpPost: Boolean = false,
    @SerialName("is_hidden") val isHidden: Boolean = false,
    @SerialName("is_sticky") val isSticky: Boolean = false,
    @SerialName("warning_type") val warningType: String? = null
)

@Serializable
data class PoolDto(
    val id: Int,
    val name: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("creator_id") val creatorId: Int = 0,
    @SerialName("creator_name") val creatorName: String = "",
    val description: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
    val category: String = "series",
    @SerialName("post_ids") val postIds: List<Int> = emptyList(),
    @SerialName("post_count") val postCount: Int = 0
)

@Serializable
data class PostSetDto(
    val id: Int,
    val name: String = "",
    val shortname: String = "",
    val description: String = "",
    @SerialName("creator_id") val creatorId: Int = 0,
    @SerialName("is_public") val isPublic: Boolean = true,
    @SerialName("post_count") val postCount: Int = 0,
    @SerialName("post_ids") val postIds: List<Int> = emptyList(),
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

@Serializable
data class NoteDto(
    val id: Int,
    @SerialName("post_id") val postId: Int = 0,
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val body: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("creator_name") val creatorName: String = ""
)

@Serializable
data class TagAutocompleteDto(
    val id: Int,
    val name: String,
    @SerialName("post_count") val postCount: Int = 0,
    val category: Int = 0,
    @SerialName("antecedent_name") val antecedentName: String? = null
)

@Serializable
data class TagDto(
    val id: Int,
    val name: String,
    @SerialName("post_count") val postCount: Int = 0,
    @SerialName("related_tags") val relatedTags: String = "",
    val category: Int = 0,
    @SerialName("is_locked") val isLocked: Boolean = false
)

@Serializable
data class WikiPageDto(
    val id: Int,
    val title: String = "",
    val body: String = "",
    @SerialName("other_names") val otherNames: List<String> = emptyList(),
    @SerialName("category_id") val categoryId: Int = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false
)

@Serializable
data class ArtistDto(
    val id: Int,
    val name: String = "",
    @SerialName("other_names") val otherNames: List<String> = emptyList(),
    @SerialName("group_name") val groupName: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
    val notes: String? = null,
    val urls: List<ArtistUrlDto> = emptyList()
)

@Serializable
data class ArtistUrlDto(
    val id: Int = 0,
    val url: String = "",
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class FullCurrentUserDto(
    val id: Int? = null,
    val name: String = "Anonymous",
    val level: Int = 0,
    @SerialName("level_string") val levelString: String = "Anonymous",
    @SerialName("favorite_count") val favoriteCount: Int = 0,
    @SerialName("post_upload_count") val postUploadCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("avatar_id") val avatarId: Int? = null,
    @SerialName("blacklisted_tags") val blacklistedTags: String = "",
    @SerialName("favorite_tags") val favoriteTags: String = "",
    @SerialName("recent_tags") val recentTags: String = "",
    @SerialName("tag_query_limit") val tagQueryLimit: Int = 40,
    @SerialName("per_page") val perPage: Int = 75,
    @SerialName("remaining_api_limit") val remainingApiLimit: Int = 60,
    @SerialName("api_burst_limit") val apiBurstLimit: Int = 60,
    @SerialName("is_banned") val isBanned: Boolean = false,
    @SerialName("is_verified") val isVerified: Boolean = false
)

@Serializable
data class PostVoteResponseDto(
    val score: Int,
    val up: Int,
    val down: Int,
    @SerialName("our_score") val ourScore: Int
)

@Serializable
data class CommentVoteResponseDto(
    val score: Int,
    @SerialName("our_score") val ourScore: Int
)

@Serializable
data class FavoriteMutationResponseDto(
    @SerialName("post_id") val postId: Int,
    @SerialName("favorite_count") val favoriteCount: Int
)

@Serializable
data class PostCountResponseDto(
    val count: Int,
    val capped: Boolean = false
)

@Serializable
data class ApiErrorDto(
    val success: Boolean = false,
    val message: String? = null,
    val reason: String? = null,
    val code: String? = null
)

// ==================== Domain Mappers ====================

fun ExtendedPostDto.toDomain(): Post {
    val videoList = buildList {
        val video = files.video
        if (video != null && video.has) {
            video.variants["mp4"]?.let { v ->
                v.url?.let { url ->
                    add(VideoVariant("mp4", "MP4 (${v.height}p)", url, v.width, v.height, v.size, v.fps, v.codec))
                }
            }
            video.samples["720p"]?.let { v ->
                v.url?.let { url ->
                    add(VideoVariant("720p", "720p MP4", url, v.width, v.height, v.size, v.fps, v.codec))
                }
            }
            video.samples["480p"]?.let { v ->
                v.url?.let { url ->
                    add(VideoVariant("480p", "480p MP4", url, v.width, v.height, v.size, v.fps, v.codec))
                }
            }
            video.variants["webm"]?.let { v ->
                v.url?.let { url ->
                    add(VideoVariant("webm", "WebM (${v.height}p)", url, v.width, v.height, v.size, v.fps, v.codec))
                }
            }
            video.original?.let { v ->
                v.url?.let { url ->
                    val extLabel = files.meta.ext.uppercase().ifBlank { "ORIG" }
                    add(VideoVariant("original", "Original $extLabel (${v.height}p)", url, v.width, v.height, v.size, v.fps, v.codec))
                }
            }
        }
    }

    return Post(
        id = id,
        createdAt = createdAt,
        updatedAt = updatedAt,
        md5 = files.meta.md5,
        ext = files.meta.ext,
        fileSize = files.meta.size,
        duration = files.meta.duration,
        width = files.original.width,
        height = files.original.height,
        originalUrl = files.original.url,
        previewUrl = files.preview?.webp ?: files.preview?.jpg,
        previewFallbackUrl = files.preview?.jpg ?: files.preview?.webp,
        previewWidth = files.preview?.width ?: 0,
        previewHeight = files.preview?.height ?: 0,
        sampleUrl = files.sample?.webp ?: files.sample?.jpg,
        sampleFallbackUrl = files.sample?.jpg ?: files.sample?.webp,
        sampleWidth = files.sample?.width ?: 0,
        sampleHeight = files.sample?.height ?: 0,
        hasSample = files.meta.hasSample || has.sample,
        videoVariants = videoList,
        uploaderId = uploaderId,
        uploaderName = uploaderName,
        approverId = approverId,
        scoreUp = stats.score.up,
        scoreDown = stats.score.down,
        scoreTotal = stats.score.total,
        favCount = stats.favCount,
        isFavorited = stats.isFavorited,
        userVote = stats.vote,
        commentCount = stats.commentCount,
        rating = Rating.fromCode(rating),
        isPending = flags.pending,
        isFlagged = flags.flagged,
        isDeleted = flags.deleted,
        hasNotes = has.notes,
        parentId = relationships.parentId,
        childrenIds = relationships.children,
        hasChildren = has.children || relationships.children.isNotEmpty(),
        pools = pools,
        sources = sources,
        description = description,
        tags = PostTags(
            artist = tags.artist.sorted(),
            contributor = tags.contributor.sorted(),
            copyright = tags.copyright.sorted(),
            character = tags.character.sorted(),
            species = tags.species.sorted(),
            general = tags.general.sorted(),
            meta = tags.meta.sorted(),
            lore = tags.lore.sorted(),
            invalid = tags.invalid.sorted()
        )
    )
}

fun CommentDto.toDomain(): Comment = Comment(
    id = id,
    postId = postId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    creatorId = creatorId,
    creatorName = creatorName,
    body = body,
    score = score,
    userVote = vote,
    isSticky = isSticky,
    isHidden = isHidden,
    warningType = warningType
)

fun PoolDto.toDomain(): Pool = Pool(
    id = id,
    name = name,
    displayName = name.replace('_', ' '),
    description = description,
    category = category,
    isActive = isActive,
    creatorId = creatorId,
    creatorName = creatorName,
    postIds = postIds,
    postCount = postCount,
    updatedAt = updatedAt
)

fun PostSetDto.toDomain(): PostSet = PostSet(
    id = id,
    name = name,
    shortname = shortname,
    description = description,
    creatorId = creatorId,
    isPublic = isPublic,
    postIds = postIds,
    postCount = postCount,
    updatedAt = updatedAt
)

fun NoteDto.toDomain(): PostNote = PostNote(
    id = id,
    postId = postId,
    x = x,
    y = y,
    width = width,
    height = height,
    body = body,
    isActive = isActive,
    creatorName = creatorName
)

fun TagAutocompleteDto.toDomain(): TagSuggestion = TagSuggestion(
    id = id,
    name = name,
    postCount = postCount,
    category = TagCategory.fromId(category),
    antecedentName = antecedentName
)

fun WikiPageDto.toDomain(): WikiPage = WikiPage(
    id = id,
    title = title,
    body = body,
    otherNames = otherNames,
    category = TagCategory.fromId(categoryId),
    updatedAt = updatedAt
)

fun ArtistDto.toDomain(): ArtistInfo = ArtistInfo(
    id = id,
    name = name,
    otherNames = otherNames,
    groupName = groupName,
    isActive = isActive,
    notes = notes,
    urls = urls.map { ArtistUrl(it.url, it.isActive) }
)

fun FullCurrentUserDto.toDomain(): UserProfile = UserProfile(
    id = id,
    name = name,
    level = level,
    levelString = levelString,
    favoriteCount = favoriteCount,
    postUploadCount = postUploadCount,
    commentCount = commentCount,
    avatarId = avatarId,
    blacklistedTagsRaw = blacklistedTags,
    favoriteTagsRaw = favoriteTags,
    tagQueryLimit = tagQueryLimit,
    perPage = perPage,
    remainingApiLimit = remainingApiLimit,
    apiBurstLimit = apiBurstLimit,
    isAuthenticated = id != null
)
