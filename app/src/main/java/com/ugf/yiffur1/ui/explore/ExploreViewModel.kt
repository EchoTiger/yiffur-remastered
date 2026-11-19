package com.ugf.yiffur1.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugf.yiffur1.data.api.ApiErrorParser
import com.ugf.yiffur1.data.local.MutedTasteTagEntity
import com.ugf.yiffur1.data.local.SeenPostEntity
import com.ugf.yiffur1.di.AppContainer
import com.ugf.yiffur1.domain.blacklist.BlacklistEngine
import com.ugf.yiffur1.domain.model.ApiHost
import com.ugf.yiffur1.domain.model.AppSettings
import com.ugf.yiffur1.domain.model.Pool
import com.ugf.yiffur1.domain.model.PopularScale
import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.domain.model.PostSet
import com.ugf.yiffur1.domain.recommend.DiscoveryMixMode
import com.ugf.yiffur1.domain.recommend.ForYouRecommendationEngine
import com.ugf.yiffur1.domain.recommend.SeenPostSignal
import com.ugf.yiffur1.domain.recommend.TasteProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class ExploreSection(val label: String) {
    FOR_YOU("For You"),
    POPULAR("Popular"),
    POOLS("Pools"),
    SETS("Sets")
}

data class ExploreUiState(
    val section: ExploreSection = ExploreSection.FOR_YOU,
    // Personalized "For You" Discovery Feed
    val forYouPosts: List<Post> = emptyList(),
    val tasteProfile: TasteProfile = TasteProfile(),
    val mutedTasteTags: List<String> = emptyList(),
    val mixMode: DiscoveryMixMode = DiscoveryMixMode.BALANCED_MIX,
    val focusedTasteTag: String? = null,
    val isLoadingForYou: Boolean = false,
    val isLoadingMoreForYou: Boolean = false,
    val forYouError: String? = null,
    val seenPostsCount: Int = 0,
    // Popular
    val popularScale: PopularScale = PopularScale.DAY,
    val popularDate: LocalDate = LocalDate.now(),
    val popularPosts: List<Post> = emptyList(),
    val isLoadingPopular: Boolean = false,
    val popularError: String? = null,
    // Pools
    val poolQuery: String = "",
    val poolCategoryFilter: String? = null, // null = all, "series", "collection"
    val pools: List<Pool> = emptyList(),
    val isLoadingPools: Boolean = false,
    val poolsError: String? = null,
    // Sets
    val setQuery: String = "",
    val postSets: List<PostSet> = emptyList(),
    val isLoadingSets: Boolean = false,
    val setsError: String? = null,
    val isFetchingRandom: Boolean = false,
    val localFavoriteIds: Set<Int> = emptySet(),
    val settings: AppSettings = AppSettings()
)

