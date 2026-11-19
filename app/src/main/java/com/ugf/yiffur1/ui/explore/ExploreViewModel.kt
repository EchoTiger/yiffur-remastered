package com.ugf.yiffur1.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugf.yiffur1.data.api.ApiErrorParser
import com.ugf.yiffur1.di.AppContainer
import com.ugf.yiffur1.domain.blacklist.BlacklistEngine
import com.ugf.yiffur1.domain.model.ApiHost
import com.ugf.yiffur1.domain.model.AppSettings
import com.ugf.yiffur1.domain.model.Pool
import com.ugf.yiffur1.domain.model.PopularScale
import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.domain.model.PostSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class ExploreSection(val label: String) {
    POPULAR("Popular"),
    POOLS("Pools"),
    SETS("Sets")
}

data class ExploreUiState(
    val section: ExploreSection = ExploreSection.POPULAR,
    val popularScale: PopularScale = PopularScale.DAY,
    val popularDate: LocalDate = LocalDate.now(),
    val popularPosts: List<Post> = emptyList(),
    val isLoadingPopular: Boolean = false,
    val popularError: String? = null,
    val poolQuery: String = "",
    val poolCategoryFilter: String? = null, // null = all, "series", "collection"
    val pools: List<Pool> = emptyList(),
    val isLoadingPools: Boolean = false,
    val poolsError: String? = null,
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
        val section: ExploreSection = ExploreSection.POPULAR,
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

    private val internal = MutableStateFlow(InternalState())
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val performanceState = container.performanceEngine.state

    private val compiledBlacklist = container.database.blacklistDao().observeEnabled()
        .map { rules -> BlacklistEngine.compile(rules.map { it.rule }) }

    private var lastHost: ApiHost? = null

    val uiState: StateFlow<ExploreUiState> = combine(
        internal,
        container.settingsRepository.settingsFlow,
        compiledBlacklist,
        container.favoritesRepository.localFavoriteIdsFlow
    ) { state, settings, blacklist, favIds ->
        val visiblePopular = state.rawPopularPosts.filter { post ->
            !post.isHiddenOrDeleted &&
                settings.allowsRating(post.rating) &&
                (settings.disableBlacklist || !BlacklistEngine.isBlacklisted(post, blacklist))
        }
        ExploreUiState(
            section = state.section,
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
        loadPopular()
        viewModelScope.launch {
            container.settingsRepository.settingsFlow.collect { settings ->
                val prev = lastHost
                lastHost = settings.apiHost
                if (prev != null && prev != settings.apiHost) {
                    loadPopular()
                    if (internal.value.pools.isNotEmpty()) loadPools(reset = true)
                    if (internal.value.postSets.isNotEmpty()) loadPostSets(reset = true)
                }
            }
        }
    }

    fun selectSection(section: ExploreSection) {
        internal.update { it.copy(section = section) }
        when (section) {
            ExploreSection.POPULAR -> if (internal.value.rawPopularPosts.isEmpty() && !internal.value.isLoadingPopular) loadPopular()
            ExploreSection.POOLS -> if (internal.value.pools.isEmpty() && !internal.value.isLoadingPools) loadPools(reset = true)
            ExploreSection.SETS -> if (internal.value.postSets.isEmpty() && !internal.value.isLoadingSets) loadPostSets(reset = true)
        }
    }

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
            cur.copy(rawPopularPosts = cur.rawPopularPosts.map { if (it.id == updated.id) updated else it })
        }
    }
}
