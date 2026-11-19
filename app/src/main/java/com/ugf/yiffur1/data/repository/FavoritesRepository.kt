package com.ugf.yiffur1.data.repository

import com.ugf.yiffur1.data.api.E621ApiService
import com.ugf.yiffur1.data.api.toDomain
import com.ugf.yiffur1.data.local.LocalFavoriteDao
import com.ugf.yiffur1.data.local.LocalFavoriteEntity
import com.ugf.yiffur1.data.local.SecureCredentialStore
import com.ugf.yiffur1.data.local.SettingsRepository
import com.ugf.yiffur1.domain.model.Post
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FavoritesRepository(
    private val api: E621ApiService,
    private val localFavoriteDao: LocalFavoriteDao,
    private val settingsRepository: SettingsRepository,
    private val credentialStore: SecureCredentialStore,
    private val json: Json
) {

    val localFavoritesFlow: Flow<List<Post>> = localFavoriteDao.observeAll().map { entities ->
        entities.mapNotNull { entity ->
            runCatching { json.decodeFromString<Post>(entity.postJson) }.getOrNull()
        }
    }

    val localFavoriteIdsFlow: Flow<Set<Int>> = localFavoriteDao.observeAllIds().map { it.toSet() }

    /**
     * Toggles favorite state locally (if enabled) and remotely on e621 (if logged in & sync enabled).
     * Returns the new favorite status and updated favorite count (if remote responded).
     */
    suspend fun toggleFavorite(post: Post, currentlyFavorited: Boolean): Result<Pair<Boolean, Int>> {
        val settings = settingsRepository.settingsFlow.first()
        val isLoggedIn = credentialStore.isLoggedIn
        val shouldSyncRemote = isLoggedIn && settings.syncFavoritesToE621
        val shouldSaveLocal = !settings.disableLocalFavorites

        if (!shouldSaveLocal && !shouldSyncRemote) {
            return Result.failure(IllegalStateException("Favoriting is disabled in Settings (local off & e621 sync off)."))
        }

        val targetFav = !currentlyFavorited
        var updatedCount = (post.favCount + if (targetFav) 1 else -1).coerceAtLeast(0)

        if (shouldSyncRemote) {
            val remoteResult = runCatching {
                if (targetFav) {
                    api.addFavorite(post.id)
                } else {
                    api.removeFavorite(post.id)
                }
            }
            if (remoteResult.isFailure) {
                return Result.failure(remoteResult.exceptionOrNull()!!)
            }
            updatedCount = remoteResult.getOrThrow().favoriteCount
        }

        if (shouldSaveLocal) {
            if (targetFav) {
                val updatedPost = post.copy(isFavorited = true, favCount = updatedCount)
                saveLocalFavorite(updatedPost)
            } else {
                localFavoriteDao.deleteById(post.id)
            }
        }

        return Result.success(targetFav to updatedCount)
    }

    suspend fun saveLocalFavorite(post: Post) {
        val searchable = buildString {
            append(post.tags.allTags.joinToString(" "))
            append(' ')
            append(post.uploaderName)
            append(' ')
            append(post.id)
            append(' ')
            append("rating:${post.rating.code}")
        }.lowercase()
        localFavoriteDao.upsert(
            LocalFavoriteEntity(
                postId = post.id,
                postJson = json.encodeToString(post),
                searchableTags = searchable,
                rating = post.rating.code
            )
        )
    }

    suspend fun removeLocalFavorite(postId: Int) {
        localFavoriteDao.deleteById(postId)
    }

    suspend fun getRemoteFavorites(
        filterTags: String,
        page: Int,
        limit: Int = 75
    ): List<Post> {
        val creds = credentialStore.currentCredentials
            ?: throw IllegalStateException("Log in to your e621 account to browse account favorites.")

        val cleanFilter = filterTags.trim()
        return if (cleanFilter.isEmpty()) {
            api.getFavorites(limit = limit, page = page.toString(), mode = "extended")
                .map { it.toDomain().copy(isFavorited = true) }
        } else {
            val query = "fav:${creds.username} $cleanFilter"
            api.getPosts(tags = query, limit = limit, page = page.toString(), mode = "extended")
                .map { it.toDomain().copy(isFavorited = true) }
        }
    }
}
