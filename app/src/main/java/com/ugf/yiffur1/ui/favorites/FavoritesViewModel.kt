package com.ugf.yiffur1.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugf.yiffur1.data.api.ApiErrorParser
import com.ugf.yiffur1.di.AppContainer
import com.ugf.yiffur1.domain.blacklist.BlacklistEngine
import com.ugf.yiffur1.domain.model.AppSettings
import com.ugf.yiffur1.domain.model.Post
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FavoritesSource {
    LOCAL,
    REMOTE_E621
}

data class FavoritesUiState(
    val source: FavoritesSource = FavoritesSource.LOCAL,
    val filterQuery: String = "",
    val posts: List<Post> = emptyList(),
    val localCount: Int = 0,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreRemote: Boolean = true,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val username: String = "",
    val localFavoriteIds: Set<Int> = emptySet(),
    val settings: AppSettings = AppSettings()
)

class FavoritesViewModel(
    private val container: AppContainer
) : ViewModel() {

    private data class RemoteState(
        val source: FavoritesSource = FavoritesSource.LOCAL,
        val filterQuery: String = "",
        val submittedRemoteQuery: String = "",
        val remotePosts: List<Post> = emptyList(),
        val remotePage: Int = 1,
        val isLoading: Boolean = false,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true,
        val error: String? = null
    )

    private val state = MutableStateFlow(RemoteState())
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private val compiledBlacklist = container.database.blacklistDao().observeEnabled()
        .map { rules -> BlacklistEngine.compile(rules.map { it.rule }) }

    val uiState: StateFlow<FavoritesUiState> = combine(
        state,
        container.favoritesRepository.localFavoritesFlow,
        container.settingsRepository.settingsFlow,
        compiledBlacklist,
        container.credentialStore.credentialsFlow
    ) { st, localPosts, settings, blacklist, creds ->
        val effectiveSource = if (settings.disableLocalFavorites) FavoritesSource.REMOTE_E621 else st.source
        val localIds = localPosts.map { it.id }.toSet()

        val displayed = when (effectiveSource) {
            FavoritesSource.LOCAL -> {
                val queryTokens = st.filterQuery
                    .trim()
                    .lowercase()
                    .replace('_', ' ')
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() }

                localPosts.filter { post ->
                    if (!settings.allowsRating(post.rating)) return@filter false
                    if (queryTokens.isEmpty()) return@filter true
                    val haystack = buildString {
                        append(post.tags.allTags.joinToString(" ").replace('_', ' '))
                        append(' ')
                        append(post.uploaderName)
                        append(' ')
                        append(post.id)
                        append(" rating:")
                        append(post.rating.code)
                    }.lowercase()
                    queryTokens.all { haystack.contains(it) }
                }
            }

            FavoritesSource.REMOTE_E621 -> {
                st.remotePosts.filter { post ->
                    !post.isHiddenOrDeleted &&
                        settings.allowsRating(post.rating) &&
                        (settings.disableBlacklist || !BlacklistEngine.isBlacklisted(post, blacklist))
                }
            }
        }

        FavoritesUiState(
            source = effectiveSource,
            filterQuery = st.filterQuery,
            posts = displayed,
            localCount = localPosts.size,
            isLoading = effectiveSource == FavoritesSource.REMOTE_E621 && st.isLoading,
            isLoadingMore = effectiveSource == FavoritesSource.REMOTE_E621 && st.isLoadingMore,
            hasMoreRemote = effectiveSource == FavoritesSource.REMOTE_E621 && st.hasMore,
            error = if (effectiveSource == FavoritesSource.REMOTE_E621) st.error else null,
            isLoggedIn = creds != null,
            username = creds?.username.orEmpty(),
            localFavoriteIds = localIds,
            settings = settings
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState())

    fun selectSource(source: FavoritesSource) {
        state.update { it.copy(source = source, error = null) }
        if (source == FavoritesSource.REMOTE_E621 && container.credentialStore.isLoggedIn) {
            if (state.value.remotePosts.isEmpty() && !state.value.isLoading) {
                loadRemoteFavorites(reset = true)
            }
        }
    }

    fun onFilterQueryChange(query: String) {
        state.update { it.copy(filterQuery = query) }
    }

    fun submitRemoteFilter() {
        if (uiState.value.source != FavoritesSource.REMOTE_E621) return
        state.update { it.copy(submittedRemoteQuery = it.filterQuery.trim()) }
        loadRemoteFavorites(reset = true)
    }

    fun refreshRemote() {
        loadRemoteFavorites(reset = true)
    }

    fun loadNextRemotePage() {
        val snap = state.value
        if (snap.isLoading || snap.isLoadingMore || !snap.hasMore) return
        loadRemoteFavorites(reset = false)
    }

    private fun loadRemoteFavorites(reset: Boolean) {
        if (!container.credentialStore.isLoggedIn) return
        viewModelScope.launch {
            val snap = state.value
            val settings = container.settingsRepository.settingsFlow.first()
            val page = if (reset) 1 else snap.remotePage + 1

            state.update {
                it.copy(
                    isLoading = reset,
                    isLoadingMore = !reset,
                    error = null,
                    remotePosts = if (reset) emptyList() else it.remotePosts
                )
            }

            runCatching {
                container.favoritesRepository.getRemoteFavorites(
                    filterTags = snap.submittedRemoteQuery,
                    page = page,
                    limit = settings.postsPerPage
                )
            }.onSuccess { fetched ->
                state.update { cur ->
                    val merged = if (reset) fetched else (cur.remotePosts + fetched).distinctBy { it.id }
                    cur.copy(
                        remotePosts = merged,
                        remotePage = page,
                        hasMore = fetched.size >= (settings.postsPerPage / 2).coerceAtLeast(10),
                        isLoading = false,
                        isLoadingMore = false
                    )
                }
            }.onFailure { err ->
                state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = ApiErrorParser.toUserMessage(err)
                    )
                }
            }
        }
    }

    fun quickToggleFavorite(post: Post) {
        viewModelScope.launch {
            val isFav = post.isFavorited || post.id in uiState.value.localFavoriteIds
            container.favoritesRepository.toggleFavorite(post, isFav)
                .onSuccess { (nowFav, count) ->
                    if (!nowFav) {
                        state.update { cur ->
                            cur.copy(remotePosts = cur.remotePosts.filterNot { it.id == post.id })
                        }
                    } else {
                        updatePostInPlace(post.copy(isFavorited = true, favCount = count))
                    }
                    _events.tryEmit(if (nowFav) "Added to favorites" else "Removed from favorites")
                }
                .onFailure { err -> _events.tryEmit(ApiErrorParser.toUserMessage(err)) }
        }
    }

    fun downloadPost(post: Post) {
        viewModelScope.launch {
            _events.tryEmit("Downloading post #${post.id}…")
            container.mediaDownloader.downloadPost(post, uiState.value.settings.hdDownloads)
                .onSuccess { _events.tryEmit(it) }
                .onFailure { _events.tryEmit(it.message ?: "Download failed") }
        }
    }

    fun updatePostInPlace(updated: Post) {
        state.update { cur ->
            cur.copy(remotePosts = cur.remotePosts.map { if (it.id == updated.id) updated else it })
        }
    }
}
