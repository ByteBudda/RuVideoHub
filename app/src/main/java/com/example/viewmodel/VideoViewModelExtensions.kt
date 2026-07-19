package com.example.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.Video
import com.example.data.RutubeCategory
import com.example.data.rutube.parser.*
import org.json.JSONObject

fun VideoViewModel.fetchRealVideos(query: String? = null, category: String? = null, targetUrl: String? = null) {
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
    
    fetchJob = viewModelScope.launch {
        _isLoading.value = true
        try {
            val q = query?.trim() ?: ""
            val searchSrc = _searchSource.value
            
            if (q.isNotEmpty() && searchSrc != "Rutube") {
                val pluginResults = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val plugin = com.example.plugins.PluginManager.getPlugins().find { it.name == searchSrc }
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
                val pluginInfo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.plugins.PluginManager.getVideoInfo(q)
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
                            val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                            val apiUrl = "https://rutube.ru/api/video/$rtVideoId/?format=json"
                            val response = apiService.getDynamicUrl(apiUrl)
                            val bodyStr = response.string()
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
                            android.util.Log.e("VideoViewModel", "Error fetching Rutube video in search", e)
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
                    
                    val response = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(finalUrl)
                    if (currentRequestId != requestId) return@launch
                    val bodyStr = response.string()
                    val jsonObj = org.json.JSONObject(bodyStr)
                    val parsed = com.example.data.rutube.parser.ResponseAnalyzer.parse(jsonObj, finalUrl)
                    
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
            android.util.Log.e("VideoViewModel", "Error in fetchRealVideos with target", e)
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
                    viewModelScope.launch {
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

fun VideoViewModel.loadNextPage() {
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

    viewModelScope.launch {
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
                val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                val response = apiService.getDynamicUrl(url)
                val bodyStr = response.string()
                repository.parseVideoListJson(bodyStr, snapshotCategory ?: "Фильмы")
            } else if (snapshotEndpoint != null) {
                val cleanEndpoint = repository.toRutubeApiUrl(snapshotEndpoint)
                val separator = if (cleanEndpoint.contains("?")) "&" else "?"
                val url = "${cleanEndpoint}${separator}format=json&page=$snapshotPage"
                val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                val response = apiService.getDynamicUrl(url)
                val bodyStr = response.string()
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
                        _channelVideos.value = (_channelVideos.value + newVideos).distinctBy { it.id }
                    } else {
                        channelPlaylistsPage = snapshotPage
                        _channelPlaylists.value = (_channelPlaylists.value + newVideos).distinctBy { it.id }
                    }
                } else {
                    currentPage = snapshotPage
                    _dynamicVideos.value = (_dynamicVideos.value + newVideos).distinctBy { it.id }
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
                    android.util.Log.e("VideoViewModel", "API Error ${e.code()} in loadNextPage", e)
                }
            } else {
                android.util.Log.e("VideoViewModel", "Error loading next page", e)
            }
        } finally {
            _isMoreLoading.value = false
        }
    }
}

internal fun VideoViewModel.triggerDebouncedSearch(query: String, category: String) {
    searchDebounceJob?.cancel()
    searchDebounceJob = viewModelScope.launch {
        delay(500)
        fetchRealVideos(query, category)
    }
}

fun VideoViewModel.fetchRealCategories() {
    viewModelScope.launch {
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

fun VideoViewModel.selectCategory(category: String, targetUrl: String? = null) {
    pushToHistory()
    navigationManager.selectTab("home")
    navigationManager.setCategory(category)
    navigationManager.setSearchQuery("")
    fetchRealVideos(query = null, category = category, targetUrl = targetUrl)
}

fun VideoViewModel.selectFeedTab(tab: com.example.data.rutube.parser.TabInfo) {
    selectFeedTabInternal(tab, pushHistory = true)
}

fun VideoViewModel.selectFeedTabInternal(tab: com.example.data.rutube.parser.TabInfo, pushHistory: Boolean) {
    if (pushHistory) {
        pushToHistory()
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
    fetchJob = viewModelScope.launch {
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
                    val cleanApiUrl = repository.toRutubeApiUrl(rawUrl)
                    val finalUrl = if (cleanApiUrl.contains("?")) {
                        if (cleanApiUrl.contains("format=json")) cleanApiUrl else "$cleanApiUrl&format=json"
                    } else {
                        "$cleanApiUrl?format=json"
                    }
                    
                    try {
                        val response = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(finalUrl)
                        val bodyStr = response.string()
                        val parsedVideos = repository.parseVideoListJson(bodyStr, navigationManager.selectedCategory.value)
                        combined.addAll(parsedVideos)
                        currentActiveApiEndpoint = repository.toRutubeApiUrl(rawUrl)
                    } catch (resEx: Exception) {
                        android.util.Log.e("VideoViewModel", "Sub-resource Tab fetch failure: $rawUrl", resEx)
                    }
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
            android.util.Log.e("VideoViewModel", "Select feed tab exception", e)
        } finally {
            _isLoading.value = false
            // Eagerly prefetch page 2 in advance right after completing the tab load
            if (_dynamicVideos.value.isNotEmpty() && currentPage == 1 && !isEndReached) {
                viewModelScope.launch {
                    delay(200)
                    loadNextPage()
                }
            }
        }
    }
}

internal suspend fun VideoViewModel.fetchVideosResolvingTabs(apiUrl: String, defaultCategory: String): List<Video> {
    val finalUrl = if (apiUrl.contains("?")) {
        if (apiUrl.contains("format=json")) apiUrl else "$apiUrl&format=json"
    } else {
        "$apiUrl?format=json"
    }
    val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
    android.util.Log.d("PlaylistDebug", "fetchVideosResolvingTabs: Fetching from $finalUrl")
    try {
        val response = apiService.getDynamicUrl(finalUrl)
        val bodyStr = response.string()
        val trimmed = bodyStr.trim()
        if (!trimmed.startsWith("{")) {
            android.util.Log.w("PlaylistDebug", "fetchVideosResolvingTabs: Response from $apiUrl is not JSON (possibly HTML/blocked/error): ${trimmed.take(200)}")
            return emptyList()
        }

        val loaded = repository.parseVideoListJson(bodyStr, defaultCategory)
        if (loaded.isNotEmpty()) {
            android.util.Log.d("PlaylistDebug", "fetchVideosResolvingTabs: Successfully loaded ${loaded.size} videos directly from $apiUrl")
            return loaded
        }
        
        // Resolve nested Matryoshka tab structures if results was empty
        val jsonObj = org.json.JSONObject(bodyStr)
        val parsedFeed = com.example.data.rutube.parser.ResponseAnalyzer.parse(jsonObj, apiUrl)
        if (parsedFeed.tabs.isNotEmpty()) {
            android.util.Log.d("PlaylistDebug", "fetchVideosResolvingTabs: Resolving ${parsedFeed.tabs.size} nested tabs from $apiUrl")
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
                try {
                    val cleanSubUrl = repository.toRutubeApiUrl(rawUrl)
                    val subFinalUrl = if (cleanSubUrl.contains("?")) {
                        if (cleanSubUrl.contains("format=json")) cleanSubUrl else "$cleanSubUrl&format=json"
                    } else {
                        "$cleanSubUrl?format=json"
                    }
                    android.util.Log.d("PlaylistDebug", "fetchVideosResolvingTabs: Fetching sub-tab from $subFinalUrl")
                    val subResponse = apiService.getDynamicUrl(subFinalUrl)
                    val subBody = subResponse.string()
                    if (subBody.trim().startsWith("{")) {
                        val subVideos = repository.parseVideoListJson(subBody, defaultCategory)
                        combined.addAll(subVideos)
                    } else {
                        android.util.Log.w("PlaylistDebug", "fetchVideosResolvingTabs: Sub-tab response is not JSON from $subFinalUrl")
                    }
                } catch (subEx: Exception) {
                    android.util.Log.e("VideoViewModel", "Error fetching tab resource $rawUrl", subEx)
                }
            }
            if (combined.isNotEmpty()) {
                android.util.Log.d("PlaylistDebug", "fetchVideosResolvingTabs: Returning ${combined.size} combined videos from tabs")
                return combined.distinctBy { it.id }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("VideoViewModel", "Error in fetchVideosResolvingTabs for $apiUrl", e)
    }
    return emptyList()
}

fun VideoViewModel.selectVideo(video: Video?) {
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

    val q = currentQuery?.trim() ?: ""
    if (q.isNotEmpty()) {
        saveSearchQuery(q)
    }

    // Logic for handling "folders" or Rutube-specific entity types which are NOT real videos yet
    if (video.duration == com.example.utils.VideoType.FOLDER || 
        video.duration == com.example.utils.VideoType.CATALOG ||
        video.duration == com.example.utils.VideoType.SERIES ||
        video.duration == com.example.utils.VideoType.CHANNEL ||
        video.duration == com.example.utils.VideoType.PLAYLIST ||
        video.duration == com.example.utils.VideoType.PROMO) {
        
        // If it's a channel, we want to open the "Channel View"
        if (video.duration == com.example.utils.VideoType.CHANNEL) {
            pushToHistory()
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
                fetchJob = viewModelScope.launch {
                    _isLoading.value = true
                    _isLoadingPlaylists.value = true
                    try {
                        val channelIdResolved = if (channelId.all { it.isDigit() }) {
                            channelId
                        } else {
                            resolveNumericChannelId(channelId)
                        }

                        // 1. Channel Profile Info (Parallel)
                        launch {
                            try {
                                val channelProfileUrl = "https://rutube.ru/api/profile/user/$channelIdResolved/?format=json"
                                val profileResponse = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(channelProfileUrl)
                                val profileBody = profileResponse.string()
                                val jsonObject = org.json.JSONObject(profileBody)
                                val name = jsonObject.optString("name", video.channel) ?: ""
                                val description = jsonObject.optString("description", video.description)
                                val subCount = jsonObject.optInt("subscribers_count", 0)
                                val avatarUrl = jsonObject.optString("avatar_url", video.authorAvatarUrl ?: "")
                                val appearance = jsonObject.optJSONObject("appearance")
                                val coverImage = appearance?.optString("cover_image", video.thumbnailUrl ?: "") ?: video.thumbnailUrl ?: ""

                                val updatedVideo = video.copy(
                                    id = "channel_${channelIdResolved}__",
                                    title = name,
                                    channel = name,
                                    description = description,
                                    views = if (subCount > 0) "$subCount подписчиков" else video.views,
                                    authorAvatarUrl = avatarUrl,
                                    thumbnailUrl = coverImage
                                )
                                _currentChannelVideo.value = updatedVideo
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        val vidUrl = "https://rutube.ru/api/video/person/$channelIdResolved/?format=json"
                        val plUrl = "https://rutube.ru/api/playlist/user/$channelIdResolved/?format=json"

                        // 2. Channel Videos (Parallel)
                        launch {
                            try {
                                val vidResponse = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(vidUrl)
                                val vidBody = vidResponse.string()
                                _channelVideos.value = repository.parseVideoListJson(vidBody, video.category)
                                currentActiveApiEndpoint = vidUrl
                                channelVideosPage = 1
                                channelVideosEndReached = false
                            } catch (e: Exception) {
                                android.util.Log.e("VideoViewModel", "Channel videos fetch error", e)
                            } finally {
                                _isLoading.value = false
                                // Eagerly prefetch page 2 in advance right after completing the channel load
                                if (_channelVideos.value.isNotEmpty() && channelVideosPage == 1 && !channelVideosEndReached) {
                                    viewModelScope.launch {
                                        delay(200)
                                        loadNextPage()
                                    }
                                }
                            }
                        }

                        // 3. Channel Playlists (Parallel)
                        launch {
                            try {
                                val plResponse = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(plUrl)
                                val plBody = plResponse.string()
                                _channelPlaylists.value = repository.parseVideoListJson(plBody, video.category)
                                channelPlaylistsPage = 1
                                channelPlaylistsEndReached = false
                            } catch (e: Exception) {
                                android.util.Log.e("VideoViewModel", "Channel playlists fetch error", e)
                            } finally {
                                _isLoadingPlaylists.value = false
                            }
                        }

                    } catch (e: Exception) {
                        android.util.Log.e("VideoViewModel", "Channel ID resolve error", e)
                        _isLoading.value = false
                        _isLoadingPlaylists.value = false
                    }
                }
            }
            return
        }

        // For other folders/playlists/series: open as a subfolder
        pushToHistory()
        navigationManager.selectTab("home")
        navigationManager.setSubfolderName(video.title)
        _currentSubfolderVideo.value = video
        navigationManager.setChannelView(false)
        val rawUrl = video.id.substringAfter("__")
        if (rawUrl.isNotBlank()) {
            val apiUrl = repository.toRutubeApiUrl(rawUrl)
            fetchJob?.cancel()
            fetchJob = viewModelScope.launch {
                _isLoading.value = true
                try {
                    // Detailed meta info for SERIES
                    if (video.duration == com.example.utils.VideoType.SERIES) {
                        val seriesId = video.id.substringAfter("tv_").substringBefore("__")
                        if (seriesId.isNotBlank() && seriesId.all { it.isDigit() }) {
                            try {
                                val infoUrl = "https://rutube.ru/api/metainfo/tv/$seriesId/?format=json"
                                val infoResponse = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(infoUrl)
                                val infoBody = infoResponse.string()
                                val infoObj = org.json.JSONObject(infoBody)
                                val description = infoObj.optString("description", video.description)
                                val year = infoObj.optString("year")
                                val picture = infoObj.optString("picture")
                                val appearance = infoObj.optJSONObject("appearance")
                                val coverImage = appearance?.optString("cover_image")?.takeIf { it.isNotBlank() && it != "null" }
                                val verticalPoster = infoObj.optString("vertical_poster_url").takeIf { it.isNotBlank() && it != "null" }
                                
                                val bannerUrl = coverImage ?: picture.takeIf { it.isNotBlank() && it != "null" } ?: video.thumbnailUrl
                                val posterUrl = verticalPoster ?: video.thumbnailUrl
                                
                                _currentSubfolderVideo.value = video.copy(
                                    description = description,
                                    thumbnailUrl = bannerUrl,
                                    authorAvatarUrl = posterUrl,
                                    views = if (year.isNotBlank() && year != "null") "Год выпуска: $year" else video.views
                                )
                            } catch(e: Exception) {
                                android.util.Log.e("VideoViewModel", "Meta info fetch error", e)
                            }
                        }
                    }

                    // Detailed meta info for PLAYLIST
                    if (video.duration == com.example.utils.VideoType.PLAYLIST || video.duration == "ПЛЕЙЛИСТ") {
                        val playlistId = video.id.substringAfter("playlist_").substringBefore("__")
                        if (playlistId.isNotBlank()) {
                            try {
                                val infoUrl = "https://rutube.ru/api/playlist/custom/$playlistId/?format=json"
                                val infoResponse = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(infoUrl)
                                val infoBody = infoResponse.string()
                                val infoObj = org.json.JSONObject(infoBody)
                                
                                val description = infoObj.optString("description", video.description)
                                val appearance = infoObj.optJSONObject("appearance")
                                val coverImage = appearance?.optString("cover_image")?.takeIf { it.isNotBlank() && it != "null" }
                                val picture = infoObj.optString("picture").takeIf { it.isNotBlank() && it != "null" }
                                val thumbnailUrl = infoObj.optString("thumbnail_url").takeIf { it.isNotBlank() && it != "null" }
                                val nameFallback = infoObj.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: video.title
                                val name = infoObj.optString("name", nameFallback).takeIf { it.isNotBlank() && it != "null" } ?: nameFallback
                                var videoCount = infoObj.optInt("video_count", -1)
                                if (videoCount <= 0) videoCount = infoObj.optInt("videos_count", -1)
                                
                                _currentSubfolderVideo.value = video.copy(
                                    title = name,
                                    description = description,
                                    thumbnailUrl = coverImage ?: picture ?: thumbnailUrl ?: video.thumbnailUrl,
                                    views = if (videoCount > 0) "$videoCount видео" else video.views
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("VideoViewModel", "Playlist meta info fetch error", e)
                            }
                        }
                    }

                    var videos = fetchVideosResolvingTabs(apiUrl, video.category)
                    
                    if (video.duration == com.example.utils.VideoType.SERIES) {
                        val allSeriesVideos = videos.toMutableList()
                        try {
                            val deferredPages = (2..15).map { seriesPage ->
                                viewModelScope.async<List<Video>> {
                                    try {
                                        val pageUrl = if (apiUrl.contains("?")) {
                                            "$apiUrl&format=json&page=$seriesPage"
                                        } else {
                                            "$apiUrl?format=json&page=$seriesPage"
                                        }
                                        val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                                        val pageResponse = apiService.getDynamicUrl(pageUrl)
                                        repository.parseVideoListJson(pageResponse.string(), video.category)
                                    } catch (e: Exception) {
                                        emptyList<Video>()
                                    }
                                }
                            }
                            val results = kotlinx.coroutines.awaitAll(*deferredPages.toTypedArray())
                            for (pageVideos in results) {
                                allSeriesVideos.addAll(pageVideos)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("VideoViewModel", "Parallel fetch for series failed", e)
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
                    
                    _dynamicVideos.value = videos
                    currentActiveApiEndpoint = apiUrl
                    currentPage = 1
                    if (video.duration != com.example.utils.VideoType.SERIES) {
                        isEndReached = false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VideoViewModel", "Folder fetch error", e)
                } finally {
                    _isLoading.value = false
                    // Eagerly prefetch page 2 in advance right after completing the folder load
                    if (_dynamicVideos.value.isNotEmpty() && currentPage == 1 && !isEndReached) {
                        viewModelScope.launch {
                            delay(200)
                            loadNextPage()
                        }
                    }
                }
            }
        }
        return
    }

    // Regular video selection: play it!
    viewModelScope.launch {
            val dbVideo = repository.getVideoById(video.id)
            if (dbVideo != null && dbVideo.lastProgress > 0) {
                playerManager.saveVideoPosition(video.id, dbVideo.lastProgress)
            }

            // Determine origin context
            var originType = video.originType ?: dbVideo?.originType
            var originId = video.originId ?: dbVideo?.originId
            var originTitle = video.originTitle ?: dbVideo?.originTitle

            if (originType == null) {
                val subfolder = _currentSubfolderVideo.value
                val channel = _currentChannelVideo.value
                if (subfolder != null) {
                    originType = "subfolder"
                    originId = subfolder.id
                    originTitle = subfolder.title
                } else if (channel != null) {
                    originType = "channel"
                    originId = channel.id
                    originTitle = channel.title
                } else if (!currentActiveApiEndpoint.isNullOrBlank()) {
                    originType = "api"
                    originId = currentActiveApiEndpoint
                    originTitle = currentCategory ?: "Канал"
                } else if (!currentCategory.isNullOrBlank()) {
                    originType = "category"
                    originId = currentCategory
                    originTitle = currentCategory
                }
            }

            val enrichedVideo = video.copy(
                originType = originType,
                originId = originId,
                originTitle = originTitle
            )

            // 1. Select the video instantly so the player overlay opens immediately!
            playerManager.selectVideo(enrichedVideo)
            addToRecentHistory(enrichedVideo)

            // 2. Populate the player playlist with what we have currently (or fallback lists) so the user has valid controls immediately
            val currentList = _dynamicVideos.value
            val containsVideo = currentList.any { it.id == video.id }
            if (!containsVideo) {
                val cwList = continueWatchingVideos.value
                val existsInCw = cwList.any { it.id == video.id }
                if (existsInCw) {
                    _dynamicVideos.value = cwList.map { savedVideo ->
                        Video(
                            id = savedVideo.id, title = savedVideo.title, channel = savedVideo.channel,
                            views = savedVideo.views, timeAgo = savedVideo.timeAgo, duration = savedVideo.duration,
                            isPro = savedVideo.isPro, category = savedVideo.category, description = "Продолжить просмотр",
                            thumbnailUrl = savedVideo.thumbnailUrl, isDownloaded = savedVideo.isDownloaded, isBookmarked = savedVideo.isBookmarked,
                            originType = savedVideo.originType, originId = savedVideo.originId, originTitle = savedVideo.originTitle
                        )
                    }
                } else {
                    val recentsList = recentSavedVideos.value
                    val existsInRecents = recentsList.any { it.id == video.id }
                    if (existsInRecents) {
                        _dynamicVideos.value = recentsList.map { saved ->
                            Video(
                                id = saved.id, title = saved.title, channel = saved.channel,
                                views = saved.views, timeAgo = saved.timeAgo, duration = saved.duration,
                                isPro = saved.isPro, category = saved.category, description = "Просмотрено недавно",
                                thumbnailUrl = saved.thumbnailUrl, isDownloaded = saved.isDownloaded, isBookmarked = saved.isBookmarked,
                                originType = saved.originType, originId = saved.originId, originTitle = saved.originTitle
                            )
                        }
                    } else {
                        _dynamicVideos.value = listOf(enrichedVideo)
                    }
                }
            }
            playerManager.currentPlaylist = _dynamicVideos.value
            playerManager.currentIndex = _dynamicVideos.value.indexOfFirst { it.id == video.id }

            // 3. Resolve the full contextual playlist in the background on IO dispatcher
            var playlistLoaded = false
            if (!containsVideo && originType != null && !originId.isNullOrBlank()) {
                withContext(Dispatchers.IO) {
                    _isLoading.value = true
                    try {
                        if (originType == "subfolder") {
                            val rawUrl = originId.substringAfter("__")
                            if (rawUrl.isNotBlank()) {
                                val apiUrl = repository.toRutubeApiUrl(rawUrl)
                                val isSeries = originId.contains("tv_") || enrichedVideo.title.contains("серия", ignoreCase = true)
                                var videos = fetchVideosResolvingTabs(apiUrl, enrichedVideo.category)
                                
                                if (isSeries) {
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
                                                    val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                                                    val pageResponse = apiService.getDynamicUrl(pageUrl)
                                                    repository.parseVideoListJson(pageResponse.string(), enrichedVideo.category)
                                                } catch (e: Exception) {
                                                    emptyList<Video>()
                                                }
                                            }
                                        }
                                        val results = awaitAll(*deferredPages.toTypedArray())
                                        for (pageVideos in results) {
                                            allSeriesVideos.addAll(pageVideos)
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("VideoViewModel", "Parallel fetch for series failed", e)
                                    }
                                    
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
                                
                                if (videos.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        _dynamicVideos.value = videos.map { it.copy(originType = originType, originId = originId, originTitle = originTitle) }
                                        currentActiveApiEndpoint = apiUrl
                                        currentCategory = enrichedVideo.category
                                        currentPage = 1
                                        isEndReached = false
                                        playlistLoaded = true
                                    }
                                }
                            }
                        } else if (originType == "channel") {
                            val channelIdRaw = originId.substringAfter("channel_").substringBefore("__")
                            if (channelIdRaw.isNotBlank()) {
                                val channelIdResolved = if (channelIdRaw.all { it.isDigit() }) {
                                    channelIdRaw
                                } else {
                                    resolveNumericChannelId(channelIdRaw)
                                }
                                val vidUrl = "https://rutube.ru/api/video/person/$channelIdResolved/?format=json"
                                val vidResponse = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(vidUrl)
                                val vidBody = vidResponse.string()
                                val loaded = repository.parseVideoListJson(vidBody, enrichedVideo.category)
                                if (loaded.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        _dynamicVideos.value = loaded.map { it.copy(originType = originType, originId = originId, originTitle = originTitle) }
                                        currentActiveApiEndpoint = vidUrl
                                        currentCategory = enrichedVideo.category
                                        currentPage = 1
                                        isEndReached = false
                                        playlistLoaded = true
                                    }
                                }
                            }
                        } else if (originType == "api" || originType == "category") {
                            val apiUrl = if (originType == "api") originId else null
                            if (apiUrl != null && apiUrl.startsWith("http")) {
                                val cleanApiUrl = repository.toRutubeApiUrl(apiUrl)
                                val loaded = fetchVideosResolvingTabs(cleanApiUrl, enrichedVideo.category)
                                if (loaded.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        _dynamicVideos.value = loaded.map { it.copy(originType = originType, originId = originId, originTitle = originTitle) }
                                        currentActiveApiEndpoint = cleanApiUrl
                                        currentCategory = enrichedVideo.category
                                        currentPage = 1
                                        isEndReached = false
                                        playlistLoaded = true
                                    }
                                }
                            } else {
                                val cat = originTitle ?: enrichedVideo.category
                                val loaded = repository.fetchRealVideos(null, cat, page = 1)
                                if (loaded.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        _dynamicVideos.value = loaded.map { it.copy(originType = originType, originId = originId, originTitle = originTitle) }
                                        currentActiveApiEndpoint = null
                                        currentCategory = cat
                                        currentPage = 1
                                        isEndReached = false
                                        playlistLoaded = true
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("VideoViewModel", "Failed to restore context playlist for video ${video.id}", e)
                    } finally {
                        withContext(Dispatchers.Main) {
                            _isLoading.value = false
                            if (playlistLoaded) {
                                playerManager.currentPlaylist = _dynamicVideos.value
                                playerManager.currentIndex = _dynamicVideos.value.indexOfFirst { it.id == video.id }
                            }
                        }
                    }
                }
            }
        }
}

fun VideoViewModel.loadVideoByUrlOrId(urlOrId: String) {
    val trimmed = urlOrId.trim()
    if (trimmed.isBlank()) return
    
    fetchJob?.cancel()
    requestId++

    viewModelScope.launch {
        _isLoading.value = true
        var shouldClearLoading = true
        try {
            // Check if plugin URL
            val pluginInfo = if (!trimmed.contains("rutube.ru", ignoreCase = true)) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.plugins.PluginManager.getVideoInfo(trimmed)
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

            // Rutube integration
            val resolved = com.example.utils.UrlResolver.resolveUrl(trimmed)
            if (resolved.type != com.example.utils.UrlResolver.EntityType.UNKNOWN) {
                when (resolved.type) {
                    com.example.utils.UrlResolver.EntityType.VIDEO -> {
                        val rtId = resolved.id
                        try {
                            android.util.Log.e("VideoViewModel", "Loading real video info for rtId=$rtId")
                            val apiUrl = "https://rutube.ru/api/video/$rtId/?format=json"
                            val response = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(apiUrl)
                            val bodyStr = response.string()
                            val jsonObj = org.json.JSONObject(bodyStr)
                            
                            val authorObj = jsonObj.optJSONObject("author")
                            val authorName = authorObj?.optString("name") ?: "Rutube"
                            val authorIdRaw = authorObj?.optString("id") ?: ""
                            
                            val isLive = jsonObj.optBoolean("is_livestream", false)
                            val durationSec = jsonObj.optInt("duration", 0)
                            val durStr = if (isLive) {
                                "трансляция"
                            } else if (durationSec >= 3600) {
                                String.format("%d:%02d:%02d", durationSec / 3600, (durationSec % 3600) / 60, durationSec % 60)
                            } else if (durationSec > 0) {
                                String.format("%02d:%02d", durationSec / 60, durationSec % 60)
                            } else {
                                "00:00"
                            }
                            
                            val title = jsonObj.optString("title", "Видео")
                            val desc = jsonObj.optString("description", "Импортировано из ссылки Rutube.")
                            val thumb = jsonObj.optString("thumbnail_url", "")
                            
                            val realVideo = Video(
                                id = rtId,
                                title = title,
                                channel = authorName,
                                views = "",
                                timeAgo = "",
                                duration = durStr,
                                category = jsonObj.optJSONObject("category")?.optString("name") ?: "Разное",
                                description = desc,
                                thumbnailUrl = thumb,
                                authorId = authorIdRaw,
                                authorAvatarUrl = authorObj?.optString("avatar_url", "") ?: "",
                                authorActionUrl = authorObj?.optString("site_url", "") ?: ""
                            )
                            
                            selectVideo(realVideo)
                        } catch (e: Exception) { 
                            android.util.Log.e("VideoViewModel", "Error loading real video", e) 
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
            
            // Fallback: try as raw ID if it looks like one
            if (trimmed.length > 5 && !trimmed.contains(" ") && !trimmed.contains(".")) {
                val apiUrl = "https://rutube.ru/api/video/$trimmed/?format=json"
                val response = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(apiUrl)
                val bodyStr = response.string()
                val list = repository.parseVideoListJson(bodyStr, "Разное")
                if (list.isNotEmpty()) {
                    selectVideo(list.first())
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoViewModel", "Error in loadVideoByUrlOrId", e)
        } finally {
            if (shouldClearLoading) {
                _isLoading.value = false
            }
        }
    }
}

internal fun VideoViewModel.parseVideoIdFromRutubeUrl(url: String): String {
    if (url.isBlank()) return ""
    val cleanUrl = url.substringBefore("?")
    
    // 1. Matches https://rutube.ru/video/XXXX/
    val videoPattern = "rutube\\.ru/video/([a-fA-F0-9]+)".toRegex()
    val match1 = videoPattern.find(cleanUrl)
    if (match1 != null) return match1.groupValues[1]
    
    // 2. Matches https://rutube.ru/video/private/XXXX/?p=YYYY
    val privatePattern = "rutube\\.ru/video/private/([a-fA-F0-9]+)".toRegex()
    val match2 = privatePattern.find(cleanUrl)
    if (match2 != null) return match2.groupValues[1]

    // 3. Last fallback: try any hex-looking ID at the end of path
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
        val html = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(url).string()
        
        // Try person API link
        val personRegex = "/api/video/person/(\\d+)".toRegex()
        val personMatch = personRegex.find(html)
        if (personMatch != null) return personMatch.groupValues[1]
        
        // Try user profile API link
        val userProfileRegex = "/api/profile/user/(\\d+)".toRegex()
        val userProfileMatch = userProfileRegex.find(html)
        if (userProfileMatch != null) return userProfileMatch.groupValues[1]

        // Try author id in JSON schema
        val authorIdRegex = "\"author_id\"\\s*:\\s*(\\d+)".toRegex()
        val authorIdMatch = authorIdRegex.find(html)
        if (authorIdMatch != null) return authorIdMatch.groupValues[1]

        // Try author json block
        val authorBlockRegex = "\"author\"\\s*:\\s*\\{\\s*\"id\"\\s*:\\s*(\\d+)".toRegex()
        val authorBlockMatch = authorBlockRegex.find(html)
        if (authorBlockMatch != null) return authorBlockMatch.groupValues[1]

        // Try video/person link
        val videoPersonRegex = "rutube\\.ru/video/person/(\\d+)".toRegex()
        val videoPersonMatch = videoPersonRegex.find(html)
        if (videoPersonMatch != null) return videoPersonMatch.groupValues[1]

        channelIdOrSlug
    } catch (e: Exception) {
        e.printStackTrace()
        channelIdOrSlug
    }
}

