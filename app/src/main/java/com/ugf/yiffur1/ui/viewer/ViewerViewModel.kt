package com.ugf.yiffur1.ui.viewer

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugf.yiffur1.data.api.ApiErrorParser
import com.ugf.yiffur1.data.local.BlacklistRuleEntity
import com.ugf.yiffur1.di.AppContainer
import com.ugf.yiffur1.domain.model.ArtistInfo
import com.ugf.yiffur1.domain.model.Comment
import com.ugf.yiffur1.domain.model.Pool
import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.domain.model.PostNote
import com.ugf.yiffur1.domain.model.PostSet
import com.ugf.yiffur1.domain.model.WikiPage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ViewerSessionState(
    val posts: List<Post> = emptyList(),
    val initialIndex: Int = 0,
    val isLoadingSinglePost: Boolean = false,
    val error: String? = null,
    val commentsByPost: Map<Int, List<Comment>> = emptyMap(),
    val loadingCommentsFor: Set<Int> = emptySet(),
    val notesByPost: Map<Int, List<PostNote>> = emptyMap(),
    val poolsByPost: Map<Int, List<Pool>> = emptyMap(),
    val isPostingComment: Boolean = false,
    val activeWikiTitle: String? = null,
    val activeWikiPage: WikiPage? = null,
    val isLoadingWiki: Boolean = false,
    val activeArtistName: String? = null,
    val activeArtistInfo: ArtistInfo? = null,
    val isLoadingArtist: Boolean = false
)

sealed interface CollectionMode {
    data class PoolCollection(val poolId: Int, val pool: Pool? = null) : CollectionMode
    data class FamilyCollection(val rootParentId: Int) : CollectionMode
    data class PostSetCollection(val postSet: PostSet) : CollectionMode
}