class ExploreViewModel(
    private val container: AppContainer
) : ViewModel() {

    private data class InternalState(
        val section: ExploreSection = ExploreSection.FOR_YOU,
        val rawForYouPosts: List<Post> = emptyList(),
        val mixMode: DiscoveryMixMode = DiscoveryMixMode.BALANCED_MIX,
        val focusedTasteTag: String? = null,
        val forYouBatchIndex: Int = 0,
        val forYouSeed: Long = System.currentTimeMillis(),
        val isLoadingForYou: Boolean = false,
        val isLoadingMoreForYou: Boolean = false,
        val forYouError: String? = null,
        val popularScale: PopularScale = PopularScale.DAY,
        val popularDate: LocalDate = LocalDate.now(),
        val rawPopularPosts: List<Post> = emptyList(),
        val isLoadingPopular: Boolean = false,
        val popularError: String? = null,
        val poolQuery: String = "",
        val poolCategoryFilter: String? = null,
        val pools: List<Pool> = emptyList(),
        val poolsPage: Int = 1,
        val isLoadingPools: Boolean = false,
        val poolsError: String? = null,
        val setQuery: String = "",
        val postSets: List<PostSet> = emptyList(),
        val setsPage: Int = 1,
        val isLoadingSets: Boolean = false,
        val setsError: String? = null,
        val isFetchingRandom: Boolean = false
    )

    private data class TasteBundle(
        val profile: TasteProfile,
        val mutedTags: List<String>,
        val seenCount: Int
    )

    private val internal = MutableStateFlow(InternalState())
    private val remoteFavCache = MutableStateFlow<List<Post>>(emptyList())
    private val remoteFavTagsRaw = MutableStateFlow("")

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val performanceState = container.performanceEngine.state

    private val enabledBlacklistRulesFlow = container.database.blacklistDao().observeEnabled()
    private val compiledBlacklist = enabledBlacklistRulesFlow
        .map { rules -> BlacklistEngine.compile(rules.map { it.rule }) }

    private val tasteBundleFlow = combine(
        container.favoritesRepository.localFavoritesFlow,
        remoteFavCache,
        combine(
            container.database.savedSearchDao().observeAll(),
            container.database.searchHistoryDao().observeRecent()
        ) { saved, hist -> saved.map { it.query } to hist.map { it.query } },
        combine(
            container.database.mutedTasteTagDao().observeAll(),
            enabledBlacklistRulesFlow,
            remoteFavTagsRaw
        ) { muted, blRules, favTags -> Triple(muted.map { it.tag }, blRules, favTags) },
        container.database.seenPostDao().observeRecent()
    ) { localFavs, remoteFavs, (savedQueries, historyQueries), (mutedTags, blRules, favTagsRaw), seenList ->
        val singleBlacklisted = blRules
            .map { it.rule.trim() }
            .filter { !it.contains(' ') && !it.contains(':') && !it.startsWith("-") && !it.startsWith("~") }
            .map { it.lowercase() }
            .toSet()

        val profile = ForYouRecommendationEngine.buildTasteProfile(
            localFavorites = localFavs,
            remoteFavorites = remoteFavs,
            savedSearchQueries = savedQueries,
            recentSearchQueries = historyQueries,
            mutedTags = mutedTags.toSet(),
            singleBlacklistedTags = singleBlacklisted,
            favoriteTagsRaw = favTagsRaw
        )
        TasteBundle(
            profile = profile,
            mutedTags = mutedTags,
            seenCount = seenList.size
        )
    }.flowOn(Dispatchers.Default)

    private var lastHost: ApiHost? = null

    val uiState: StateFlow<ExploreUiState> = combine(
        internal,
        tasteBundleFlow,
        container.settingsRepository.settingsFlow,
        compiledBlacklist,
        container.favoritesRepository.localFavoriteIdsFlow
    ) { state, taste, settings, blacklist, favIds ->
        val visibleForYou = state.rawForYouPosts.filter { post ->
            !post.isHiddenOrDeleted &&
                settings.allowsRating(post.rating) &&
                (settings.disableBlacklist || !BlacklistEngine.isBlacklisted(post, blacklist))
        }
        val visiblePopular = state.rawPopularPosts.filter { post ->
            !post.isHiddenOrDeleted &&
                settings.allowsRating(post.rating) &&
                (settings.disableBlacklist || !BlacklistEngine.isBlacklisted(post, blacklist))
        }
        ExploreUiState(
            section = state.section,
            forYouPosts = visibleForYou,
            tasteProfile = taste.profile,
            mutedTasteTags = taste.mutedTags,
            mixMode = state.mixMode,
            focusedTasteTag = state.focusedTasteTag,
            isLoadingForYou = state.isLoadingForYou,
            isLoadingMoreForYou = state.isLoadingMoreForYou,
            forYouError = state.forYouError,
            seenPostsCount = taste.seenCount,
            popularScale = state.popularScale,
            popularDate = state.popularDate,
            popularPosts = visiblePopular,
            isLoadingPopular = state.isLoadingPopular,
            popularError = state.popularError,
            poolQuery = state.poolQuery,
            poolCategoryFilter = state.poolCategoryFilter,
            pools = state.pools,
            isLoadingPools = state.isLoadingPools,
            poolsError = state.poolsError,
            setQuery = state.setQuery,
            postSets = state.postSets,
            isLoadingSets = state.isLoadingSets,
            setsError = state.setsError,
            isFetchingRandom = state.isFetchingRandom,
            localFavoriteIds = favIds,
            settings = settings
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExploreUiState())

    init {
        viewModelScope.launch {
            warmRemoteTasteSignalsIfLoggedIn()
            loadForYou(reset = true)
        }
        viewModelScope.launch {
            container.settingsRepository.settingsFlow.collect { settings ->
                val prev = lastHost
                lastHost = settings.apiHost
                if (prev != null && prev != settings.apiHost) {
                    loadForYou(reset = true)
                    if (internal.value.rawPopularPosts.isNotEmpty()) loadPopular()
                    if (internal.value.pools.isNotEmpty()) loadPools(reset = true)
                    if (internal.value.postSets.isNotEmpty()) loadPostSets(reset = true)
                }
            }
        }
    }

    private suspend fun warmRemoteTasteSignalsIfLoggedIn() {
        if (!container.credentialStore.isLoggedIn) return
        runCatching {
            val user = container.postRepository.getCurrentUser()
            if (user.isAuthenticated) {
                remoteFavTagsRaw.value = user.favoriteTagsRaw
            }
        }
        runCatching {
            val favs = container.favoritesRepository.getRemoteFavorites(filterTags = "", page = 1, limit = 40)
            remoteFavCache.value = favs
        }
    }

    fun selectSection(section: ExploreSection) {
        internal.update { it.copy(section = section) }
        when (section) {
            ExploreSection.FOR_YOU -> if (internal.value.rawForYouPosts.isEmpty() && !internal.value.isLoadingForYou) loadForYou(reset = true)
            ExploreSection.POPULAR -> if (internal.value.rawPopularPosts.isEmpty() && !internal.value.isLoadingPopular) loadPopular()
            ExploreSection.POOLS -> if (internal.value.pools.isEmpty() && !internal.value.isLoadingPools) loadPools(reset = true)
            ExploreSection.SETS -> if (internal.value.postSets.isEmpty() && !internal.value.isLoadingSets) loadPostSets(reset = true)
        }
    }

    // ==================== "For You" Personalized Discovery Feed ====================

    fun selectMixMode(mode: DiscoveryMixMode) {
        if (internal.value.mixMode == mode) return
        internal.update { it.copy(mixMode = mode, forYouSeed = System.currentTimeMillis()) }
        loadForYou(reset = true)
    }

    fun focusTasteTag(tag: String?) {
        val next = if (internal.value.focusedTasteTag.equals(tag, ignoreCase = true)) null else tag
        internal.update { it.copy(focusedTasteTag = next, forYouSeed = System.currentTimeMillis()) }
        loadForYou(reset = true)
    }

    fun shuffleForYou() {
        internal.update { it.copy(forYouSeed = System.currentTimeMillis()) }
        loadForYou(reset = true)
    }

    fun loadMoreForYou() {
        val snap = internal.value
        if (snap.isLoadingForYou || snap.isLoadingMoreForYou) return
        loadForYou(reset = false)
    }

    fun loadForYou(reset: Boolean) {
        viewModelScope.launch {
            val snap = internal.value
            if (reset && snap.isLoadingForYou) return@launch
            if (!reset && (snap.isLoadingForYou || snap.isLoadingMoreForYou)) return@launch

            val nextBatch = if (reset) 0 else snap.forYouBatchIndex + 1
            val seed = if (reset) System.currentTimeMillis() else snap.forYouSeed

            internal.update {
                it.copy(
                    isLoadingForYou = reset,
                    isLoadingMoreForYou = !reset,
                    forYouError = null,
                    forYouBatchIndex = nextBatch,
                    forYouSeed = seed,
                    rawForYouPosts = if (reset) emptyList() else it.rawForYouPosts
                )
            }

            runCatching {
                val bundle = tasteBundleFlow.first()
                val settings = container.settingsRepository.settingsFlow.first()
                val compiledBl = compiledBlacklist.first()
                val seenEntities = container.database.seenPostDao().getRecentSnapshot(limit = 2500)
                val seenMap = seenEntities.associate {
                    it.postId to SeenPostSignal(it.postId, it.seenAtMs, it.engagementScore)
                }
                val favIds = container.favoritesRepository.localFavoriteIdsFlow.first() +
                    remoteFavCache.value.map { it.id }

                val queries = ForYouRecommendationEngine.buildDiscoveryQueries(
                    profile = bundle.profile,
                    mixMode = snap.mixMode,
                    focusedTag = snap.focusedTasteTag,
                    batchIndex = nextBatch,
                    randomSeed = seed
                )

                val fetchedAll = mutableListOf<Post>()
                var lastErr: Throwable? = null

                for (q in queries) {
                    runCatching {
                        container.postRepository.getPosts(
                            tags = q.tags,
                            limit = q.limit,
                            page = q.page
                        )
                    }.onSuccess { posts ->
                        fetchedAll += posts
                    }.onFailure { err ->
                        lastErr = err
                    }
                }

                if (fetchedAll.isEmpty() && lastErr != null) {
                    throw lastErr!!
                }

                val allowed = fetchedAll.filter { post ->
                    !post.isHiddenOrDeleted &&
                        settings.allowsRating(post.rating) &&
                        (settings.disableBlacklist || !BlacklistEngine.isBlacklisted(post, compiledBl))
                }

                val existingIds = if (reset) emptySet() else snap.rawForYouPosts.map { it.id }.toHashSet()
                ForYouRecommendationEngine.rankAndDiversify(
                    candidates = allowed,
                    profile = bundle.profile,
                    seenSignals = seenMap,
                    favoritePostIds = favIds,
                    mixMode = snap.mixMode,
                    alreadyDisplayedIds = existingIds,
                    randomSeed = seed + nextBatch * 31L
                )
            }.onSuccess { ranked ->
                internal.update { cur ->
                    val merged = if (reset) ranked else (cur.rawForYouPosts + ranked).distinctBy { it.id }
                    cur.copy(
                        rawForYouPosts = merged,
                        isLoadingForYou = false,
                        isLoadingMoreForYou = false,
                        forYouError = null
                    )
                }
            }.onFailure { err ->
                internal.update {
                    it.copy(
                        isLoadingForYou = false,
                        isLoadingMoreForYou = false,
                        forYouError = ApiErrorParser.toUserMessage(err)
                    )
                }
            }
        }
    }

    fun markNotInterested(post: Post) {
        viewModelScope.launch(Dispatchers.IO) {
            container.database.seenPostDao().upsert(
                SeenPostEntity(
                    postId = post.id,
                    seenAtMs = System.currentTimeMillis(),
                    engagementScore = -5
                )
            )
            internal.update { cur ->
                cur.copy(rawForYouPosts = cur.rawForYouPosts.filterNot { it.id == post.id })
            }
            _events.tryEmit("Hid post #${post.id} — tuning For You away from it")
        }
    }

    fun muteTasteTag(tag: String) {
        val clean = tag.trim().lowercase()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            container.database.mutedTasteTagDao().upsert(MutedTasteTagEntity(clean))
            if (internal.value.focusedTasteTag.equals(clean, ignoreCase = true)) {
                internal.update { it.copy(focusedTasteTag = null) }
            }
            _events.tryEmit("Muted \"${clean.replace('_', ' ')}\" from For You recommendations")
            loadForYou(reset = true)
        }
    }

    fun unmuteTasteTag(tag: String) {
        viewModelScope.launch {
            container.database.mutedTasteTagDao().delete(tag)
            _events.tryEmit("Restored \"${tag.replace('_', ' ')}\" to For You")
        }
    }

    fun clearSeenHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            container.database.seenPostDao().clearAll()
            _events.tryEmit("Cleared viewed history — refreshing For You")
            loadForYou(reset = true)
        }
    }

    // ==================== Popular / Pools / Sets ====================

    fun selectPopularScale(scale: PopularScale) {
        internal.update { it.copy(popularScale = scale) }
        loadPopular()
    }

    fun stepPopularDate(delta: Long) {
        val current = internal.value
        val next = when (current.popularScale) {
            PopularScale.DAY -> current.popularDate.plusDays(delta)
            PopularScale.WEEK -> current.popularDate.plusWeeks(delta)
            PopularScale.MONTH -> current.popularDate.plusMonths(delta)
        }
        val clamped = if (next.isAfter(LocalDate.now())) LocalDate.now() else next
        internal.update { it.copy(popularDate = clamped) }
        loadPopular()
    }

    fun loadPopular() {
        viewModelScope.launch {
            val snap = internal.value
            internal.update { it.copy(isLoadingPopular = true, popularError = null) }
            val dateParam = snap.popularDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            runCatching {
                container.postRepository.getPopularPosts(scale = snap.popularScale, date = dateParam)
            }.onSuccess { posts ->
                internal.update { it.copy(rawPopularPosts = posts, isLoadingPopular = false) }
            }.onFailure { err ->
                internal.update { it.copy(isLoadingPopular = false, popularError = ApiErrorParser.toUserMessage(err)) }
            }
        }
    }

    fun onPoolQueryChange(query: String) {
        internal.update { it.copy(poolQuery = query) }
    }

    fun selectPoolCategory(category: String?) {
        internal.update { it.copy(poolCategoryFilter = category) }
        loadPools(reset = true)
    }

    fun loadPools(reset: Boolean) {
        viewModelScope.launch {
            val snap = internal.value
            if (snap.isLoadingPools) return@launch
            val page = if (reset) 1 else snap.poolsPage + 1
            internal.update {
                it.copy(
                    isLoadingPools = true,
                    poolsError = null,
                    pools = if (reset) emptyList() else it.pools
                )
            }
            runCatching {
                container.postRepository.getPools(
                    nameQuery = snap.poolQuery,
                    category = snap.poolCategoryFilter,
                    page = page
                )
            }.onSuccess { fetched ->
                internal.update { cur ->
                    val merged = if (reset) fetched else (cur.pools + fetched).distinctBy { it.id }
                    cur.copy(pools = merged, poolsPage = page, isLoadingPools = false)
                }
            }.onFailure { err ->
                internal.update { it.copy(isLoadingPools = false, poolsError = ApiErrorParser.toUserMessage(err)) }
            }
        }
    }

    fun onSetQueryChange(query: String) {
        internal.update { it.copy(setQuery = query) }
    }

    fun loadPostSets(reset: Boolean) {
        viewModelScope.launch {
            val snap = internal.value
            if (snap.isLoadingSets) return@launch
            val page = if (reset) 1 else snap.setsPage + 1
            internal.update {
                it.copy(
                    isLoadingSets = true,
                    setsError = null,
                    postSets = if (reset) emptyList() else it.postSets
                )
            }
            runCatching {
                container.postRepository.getPostSets(nameQuery = snap.setQuery, page = page)
            }.onSuccess { fetched ->
                internal.update { cur ->
                    val merged = if (reset) fetched else (cur.postSets + fetched).distinctBy { it.id }
                    cur.copy(postSets = merged, setsPage = page, isLoadingSets = false)
                }
            }.onFailure { err ->
                internal.update { it.copy(isLoadingSets = false, setsError = ApiErrorParser.toUserMessage(err)) }
            }
        }
    }

    fun fetchRandomPost(onLoaded: (Post) -> Unit) {
        viewModelScope.launch {
            internal.update { it.copy(isFetchingRandom = true) }
            runCatching { container.postRepository.getRandomPost() }
                .onSuccess { post ->
                    internal.update { it.copy(isFetchingRandom = false) }
                    onLoaded(post)
                }
                .onFailure { err ->
                    internal.update { it.copy(isFetchingRandom = false) }
                    _events.tryEmit(ApiErrorParser.toUserMessage(err))
                }
        }
    }

    fun quickToggleFavorite(post: Post) {
        viewModelScope.launch {
            val fav = post.isFavorited || post.id in uiState.value.localFavoriteIds
            container.favoritesRepository.toggleFavorite(post, fav)
                .onSuccess { (newFav, count) ->
                    updatePostInPlace(post.copy(isFavorited = newFav, favCount = count))
                    _events.tryEmit(if (newFav) "Added to favorites" else "Removed from favorites")
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
        internal.update { cur ->
            cur.copy(
                rawForYouPosts = cur.rawForYouPosts.map { if (it.id == updated.id) updated else it },
                rawPopularPosts = cur.rawPopularPosts.map { if (it.id == updated.id) updated else it }
            )
        }
    }
}
