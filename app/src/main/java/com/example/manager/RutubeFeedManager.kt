package com.example.manager

import com.example.data.Video
import com.example.data.VideoRepository
import com.example.data.RutubeCategory
import com.example.data.rutube.parser.*
import com.example.plugins.PluginManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

class RutubeFeedManager(
    val repository: VideoRepository,
    val playerManager: PlayerManager,
    val navigationManager: NavigationManager,
    val settingsManager: SettingsManager,
    val mediaResolver: RutubeMediaResolver,
    val coroutineScope: CoroutineScope
) {
    private val parser = RutubeParser()

    internal val categorySlugs = mapOf(
        "Фильмы" to "movies",
        "Сериалы" to "serials",
        "Телепередачи" to "tv",
        "Мультфильмы" to "cartoons",
        "Музыка" to "music",
        "Спорт" to "sport",
        "Юмор" to "umor",
        "Видеоигры" to "games",
        "Технологии" to "technologies",
        "Блоги" to "blogs",
        "Новости" to "news",
        "Лайфхаки" to "lifehacks",
        "Детям" to "kids",
        "Авто-мото" to "auto",
        "Обучение" to "education",
        "Путешествия" to "travel",
        "Кулинария" to "food",
        "Аниме" to "anime"
    )

    var currentPage = 1
    var isEndReached = false
    val isEndReachedPublic: Boolean get() = isEndReached
    var channelVideosPage = 1
    var channelVideosEndReached = false
    var channelPlaylistsPage = 1
    var channelPlaylistsEndReached = false
    var currentQuery: String? = null
    var currentCategory: String? = "Фильмы"
    var currentActiveApiEndpoint: String? = null

    var fetchJob: Job? = null
    var requestId = 0
    var searchDebounceJob: Job? = null

    val _dynamicVideos = MutableStateFlow<List<Video>>(emptyList())
    val dynamicVideos = _dynamicVideos.asStateFlow()

    val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    val _realCategories = MutableStateFlow<List<RutubeCategory>>(emptyList())
    val realCategories = _realCategories.asStateFlow()

    val _isCategoriesLoading = MutableStateFlow(true)
    val isCategoriesLoading = _isCategoriesLoading.asStateFlow()

    val _feedTabs = MutableStateFlow<List<TabInfo>>(emptyList())
    val feedTabs = _feedTabs.asStateFlow()

    val _currentChannelVideo = MutableStateFlow<Video?>(null)
    val currentChannelVideo: StateFlow<Video?> = combine(_currentChannelVideo, repository.getSavedVideosOnly()) { activeChannel, savedList ->
        if (activeChannel == null) return@combine null
        val saved = savedList.firstOrNull { it.id == activeChannel.id }
        val progress = saved?.let {
            if (it.lastDuration > 0L) {
                (it.lastProgress.toFloat() / it.lastDuration.toFloat()).coerceIn(0f, 1f)
            } else 0f
        } ?: 0f
        activeChannel.copy(
            isDownloaded = saved?.isDownloaded ?: false,
            isBookmarked = saved?.isBookmarked ?: false,
            isWatched = progress >= 0.80f,
            playbackProgress = progress
        )
    }.stateIn(coroutineScope, SharingStarted.WhileSubscribed(5000), null)

    val _currentSubfolderVideo = MutableStateFlow<Video?>(null)
    val currentSubfolderVideo: StateFlow<Video?> = combine(_currentSubfolderVideo, repository.getSavedVideosOnly()) { activeFolder, savedList ->
        if (activeFolder == null) return@combine null
        val saved = savedList.firstOrNull { it.id == activeFolder.id }
        val progress = saved?.let {
            if (it.lastDuration > 0L) {
                (it.lastProgress.toFloat() / it.lastDuration.toFloat()).coerceIn(0f, 1f)
            } else 0f
        } ?: 0f
        activeFolder.copy(
            isDownloaded = saved?.isDownloaded ?: false,
            isBookmarked = saved?.isBookmarked ?: false,
            isWatched = progress >= 0.80f,
            playbackProgress = progress
        )
    }.stateIn(coroutineScope, SharingStarted.WhileSubscribed(5000), null)

    val _channelVideos = MutableStateFlow<List<Video>>(emptyList())
    val channelVideos: StateFlow<List<Video>> = combine(_channelVideos, repository.getSavedVideosOnly()) { videos, savedList ->
        val savedMap = savedList.associateBy { it.id }
        videos.map { vid ->
            val saved = savedMap[vid.id]
            val progress = saved?.let {
                if (it.lastDuration > 0L) {
                    (it.lastProgress.toFloat() / it.lastDuration.toFloat()).coerceIn(0f, 1f)
                } else 0f
            } ?: 0f
            vid.copy(
                isDownloaded = saved?.isDownloaded ?: false,
                isBookmarked = saved?.isBookmarked ?: false,
                isWatched = progress >= 0.80f,
                playbackProgress = progress
            )
        }
    }.stateIn(coroutineScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val _channelPlaylists = MutableStateFlow<List<Video>>(emptyList())
    val channelPlaylists: StateFlow<List<Video>> = combine(_channelPlaylists, repository.getSavedVideosOnly()) { playlists, savedList ->
        val savedMap = savedList.associateBy { it.id }
        playlists.map { pl ->
            val saved = savedMap[pl.id]
            val progress = saved?.let {
                if (it.lastDuration > 0L) {
                    (it.lastProgress.toFloat() / it.lastDuration.toFloat()).coerceIn(0f, 1f)
                } else 0f
            } ?: 0f
            pl.copy(
                isDownloaded = saved?.isDownloaded ?: false,
                isBookmarked = saved?.isBookmarked ?: false,
                isWatched = progress >= 0.80f,
                playbackProgress = progress
            )
        }
    }.stateIn(coroutineScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val _isLoadingPlaylists = MutableStateFlow(false)
    val isLoadingPlaylists = _isLoadingPlaylists.asStateFlow()

    val _isMoreLoading = MutableStateFlow(false)
    val isMoreLoading = _isMoreLoading.asStateFlow()

    val _searchSource = MutableStateFlow("Rutube")
    val searchSource = _searchSource.asStateFlow()

    val resolvedPostersCache = ConcurrentHashMap<String, String>()
    val resolvingVideoIds = ConcurrentHashMap.newKeySet<String>()

    fun setSearchSource(source: String) {
        _searchSource.value = source
    }

    // DRY: Helper function to get response body string from a URL
    private suspend fun getResponseBody(url: String): String = withContext(Dispatchers.IO) {
        val response = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(url)
        response.string()
    }

    // DRY: Helper function to load and parse videos directly from a URL, reducing massive duplication
    private suspend fun fetchAndParseVideos(url: String, category: String): List<Video> {
        val cleanApiUrl = repository.toRutubeApiUrl(url)
        val separator = if (cleanApiUrl.contains("?")) {
            if (cleanApiUrl.contains("format=json")) "" else "&format=json"
        } else {
            "?format=json"
        }
        val finalUrl = "$cleanApiUrl$separator"
        return try {
            val body = getResponseBody(finalUrl)
            repository.parseVideoListJson(body, category)
        } catch (e: Exception) {
            android.util.Log.e("RutubeFeedManager", "Error parsing videos from URL: $finalUrl", e)
            emptyList()
        }
    }

    fun cleanUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        var u = url.trim()
        if (u.startsWith("//")) {
            u = "https:" + u
        }
        return u
    }

    internal fun formatDurationMs(ms: Long): String {
        if (ms <= 0) return ""
        val totalSecs = ms / 1000
        val h = totalSecs / 3600
        val m = (totalSecs % 3600) / 60
        val s = totalSecs % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
    }

    fun fetchRealVideos(query: String? = null, category: String? = null, targetUrl: String? = null) {
        fetchJob?.cancel()
        val currentRequestId = ++requestId
        navigationManager.setSubfolderName(null)
        navigationManager.setChannelView(false)
        _currentChannelVideo.value = null
        _currentSubfolderVideo.value = null
        _channelVideos.value = emptyList()
        _channelPlaylists.value = emptyList()
        navigationManager.setChannelActiveTab("Видео")
        currentQuery = query
        val targetCategory = category ?: navigationManager.selectedCategory.value
        var currentTargetCategory = targetCategory
        currentCategory = targetCategory
        currentPage = 1
        isEndReached = false
        currentActiveApiEndpoint = null
        
        fetchJob = coroutineScope.launch {
            _isLoading.value = true
            try {
                val q = query?.trim() ?: ""
                val searchSrc = _searchSource.value
                
                if (q.isNotEmpty() && searchSrc != "Rutube") {
                    val pluginResults = withContext(Dispatchers.IO) {
                        val plugin = PluginManager.getPlugins().find { it.name == searchSrc }
                        plugin?.search(q, 30) ?: emptyList()
                    }
                    
                    val mapped = pluginResults.map { item ->
                        val safeId = if (item.source == "VK Video" || item.url.contains("vk.com") || item.url.contains("vkvideo.ru")) {
                            "vk_${item.id}"
                        } else {
                            "plugin_${item.source.replace(" ", "_")}_${item.id}"
                        }
                        mediaResolver.masterUrlCache.put(safeId + "_page", item.url)
                        
                        Video(
                            id = safeId,
                            title = item.title,
                            channel = item.author.ifEmpty { item.source },
                            views = item.views,
                            timeAgo = "Только что",
                            duration = formatDurationMs(item.duration),
                            category = item.source,
                            description = "Поиск ${item.source}",
                            thumbnailUrl = item.thumbnail,
                            isDownloaded = false,
                            isBookmarked = false,
                            pageUrl = item.url
                        )
                    }
                    
                    if (currentRequestId == requestId) {
                        _dynamicVideos.value = mapped
                        _feedTabs.value = emptyList()
                        _isLoading.value = false
                    }
                    return@launch
                }
                if (q.isNotEmpty()) {
                    // Check if plugin URL
                    val pluginInfo = withContext(Dispatchers.IO) {
                        PluginManager.getVideoInfo(q)
                    }
                    if (pluginInfo != null) {
                        val safeId = if (pluginInfo.source == "VK Video" || pluginInfo.url.contains("vk.com") || pluginInfo.url.contains("vkvideo.ru")) {
                            "vk_${pluginInfo.id}"
                        } else {
                            "plugin_${pluginInfo.source.replace(" ", "_")}_${pluginInfo.id}"
                        }
                        mediaResolver.masterUrlCache.put(safeId + "_page", pluginInfo.url)
                        
                        val pluginVideo = Video(
                            id = safeId,
                            title = pluginInfo.title,
                            channel = pluginInfo.author.ifEmpty { pluginInfo.source },
                            views = pluginInfo.views,
                            timeAgo = "Только что",
                            duration = formatDurationMs(pluginInfo.duration),
                            category = pluginInfo.source,
                            description = "Видео из ${pluginInfo.source}. Воспроизведение и скачивание.",
                            thumbnailUrl = pluginInfo.thumbnail,
                            isDownloaded = false,
                            isBookmarked = false,
                            pageUrl = pluginInfo.url
                        )
                        val savedMap = repository.getSavedVideosOnly().first().associateBy { it.id }
                        val saved = savedMap[safeId]
                        val finalVideo = pluginVideo.copy(
                            isDownloaded = saved?.isDownloaded ?: false,
                            isBookmarked = saved?.isBookmarked ?: false
                        )
                        if (currentRequestId == requestId) {
                            _feedTabs.value = emptyList()
                            navigationManager.setFeedTab(null)
                            _dynamicVideos.value = listOf(finalVideo)
                        }
                        return@launch
                    }
                    if (q.contains("rutube.ru") || q.contains("rutube")) {
                        val rtVideoId = parseVideoIdFromRutubeUrl(q)
                        if (rtVideoId.isNotBlank()) {
                            try {
                                val apiUrl = "https://rutube.ru/api/video/$rtVideoId/?format=json"
                                val bodyStr = getResponseBody(apiUrl)
                                val parsedVideoList = repository.parseVideoListJson(bodyStr, "Разное")
                                
                                val resolvedVideo = if (parsedVideoList.isNotEmpty()) {
                                    parsedVideoList.first()
                                } else {
                                    Video(
                                        id = rtVideoId,
                                        title = "Видео Rutube ($rtVideoId)",
                                        channel = "Rutube",
                                        views = "",
                                        timeAgo = "Только что",
                                        duration = "00:00",
                                        category = "Разное",
                                        description = "Импортировано из внешнего приложения. Приятного просмотра!",
                                        thumbnailUrl = ""
                                    )
                                }
                                
                                val savedMap = repository.getSavedVideosOnly().first().associateBy { it.id }
                                val saved = savedMap[resolvedVideo.id]
                                val finalVideo = resolvedVideo.copy(
                                    isDownloaded = saved?.isDownloaded ?: false,
                                    isBookmarked = saved?.isBookmarked ?: false
                                )
                                if (currentRequestId == requestId) {
                                    _feedTabs.value = emptyList()
                                    navigationManager.setFeedTab(null)
                                    _dynamicVideos.value = listOf(finalVideo)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("RutubeFeedManager", "Error fetching Rutube video in search", e)
                                if (currentRequestId == requestId) {
                                    _dynamicVideos.value = emptyList()
                                }
                            }
                            return@launch
                        }
                    }

                    val fetched = repository.fetchRealVideos(q, currentTargetCategory, page = 1)
                    if (currentRequestId == requestId) {
                        _feedTabs.value = emptyList()
                        navigationManager.setFeedTab(null)
                        _dynamicVideos.value = fetched
                    }
                } else {
                    var urlToFetch = targetUrl

                    if (urlToFetch.isNullOrBlank() && currentTargetCategory == "Стартовая") {
                        urlToFetch = settingsManager.startPageCustomUrl.value
                        if (urlToFetch.isNullOrBlank()) {
                            currentTargetCategory = "Фильмы"
                        }
                    }

                    if (urlToFetch.isNullOrBlank()) {
                        val matched = _realCategories.value.firstOrNull { it.title.equals(currentTargetCategory, ignoreCase = true) }
                        urlToFetch = matched?.target
                    }
                    if (urlToFetch.isNullOrBlank()) {
                        val slug = repository.getCategorySlug(currentTargetCategory)
                        urlToFetch = if (slug != null) "/api/feeds/$slug/" else null
                    }
                    if (urlToFetch.isNullOrBlank()) {
                        val slug = categorySlugs[currentTargetCategory]
                        urlToFetch = if (slug != null) "/api/feeds/$slug/" else null
                    }
                    if (urlToFetch.isNullOrBlank()) {
                        val slug = currentTargetCategory.lowercase()
                        urlToFetch = "/api/feeds/$slug/"
                    }
                    
                    if (!urlToFetch.isNullOrBlank()) {
                        val cleanApiUrl = repository.toRutubeApiUrl(urlToFetch)
                        val finalUrl = if (cleanApiUrl.contains("?")) {
                            if (cleanApiUrl.contains("format=json")) cleanApiUrl else "$cleanApiUrl&format=json"
                        } else {
                            "$cleanApiUrl?format=json"
                        }
                        
                        val bodyStr = getResponseBody(finalUrl)
                        if (currentRequestId != requestId) return@launch
                        val parsed = parser.parseResponse(bodyStr, finalUrl)
                        
                        if (currentRequestId != requestId) return@launch
                        
                        val filteredTabs = parsed.tabs.filter { tab ->
                            !repository.isBlockedText(tab.name, includeTvOnline = false)
                        }
                        if (filteredTabs.isNotEmpty()) {
                            _feedTabs.value = filteredTabs
                            selectFeedTabInternal(filteredTabs.first(), pushHistory = false)
                        } else {
                            _feedTabs.value = emptyList()
                            navigationManager.setFeedTab(null)
                            val parsedVideos = repository.parseVideoListJson(bodyStr, currentTargetCategory ?: "Фильмы")
                            if (parsedVideos.isNotEmpty()) {
                                _dynamicVideos.value = parsedVideos
                                currentActiveApiEndpoint = repository.toRutubeApiUrl(urlToFetch)
                            } else {
                                _dynamicVideos.value = repository.fetchRealVideos(null, currentTargetCategory, page = 1)
                            }
                        }
                    } else {
                        if (currentRequestId != requestId) return@launch
                        _feedTabs.value = emptyList()
                        navigationManager.setFeedTab(null)
                        _dynamicVideos.value = repository.fetchRealVideos(null, currentTargetCategory, page = 1)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("RutubeFeedManager", "Error in fetchRealVideos with target", e)
                if (currentRequestId == requestId) {
                    _feedTabs.value = emptyList()
                    navigationManager.setFeedTab(null)
                    _dynamicVideos.value = repository.fetchRealVideos(query, currentTargetCategory, page = 1)
                }
            } finally {
                if (currentRequestId == requestId) {
                    _isLoading.value = false
                    // Eagerly prefetch page 2 in advance right after completing the page 1 load
                    if (_dynamicVideos.value.isNotEmpty() && currentPage == 1 && !isEndReached) {
                        coroutineScope.launch {
                            delay(200)
                            if (currentRequestId == requestId) {
                                loadNextPage()
                            }
                        }
                    }
                }
            }
        }
    }

    fun loadNextPage() {
        val snapshotChannelView = navigationManager.isChannelView.value
        val snapshotChannelActiveTab = navigationManager.channelActiveTab.value

        if (snapshotChannelView) {
            if (snapshotChannelActiveTab == "Видео") {
                if (_isMoreLoading.value || channelVideosEndReached || _isLoading.value) return
            } else {
                if (_isMoreLoading.value || channelPlaylistsEndReached || _isLoadingPlaylists.value) return
            }
        } else {
            if (_isMoreLoading.value || isEndReached || _isLoading.value) return
        }
        
        val snapshotEndpoint = currentActiveApiEndpoint
        val snapshotCategory = currentCategory
        val snapshotQuery = currentQuery
        val snapshotPage = if (snapshotChannelView) {
            if (snapshotChannelActiveTab == "Видео") {
                channelVideosPage + 1
            } else {
                channelPlaylistsPage + 1
            }
        } else {
            currentPage + 1
        }
        
        if (snapshotQuery != null && snapshotQuery.isNotEmpty() && _searchSource.value != "Rutube") {
            return
        }

        coroutineScope.launch {
            _isMoreLoading.value = true
            try {
                val newVideos = if (snapshotChannelView) {
                    val channelIdRaw = _currentChannelVideo.value?.id?.substringAfter("channel_")?.substringBefore("__") ?: ""
                    val endpoint = if (snapshotChannelActiveTab == "Видео") {
                        snapshotEndpoint ?: "https://rutube.ru/api/video/person/$channelIdRaw/"
                    } else {
                        "https://rutube.ru/api/playlist/user/$channelIdRaw/"
                    }
                    val cleanEndpoint = repository.toRutubeApiUrl(endpoint)
                    val separator = if (cleanEndpoint.contains("?")) "&" else "?"
                    val url = "${cleanEndpoint}${separator}format=json&page=$snapshotPage"
                    val bodyStr = getResponseBody(url)
                    repository.parseVideoListJson(bodyStr, snapshotCategory ?: "Фильмы")
                } else if (snapshotEndpoint != null) {
                    val cleanEndpoint = repository.toRutubeApiUrl(snapshotEndpoint)
                    val separator = if (cleanEndpoint.contains("?")) "&" else "?"
                    val url = "${cleanEndpoint}${separator}format=json&page=$snapshotPage"
                    val bodyStr = getResponseBody(url)
                    repository.parseVideoListJson(bodyStr, snapshotCategory ?: "Фильмы")
                } else {
                    repository.fetchRealVideos(snapshotQuery, snapshotCategory, snapshotPage)
                }

                if (currentActiveApiEndpoint != snapshotEndpoint || 
                    currentCategory != snapshotCategory || 
                    currentQuery != snapshotQuery || 
                    navigationManager.isChannelView.value != snapshotChannelView) {
                    return@launch
                }

                if (newVideos.isEmpty()) {
                    if (snapshotChannelView) {
                        if (snapshotChannelActiveTab == "Видео") {
                            channelVideosEndReached = true
                        } else {
                            channelPlaylistsEndReached = true
                        }
                    } else {
                        isEndReached = true
                    }
                } else {
                    if (snapshotChannelView) {
                        if (snapshotChannelActiveTab == "Видео") {
                            channelVideosPage = snapshotPage
                            val mappedVideos = newVideos.map { child ->
                                child.copy(
                                    originType = com.example.utils.VideoType.CHANNEL,
                                    originId = _currentChannelVideo.value?.id ?: "",
                                    originTitle = _currentChannelVideo.value?.title ?: ""
                                )
                            }
                            _channelVideos.value = (_channelVideos.value + mappedVideos).distinctBy { it.id }
                        } else {
                            channelPlaylistsPage = snapshotPage
                            _channelPlaylists.value = (_channelPlaylists.value + newVideos).distinctBy { it.id }
                        }
                    } else {
                        currentPage = snapshotPage
                        val mappedVideos = if (_currentSubfolderVideo.value != null) {
                            val oType = _currentSubfolderVideo.value?.duration ?: ""
                            val oId = _currentSubfolderVideo.value?.id ?: ""
                            val oTitle = _currentSubfolderVideo.value?.title ?: ""
                            newVideos.map { child ->
                                child.copy(
                                    originType = oType,
                                    originId = oId,
                                    originTitle = oTitle
                                )
                            }
                        } else newVideos
                        _dynamicVideos.value = (_dynamicVideos.value + mappedVideos).distinctBy { it.id }
                    }
                }
            } catch (e: Exception) {
                if (e is retrofit2.HttpException) {
                    if (e.code() == 404) {
                        if (snapshotChannelView) {
                            if (snapshotChannelActiveTab == "Видео") {
                                channelVideosEndReached = true
                            } else {
                                channelPlaylistsEndReached = true
                            }
                        } else {
                            isEndReached = true
                        }
                    } else if (e.code() == 403 || e.code() == 429) {
                        if (snapshotChannelView) {
                            if (snapshotChannelActiveTab == "Видео") {
                                channelVideosEndReached = true
                            } else {
                                channelPlaylistsEndReached = true
                            }
                        } else {
                            isEndReached = true
                        }
                        android.util.Log.e("RutubeFeedManager", "API Error ${e.code()} in loadNextPage", e)
                    }
                } else {
                    android.util.Log.e("RutubeFeedManager", "Error loading next page", e)
                }
            } finally {
                _isMoreLoading.value = false
            }
        }
    }

    fun triggerDebouncedSearch(query: String, category: String) {
        searchDebounceJob?.cancel()
        searchDebounceJob = coroutineScope.launch {
            delay(500)
            fetchRealVideos(query, category)
        }
    }

    fun fetchRealCategories() {
        coroutineScope.launch {
            _isCategoriesLoading.value = true
            try {
                val cats = repository.fetchRealCategories()
                _realCategories.value = cats
            } catch (e: Exception) {
                // Ignore
            } finally {
                _isCategoriesLoading.value = false
            }
        }
    }

    fun selectCategory(category: String, targetUrl: String? = null) {
        val currentSnapshot = NavigationSnapshot(
            tab = navigationManager.currentTab.value,
            category = navigationManager.selectedCategory.value,
            feedTab = navigationManager.selectedFeedTab.value,
            subfolderName = navigationManager.selectedSubfolderName.value,
            searchQuery = navigationManager.searchQuery.value,
            selectedVideo = playerManager.currentSelectedVideo.value,
            isChannelView = navigationManager.isChannelView.value,
            currentChannelVideo = _currentChannelVideo.value,
            channelVideos = _channelVideos.value,
            channelPlaylists = _channelPlaylists.value,
            channelActiveTab = navigationManager.channelActiveTab.value,
            dynamicVideos = _dynamicVideos.value,
            currentPage = currentPage,
            isEndReached = isEndReached,
            currentQuery = currentQuery,
            currentCategory = currentCategory,
            currentActiveApiEndpoint = currentActiveApiEndpoint,
            currentSubfolderVideo = _currentSubfolderVideo.value,
            channelVideosPage = channelVideosPage,
            channelVideosEndReached = channelVideosEndReached,
            channelPlaylistsPage = channelPlaylistsPage,
            channelPlaylistsEndReached = channelPlaylistsEndReached
        )
        navigationManager.pushToHistory(currentSnapshot)
        navigationManager.selectTab("home")
        navigationManager.setCategory(category)
        navigationManager.setSearchQuery("")
        fetchRealVideos(query = null, category = category, targetUrl = targetUrl)
    }

    fun selectFeedTab(tab: TabInfo) {
        selectFeedTabInternal(tab, pushHistory = true)
    }

    fun selectFeedTabInternal(tab: TabInfo, pushHistory: Boolean) {
        if (pushHistory) {
            val currentSnapshot = NavigationSnapshot(
                tab = navigationManager.currentTab.value,
                category = navigationManager.selectedCategory.value,
                feedTab = navigationManager.selectedFeedTab.value,
                subfolderName = navigationManager.selectedSubfolderName.value,
                searchQuery = navigationManager.searchQuery.value,
                selectedVideo = playerManager.currentSelectedVideo.value,
                isChannelView = navigationManager.isChannelView.value,
                currentChannelVideo = _currentChannelVideo.value,
                channelVideos = _channelVideos.value,
                channelPlaylists = _channelPlaylists.value,
                channelActiveTab = navigationManager.channelActiveTab.value,
                dynamicVideos = _dynamicVideos.value,
                currentPage = currentPage,
                isEndReached = isEndReached,
                currentQuery = currentQuery,
                currentCategory = currentCategory,
                currentActiveApiEndpoint = currentActiveApiEndpoint,
                currentSubfolderVideo = _currentSubfolderVideo.value,
                channelVideosPage = channelVideosPage,
                channelVideosEndReached = channelVideosEndReached,
                channelPlaylistsPage = channelPlaylistsPage,
                channelPlaylistsEndReached = channelPlaylistsEndReached
            )
            navigationManager.pushToHistory(currentSnapshot)
        }
        navigationManager.setFeedTab(tab)
        navigationManager.setSubfolderName(null)
        navigationManager.setChannelView(false)
        _currentChannelVideo.value = null
        _currentSubfolderVideo.value = null
        _channelVideos.value = emptyList()
        _channelPlaylists.value = emptyList()
        navigationManager.setChannelActiveTab("Видео")
        fetchJob?.cancel()
        fetchJob = coroutineScope.launch {
            _isLoading.value = true
            try {
                // Any tab with more than 1 resource contains subcategories/folders to display
                val isFolderTab = tab.resources.size > 1

                if (isFolderTab) {
                    val folderVideos = tab.resources.map { resource ->
                        mapResourceToVideo(resource, tab.id, navigationManager.selectedCategory.value)
                    }.filter { !repository.isBlockedContent(it) }
                    _dynamicVideos.value = folderVideos
                    currentPage = 1
                    isEndReached = true
                    currentActiveApiEndpoint = null
                } else {
                    val combined = mutableListOf<Video>()
                    for (resource in tab.resources.take(3)) {
                        val rawUrl = resource.url ?: continue
                        val parsedVideos = fetchAndParseVideos(rawUrl, navigationManager.selectedCategory.value)
                        combined.addAll(parsedVideos)
                        currentActiveApiEndpoint = repository.toRutubeApiUrl(rawUrl)
                    }
                    if (combined.isNotEmpty()) {
                        _dynamicVideos.value = combined.distinctBy { it.id }
                        currentPage = 1
                        isEndReached = false
                    } else {
                        _dynamicVideos.value = repository.fetchRealVideos(null, navigationManager.selectedCategory.value, page = 1)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("RutubeFeedManager", "Select feed tab exception", e)
            } finally {
                _isLoading.value = false
                // Eagerly prefetch page 2 in advance right after completing the tab load
                if (_dynamicVideos.value.isNotEmpty() && currentPage == 1 && !isEndReached) {
                    coroutineScope.launch {
                        delay(200)
                        loadNextPage()
                    }
                }
            }
        }
    }

    suspend fun fetchVideosResolvingTabs(apiUrl: String, defaultCategory: String): List<Video> {
        val finalUrl = if (apiUrl.contains("?")) {
            if (apiUrl.contains("format=json")) apiUrl else "$apiUrl&format=json"
        } else {
            "$apiUrl?format=json"
        }
        android.util.Log.d("PlaylistDebug", "fetchVideosResolvingTabs: Fetching from $finalUrl")
        try {
            val bodyStr = getResponseBody(finalUrl)
            val trimmed = bodyStr.trim()
            if (!trimmed.startsWith("{")) {
                android.util.Log.w("PlaylistDebug", "fetchVideosResolvingTabs: Response from $apiUrl is not JSON: ${trimmed.take(200)}")
                return emptyList()
            }

            val loaded = repository.parseVideoListJson(bodyStr, defaultCategory)
            if (loaded.isNotEmpty()) {
                android.util.Log.d("PlaylistDebug", "fetchVideosResolvingTabs: Successfully loaded ${loaded.size} videos directly")
                return loaded
            }
            
            // Resolve nested structures if empty
            val parsedFeed = parser.parseResponse(bodyStr, apiUrl)
            if (parsedFeed.tabs.isNotEmpty()) {
                val urls = mutableListOf<String>()
                for (tab in parsedFeed.tabs) {
                    if (repository.isBlockedText(tab.name, includeTvOnline = false)) continue
                    for (res in tab.resources) {
                        val resUrl = res.url
                        if (!resUrl.isNullOrBlank() && !repository.isBlockedText(res.name, includeTvOnline = false) && !repository.isBlockedText(resUrl, includeTvOnline = false)) {
                            urls.add(resUrl)
                        }
                    }
                }
                
                val combined = mutableListOf<Video>()
                for (rawUrl in urls.take(2)) {
                    val subVideos = fetchAndParseVideos(rawUrl, defaultCategory)
                    combined.addAll(subVideos)
                }
                if (combined.isNotEmpty()) {
                    return combined.distinctBy { it.id }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RutubeFeedManager", "Error in fetchVideosResolvingTabs for $apiUrl", e)
        }
        return emptyList()
    }

    fun selectVideo(video: Video?) {
        if (video == null) {
            playerManager.selectVideo(null)
            return
        }
        
        // Auto-fix stale "Загрузка..." entries from history
        if (video.title == "Загрузка...") {
            val targetUrl = video.pageUrl ?: "https://rutube.ru/video/${video.id}/"
            loadVideoByUrlOrId(targetUrl)
            return
        }

        // Folder/series/playlist/channel selection
        if (video.duration == com.example.utils.VideoType.FOLDER || 
            video.duration == com.example.utils.VideoType.CATALOG ||
            video.duration == com.example.utils.VideoType.SERIES ||
            video.duration == com.example.utils.VideoType.CHANNEL ||
            video.duration == com.example.utils.VideoType.PLAYLIST ||
            video.duration == com.example.utils.VideoType.PROMO) {
            
            if (video.duration == com.example.utils.VideoType.CHANNEL) {
                val currentSnapshot = NavigationSnapshot(
                    tab = navigationManager.currentTab.value,
                    category = navigationManager.selectedCategory.value,
                    feedTab = navigationManager.selectedFeedTab.value,
                    subfolderName = navigationManager.selectedSubfolderName.value,
                    searchQuery = navigationManager.searchQuery.value,
                    selectedVideo = playerManager.currentSelectedVideo.value,
                    isChannelView = navigationManager.isChannelView.value,
                    currentChannelVideo = _currentChannelVideo.value,
                    channelVideos = _channelVideos.value,
                    channelPlaylists = _channelPlaylists.value,
                    channelActiveTab = navigationManager.channelActiveTab.value,
                    dynamicVideos = _dynamicVideos.value,
                    currentPage = currentPage,
                    isEndReached = isEndReached,
                    currentQuery = currentQuery,
                    currentCategory = currentCategory,
                    currentActiveApiEndpoint = currentActiveApiEndpoint,
                    currentSubfolderVideo = _currentSubfolderVideo.value,
                    channelVideosPage = channelVideosPage,
                    channelVideosEndReached = channelVideosEndReached,
                    channelPlaylistsPage = channelPlaylistsPage,
                    channelPlaylistsEndReached = channelPlaylistsEndReached
                )
                navigationManager.pushToHistory(currentSnapshot)
                navigationManager.selectTab("home")
                navigationManager.setChannelView(true)
                _currentChannelVideo.value = video
                _channelVideos.value = emptyList()
                _channelPlaylists.value = emptyList()
                
                val channelIdRaw = video.id.substringAfter("channel_").substringBefore("__")
                val channelId = if (channelIdRaw.isNotBlank()) channelIdRaw else video.authorId ?: ""
                
                if (channelId.isNotBlank()) {
                    fetchJob?.cancel()
                    requestId++
                    fetchJob = coroutineScope.launch {
                        _isLoading.value = true
                        _isLoadingPlaylists.value = true
                        try {
                            val channelIdResolved = if (channelId.all { it.isDigit() }) {
                                channelId
                            } else {
                                resolveNumericChannelId(channelId)
                            }

                            // 1. Channel Profile Info
                            launch {
                                try {
                                    val channelProfileUrl = "https://rutube.ru/api/profile/user/$channelIdResolved/?format=json"
                                    val profileBody = getResponseBody(channelProfileUrl)
                                    val profile = parser.parseChannelProfile(
                                        profileBody,
                                        fallbackName = video.channel,
                                        fallbackDescription = video.description ?: "",
                                        fallbackAvatarUrl = video.authorAvatarUrl ?: "",
                                        fallbackCoverUrl = video.thumbnailUrl ?: ""
                                    )

                                    val updatedVideo = video.copy(
                                        id = "channel_${channelIdResolved}__",
                                        title = profile.name,
                                        channel = profile.name,
                                        description = profile.description,
                                        views = if (profile.subscribersCount > 0) "${profile.subscribersCount} подписчиков" else video.views,
                                        authorAvatarUrl = profile.avatarUrl,
                                        thumbnailUrl = profile.coverImage
                                    )
                                    _currentChannelVideo.value = updatedVideo
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            val vidUrl = "https://rutube.ru/api/video/person/$channelIdResolved/?format=json"
                            val plUrl = "https://rutube.ru/api/playlist/user/$channelIdResolved/?format=json"

                            // 2. Channel Videos
                            launch {
                                try {
                                    val vidBody = getResponseBody(vidUrl)
                                    val parsedVids = repository.parseVideoListJson(vidBody, video.category)
                                    _channelVideos.value = parsedVids.map { child ->
                                        child.copy(
                                            originType = com.example.utils.VideoType.CHANNEL,
                                            originId = video.id,
                                            originTitle = video.title
                                        )
                                    }
                                    currentActiveApiEndpoint = vidUrl
                                    channelVideosPage = 1
                                    channelVideosEndReached = false
                                } catch (e: Exception) {
                                    android.util.Log.e("RutubeFeedManager", "Channel videos fetch error", e)
                                } finally {
                                    _isLoading.value = false
                                    if (_channelVideos.value.isNotEmpty() && channelVideosPage == 1 && !channelVideosEndReached) {
                                        coroutineScope.launch {
                                            delay(200)
                                            loadNextPage()
                                        }
                                    }
                                }
                            }

                            // 3. Channel Playlists
                            launch {
                                try {
                                    val plBody = getResponseBody(plUrl)
                                    _channelPlaylists.value = repository.parseVideoListJson(plBody, video.category)
                                    channelPlaylistsPage = 1
                                    channelPlaylistsEndReached = false
                                } catch (e: Exception) {
                                    android.util.Log.e("RutubeFeedManager", "Channel playlists fetch error", e)
                                } finally {
                                    _isLoadingPlaylists.value = false
                                }
                            }

                        } catch (e: Exception) {
                            android.util.Log.e("RutubeFeedManager", "Channel ID resolve error", e)
                            _isLoading.value = false
                            _isLoadingPlaylists.value = false
                        }
                    }
                }
                return
            }

            // Subfolder / series / playlist selection
            val currentSnapshot = NavigationSnapshot(
                tab = navigationManager.currentTab.value,
                category = navigationManager.selectedCategory.value,
                feedTab = navigationManager.selectedFeedTab.value,
                subfolderName = navigationManager.selectedSubfolderName.value,
                searchQuery = navigationManager.searchQuery.value,
                selectedVideo = playerManager.currentSelectedVideo.value,
                isChannelView = navigationManager.isChannelView.value,
                currentChannelVideo = _currentChannelVideo.value,
                channelVideos = _channelVideos.value,
                channelPlaylists = _channelPlaylists.value,
                channelActiveTab = navigationManager.channelActiveTab.value,
                dynamicVideos = _dynamicVideos.value,
                currentPage = currentPage,
                isEndReached = isEndReached,
                currentQuery = currentQuery,
                currentCategory = currentCategory,
                currentActiveApiEndpoint = currentActiveApiEndpoint,
                currentSubfolderVideo = _currentSubfolderVideo.value,
                channelVideosPage = channelVideosPage,
                channelVideosEndReached = channelVideosEndReached,
                channelPlaylistsPage = channelPlaylistsPage,
                channelPlaylistsEndReached = channelPlaylistsEndReached
            )
            navigationManager.pushToHistory(currentSnapshot)
            navigationManager.selectTab("home")
            navigationManager.setSubfolderName(video.title)
            _currentSubfolderVideo.value = video
            navigationManager.setChannelView(false)
            val rawUrl = video.id.substringAfter("__")
            if (rawUrl.isNotBlank()) {
                val apiUrl = repository.toRutubeApiUrl(rawUrl)
                fetchJob?.cancel()
                fetchJob = coroutineScope.launch {
                    _isLoading.value = true
                    try {
                        if (video.duration == com.example.utils.VideoType.SERIES) {
                            val seriesId = video.id.substringAfter("tv_").substringBefore("__")
                            if (seriesId.isNotBlank() && seriesId.all { it.isDigit() }) {
                                try {
                                    val infoUrl = "https://rutube.ru/api/metainfo/tv/$seriesId/?format=json"
                                    val infoBody = getResponseBody(infoUrl)
                                    val meta = parser.parseTvSeriesMeta(infoBody, video.description ?: "", video.thumbnailUrl)
                                    
                                    _currentSubfolderVideo.value = video.copy(
                                        description = meta.description,
                                        thumbnailUrl = meta.bannerUrl,
                                        authorAvatarUrl = meta.posterUrl,
                                        views = if (meta.year.isNotBlank() && meta.year != "null") "Год выпуска: ${meta.year}" else video.views
                                    )
                                } catch(e: Exception) {
                                    android.util.Log.e("RutubeFeedManager", "Meta info fetch error", e)
                                }
                            }
                        }

                        if (video.duration == com.example.utils.VideoType.PLAYLIST || video.duration == "ПЛЕЙЛИСТ") {
                            val playlistId = video.id.substringAfter("playlist_").substringBefore("__")
                            if (playlistId.isNotBlank()) {
                                try {
                                    val infoUrl = "https://rutube.ru/api/playlist/custom/$playlistId/?format=json"
                                    val infoBody = getResponseBody(infoUrl)
                                    val meta = parser.parsePlaylistMeta(infoBody, video.title, video.description ?: "", video.thumbnailUrl)
                                    
                                    _currentSubfolderVideo.value = video.copy(
                                        title = meta.title,
                                        description = meta.description,
                                        thumbnailUrl = meta.thumbnailUrl,
                                        views = if (meta.videoCount > 0) "${meta.videoCount} видео" else video.views
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e("RutubeFeedManager", "Playlist meta info fetch error", e)
                                }
                            }
                        }

                        var videos = fetchVideosResolvingTabs(apiUrl, video.category)
                        
                        if (video.duration == com.example.utils.VideoType.SERIES) {
                            val allSeriesVideos = videos.toMutableList()
                            try {
                                val deferredPages = (2..15).map { seriesPage ->
                                    async<List<Video>> {
                                        try {
                                            val pageUrl = if (apiUrl.contains("?")) {
                                                "$apiUrl&format=json&page=$seriesPage"
                                            } else {
                                                "$apiUrl?format=json&page=$seriesPage"
                                            }
                                            val pageBody = getResponseBody(pageUrl)
                                            repository.parseVideoListJson(pageBody, video.category)
                                        } catch (e: Exception) {
                                            emptyList()
                                        }
                                    }
                                }
                                val results = awaitAll(*deferredPages.toTypedArray())
                                for (pageVideos in results) {
                                    allSeriesVideos.addAll(pageVideos)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("RutubeFeedManager", "Parallel fetch for series failed", e)
                            }
                            
                            isEndReached = true // Prevent further pagination since we fetched everything
                            
                            videos = allSeriesVideos.distinctBy { it.id }.sortedWith(Comparator { v1, v2 ->
                                val extractNumbers = { s: String -> Regex("\\d+").findAll(s).map { it.value.toInt() }.toList() }
                                val nums1 = extractNumbers(v1.title)
                                val nums2 = extractNumbers(v2.title)
                                for (i in 0 until minOf(nums1.size, nums2.size)) {
                                    if (nums1[i] != nums2[i]) return@Comparator nums1[i].compareTo(nums2[i])
                                }
                                v1.title.compareTo(v2.title)
                            })
                        }
                        
                        val originType = video.duration
                        val originId = video.id
                        val originTitle = video.title
                        
                        val mapped = videos.map { child ->
                            child.copy(
                                originType = originType,
                                originId = originId,
                                originTitle = originTitle
                            )
                        }
                        
                        _dynamicVideos.value = mapped
                        currentActiveApiEndpoint = apiUrl
                        currentPage = 1
                        if (video.duration != com.example.utils.VideoType.SERIES) {
                            isEndReached = false
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("RutubeFeedManager", "Folder fetch error", e)
                    } finally {
                        _isLoading.value = false
                        if (_dynamicVideos.value.isNotEmpty() && currentPage == 1 && !isEndReached) {
                            coroutineScope.launch {
                                delay(200)
                                loadNextPage()
                            }
                        }
                    }
                }
            }
            return
        }

        playerManager.selectVideo(video)

        val originType = video.originType
        val originId = video.originId
        if (!originType.isNullOrBlank() && !originId.isNullOrBlank()) {
            coroutineScope.launch {
                try {
                    if (_dynamicVideos.value.any { it.id == video.id }) {
                        return@launch
                    }

                    if (originType == com.example.utils.VideoType.CHANNEL) {
                        val channelIdRaw = originId.substringAfter("channel_").substringBefore("__")
                        val channelId = if (channelIdRaw.isNotBlank()) channelIdRaw else video.authorId ?: ""
                        if (channelId.isNotBlank()) {
                            val channelIdResolved = if (channelId.all { it.isDigit() }) {
                                channelId
                            } else {
                                resolveNumericChannelId(channelId)
                            }
                            val vidUrl = "https://rutube.ru/api/video/person/$channelIdResolved/?format=json"
                            val vidBody = getResponseBody(vidUrl)
                            val loaded = repository.parseVideoListJson(vidBody, video.category).map { child ->
                                child.copy(
                                    originType = originType,
                                    originId = originId,
                                    originTitle = video.originTitle ?: video.channel
                                )
                            }
                            if (loaded.isNotEmpty()) {
                                _dynamicVideos.value = loaded
                                currentActiveApiEndpoint = vidUrl
                                currentPage = 1
                                isEndReached = false
                            }
                        }
                    } else if (originType == com.example.utils.VideoType.SERIES || originType == com.example.utils.VideoType.PLAYLIST || originType == "ПЛЕЙЛИСТ") {
                        val rawUrl = originId.substringAfter("__")
                        if (rawUrl.isNotBlank()) {
                            val apiUrl = repository.toRutubeApiUrl(rawUrl)
                            var loaded = fetchVideosResolvingTabs(apiUrl, video.category)
                            
                            if (originType == com.example.utils.VideoType.SERIES) {
                                val allSeriesVideos = loaded.toMutableList()
                                try {
                                    val deferredPages = (2..5).map { seriesPage ->
                                        async<List<Video>> {
                                            try {
                                                val pageUrl = if (apiUrl.contains("?")) {
                                                    "$apiUrl&format=json&page=$seriesPage"
                                                } else {
                                                    "$apiUrl?format=json&page=$seriesPage"
                                                }
                                                val pageBody = getResponseBody(pageUrl)
                                                repository.parseVideoListJson(pageBody, video.category)
                                            } catch (e: Exception) {
                                                emptyList()
                                            }
                                        }
                                    }
                                    val results = awaitAll(*deferredPages.toTypedArray())
                                    for (pageVideos in results) {
                                        allSeriesVideos.addAll(pageVideos)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                loaded = allSeriesVideos.distinctBy { it.id }.sortedWith(Comparator { v1, v2 ->
                                    val extractNumbers = { s: String -> Regex("\\d+").findAll(s).map { it.value.toInt() }.toList() }
                                    val nums1 = extractNumbers(v1.title)
                                    val nums2 = extractNumbers(v2.title)
                                    for (i in 0 until minOf(nums1.size, nums2.size)) {
                                        if (nums1[i] != nums2[i]) return@Comparator nums1[i].compareTo(nums2[i])
                                    }
                                    v1.title.compareTo(v2.title)
                                })
                            }

                            val finalLoaded = loaded.map { child ->
                                child.copy(
                                    originType = originType,
                                    originId = originId,
                                    originTitle = video.originTitle ?: video.title
                                )
                            }
                            if (finalLoaded.isNotEmpty()) {
                                _dynamicVideos.value = finalLoaded
                                currentActiveApiEndpoint = apiUrl
                                currentPage = 1
                                if (originType != com.example.utils.VideoType.SERIES) {
                                    isEndReached = false
                                } else {
                                    isEndReached = true
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RutubeFeedManager", "Error autoloading related origin videos", e)
                }
            }
        }
    }

    fun loadVideoByUrlOrId(urlOrId: String) {
        val trimmed = urlOrId.trim()
        if (trimmed.isBlank()) return
        
        fetchJob?.cancel()
        requestId++

        coroutineScope.launch {
            _isLoading.value = true
            var shouldClearLoading = true
            try {
                val pluginInfo = if (!trimmed.contains("rutube.ru", ignoreCase = true)) {
                    withContext(Dispatchers.IO) {
                        PluginManager.getVideoInfo(trimmed)
                    }
                } else null
                if (pluginInfo != null) {
                    val safeId = if (pluginInfo.source == "VK Video" || pluginInfo.url.contains("vk.com") || pluginInfo.url.contains("vkvideo.ru")) {
                        "vk_${pluginInfo.id}"
                    } else {
                        "plugin_${pluginInfo.source.replace(" ", "_")}_${pluginInfo.id}"
                    }
                    mediaResolver.masterUrlCache.put(safeId + "_page", pluginInfo.url)
                    val pluginVideo = Video(
                        id = safeId,
                        title = pluginInfo.title,
                        channel = pluginInfo.author.ifEmpty { pluginInfo.source },
                        views = pluginInfo.views,
                        timeAgo = "Только что",
                        duration = formatDurationMs(pluginInfo.duration),
                        category = pluginInfo.source,
                        description = "Видео из ${pluginInfo.source}. Импортировано по ссылке.",
                        thumbnailUrl = pluginInfo.thumbnail,
                        isDownloaded = false,
                        isBookmarked = false,
                        pageUrl = pluginInfo.url
                    )
                    selectVideo(pluginVideo)
                    return@launch
                }

                val resolved = com.example.utils.UrlResolver.resolveUrl(trimmed)
                if (resolved.type != com.example.utils.UrlResolver.EntityType.UNKNOWN) {
                    when (resolved.type) {
                        com.example.utils.UrlResolver.EntityType.VIDEO -> {
                            val rtId = resolved.id
                            try {
                                val apiUrl = "https://rutube.ru/api/video/$rtId/?format=json"
                                val bodyStr = getResponseBody(apiUrl)
                                val realVideo = parser.parseSingleVideo(bodyStr, rtId)
                                    ?: throw Exception("Не удалось распарсить информацию о видео.")
                                
                                selectVideo(realVideo)
                            } catch (e: Exception) { 
                                android.util.Log.e("RutubeFeedManager", "Error loading real video", e) 
                                val errV = Video(
                                    id = rtId,
                                    title = "Ошибка загрузки",
                                    channel = "Rutube",
                                    views = "",
                                    timeAgo = "",
                                    duration = "00:00",
                                    category = "Разное",
                                    description = "Не удалось загрузить информацию о видео: ${e.message}",
                                    thumbnailUrl = ""
                                )
                                selectVideo(errV)
                            }
                        }
                        com.example.utils.UrlResolver.EntityType.CHANNEL -> {
                            val dummyChannel = Video(
                                id = "channel_${resolved.id}__",
                                title = "Канал",
                                channel = "Канал",
                                duration = com.example.utils.VideoType.CHANNEL,
                                thumbnailUrl = "",
                                views = "",
                                timeAgo = "",
                                description = "",
                                category = "Разное"
                            )
                            shouldClearLoading = false
                            selectVideo(dummyChannel)
                        }
                        com.example.utils.UrlResolver.EntityType.PLAYLIST -> {
                            val dummyPlaylist = Video(
                                id = "playlist_${resolved.id}__${resolved.rawUrl ?: ""}",
                                title = "Плейлист",
                                channel = "Плейлист",
                                duration = com.example.utils.VideoType.PLAYLIST,
                                thumbnailUrl = "",
                                views = "",
                                timeAgo = "",
                                description = "",
                                category = "Разное"
                            )
                            shouldClearLoading = false
                            selectVideo(dummyPlaylist)
                        }
                        com.example.utils.UrlResolver.EntityType.SERIES -> {
                            val dummySeries = Video(
                                id = "tv_${resolved.id}__${resolved.rawUrl ?: ""}",
                                title = "Сериал / Шоу",
                                channel = "Шоу",
                                duration = com.example.utils.VideoType.SERIES,
                                thumbnailUrl = "",
                                views = "",
                                timeAgo = "",
                                description = "",
                                category = "Разное"
                            )
                            shouldClearLoading = false
                            selectVideo(dummySeries)
                        }
                        com.example.utils.UrlResolver.EntityType.FEED -> {
                            val slug = resolved.id
                            val catName = repository.findCategoryBySlug(slug) ?: slug.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                            shouldClearLoading = false
                            selectCategory(catName)
                        }
                        else -> {}
                    }
                    return@launch
                }
                
                if (trimmed.length > 5 && !trimmed.contains(" ") && !trimmed.contains(".")) {
                    val apiUrl = "https://rutube.ru/api/video/$trimmed/?format=json"
                    val bodyStr = getResponseBody(apiUrl)
                    val list = repository.parseVideoListJson(bodyStr, "Разное")
                    if (list.isNotEmpty()) {
                        selectVideo(list.first())
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("RutubeFeedManager", "Error in loadVideoByUrlOrId", e)
            } finally {
                if (shouldClearLoading) {
                    _isLoading.value = false
                }
            }
        }
    }

    fun restoreFeedState(snapshot: NavigationSnapshot) {
        _currentChannelVideo.value = snapshot.currentChannelVideo
        _currentSubfolderVideo.value = snapshot.currentSubfolderVideo
        _channelVideos.value = snapshot.channelVideos
        _channelPlaylists.value = snapshot.channelPlaylists
        _dynamicVideos.value = snapshot.dynamicVideos
        
        currentPage = snapshot.currentPage
        isEndReached = snapshot.isEndReached
        currentQuery = snapshot.currentQuery
        currentCategory = snapshot.currentCategory
        currentActiveApiEndpoint = snapshot.currentActiveApiEndpoint

        channelVideosPage = snapshot.channelVideosPage
        channelVideosEndReached = snapshot.channelVideosEndReached
        channelPlaylistsPage = snapshot.channelPlaylistsPage
        channelPlaylistsEndReached = snapshot.channelPlaylistsEndReached
    }

    fun parseVideoIdFromRutubeUrl(url: String): String {
        if (url.isBlank()) return ""
        val cleanUrl = url.substringBefore("?")
        val videoPattern = "rutube\\.ru/video/([a-fA-F0-9]+)".toRegex()
        val match1 = videoPattern.find(cleanUrl)
        if (match1 != null) return match1.groupValues[1]
        
        val privatePattern = "rutube\\.ru/video/private/([a-fA-F0-9]+)".toRegex()
        val match2 = privatePattern.find(cleanUrl)
        if (match2 != null) return match2.groupValues[1]

        val parts = cleanUrl.trimEnd('/').split("/")
        val last = parts.last()
        if (last.length >= 20 && last.matches("[a-fA-F0-9]+".toRegex())) {
            return last
        }
        return ""
    }

    suspend fun resolveNumericChannelId(channelIdOrSlug: String): String {
        if (channelIdOrSlug.all { it.isDigit() }) {
            return channelIdOrSlug
        }
        return try {
            val url = "https://rutube.ru/channel/$channelIdOrSlug/"
            val html = getResponseBody(url)
            
            val personRegex = "/api/video/person/(\\d+)".toRegex()
            val personMatch = personRegex.find(html)
            if (personMatch != null) return personMatch.groupValues[1]
            
            val userProfileRegex = "/api/profile/user/(\\d+)".toRegex()
            val userProfileMatch = userProfileRegex.find(html)
            if (userProfileMatch != null) return userProfileMatch.groupValues[1]

            val authorIdRegex = "\"author_id\"\\s*:\\s*(\\d+)".toRegex()
            val authorIdMatch = authorIdRegex.find(html)
            if (authorIdMatch != null) return authorIdMatch.groupValues[1]

            val authorBlockRegex = "\"author\"\\s*:\\s*\\{\\s*\"id\"\\s*:\\s*(\\d+)".toRegex()
            val authorBlockMatch = authorBlockRegex.find(html)
            if (authorBlockMatch != null) return authorBlockMatch.groupValues[1]

            val videoPersonRegex = "rutube\\.ru/video/person/(\\d+)".toRegex()
            val videoPersonMatch = videoPersonRegex.find(html)
            if (videoPersonMatch != null) return videoPersonMatch.groupValues[1]

            channelIdOrSlug
        } catch (e: Exception) {
            e.printStackTrace()
            channelIdOrSlug
        }
    }

    fun mapResourceToVideo(resource: ResourceInfo, tabId: Int, categoryName: String): Video {
        val url = resource.url ?: ""
        val extractedId = "\\d+".toRegex().find(url)?.value ?: ""
        
        return when (resource.type) {
            EntityType.CHANNEL -> {
                Video(
                    id = "channel_${extractedId}__$url",
                    title = resource.name,
                    channel = "Авторский канал",
                    views = "Открыть канал",
                    timeAgo = "Автор",
                    duration = com.example.utils.VideoType.CHANNEL,
                    isPro = false,
                    category = categoryName,
                    description = "Официальный канал: ${resource.name}",
                    thumbnailUrl = null
                )
            }
            EntityType.TV_SERIES -> {
                Video(
                    id = "tv_${extractedId}__$url",
                    title = resource.name,
                    channel = "Телешоу / Передача",
                    views = "Смотреть выпуски",
                    timeAgo = "Шоу",
                    duration = com.example.utils.VideoType.SERIES,
                    isPro = false,
                    category = categoryName,
                    description = "Смотрите оригинальные сезоны: ${resource.name}",
                    thumbnailUrl = null
                )
            }
            EntityType.PLAYLIST -> {
                Video(
                    id = "playlist_${extractedId}__$url",
                    title = resource.name,
                    channel = "Плейлист • Подборка",
                    views = "Смотреть плейлист",
                    timeAgo = "Плейлист",
                    duration = com.example.utils.VideoType.PLAYLIST,
                    isPro = false,
                    category = categoryName,
                    description = "Смотрите полную подборку видео из плейлиста: ${resource.name}",
                    thumbnailUrl = null
                )
            }
            else -> {
                Video(
                    id = "unknown_res_${tabId}_${resource.name.hashCode()}__$url",
                    title = resource.name,
                    channel = "Папка каталога",
                    views = "Подраздел",
                    timeAgo = "Открыть",
                    duration = com.example.utils.VideoType.FOLDER,
                    isPro = false,
                    category = categoryName,
                    description = "Коллекция контента из раздела: ${resource.name}",
                    thumbnailUrl = null
                )
            }
        }
    }

    fun resolveMissingPosters(videos: List<Video>) {
        videos.forEach { video ->
            if (video.thumbnailUrl.isNullOrBlank()) {
                if (resolvingVideoIds.add(video.id)) {
                    val actionUrl = video.id.substringAfter("__")
                    val match = Regex("/metainfo/tv/(\\d+)").find(actionUrl)
                    val objectId = match?.groupValues?.get(1)
                    if (objectId != null) {
                        val cached = resolvedPostersCache[objectId]
                        if (cached != null) {
                            updateVideoThumbnail(video.id, cached)
                        } else {
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val metainfoUrl = "https://rutube.ru/api/metainfo/tv/$objectId/"
                                    val body = getResponseBody(metainfoUrl)
                                    val resolvedUrl = parser.parseMissingPosterUrl(body)
                                    if (!resolvedUrl.isNullOrBlank()) {
                                        val clean = cleanUrl(resolvedUrl)
                                        resolvedPostersCache[objectId] = clean
                                        withContext(Dispatchers.Main) {
                                            updateVideoThumbnail(video.id, clean)
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("RutubeFeedManager", "Failed to fetch metainfo for $objectId", e)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun updateVideoThumbnail(videoId: String, thumbnailUrl: String) {
        val updater = { list: List<Video> ->
            list.map { if (it.id == videoId) it.copy(thumbnailUrl = thumbnailUrl) else it }
        }
        _dynamicVideos.value = updater(_dynamicVideos.value)
        _channelVideos.value = updater(_channelVideos.value)
        _channelPlaylists.value = updater(_channelPlaylists.value)
        
        val currentActive = playerManager.currentSelectedVideo.value
        if (currentActive != null && currentActive.id == videoId) {
            playerManager.selectVideo(currentActive.copy(thumbnailUrl = thumbnailUrl))
        }
        val currentChanVid = _currentChannelVideo.value
        if (currentChanVid != null && currentChanVid.id == videoId) {
            _currentChannelVideo.value = currentChanVid.copy(thumbnailUrl = thumbnailUrl)
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val saved = repository.getVideoById(videoId)
                if (saved != null && saved.thumbnailUrl.isNullOrBlank()) {
                    repository.insertOrUpdate(saved.copy(thumbnailUrl = thumbnailUrl))
                }
            } catch (e: Exception) {
                android.util.Log.e("RutubeFeedManager", "Failed to update db thumbnail for $videoId", e)
                ErrorHandler.reportError("Failed to update database: ${e.message}")
            }
        }
    }
}
