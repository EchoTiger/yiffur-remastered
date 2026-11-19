package com.ugf.yiffur1.data.api

import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit service interface aligned with the November 19, 2026 e621/e926 OpenAPI v2 spec.
 * All post endpoints request `mode=extended` to receive the modern `files`/`stats`/`has` schema
 * alongside categorized tags (`artist`, `contributor`, `species`, `character`, `copyright`, `lore`, `meta`, `general`).
 */
interface E621ApiService {

    // ==================== Posts ====================

    @GET("posts.json")
    suspend fun getPosts(
        @Query("tags") tags: String? = null,
        @Query("limit") limit: Int = 75,
        @Query("page") page: String? = null,
        @Query("mode") mode: String = "extended"
    ): List<ExtendedPostDto>

    @GET("posts/{id}.json")
    suspend fun getPost(
        @Path("id") id: Int,
        @Query("mode") mode: String = "extended"
    ): ExtendedPostDto

    @GET("posts/random.json")
    suspend fun getRandomPost(
        @Query("tags") tags: String? = null,
        @Query("mode") mode: String = "extended"
    ): ExtendedPostDto

    @GET("posts/count.json")
    suspend fun getPostCount(
        @Query("tags") tags: String
    ): PostCountResponseDto

    @GET("popular.json")
    suspend fun getPopularPosts(
        @Query("date") date: String? = null,
        @Query("scale") scale: String = "day",
        @Query("mode") mode: String = "extended"
    ): List<ExtendedPostDto>

    @POST("posts/{id}/votes.json")
    suspend fun votePost(
        @Path("id") postId: Int,
        @Query("score") score: Int,
        @Query("no_unvote") noUnvote: Boolean = false
    ): PostVoteResponseDto

    @DELETE("posts/{id}/votes.json")
    suspend fun removePostVote(
        @Path("id") postId: Int
    ): Response<Unit>

    // ==================== Favorites ====================

    @GET("favorites.json")
    suspend fun getFavorites(
        @Query("limit") limit: Int = 75,
        @Query("page") page: String? = null,
        @Query("mode") mode: String = "extended"
    ): List<ExtendedPostDto>

    @FormUrlEncoded
    @POST("favorites.json")
    suspend fun addFavorite(
        @Field("post_id") postId: Int
    ): FavoriteMutationResponseDto

    @DELETE("favorites/{id}.json")
    suspend fun removeFavorite(
        @Path("id") postId: Int
    ): FavoriteMutationResponseDto

    // ==================== Comments ====================

    @GET("comments.json")
    suspend fun getComments(
        @Query("search[post_id]") postId: Int,
        @Query("group_by") groupBy: String = "comment",
        @Query("search[order]") order: String = "id_asc",
        @Query("limit") limit: Int = 100,
        @Query("page") page: Int = 1
    ): List<CommentDto>

    @FormUrlEncoded
    @POST("comments.json")
    suspend fun createComment(
        @Field("comment[post_id]") postId: Int,
        @Field("comment[body]") body: String,
        @Field("comment[do_not_bump_post]") doNotBumpPost: Boolean = false
    ): CommentDto

    @FormUrlEncoded
    @PATCH("comments/{id}.json")
    suspend fun updateComment(
        @Path("id") commentId: Int,
        @Field("comment[body]") body: String
    ): Response<Unit>

    @POST("comments/{id}/votes.json")
    suspend fun voteComment(
        @Path("id") commentId: Int,
        @Query("score") score: Int,
        @Query("no_unvote") noUnvote: Boolean = false
    ): CommentVoteResponseDto

    @DELETE("comments/{id}/votes.json")
    suspend fun removeCommentVote(
        @Path("id") commentId: Int
    ): Response<Unit>

    // ==================== Pools & Post Sets ====================

    @GET("pools.json")
    suspend fun getPools(
        @Query("search[id]") ids: String? = null,
        @Query("search[name_matches]") nameMatches: String? = null,
        @Query("search[category]") category: String? = null,
        @Query("search[order]") order: String? = "updated_at",
        @Query("limit") limit: Int = 40,
        @Query("page") page: Int = 1
    ): List<PoolDto>

    @GET("pools/{id}.json")
    suspend fun getPool(
        @Path("id") poolId: Int
    ): PoolDto

    @GET("post_sets.json")
    suspend fun getPostSets(
        @Query("search[name]") name: String? = null,
        @Query("search[creator_name]") creatorName: String? = null,
        @Query("search[order]") order: String? = "post_count",
        @Query("limit") limit: Int = 40,
        @Query("page") page: Int = 1
    ): List<PostSetDto>

    @GET("post_sets/{id}.json")
    suspend fun getPostSet(
        @Path("id") setId: Int
    ): PostSetDto

    // ==================== Notes, Tags, Wiki, Artists ====================

    @GET("notes.json")
    suspend fun getNotes(
        @Query("search[post_id]") postId: Int,
        @Query("limit") limit: Int = 320
    ): List<NoteDto>

    @GET("tags/autocomplete.json")
    suspend fun autocompleteTags(
        @Query("search[name_matches]") query: String
    ): List<TagAutocompleteDto>

    @GET("tags.json")
    suspend fun getTags(
        @Query("search[name]") names: String? = null,
        @Query("search[name_matches]") nameMatches: String? = null,
        @Query("search[order]") order: String = "count",
        @Query("limit") limit: Int = 20
    ): List<TagDto>

    @GET("wiki_pages.json")
    suspend fun getWikiPages(
        @Query("search[title]") title: String,
        @Query("limit") limit: Int = 5
    ): List<WikiPageDto>

    @GET("artists.json")
    suspend fun getArtists(
        @Query("search[name]") name: String,
        @Query("limit") limit: Int = 5
    ): List<ArtistDto>

    // ==================== Account / Current User ====================

    @GET("users/me.json")
    suspend fun getCurrentUser(): FullCurrentUserDto

    @GET("users/me.json")
    suspend fun verifyCredentials(
        @Header("Authorization") authHeader: String
    ): FullCurrentUserDto
}
