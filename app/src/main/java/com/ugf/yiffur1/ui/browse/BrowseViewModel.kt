package com.ugf.yiffur1.ui.browse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugf.yiffur1.data.api.ApiErrorParser
import com.ugf.yiffur1.data.local.BrowseTabEntity
import com.ugf.yiffur1.data.local.SavedSearchEntity
import com.ugf.yiffur1.data.local.SearchHistoryEntity
import com.ugf.yiffur1.di.AppContainer
import com.ugf.yiffur1.domain.blacklist.BlacklistEngine
import com.ugf.yiffur1.domain.model.ApiHost
import com.ugf.yiffur1.domain.model.AppSettings
import com.ugf.yiffur1.domain.model.MediaFilter
import com.ugf.yiffur1.domain.model.PopularScale
import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.domain.model.SearchOrder
import com.ugf.yiffur1.domain.model.TagSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TabFeedCache(
    val rawPosts: List<Post> = emptyList(),
    val currentPage: Int = 1,
    val lowestPostId: Int? = null,
    val hasMore: Boolean = true,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val totalCount: Int? = null,
    val totalCountCapped: Boolean = false,
    val allFilteredByBlacklist: Boolean = false,
    val jumpedPageMode: Boolean = false
)

data class BrowseUiState(
    val selectedOrder: SearchOrder = SearchOrder.RECENT,
    val selectedMediaFilter: MediaFilter = MediaFilter.ALL,
    val tabs: List<BrowseTabEntity> = emptyList(),
    val activeTab: BrowseTabEntity? = null,
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val currentPage: Int = 1,
    val error: String? = null,
    val allFilteredByBlacklist: Boolean = false,
    val totalCount: Int? = null,
    val totalCountCapped: Boolean = false,
    val savedSearches: List<SavedSearchEntity> = emptyList(),
    val recentQueries: List<String> = emptyList(),
    val localFavoriteIds: Set<Int> = emptySet(),
    val settings: AppSettings = AppSettings()
)

class BrowseViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val tabDao = container.database.browseTabDao()
    private val savedSearchDao = container.database.savedSearchDao()
    private val historyDao = container.database.searchHistoryDao()
    private val blacklistDao = container.database.blacklistDao()

    /**
     * Synchronous Compose Snapshot text state: prevents IME cursor displacement caused by
     * async StateFlow/Dispatchers.Default round-trips and avoids re-filtering posts on every keystroke.
     */
    var searchInput by mutableStateOf("")
        private set

    private val _selectedOrder = MutableStateFlow(SearchOrder.RECENT)
    private val _selectedMediaFilter = MutableStateFlow(MediaFilter.ALL)
    private val _suggestions = MutableStateFlow<List<TagSuggestion>>(emptyList())
    val suggestions: StateFlow<List<TagSuggestion>> = _suggestions.asStateFlow()

    private val _tabCaches = MutableStateFlow<Map<Long, TabFeedCache>>(emptyMap())
    private val recentTabAccessOrder = ArrayDeque<Long>()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    val performanceBudget = container.performanceEngine.budgetState

    private var autocompleteJob: Job? = null
    private var lastObservedHost: ApiHost? = null

    private val compiledBlacklistFlow = blacklistDao.observeEnabled().map { rules ->
        BlacklistEngine.compile(rules.map { it.rule })
    }

    val uiState: StateFlow<BrowseUiState> = combine(
        combine(_selectedOrder, _selectedMediaFilter) { o, m -> o to m },
        tabDao.observeAll(),
        _tabCaches,
        combine(
            container.settingsRepository.settingsFlow,
            compiledBlacklistFlow,
            container.favoritesRepository.localFavoriteIdsFlow
        ) { settings, bl, favIds -> Triple(settings, bl, favIds) },
        combine(
            savedSearchDao.observeAll(),
            historyDao.observeRecent()
        ) { saved, hist -> saved to hist.map { it.query } }
    ) { (order, media), tabs, caches, (settings, compiledBlacklist, favIds), (savedSearches, recentQueries) ->
        val activeTab = tabs.firstOrNull { it.isActive } ?: tabs.firstOrNull()
        val cache = activeTab?.let { caches[it.id] } ?: TabFeedCache()

        val visiblePosts = cache.rawPosts.filter { post ->
            if (post.isHiddenOrDeleted) return@filter false
            if (!settings.allowsRating(post.rating)) return@filter false
            if (!settings.disableBlacklist && BlacklistEngine.isBlacklisted(post, compiledBlacklist)) {
                return@filter false
            }
            true
        }

        val allBlacklisted = cache.rawPosts.isNotEmpty() && visiblePosts.isEmpty()

        BrowseUiState(
            selectedOrder = order,
            selectedMediaFilter = media,
            tabs = tabs,
            activeTab = activeTab,
            posts = visiblePosts,
            isLoading = cache.isLoading,
            isLoadingMore = cache.isLoadingMore,
            hasMore = cache.hasMore,
            currentPage = cache.currentPage,
            error = cache.error,
            allFilteredByBlacklist = allBlacklisted,
            totalCount = cache.totalCount,
            totalCountCapped = cache.totalCountCapped,
            savedSearches = savedSearches,
            recentQueries = recentQueries,
            localFavoriteIds = favIds,
            settings = settings
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseUiState())

    init {
        viewModelScope.launch {
            val existingTabs = tabDao.getAllSnapshot()
            if (existingTabs.isEmpty()) {
                openNewSearchTab(query = "", title = "Recent", isPopular = false)
            } else {
                val active = existingTabs.firstOrNull { it.isActive } ?: existingTabs.first()
                searchInput = active.query
                loadTab(active, reset = true)
            }
        }

        // Reload active tab automatically whenever user switches between e926.net and e621.net
        viewModelScope.launch {
            container.settingsRepository.settingsFlow.collect { settings ->
                val prev = lastObservedHost
                lastObservedHost = settings.apiHost
                if (prev != null && prev != settings.apiHost) {
                    _tabCaches.value = emptyMap()
                    recentTabAccessOrder.clear()
                    val active = tabDao.getAllSnapshot().firstOrNull { it.isActive }
                    if (active != null) loadTab(active, reset = true)
                }
            }
        }
    }

    fun onSearchInputChange(newText: String) {
        searchInput = newText
        autocompleteJob?.cancel()

        val lastToken = if (newText.endsWith(" ")) "" else newText.substringAfterLast(' ').trim()
        val cleanToken = lastToken.removePrefix("-").removePrefix("~")
        if (cleanToken.length < 2 || cleanToken.contains(':')) {
            _suggestions.value = emptyList()
            return
        }

        autocompleteJob = viewModelScope.launch {
            delay(280L)
            val result = runCatching { container.postRepository.autocompleteTags(cleanToken) }
            _suggestions.value = result.getOrDefault(emptyList())
        }
    }

    fun clearSuggestions() {
        _suggestions.value = emptyList()
    }

    fun selectOrder(order: SearchOrder) {
        _selectedOrder.value = order
        rebuildDraftWithMeta(order, _selectedMediaFilter.value)
    }

    fun selectMediaFilter(filter: MediaFilter) {
        _selectedMediaFilter.value = filter
        rebuildDraftWithMeta(_selectedOrder.value, filter)
    }

    fun resetFilters() {
        _selectedOrder.value = SearchOrder.RECENT
        _selectedMediaFilter.value = MediaFilter.ALL
        rebuildDraftWithMeta(SearchOrder.RECENT, MediaFilter.ALL)
    }

    private fun rebuildDraftWithMeta(order: SearchOrder, media: MediaFilter) {
        val tokens = searchInput
            .split(Regex("\\s+"))
            .filter { token ->
                token.isNotBlank() &&
                    !token.startsWith("order:", ignoreCase = true) &&
                    !token.startsWith("type:", ignoreCase = true) &&
                    !token.equals("animated", ignoreCase = true) &&
                    !token.equals("-animated", ignoreCase = true) &&
                    !token.equals("sound", ignoreCase = true)
            }
            .toMutableList()
        if (order.metatag.isNotEmpty()) tokens.add(order.metatag)
        if (media.metatag.isNotEmpty()) tokens.add(media.metatag)
        searchInput = tokens.joinToString(" ")
    }

    fun submitSearch(rawQuery: String = searchInput) {
        val cleaned = rawQuery.replace(Regex("\\s+"), " ").trim()
        searchInput = cleaned
        _suggestions.value = emptyList()
        viewModelScope.launch {
            if (cleaned.isNotEmpty()) {
                historyDao.upsert(SearchHistoryEntity(cleaned))
            }
            val title = cleaned.ifEmpty { "Recent" }
            openNewSearchTab(query = cleaned, title = title, isPopular = false)
        }
    }

    fun openPopularTab(scale: PopularScale = PopularScale.DAY) {
        viewModelScope.launch {
            openNewSearchTab(
                query = "",
                title = "Popular (${scale.label})",
                isPopular = true,
                popularScale = scale.apiValue
            )
        }
    }

    fun openNewSearchTab(
        query: String,
        title: String = query.ifBlank { "Recent" },
        isPopular: Boolean = false,
        popularScale: String = "day"
    ) {
        viewModelScope.launch {
            val settings = container.settingsRepository.settingsFlow.first()
            if (!settings.showSearchTabs) {
                tabDao.clearAll()
                _tabCaches.value = emptyMap()
            } else {
                tabDao.deactivateAll()
            }
            val newId = tabDao.insert(
                BrowseTabEntity(
                    title = title,
                    query = query.trim(),
                    isPopular = isPopular,
                    popularScale = popularScale,
                    isActive = true
                )
            )
            searchInput = query.trim()
            val tab = BrowseTabEntity(
                id = newId,
                title = title,
                query = query.trim(),
                isPopular = isPopular,
                popularScale = popularScale,
                isActive = true
            )
            loadTab(tab, reset = true)
        }
    }

    fun selectTab(tab: BrowseTabEntity) {
        viewModelScope.launch {
            tabDao.setActive(tab.id)
            searchInput = tab.query
            touchTabCacheLru(tab.id)
            val existing = _tabCaches.value[tab.id]
            if (existing == null || (existing.rawPosts.isEmpty() && !existing.isLoading)) {
                loadTab(tab, reset = true)
            }
        }
    }

    fun refreshActiveTab() {
        val tab = uiState.value.activeTab ?: return
        loadTab(tab, reset = true)
    }

    fun closeTab(tab: BrowseTabEntity) {
        viewModelScope.launch {
            tabDao.deleteById(tab.id)
            recentTabAccessOrder.remove(tab.id)
            _tabCaches.update { it - tab.id }
            val remaining = tabDao.getAllSnapshot()
            if (remaining.isEmpty()) {
                openNewSearchTab(query = "", title = "Recent")
            } else if (tab.isActive) {
                val next = remaining.first()
                tabDao.setActive(next.id)
                searchInput = next.query
                if (_tabCaches.value[next.id]?.rawPosts.isNullOrEmpty()) {
                    loadTab(next, reset = true)
                }
            }
        }
    }

    fun closeAllTabs() {
        viewModelScope.launch {
            tabDao.clearAll()
            recentTabAccessOrder.clear()
            _tabCaches.value = emptyMap()
            openNewSearchTab(query = "", title = "Recent")
        }
    }

    fun loadNextPage() {
        val tab = uiState.value.activeTab ?: return
        val cache = _tabCaches.value[tab.id] ?: return
        if (tab.isPopular || cache.isLoading || cache.isLoadingMore || !cache.hasMore) return
        loadTab(tab, reset = false)
    }

    fun jumpToPage(pageNumber: Int) {
        val tab = uiState.value.activeTab ?: return
        if (tab.isPopular) return
        val target = pageNumber.coerceIn(1, 750)
        loadTab(tab, reset = true, explicitPage = target)
    }

    private fun loadTab(tab: BrowseTabEntity, reset: Boolean, explicitPage: Int? = null) {
        viewModelScope.launch {
            val settings = container.settingsRepository.settingsFlow.first()
            val prevCache = _tabCaches.value[tab.id] ?: TabFeedCache()

            val nextPage = when {
                explicitPage != null -> explicitPage
                reset -> 1
                else -> prevCache.currentPage + 1
            }

            // Use fast cursor pagination (`b<lowestId>`) when browsing default newest order without page jump
            val hasCustomOrder = tab.query.contains("order:", ignoreCase = true)
            val useCursor = !reset && explicitPage == null && !hasCustomOrder && !prevCache.jumpedPageMode && prevCache.lowestPostId != null

            if (!useCursor && nextPage > 750) {
                _tabCaches.update { map ->
                    map + (tab.id to prevCache.copy(hasMore = false, isLoading = false, isLoadingMore = false))
                }
                _snackbarEvents.tryEmit("Reached e621's 750-page limit. Narrow your search tags to go deeper.")
                return@launch
            }

            _tabCaches.update { map ->
                map + (tab.id to prevCache.copy(
                    rawPosts = if (reset) emptyList() else prevCache.rawPosts,
                    isLoading = reset,
                    isLoadingMore = !reset,
                    error = null,
                    currentPage = nextPage,
                    jumpedPageMode = if (explicitPage != null) true else if (reset) false else prevCache.jumpedPageMode
                ))
            }

            val pageParam = if (useCursor) "b${prevCache.lowestPostId}" else nextPage.toString()

            val result = runCatching {
                if (tab.isPopular) {
                    val scale = PopularScale.entries.firstOrNull { it.apiValue == tab.popularScale } ?: PopularScale.DAY
                    container.postRepository.getPopularPosts(scale = scale)
                } else {
                    container.postRepository.getPosts(
                        tags = tab.query,
                        limit = settings.postsPerPage,
                        page = pageParam
                    )
                }
            }

            result.onSuccess { fetched ->
                val merged = if (reset) {
                    fetched
                } else {
                    val existingIds = prevCache.rawPosts.map { it.id }.toHashSet()
                    prevCache.rawPosts + fetched.filter { it.id !in existingIds }
                }
                val lowestId = merged.minOfOrNull { it.id }
                val hasMore = !tab.isPopular && fetched.size >= (settings.postsPerPage / 2).coerceAtLeast(10)

                _tabCaches.update { map ->
                    val current = map[tab.id] ?: TabFeedCache()
                    val updatedMap = map + (tab.id to current.copy(
                        rawPosts = merged,
                        currentPage = nextPage,
                        lowestPostId = lowestId,
                        hasMore = hasMore,
                        isLoading = false,
                        isLoadingMore = false,
                        error = null
                    ))
                    pruneOldTabCaches(updatedMap, activeTabId = tab.id)
                }

                // Fetch total post count asynchronously on fresh searches
                if (reset && !tab.isPopular && tab.query.isNotBlank()) {
                    launch {
                        runCatching { container.postRepository.getPostCount(tab.query) }
                            .onSuccess { (count, capped) ->
                                _tabCaches.update { map ->
                                    val cur = map[tab.id] ?: return@update map
                                    map + (tab.id to cur.copy(totalCount = count, totalCountCapped = capped))
                                }
                            }
                    }
                }
            }.onFailure { err ->
                _tabCaches.update { map ->
                    val current = map[tab.id] ?: TabFeedCache()
                    map + (tab.id to current.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = ApiErrorParser.toUserMessage(err)
                    ))
                }
            }
        }
    }

    // ==================== Saved Searches (Quick Browse Shortcuts) ====================

    fun addSavedSearch(title: String, query: String) {
        val cleanTitle = title.trim()
        val cleanQuery = query.trim()
        if (cleanTitle.isEmpty() || cleanQuery.isEmpty()) return
        viewModelScope.launch {
            savedSearchDao.insert(SavedSearchEntity(title = cleanTitle, query = cleanQuery))
            _snackbarEvents.tryEmit("Saved shortcut \"$cleanTitle\"")
        }
    }

    fun updateSavedSearch(item: SavedSearchEntity, newTitle: String, newQuery: String) {
        val cleanTitle = newTitle.trim()
        val cleanQuery = newQuery.trim()
        if (cleanTitle.isEmpty() || cleanQuery.isEmpty()) return
        viewModelScope.launch {
            savedSearchDao.update(item.copy(title = cleanTitle, query = cleanQuery))
        }
    }

    fun deleteSavedSearch(item: SavedSearchEntity) {
        viewModelScope.launch {
            savedSearchDao.deleteById(item.id)
        }
    }

    // ==================== Quick Actions ====================

    fun quickToggleFavorite(post: Post) {
        viewModelScope.launch {
            val currentlyFav = post.isFavorited || post.id in uiState.value.localFavoriteIds
            container.favoritesRepository.toggleFavorite(post, currentlyFav)
                .onSuccess { (isFav, favCount) ->
                    updatePostInPlace(post.copy(isFavorited = isFav, favCount = favCount))
                    _snackbarEvents.tryEmit(if (isFav) "Added to favorites" else "Removed from favorites")
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(ApiErrorParser.toUserMessage(err))
                }
        }
    }

    fun downloadPost(post: Post) {
        viewModelScope.launch {
            _snackbarEvents.tryEmit("Downloading post #${post.id}…")
            val hd = uiState.value.settings.hdDownloads
            container.mediaDownloader.downloadPost(post, hd)
                .onSuccess { msg -> _snackbarEvents.tryEmit(msg) }
                .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Download failed") }
        }
    }

    fun updatePostInPlace(updated: Post) {
        _tabCaches.update { map ->
            map.mapValues { (_, cache) ->
                if (cache.rawPosts.any { it.id == updated.id }) {
                    cache.copy(rawPosts = cache.rawPosts.map { if (it.id == updated.id) updated else it })
                } else {
                    cache
                }
            }
        }
    }

    private fun touchTabCacheLru(tabId: Long) {
        recentTabAccessOrder.remove(tabId)
        recentTabAccessOrder.addLast(tabId)
    }

    private fun pruneOldTabCaches(
        map: Map<Long, TabFeedCache>,
        activeTabId: Long
    ): Map<Long, TabFeedCache> {
        touchTabCacheLru(activeTabId)
        if (map.size <= MAX_CACHED_TABS) return map
        val keepIds = recentTabAccessOrder.takeLast(MAX_CACHED_TABS).toSet() + activeTabId
        return map.filterKeys { it in keepIds }
    }

    companion object {
        private const val MAX_CACHED_TABS = 6
    }
}