data class CollectionUiState(
    val title: String = "",
    val subtitle: String = "",
    val description: String = "",
    val browseQuery: String? = null,
    val isComicSeries: Boolean = false,
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ViewerViewModel(
    private val container: AppContainer,
    private val onGlobalPostUpdated: (Post) -> Unit
) : ViewModel() {

    private val _viewerState = MutableStateFlow(ViewerSessionState())
    val viewerState: StateFlow<ViewerSessionState> = _viewerState.asStateFlow()

    private val _collectionState = MutableStateFlow(CollectionUiState())
    val collectionState: StateFlow<CollectionUiState> = _collectionState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val _shareIntentEvents = MutableSharedFlow<Intent>(extraBufferCapacity = 2)
    val shareIntentEvents = _shareIntentEvents.asSharedFlow()

    val settings = container.settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.ugf.yiffur1.domain.model.AppSettings())

    val localFavoriteIds = container.favoritesRepository.localFavoriteIdsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val credentials = container.credentialStore.credentialsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val blacklistedTagStrings: StateFlow<Set<String>> = container.database.blacklistDao().observeAll()
        .map { rules -> rules.map { it.rule.trim().lowercase() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun openPosts(posts: List<Post>, startIndex: Int) {
        _viewerState.update {
            it.copy(
                posts = posts,
                initialIndex = startIndex.coerceIn(0, (posts.size - 1).coerceAtLeast(0)),
                isLoadingSinglePost = false,
                error = null
            )
        }
    }

    fun openPostById(postId: Int) {
        viewModelScope.launch {
            _viewerState.update {
                it.copy(posts = emptyList(), initialIndex = 0, isLoadingSinglePost = true, error = null)
            }
            runCatching { container.postRepository.getPost(postId) }
                .onSuccess { post ->
                    _viewerState.update {
                        it.copy(posts = listOf(post), initialIndex = 0, isLoadingSinglePost = false)
                    }
                }
                .onFailure { err ->
                    _viewerState.update {
                        it.copy(isLoadingSinglePost = false, error = ApiErrorParser.toUserMessage(err))
                    }
                }
        }
    }

    fun loadCommentsIfNeeded(postId: Int, force: Boolean = false) {
        val current = _viewerState.value
        if (!force && (postId in current.commentsByPost || postId in current.loadingCommentsFor)) return
        viewModelScope.launch {
            _viewerState.update { it.copy(loadingCommentsFor = it.loadingCommentsFor + postId) }
            runCatching { container.postRepository.getComments(postId) }
                .onSuccess { list ->
                    _viewerState.update {
                        it.copy(
                            commentsByPost = it.commentsByPost + (postId to list),
                            loadingCommentsFor = it.loadingCommentsFor - postId
                        )
                    }
                }
                .onFailure { err ->
                    _viewerState.update { it.copy(loadingCommentsFor = it.loadingCommentsFor - postId) }
                    _messages.tryEmit(ApiErrorParser.toUserMessage(err))
                }
        }
    }

    fun loadNotesIfNeeded(post: Post) {
        if (!post.hasNotes || post.id in _viewerState.value.notesByPost) return
        viewModelScope.launch {
            runCatching { container.postRepository.getNotes(post.id) }
                .onSuccess { notes ->
                    _viewerState.update { it.copy(notesByPost = it.notesByPost + (post.id to notes)) }
                }
        }
    }

    fun loadPostPoolsIfNeeded(post: Post) {
        if (post.pools.isEmpty() || post.id in _viewerState.value.poolsByPost) return
        viewModelScope.launch {
            runCatching { container.postRepository.getPoolsByIds(post.pools) }
                .onSuccess { pools ->
                    _viewerState.update { it.copy(poolsByPost = it.poolsByPost + (post.id to pools)) }
                }
        }
    }

    fun votePost(post: Post, scoreDelta: Int) {
        if (!container.credentialStore.isLoggedIn) {
            _messages.tryEmit("Log in via Settings to vote on posts.")
            return
        }
        viewModelScope.launch {
            runCatching { container.postRepository.votePost(post.id, scoreDelta) }
                .onSuccess { resp ->
                    val updated = post.copy(
                        scoreTotal = resp.score,
                        scoreUp = resp.up,
                        scoreDown = resp.down,
                        userVote = resp.ourScore
                    )
                    applyPostUpdate(updated)
                }
                .onFailure { err -> _messages.tryEmit(ApiErrorParser.toUserMessage(err)) }
        }
    }

    fun toggleFavorite(post: Post) {
        viewModelScope.launch {
            val currentlyFav = post.isFavorited || post.id in localFavoriteIds.value
            container.favoritesRepository.toggleFavorite(post, currentlyFav)
                .onSuccess { (isFav, favCount) ->
                    applyPostUpdate(post.copy(isFavorited = isFav, favCount = favCount))
                    _messages.tryEmit(if (isFav) "Added to favorites" else "Removed from favorites")
                }
                .onFailure { err -> _messages.tryEmit(ApiErrorParser.toUserMessage(err)) }
        }
    }

    fun postComment(post: Post, body: String, doNotBump: Boolean = false) {
        val text = body.trim()
        if (text.isEmpty()) {
            _messages.tryEmit("Comment cannot be empty.")
            return
        }
        if (!container.credentialStore.isLoggedIn) {
            _messages.tryEmit("Log in via Settings to post comments.")
            return
        }
        viewModelScope.launch {
            _viewerState.update { it.copy(isPostingComment = true) }
            runCatching { container.postRepository.createComment(post.id, text, doNotBump) }
                .onSuccess { created ->
                    _viewerState.update { st ->
                        val existing = st.commentsByPost[post.id].orEmpty()
                        st.copy(
                            commentsByPost = st.commentsByPost + (post.id to (existing + created)),
                            isPostingComment = false
                        )
                    }
                    applyPostUpdate(post.copy(commentCount = post.commentCount + 1))
                    _messages.tryEmit("Comment posted!")
                }
                .onFailure { err ->
                    _viewerState.update { it.copy(isPostingComment = false) }
                    _messages.tryEmit(ApiErrorParser.toUserMessage(err))
                }
        }
    }

    fun editComment(postId: Int, commentId: Int, newBody: String) {
        val text = newBody.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            runCatching { container.postRepository.updateComment(commentId, text) }
                .onSuccess {
                    _viewerState.update { st ->
                        val updatedList = st.commentsByPost[postId].orEmpty().map {
                            if (it.id == commentId) it.copy(body = text) else it
                        }
                        st.copy(commentsByPost = st.commentsByPost + (postId to updatedList))
                    }
                    _messages.tryEmit("Comment updated")
                }
                .onFailure { err -> _messages.tryEmit(ApiErrorParser.toUserMessage(err)) }
        }
    }

    fun voteComment(postId: Int, comment: Comment, delta: Int) {
        if (!container.credentialStore.isLoggedIn) {
            _messages.tryEmit("Log in via Settings to vote on comments.")
            return
        }
        viewModelScope.launch {
            runCatching { container.postRepository.voteComment(comment.id, delta) }
                .onSuccess { resp ->
                    _viewerState.update { st ->
                        val updated = st.commentsByPost[postId].orEmpty().map {
                            if (it.id == comment.id) it.copy(score = resp.score, userVote = resp.ourScore) else it
                        }
                        st.copy(commentsByPost = st.commentsByPost + (postId to updated))
                    }
                }
                .onFailure { err -> _messages.tryEmit(ApiErrorParser.toUserMessage(err)) }
        }
    }

    fun toggleBlacklistTag(tag: String) {
        val clean = tag.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val dao = container.database.blacklistDao()
            if (clean.lowercase() in blacklistedTagStrings.value) {
                dao.deleteByRuleText(clean)
                _messages.tryEmit("Removed \"$clean\" from blacklist")
            } else {
                dao.insert(BlacklistRuleEntity(rule = clean))
                _messages.tryEmit("Blacklisted \"$clean\"")
            }
        }
    }

    fun openWiki(tagTitle: String) {
        val clean = tagTitle.trim().replace(' ', '_')
        _viewerState.update { it.copy(activeWikiTitle = clean, activeWikiPage = null, isLoadingWiki = true) }
        viewModelScope.launch {
            val page = runCatching { container.postRepository.getWikiPage(clean) }.getOrNull()
            _viewerState.update { it.copy(activeWikiPage = page, isLoadingWiki = false) }
        }
    }

    fun dismissWiki() {
        _viewerState.update { it.copy(activeWikiTitle = null, activeWikiPage = null, isLoadingWiki = false) }
    }

    fun openArtist(artistName: String) {
        val clean = artistName.trim()
        _viewerState.update { it.copy(activeArtistName = clean, activeArtistInfo = null, isLoadingArtist = true) }
        viewModelScope.launch {
            val info = runCatching { container.postRepository.getArtistInfo(clean) }.getOrNull()
            _viewerState.update { it.copy(activeArtistInfo = info, isLoadingArtist = false) }
        }
    }

    fun dismissArtist() {
        _viewerState.update { it.copy(activeArtistName = null, activeArtistInfo = null, isLoadingArtist = false) }
    }

    fun downloadPost(post: Post) {
        viewModelScope.launch {
            _messages.tryEmit("Downloading post #${post.id}…")
            val hd = settings.value.hdDownloads
            container.mediaDownloader.downloadPost(post, hd)
                .onSuccess { _messages.tryEmit(it) }
                .onFailure { _messages.tryEmit(it.message ?: "Download failed") }
        }
    }

    fun sharePostMedia(post: Post) {
        viewModelScope.launch {
            _messages.tryEmit("Preparing media to share…")
            val hd = settings.value.hdDownloads
            container.mediaDownloader.prepareShareMediaIntent(post, hd)
                .onSuccess { intent -> _shareIntentEvents.tryEmit(intent) }
                .onFailure { err -> _messages.tryEmit(err.message ?: "Failed to share media") }
        }
    }

    // ==================== Collection (Pools, Post Sets, Parent/Children) ====================

    fun loadCollection(mode: CollectionMode) {
        viewModelScope.launch {
            val currentSettings = container.settingsRepository.settingsFlow.first()
            _collectionState.value = CollectionUiState(isLoading = true)
            runCatching {
                when (mode) {
                    is CollectionMode.PoolCollection -> {
                        val pool = mode.pool ?: container.postRepository.getPool(mode.poolId)
                        val posts = container.postRepository.getPoolPostsOrdered(pool)
                            .filter { !it.isHiddenOrDeleted && currentSettings.allowsRating(it.rating) }
                        CollectionUiState(
                            title = pool.displayName,
                            subtitle = "Pool #${pool.id} · ${posts.size} posts · by ${pool.creatorName}",
                            description = pool.description,
                            browseQuery = "pool:${pool.id}",
                            isComicSeries = pool.category.equals("series", ignoreCase = true),
                            posts = posts,
                            isLoading = false
                        )
                    }

                    is CollectionMode.FamilyCollection -> {
                        val posts = container.postRepository.getFamilyPosts(mode.rootParentId)
                            .filter { !it.isHiddenOrDeleted && currentSettings.allowsRating(it.rating) }
                        CollectionUiState(
                            title = "Related Posts (#${mode.rootParentId})",
                            subtitle = "Parent & ${((posts.size - 1).coerceAtLeast(0))} children",
                            description = "",
                            browseQuery = "parent:${mode.rootParentId}",
                            isComicSeries = false,
                            posts = posts,
                            isLoading = false
                        )
                    }

                    is CollectionMode.PostSetCollection -> {
                        val posts = container.postRepository.getPostSetPosts(mode.postSet, page = 1, limit = 150)
                            .filter { !it.isHiddenOrDeleted && currentSettings.allowsRating(it.rating) }
                        CollectionUiState(
                            title = mode.postSet.name,
                            subtitle = "Set #${mode.postSet.id} (${mode.postSet.shortname}) · ${mode.postSet.postCount} posts",
                            description = mode.postSet.description,
                            browseQuery = "set:${mode.postSet.shortname}",
                            isComicSeries = false,
                            posts = posts,
                            isLoading = false
                        )
                    }
                }
            }.onSuccess { ui ->
                _collectionState.value = ui
            }.onFailure { err ->
                _collectionState.value = CollectionUiState(
                    isLoading = false,
                    error = ApiErrorParser.toUserMessage(err)
                )
            }
        }
    }

    fun reverseCollectionOrder() {
        _collectionState.update { it.copy(posts = it.posts.asReversed()) }
    }

    private fun applyPostUpdate(updated: Post) {
        _viewerState.update { st ->
            st.copy(posts = st.posts.map { if (it.id == updated.id) updated else it })
        }
        _collectionState.update { st ->
            st.copy(posts = st.posts.map { if (it.id == updated.id) updated else it })
        }
        onGlobalPostUpdated(updated)
    }
}
