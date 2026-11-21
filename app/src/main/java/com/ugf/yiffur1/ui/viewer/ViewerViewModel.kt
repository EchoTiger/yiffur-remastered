package com.ugf.yiffur1.ui.viewer

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugf.yiffur1.data.api.ApiErrorParser
import com.ugf.yiffur1.data.local.BlacklistRuleEntity
import com.ugf.yiffur1.data.local.SeenPostEntity
import com.ugf.yiffur1.di.AppContainer
import com.ugf.yiffur1.domain.blacklist.BlacklistEngine
import com.ugf.yiffur1.domain.model.ArtistInfo
import com.ugf.yiffur1.domain.model.Comment
import com.ugf.yiffur1.domain.model.Pool
import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.domain.model.PostNote
import com.ugf.yiffur1.domain.model.PostSet
import com.ugf.yiffur1.domain.model.WikiPage
import kotlinx.coroutines.Dispatchers
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Isolated per-route viewer session so nested navigation (e.g., Browse -> Post #35 ->
 * Parent/Children Collection -> Child Post -> Back -> Back) never overwrites an earlier
 * viewer's post list or crashes with IndexOutOfBoundsException.
 */
data class ViewerSession(
    val sessionId: Long,
    val posts: List<Post> = emptyList(),
    val initialIndex: Int = 0,
    val currentIndex: Int = initialIndex,
    val hasSwipedFromInitial: Boolean = false,
    val sourceFeedKey: String? = null,
    val isLoadingSinglePost: Boolean = false,
    val error: String? = null
)

