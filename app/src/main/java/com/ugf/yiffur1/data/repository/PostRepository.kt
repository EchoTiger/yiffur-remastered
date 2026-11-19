package com.ugf.yiffur1.data.repository

import com.ugf.yiffur1.data.api.E621ApiService
import com.ugf.yiffur1.data.api.PostVoteResponseDto
import com.ugf.yiffur1.data.api.CommentVoteResponseDto
import com.ugf.yiffur1.data.api.toDomain
import com.ugf.yiffur1.domain.model.ArtistInfo
import com.ugf.yiffur1.domain.model.Comment
import com.ugf.yiffur1.domain.model.Pool
import com.ugf.yiffur1.domain.model.PopularScale
import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.domain.model.PostNote
import com.ugf.yiffur1.domain.model.PostSet
import com.ugf.yiffur1.domain.model.TagSuggestion
import com.ugf.yiffur1.domain.model.UserProfile
import com.ugf.yiffur1.domain.model.WikiPage
import okhttp3.Credentials

class PostRepository(
    private val api: E621ApiService
) {

    suspend fun getPosts(
        tags: String,
        limit: Int = 75,
        page: String? = null
    ): List<Post> {
        val cleaned = tags.trim().takeIf { it.isNotEmpty() }
        return api.getPosts(tags = cleaned, limit = limit, page = page, mode = "extended")
            .map { it.toDomain() }
    }

    suspend fun getPost(postId: Int): Post {
        return api.getPost(id = postId, mode = "extended").toDomain()
    }

    suspend fun getRandomPost(tags: String? = null): Post {
        val cleaned = tags?.trim()?.takeIf { it.isNotEmpty() }
        return api.getRandomPost(tags = cleaned, mode = "extended").toDomain()
    }

    suspend fun getPostCount(tags: String): Pair<Int, Boolean> {
        val resp = api.getPostCount(tags = tags.trim())
        return resp.count to resp.capped
    }

    suspend fun getPopularPosts(
        scale: PopularScale = PopularScale.DAY,
        date: String? = null
    ): List<Post> {
        return api.getPopularPosts(date = date, scale = scale.apiValue, mode = "extended")
            .map { it.toDomain() }
    }

    suspend fun votePost(postId: Int, score: Int, noUnvote: Boolean = false): PostVoteResponseDto {
        return api.votePost(postId = postId, score = score, noUnvote = noUnvote)
    }

    suspend fun getComments(postId: Int, page: Int = 1, limit: Int = 100): List<Comment> {
        return api.getComments(postId = postId, groupBy = "comment", order = "id_asc", limit = limit, page = page)
            .map { it.toDomain() }
    }

    suspend fun createComment(postId: Int, body: String, doNotBumpPost: Boolean = false): Comment {
        return api.createComment(postId = postId, body = body, doNotBumpPost = doNotBumpPost).toDomain()
    }

    suspend fun updateComment(commentId: Int, body: String) {
        api.updateComment(commentId = commentId, body = body)
    }

    suspend fun voteComment(commentId: Int, score: Int, noUnvote: Boolean = false): CommentVoteResponseDto {
        return api.voteComment(commentId = commentId, score = score, noUnvote = noUnvote)
    }

    suspend fun getNotes(postId: Int): List<PostNote> {
        return api.getNotes(postId = postId)
            .filter { it.isActive }
            .map { it.toDomain() }
    }

    suspend fun getPools(
        nameQuery: String? = null,
        category: String? = null,
        order: String = "updated_at",
        page: Int = 1,
        limit: Int = 40
    ): List<Pool> {
        val cleanName = nameQuery?.trim()?.takeIf { it.isNotEmpty() }
        val cleanCat = category?.trim()?.takeIf { it.isNotEmpty() }
        return api.getPools(
            nameMatches = cleanName,
            category = cleanCat,
            order = order,
            page = page,
            limit = limit
        ).map { it.toDomain() }
    }

    suspend fun getPool(poolId: Int): Pool = api.getPool(poolId).toDomain()

    suspend fun getPoolsByIds(poolIds: List<Int>): List<Pool> {
        if (poolIds.isEmpty()) return emptyList()
        return api.getPools(ids = poolIds.joinToString(","), limit = poolIds.size).map { it.toDomain() }
    }

    /**
     * Loads posts belonging to a pool in the EXACT canonical reading order specified by `pool.postIds`.
     */
    suspend fun getPoolPostsOrdered(pool: Pool): List<Post> {
        if (pool.postIds.isEmpty()) return emptyList()
        val fetched = mutableListOf<Post>()
        // e621 returns up to 320 posts per request; chunk pool ids safely
        var page = 1
        while (fetched.size < pool.postIds.size && page <= 10) {
            val batch = getPosts(tags = "pool:${pool.id}", limit = 320, page = page.toString())
            if (batch.isEmpty()) break
            fetched.addAll(batch)
            if (batch.size < 320) break
            page++
        }
        val byId = fetched.associateBy { it.id }
        return pool.postIds.mapNotNull { byId[it] }
    }

    /**
     * Loads a post's parent and all sibling/child posts cleanly ordered with parent first.
     */
    suspend fun getFamilyPosts(rootParentId: Int): List<Post> {
        val family = getPosts(tags = "parent:$rootParentId", limit = 320)
        val sorted = family.sortedBy { it.id }
        val parent = sorted.firstOrNull { it.id == rootParentId }
        val others = sorted.filter { it.id != rootParentId }
        return if (parent != null) listOf(parent) + others else sorted
    }

    suspend fun getPostSets(
        nameQuery: String? = null,
        creatorName: String? = null,
        order: String = "post_count",
        page: Int = 1,
        limit: Int = 40
    ): List<PostSet> {
        return api.getPostSets(
            name = nameQuery?.trim()?.takeIf { it.isNotEmpty() },
            creatorName = creatorName?.trim()?.takeIf { it.isNotEmpty() },
            order = order,
            page = page,
            limit = limit
        ).map { it.toDomain() }
    }

    suspend fun getPostSet(setId: Int): PostSet = api.getPostSet(setId).toDomain()

    suspend fun getPostSetPosts(set: PostSet, page: Int = 1, limit: Int = 75): List<Post> {
        return getPosts(tags = "set:${set.shortname}", limit = limit, page = page.toString())
    }

    suspend fun autocompleteTags(query: String): List<TagSuggestion> {
        val clean = query.trim().removePrefix("-").removePrefix("~")
        if (clean.length < 2) return emptyList()
        return api.autocompleteTags(clean).map { it.toDomain() }
    }

    suspend fun getWikiPage(tagTitle: String): WikiPage? {
        val pages = api.getWikiPages(title = tagTitle.trim(), limit = 5)
        return pages.firstOrNull { !it.isDeleted && it.title.equals(tagTitle.trim(), ignoreCase = true) }?.toDomain()
            ?: pages.firstOrNull { !it.isDeleted }?.toDomain()
    }

    suspend fun getArtistInfo(artistName: String): ArtistInfo? {
        val artists = api.getArtists(name = artistName.trim(), limit = 5)
        return artists.firstOrNull { it.name.equals(artistName.trim(), ignoreCase = true) }?.toDomain()
            ?: artists.firstOrNull()?.toDomain()
    }

    suspend fun getCurrentUser(): UserProfile {
        return api.getCurrentUser().toDomain()
    }

    suspend fun verifyCredentials(username: String, apiKey: String): UserProfile {
        val header = Credentials.basic(username.trim(), apiKey.trim())
        val profile = api.verifyCredentials(header).toDomain()
        require(profile.isAuthenticated) { "Authentication failed. Check username and API key." }
        return profile
    }
}