data class ViewerMetadataState(
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
    val sessionId: Long = 0L,
    val title: String = "",
    val subtitle: String = "",
    val description: String = "",
    val browseQuery: String? = null,
    val isComicSeries: Boolean = false,
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Inserts/updates [key] -> [value] into an insertion-ordered map while capping its size at [maxEntries]
 * (LRU eviction of oldest keys) so long viewing sessions keep memory strictly bounded.
 */
internal fun <K, V> putBoundedLru(
    current: Map<K, V>,
    key: K,
    value: V,
    maxEntries: Int
): Map<K, V> {
    val next = LinkedHashMap<K, V>(current.size + 1)
    for ((k, v) in current) {
        if (k != key) next[k] = v
    }
    next[key] = value
    while (next.size > maxEntries) {
        val oldestKey = next.keys.firstOrNull() ?: break
        next.remove(oldestKey)
    }
    return next
}

/**
 * Pure, unit-testable state store managing isolated nested viewer/collection sessions and
 * gallery scroll-sync targets.
 */
class ViewerSessionStore {
    private val nextSessionId = AtomicLong(1L)

    private val _viewerSessions = MutableStateFlow<Map<Long, ViewerSession>>(emptyMap())
    val viewerSessions: StateFlow<Map<Long, ViewerSession>> = _viewerSessions.asStateFlow()

    private val _collectionSessions = MutableStateFlow<Map<Long, CollectionUiState>>(emptyMap())
    val collectionSessions: StateFlow<Map<Long, CollectionUiState>> = _collectionSessions.asStateFlow()

    private val _scrollSyncTargets = MutableStateFlow<Map<String, Int>>(emptyMap())
    val scrollSyncTargets: StateFlow<Map<String, Int>> = _scrollSyncTargets.asStateFlow()

    fun openPostsSession(
        posts: List<Post>,
        startIndex: Int,
        sourceFeedKey: String? = null
    ): Long {
        val id = nextSessionId.getAndIncrement()
        val clampedStart = if (posts.isEmpty()) 0 else startIndex.coerceIn(0, posts.lastIndex)
        val session = ViewerSession(
            sessionId = id,
            posts = posts,
            initialIndex = clampedStart,
            currentIndex = clampedStart,
            hasSwipedFromInitial = false,
            sourceFeedKey = sourceFeedKey,
            isLoadingSinglePost = false,
            error = null
        )
        _viewerSessions.update { it + (id to session) }
        return id
    }

    fun openLoadingSinglePostSession(): Long {
        val id = nextSessionId.getAndIncrement()
        val session = ViewerSession(
            sessionId = id,
            posts = emptyList(),
            initialIndex = 0,
            currentIndex = 0,
            isLoadingSinglePost = true,
            error = null
        )
        _viewerSessions.update { it + (id to session) }
        return id
    }

    fun resolveSinglePostSession(sessionId: Long, post: Post?, error: String?) {
        _viewerSessions.update { map ->
            val existing = map[sessionId] ?: return@update map
            map + (sessionId to existing.copy(
                posts = if (post != null) listOf(post) else emptyList(),
                initialIndex = 0,
                currentIndex = 0,
                isLoadingSinglePost = false,
                error = error
            ))
        }
    }

    fun reportViewerPosition(sessionId: Long, pageIndex: Int, postId: Int) {
        val session = _viewerSessions.value[sessionId] ?: return
        val clampedIndex = if (session.posts.isEmpty()) 0 else pageIndex.coerceIn(0, session.posts.lastIndex)
        val swiped = session.hasSwipedFromInitial || clampedIndex != session.initialIndex

        if (session.currentIndex != clampedIndex || session.hasSwipedFromInitial != swiped) {
            _viewerSessions.update { map ->
                val cur = map[sessionId] ?: return@update map
                map + (sessionId to cur.copy(currentIndex = clampedIndex, hasSwipedFromInitial = swiped))
            }
        }

        val feedKey = session.sourceFeedKey
        if (feedKey != null && swiped) {
            _scrollSyncTargets.update { it + (feedKey to postId) }
        }
    }

    fun consumeScrollSyncTarget(feedKey: String) {
        if (feedKey in _scrollSyncTargets.value) {
            _scrollSyncTargets.update { it - feedKey }
        }
    }

    fun openLoadingCollectionSession(): Long {
        val id = nextSessionId.getAndIncrement()
        _collectionSessions.update { it + (id to CollectionUiState(sessionId = id, isLoading = true)) }
        return id
    }

    fun resolveCollectionSession(sessionId: Long, state: CollectionUiState) {
        _collectionSessions.update { map ->
            if (sessionId !in map) return@update map
            map + (sessionId to state.copy(sessionId = sessionId))
        }
    }

    fun reverseCollectionOrder(sessionId: Long) {
        _collectionSessions.update { map ->
            val cur = map[sessionId] ?: return@update map
            map + (sessionId to cur.copy(posts = cur.posts.asReversed()))
        }
    }

    fun updatePostAcrossSessions(updated: Post) {
        _viewerSessions.update { map ->
            map.mapValues { (_, session) ->
                if (session.posts.any { it.id == updated.id }) {
                    session.copy(posts = session.posts.map { if (it.id == updated.id) updated else it })
                } else {
                    session
                }
            }
        }
        _collectionSessions.update { map ->
            map.mapValues { (_, col) ->
                if (col.posts.any { it.id == updated.id }) {
                    col.copy(posts = col.posts.map { if (it.id == updated.id) updated else it })
                } else {
                    col
                }
            }
        }
    }

    fun closeViewerSession(sessionId: Long) {
        if (sessionId in _viewerSessions.value) {
            _viewerSessions.update { it - sessionId }
        }
    }

    fun closeCollectionSession(sessionId: Long) {
        if (sessionId in _collectionSessions.value) {
            _collectionSessions.update { it - sessionId }
        }
    }

    fun retainOnlySessions(activeViewerIds: Set<Long>, activeCollectionIds: Set<Long>) {
        _viewerSessions.update { map ->
            if (map.keys.all { it in activeViewerIds }) map else map.filterKeys { it in activeViewerIds }
        }
        _collectionSessions.update { map ->
            if (map.keys.all { it in activeCollectionIds }) map else map.filterKeys { it in activeCollectionIds }
        }
    }
}

class ViewerViewModel(
    private val container: AppContainer,
    private val onGlobalPostUpdated: (Post) -> Unit
) : ViewModel() {

    private val sessionStore = ViewerSessionStore()
    val viewerSessions: StateFlow<Map<Long, ViewerSession>> = sessionStore.viewerSessions
    val collectionSessions: StateFlow<Map<Long, CollectionUiState>> = sessionStore.collectionSessions
    val scrollSyncTargets: StateFlow<Map<String, Int>> = sessionStore.scrollSyncTargets

    private val _metadataState = MutableStateFlow(ViewerMetadataState())
    val metadataState: StateFlow<ViewerMetadataState> = _metadataState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val _shareIntentEvents = MutableSharedFlow<Intent>(extraBufferCapacity = 2)
    val shareIntentEvents = _shareIntentEvents.asSharedFlow()

    val settings = container.settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.ugf.yiffur1.domain.model.AppSettings())

    val performanceBudget = container.performanceEngine.budgetState

    val localFavoriteIds = container.favoritesRepository.localFavoriteIdsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val credentials = container.credentialStore.credentialsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val blacklistedTagStrings: StateFlow<Set<String>> = container.database.blacklistDao().observeAll()
        .map { rules -> rules.map { it.rule.trim().lowercase() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val seenWritesSincePrune = AtomicInteger(0)

    fun openPostsSession(posts: List<Post>, startIndex: Int, sourceFeedKey: String? = null): Long =
        sessionStore.openPostsSession(posts, startIndex, sourceFeedKey)

    fun openPostByIdSession(postId: Int): Long {
        val sessionId = sessionStore.openLoadingSinglePostSession()
        viewModelScope.launch {
            runCatching { container.postRepository.getPost(postId) }
                .onSuccess { post ->
                    sessionStore.resolveSinglePostSession(sessionId, post = post, error = null)
                }
                .onFailure { err ->
                    sessionStore.resolveSinglePostSession(
                        sessionId,
                        post = null,
                        error = ApiErrorParser.toUserMessage(err)
                    )
                }
        }
        return sessionId
    }

    fun reportViewerPosition(sessionId: Long, pageIndex: Int, postId: Int) {
        sessionStore.reportViewerPosition(sessionId, pageIndex, postId)
    }

    fun consumeScrollSyncTarget(feedKey: String) {
        sessionStore.consumeScrollSyncTarget(feedKey)
    }

    fun closeViewerSession(sessionId: Long) {
        sessionStore.closeViewerSession(sessionId)
    }

    fun closeCollectionSession(sessionId: Long) {
        sessionStore.closeCollectionSession(sessionId)
    }

    fun retainOnlySessions(activeViewerIds: Set<Long>, activeCollectionIds: Set<Long>) {
        sessionStore.retainOnlySessions(activeViewerIds, activeCollectionIds)
    }

    fun loadCommentsIfNeeded(postId: Int, force: Boolean = false) {
        val current = _metadataState.value
        if (!force && (postId in current.commentsByPost || postId in current.loadingCommentsFor)) return
        viewModelScope.launch {
            _metadataState.update { it.copy(loadingCommentsFor = it.loadingCommentsFor + postId) }
            runCatching { container.postRepository.getComments(postId) }
                .onSuccess { list ->
                    _metadataState.update {
                        it.copy(
                            commentsByPost = putBoundedLru(it.commentsByPost, postId, list, MAX_CACHED_METADATA_POSTS),
                            loadingCommentsFor = it.loadingCommentsFor - postId
                        )
                    }
                }
                .onFailure { err ->
                    _metadataState.update { it.copy(loadingCommentsFor = it.loadingCommentsFor - postId) }
                    _messages.tryEmit(ApiErrorParser.toUserMessage(err))
                }
        }
    }

    fun loadNotesIfNeeded(post: Post) {
        if (!post.hasNotes || post.id in _metadataState.value.notesByPost) return
        viewModelScope.launch {
            runCatching { container.postRepository.getNotes(post.id) }
                .onSuccess { notes ->
                    _metadataState.update {
                        it.copy(notesByPost = putBoundedLru(it.notesByPost, post.id, notes, MAX_CACHED_METADATA_POSTS))
                    }
                }
        }
    }

    fun loadPostPoolsIfNeeded(post: Post) {
        if (post.pools.isEmpty() || post.id in _metadataState.value.poolsByPost) return
        viewModelScope.launch {
            runCatching { container.postRepository.getPoolsByIds(post.pools) }
                .onSuccess { pools ->
                    _metadataState.update {
                        it.copy(poolsByPost = putBoundedLru(it.poolsByPost, post.id, pools, MAX_CACHED_METADATA_POSTS))
                    }
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
                    if (resp.ourScore > 0) markPostSeen(post.id, engagementScore = 3)
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
                    if (isFav) markPostSeen(post.id, engagementScore = 3)
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
            _metadataState.update { it.copy(isPostingComment = true) }
            runCatching { container.postRepository.createComment(post.id, text, doNotBump) }
                .onSuccess { created ->
                    _metadataState.update { st ->
                        val existing = st.commentsByPost[post.id].orEmpty()
                        st.copy(
                            commentsByPost = putBoundedLru(
                                st.commentsByPost,
                                post.id,
                                existing + created,
                                MAX_CACHED_METADATA_POSTS
                            ),
                            isPostingComment = false
                        )
                    }
                    applyPostUpdate(post.copy(commentCount = post.commentCount + 1))
                    _messages.tryEmit("Comment posted!")
                }
                .onFailure { err ->
                    _metadataState.update { it.copy(isPostingComment = false) }
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
                    _metadataState.update { st ->
                        val updatedList = st.commentsByPost[postId].orEmpty().map {
                            if (it.id == commentId) it.copy(body = text) else it
                        }
                        st.copy(
                            commentsByPost = putBoundedLru(
                                st.commentsByPost,
                                postId,
                                updatedList,
                                MAX_CACHED_METADATA_POSTS
                            )
                        )
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
                    _metadataState.update { st ->
                        val updated = st.commentsByPost[postId].orEmpty().map {
                            if (it.id == comment.id) it.copy(score = resp.score, userVote = resp.ourScore) else it
                        }
                        st.copy(
                            commentsByPost = putBoundedLru(
                                st.commentsByPost,
                                postId,
                                updated,
                                MAX_CACHED_METADATA_POSTS
                            )
                        )
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

    fun addCompoundBlacklistRule(rawRule: String) {
        val normalized = BlacklistEngine.formatTerms(BlacklistEngine.parseTerms(rawRule))
        if (normalized.isBlank()) return
        viewModelScope.launch {
            val dao = container.database.blacklistDao()
            if (normalized.lowercase() in blacklistedTagStrings.value) {
                _messages.tryEmit("Rule \"$normalized\" is already in blacklist")
            } else {
                dao.insert(BlacklistRuleEntity(rule = normalized))
                val label = if (BlacklistEngine.isCompoundRule(normalized)) {
                    "Added compound blacklist rule: $normalized"
                } else {
                    "Blacklisted \"$normalized\""
                }
                _messages.tryEmit(label)
            }
        }
    }

    fun markPostSeen(postId: Int, engagementScore: Int = 1) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val dao = container.database.seenPostDao()
                dao.upsert(
                    SeenPostEntity(
                        postId = postId,
                        seenAtMs = System.currentTimeMillis(),
                        engagementScore = engagementScore
                    )
                )
                if (seenWritesSincePrune.incrementAndGet() >= 50) {
                    seenWritesSincePrune.set(0)
                    dao.pruneOld(keepCount = 2500)
                }
            }
        }
    }

    fun openWiki(tagTitle: String) {
        val clean = tagTitle.trim().replace(' ', '_')
        _metadataState.update { it.copy(activeWikiTitle = clean, activeWikiPage = null, isLoadingWiki = true) }
        viewModelScope.launch {
            val page = runCatching { container.postRepository.getWikiPage(clean) }.getOrNull()
            _metadataState.update { it.copy(activeWikiPage = page, isLoadingWiki = false) }
        }
    }

    fun dismissWiki() {
        _metadataState.update { it.copy(activeWikiTitle = null, activeWikiPage = null, isLoadingWiki = false) }
    }

    fun openArtist(artistName: String) {
        val clean = artistName.trim()
        _metadataState.update { it.copy(activeArtistName = clean, activeArtistInfo = null, isLoadingArtist = true) }
        viewModelScope.launch {
            val info = runCatching { container.postRepository.getArtistInfo(clean) }.getOrNull()
            _metadataState.update { it.copy(activeArtistInfo = info, isLoadingArtist = false) }
        }
    }

    fun dismissArtist() {
        _metadataState.update { it.copy(activeArtistName = null, activeArtistInfo = null, isLoadingArtist = false) }
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

    fun loadCollectionSession(mode: CollectionMode): Long {
        val sessionId = sessionStore.openLoadingCollectionSession()
        viewModelScope.launch {
            val currentSettings = container.settingsRepository.settingsFlow.first()
            runCatching {
                when (mode) {
                    is CollectionMode.PoolCollection -> {
                        val pool = mode.pool ?: container.postRepository.getPool(mode.poolId)
                        val posts = container.postRepository.getPoolPostsOrdered(pool)
                            .filter { !it.isHiddenOrDeleted && currentSettings.allowsRating(it.rating) }
                        CollectionUiState(
                            sessionId = sessionId,
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
                            sessionId = sessionId,
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
                            sessionId = sessionId,
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
                sessionStore.resolveCollectionSession(sessionId, ui)
            }.onFailure { err ->
                sessionStore.resolveCollectionSession(
                    sessionId,
                    CollectionUiState(
                        sessionId = sessionId,
                        isLoading = false,
                        error = ApiErrorParser.toUserMessage(err)
                    )
                )
            }
        }
        return sessionId
    }

    fun reverseCollectionOrder(sessionId: Long) {
        sessionStore.reverseCollectionOrder(sessionId)
    }

    private fun applyPostUpdate(updated: Post) {
        sessionStore.updatePostAcrossSessions(updated)
        onGlobalPostUpdated(updated)
    }

    companion object {
        private const val MAX_CACHED_METADATA_POSTS = 24
    }
}
