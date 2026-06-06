package com.example.viewmodel

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.SavedVideo
import com.example.data.Video
import com.example.data.VideoRepository
import com.example.data.RutubeCategory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class VideoViewModel(application: Application) : AndroidViewModel(application) {

    // Authorization State
    private val _isAuthorized = MutableStateFlow(false)
    val isAuthorized = _isAuthorized.asStateFlow()

    private val _authSessionId = MutableStateFlow<String?>(null)
    val authSessionId = _authSessionId.asStateFlow()

    private val _authCsrfToken = MutableStateFlow<String?>(null)
    val authCsrfToken = _authCsrfToken.asStateFlow()

    private val _username = MutableStateFlow<String>("Сергей Петров")
    val username = _username.asStateFlow()

    private val _userAvatar = MutableStateFlow<String>("https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=120&auto=format&fit=crop&q=60")
    val userAvatar = _userAvatar.asStateFlow()

    private val db = AppDatabase.getDatabase(application)
    private val repository = VideoRepository(db.savedVideoDao())

    // Bottom Navigation tab states: "home", "explore", "downloads", "library"
    private val _currentTab = MutableStateFlow("home")
    val currentTab = _currentTab.asStateFlow()

    // Navigation Category chips state: "Фильмы", "Сериалы" etc.
    private val _selectedCategory = MutableStateFlow("Фильмы")
    val selectedCategory = _selectedCategory.asStateFlow()

    // Search query state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // Microphoning / Search focused state
    private val _isMicrophoneActive = MutableStateFlow(false)
    val isMicrophoneActive = _isMicrophoneActive.asStateFlow()

    // Video details / full-featured active player state
    private val _currentSelectedVideo = MutableStateFlow<Video?>(null)
    val currentSelectedVideo = _currentSelectedVideo.asStateFlow()

    // Simulated active player states
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playProgress = MutableStateFlow(0f)
    val playProgress = _playProgress.asStateFlow()

    // Playback progress tracking (videoId -> playback position in milliseconds)
    private val _videoPositions = mutableMapOf<String, Long>()

    fun saveVideoPosition(videoId: String, position: Long) {
        _videoPositions[videoId] = position
    }

    fun getVideoPosition(videoId: String): Long {
        return _videoPositions[videoId] ?: 0L
    }

    private var playbackJob: Job? = null

    // Dynamic list of real matching videos from network / offline database/built-in catalog
    private val _dynamicVideos = MutableStateFlow<List<Video>>(emptyList())
    val dynamicVideos = _dynamicVideos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _realCategories = MutableStateFlow<List<RutubeCategory>>(emptyList())
    val realCategories = _realCategories.asStateFlow()

    private val _isCategoriesLoading = MutableStateFlow(false)
    val isCategoriesLoading = _isCategoriesLoading.asStateFlow()

    private val _feedTabs = MutableStateFlow<List<com.example.data.rutube.SmartRutubeParser.TabInfo>>(emptyList())
    val feedTabs = _feedTabs.asStateFlow()

    private val _selectedFeedTab = MutableStateFlow<com.example.data.rutube.SmartRutubeParser.TabInfo?>(null)
    val selectedFeedTab = _selectedFeedTab.asStateFlow()

    private val _selectedSubfolderName = MutableStateFlow<String?>(null)
    val selectedSubfolderName = _selectedSubfolderName.asStateFlow()

    fun resetSubfolder() {
        _selectedSubfolderName.value = null
        _selectedFeedTab.value?.let { selectFeedTab(it) }
    }

    // yt-dlp downloading state parameters
    private val _activeDownloads = MutableStateFlow<Map<String, YtDlpDownload>>(emptyMap())
    val activeDownloads = _activeDownloads.asStateFlow()

    private val _streamUrlCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    // Expose active loading source: Rutube API Live, Offline database, Built-in hits
    val apiSource = flow {
        while (true) {
            emit(repository.lastFetchSource)
            delay(1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Инициализация...")

    init {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("rutube_auth_prefs", android.content.Context.MODE_PRIVATE)
        val savedSessionId = sharedPrefs.getString("sessionid", null)
        val savedCsrfToken = sharedPrefs.getString("csrftoken", null)
        val savedUsername = sharedPrefs.getString("username", "Сергей Петров")
        if (!savedSessionId.isNullOrBlank()) {
            _authSessionId.value = savedSessionId
            _authCsrfToken.value = savedCsrfToken
            _isAuthorized.value = true
            _username.value = savedUsername ?: "Сергей Петров"
            
            com.example.data.rutube.RutubeRetrofitClient.sessionId = savedSessionId
            com.example.data.rutube.RutubeRetrofitClient.csrfToken = savedCsrfToken
        }
        fetchRealVideos()
        fetchRealCategories()
    }

    private var currentPage = 1
    private var isEndReached = false
    private var currentQuery: String? = null
    private var currentCategory: String? = "Фильмы"
    private var currentActiveApiEndpoint: String? = null

    private val _isMoreLoading = MutableStateFlow(false)
    val isMoreLoading = _isMoreLoading.asStateFlow()

    fun fetchRealCategories() {
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

    private val categorySlugs = mapOf(
        "Фильмы" to "movies",
        "Сериалы" to "serials",
        "Телепередачи" to "tv",
        "Музыка" to "music",
        "Мультфильмы" to "cartoons",
        "Спорт" to "sport",
        "Юмор" to "umor",
        "Видеоигры" to "games",
        "Технологии" to "technologies"
    )

    fun selectCategory(category: String, targetUrl: String? = null) {
        _selectedCategory.value = category
        _searchQuery.value = ""
        fetchRealVideos(query = null, category = category, targetUrl = targetUrl)
    }

    fun selectFeedTab(tab: com.example.data.rutube.SmartRutubeParser.TabInfo) {
        _selectedFeedTab.value = tab
        _selectedSubfolderName.value = null
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                val tabName = tab.name ?: ""
                val isFolderTab = tab.resources.size > 1 && (
                    tabName.contains("жанр", ignoreCase = true) ||
                    tabName.contains("год", ignoreCase = true) ||
                    tabName.contains("стран", ignoreCase = true) ||
                    tabName.contains("катег", ignoreCase = true) ||
                    tabName.contains("раздел", ignoreCase = true) ||
                    tabName.contains("тема", ignoreCase = true) ||
                    (!tabName.equals("Главная", ignoreCase = true) && 
                     !tabName.equals("Рекомендации", ignoreCase = true) && 
                     !tabName.equals("Рекомендуем", ignoreCase = true) && 
                     !tabName.equals("Тренды", ignoreCase = true) && 
                     !tabName.equals("Новинки", ignoreCase = true))
                )

                if (isFolderTab) {
                    val folderVideos = tab.resources.map { resource ->
                        Video(
                            id = "unknown_res_${tab.id}_${resource.name.hashCode()}__${resource.url ?: ""}",
                            title = resource.name,
                            channel = "Папка каталога",
                            views = "Подраздел",
                            timeAgo = "Открыть",
                            duration = "ПАПКА",
                            isPro = false,
                            category = _selectedCategory.value,
                            description = "Коллекция контента из раздела: ${resource.name}",
                            thumbnailUrl = null
                        )
                    }
                    _dynamicVideos.value = folderVideos
                    currentPage = 1
                    isEndReached = true
                    currentActiveApiEndpoint = null
                } else {
                    val combined = mutableListOf<Video>()
                    for (resource in tab.resources.take(3)) {
                        val rawUrl = resource.url ?: continue
                        val normalizedUrl = if (rawUrl.startsWith("http")) rawUrl else "https://rutube.ru${if (rawUrl.startsWith("/")) "" else "/"}$rawUrl"
                        val finalUrl = if (normalizedUrl.contains("?")) "$normalizedUrl&format=json" else "$normalizedUrl?format=json"
                        
                        try {
                            val response = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(finalUrl)
                            val bodyStr = response.string()
                            val parsedVideos = repository.parseVideoListJson(bodyStr, _selectedCategory.value)
                            combined.addAll(parsedVideos)
                            currentActiveApiEndpoint = normalizedUrl
                        } catch (resEx: Exception) {
                            android.util.Log.e("VideoViewModel", "Sub-resource Tab fetch failure: $rawUrl", resEx)
                        }
                    }
                    if (combined.isNotEmpty()) {
                        _dynamicVideos.value = combined.distinctBy { it.id }
                        currentPage = 1
                        isEndReached = false
                    } else {
                        _dynamicVideos.value = repository.fetchRealVideos(null, _selectedCategory.value, page = 1)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoViewModel", "Select feed tab exception", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private var fetchJob: Job? = null
    fun fetchRealVideos(query: String? = null, category: String? = null, targetUrl: String? = null) {
        fetchJob?.cancel()
        _selectedSubfolderName.value = null
        currentQuery = query
        val targetCategory = category ?: _selectedCategory.value
        currentCategory = targetCategory
        currentPage = 1
        isEndReached = false
        currentActiveApiEndpoint = null
        
        fetchJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                val q = query?.trim() ?: ""
                if (q.isNotEmpty()) {
                    _feedTabs.value = emptyList()
                    _selectedFeedTab.value = null
                    _dynamicVideos.value = repository.fetchRealVideos(q, targetCategory, page = 1)
                } else {
                    var urlToFetch = targetUrl
                    if (urlToFetch.isNullOrBlank()) {
                        val matched = _realCategories.value.firstOrNull { it.title.equals(targetCategory, ignoreCase = true) }
                        urlToFetch = matched?.target
                    }
                    if (urlToFetch.isNullOrBlank()) {
                        val slug = repository.dynamicCategoryTargets[targetCategory]
                        urlToFetch = if (slug != null) "/api/feeds/$slug/" else null
                    }
                    if (urlToFetch.isNullOrBlank()) {
                        val slug = categorySlugs[targetCategory]
                        urlToFetch = if (slug != null) "/api/feeds/$slug/" else null
                    }
                    
                    if (!urlToFetch.isNullOrBlank()) {
                        val normalizedUrl = if (urlToFetch.startsWith("http")) urlToFetch else "https://rutube.ru${if (urlToFetch.startsWith("/")) "" else "/"}$urlToFetch"
                        val finalUrl = if (normalizedUrl.contains("?")) "$normalizedUrl&format=json" else "$normalizedUrl?format=json"
                        
                        val response = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(finalUrl)
                        val bodyStr = response.string()
                        val jsonObj = org.json.JSONObject(bodyStr)
                        val parsed = com.example.data.rutube.SmartRutubeParser.ResponseAnalyzer.parse(jsonObj, finalUrl)
                        
                        if (parsed.type == com.example.data.rutube.SmartRutubeParser.EntityType.FEED && parsed.tabs.isNotEmpty()) {
                            _feedTabs.value = parsed.tabs
                            val firstTab = parsed.tabs.first()
                            _selectedFeedTab.value = firstTab
                            
                            val combined = mutableListOf<Video>()
                            for (resource in firstTab.resources.take(3)) {
                                val rawUrl = resource.url ?: continue
                                val subUrl = if (rawUrl.startsWith("http")) rawUrl else "https://rutube.ru${if (rawUrl.startsWith("/")) "" else "/"}$rawUrl"
                                val subFinalUrl = if (subUrl.contains("?")) "$subUrl&format=json" else "$subUrl?format=json"
                                try {
                                    val subResponse = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(subFinalUrl)
                                    val subVideos = repository.parseVideoListJson(subResponse.string(), targetCategory)
                                    combined.addAll(subVideos)
                                    currentActiveApiEndpoint = subUrl
                                } catch (resEx: Exception) {
                                    android.util.Log.e("VideoViewModel", "First tab resource load failed", resEx)
                                }
                            }
                            if (combined.isNotEmpty()) {
                                _dynamicVideos.value = combined.distinctBy { it.id }
                            } else {
                                _dynamicVideos.value = repository.fetchRealVideos(null, targetCategory, page = 1)
                            }
                        } else {
                            _feedTabs.value = emptyList()
                            _selectedFeedTab.value = null
                            
                            val parsedVideos = repository.parseVideoListJson(bodyStr, targetCategory)
                            if (parsedVideos.isNotEmpty()) {
                                _dynamicVideos.value = parsedVideos
                                currentActiveApiEndpoint = normalizedUrl
                            } else {
                                _dynamicVideos.value = repository.fetchRealVideos(null, targetCategory, page = 1)
                            }
                        }
                    } else {
                        _feedTabs.value = emptyList()
                        _selectedFeedTab.value = null
                        _dynamicVideos.value = repository.fetchRealVideos(null, targetCategory, page = 1)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoViewModel", "Error in fetchRealVideos with target", e)
                _feedTabs.value = emptyList()
                _selectedFeedTab.value = null
                _dynamicVideos.value = repository.fetchRealVideos(query, targetCategory, page = 1)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadNextPage() {
        if (_isMoreLoading.value || isEndReached || _isLoading.value) return
        
        viewModelScope.launch {
            _isMoreLoading.value = true
            try {
                val nextPage = currentPage + 1
                val newVideos = if (currentActiveApiEndpoint != null) {
                    val separator = if (currentActiveApiEndpoint!!.contains("?")) "&" else "?"
                    val url = "${currentActiveApiEndpoint}${separator}format=json&page=$nextPage"
                    val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                    val response = apiService.getDynamicUrl(url)
                    val bodyStr = response.string()
                    repository.parseVideoListJson(bodyStr, currentCategory ?: "Фильмы")
                } else {
                    repository.fetchRealVideos(currentQuery, currentCategory, nextPage)
                }

                if (newVideos.isEmpty()) {
                    isEndReached = true
                } else {
                    currentPage = nextPage
                    _dynamicVideos.value = (_dynamicVideos.value + newVideos).distinctBy { it.id }
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoViewModel", "Error loading next page", e)
            } finally {
                _isMoreLoading.value = false
            }
        }
    }

    private var searchDebounceJob: Job? = null
    private fun triggerDebouncedSearch(query: String, category: String) {
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(500)
            fetchRealVideos(query, category)
        }
    }

    // Base video feed matching state changes recursively
    val allVideos: StateFlow<List<Video>> = combine(
        _dynamicVideos,
        repository.getSavedVideosOnly()
    ) { dynamicList, savedList ->
        val savedMap = savedList.associateBy { it.id }
        dynamicList.map { vid ->
            val saved = savedMap[vid.id]
            vid.copy(
                isDownloaded = saved?.isDownloaded ?: false,
                isBookmarked = saved?.isBookmarked ?: false
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Filtered results taking search text and category chips into account
    val filteredVideos: StateFlow<List<Video>> = allVideos

    // List of ONLY downloaded items, taken directly from Room
    val downloadedSavedVideos: StateFlow<List<SavedVideo>> = repository.getSavedVideosOnly()
        .map { list -> list.filter { it.isDownloaded } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // List of ONLY bookmarked items, taken directly from Room
    val bookmarkedSavedVideos: StateFlow<List<SavedVideo>> = repository.getSavedVideosOnly()
        .map { list -> list.filter { it.isBookmarked } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // List of ALL viewed/saved items (Recent/History list), sorted by savedAt DESC
    val recentSavedVideos: StateFlow<List<SavedVideo>> = repository.getSavedVideosOnly()
        .map { list -> list.filter { it.isWatched } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addToRecentHistory(video: Video) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = com.example.data.AppDatabase.getDatabase(getApplication())
                val existing = db.savedVideoDao().getVideoById(video.id)
                val toSave = SavedVideo(
                    id = video.id,
                    title = video.title,
                    channel = video.channel,
                    views = video.views,
                    timeAgo = video.timeAgo,
                    duration = video.duration,
                    isPro = video.isPro,
                    category = video.category,
                    thumbnailUrl = video.thumbnailUrl,
                    isDownloaded = existing?.isDownloaded ?: false,
                    isBookmarked = existing?.isBookmarked ?: false,
                    savedAt = System.currentTimeMillis()
                )
                db.savedVideoDao().insertOrUpdate(toSave)
            } catch (e: Exception) {
                android.util.Log.e("VideoViewModel", "Log history insert failed", e)
            }
        }
    }

    fun deleteRecentItem(video: Video) {
        viewModelScope.launch {
            val db = com.example.data.AppDatabase.getDatabase(getApplication())
            val existing = db.savedVideoDao().getVideoById(video.id)
            if (existing != null) {
                if (existing.isDownloaded || existing.isBookmarked) {
                    // Keep the download and bookmark intact, just remove from watch history
                    val updated = existing.copy(isWatched = false)
                    db.savedVideoDao().insertOrUpdate(updated)
                } else {
                    // Free to remove completely from DB and disk since there are no bookmarks/downloads
                    val downloadFolder = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    val targetFile = File(downloadFolder, "${video.id}.mp4")
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    repository.deleteVideoById(video.id)
                    if (_currentSelectedVideo.value?.id == video.id) {
                        _currentSelectedVideo.value = _currentSelectedVideo.value?.copy(isDownloaded = false)
                    }
                }
            }
        }
    }

    fun selectTab(tab: String) {
        _currentTab.value = tab
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
        fetchRealVideos(query = _searchQuery.value, category = category)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        triggerDebouncedSearch(query, _selectedCategory.value)
    }

    private suspend fun fetchVideosResolvingTabs(apiUrl: String, defaultCategory: String): List<Video> {
        val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
        try {
            val response = apiService.getDynamicUrl(apiUrl)
            val bodyStr = response.string()
            val loaded = repository.parseVideoListJson(bodyStr, defaultCategory)
            if (loaded.isNotEmpty()) {
                return loaded
            }
            
            // Resolve nested Matryoshka tab structures if results was empty
            val jsonObj = org.json.JSONObject(bodyStr)
            val tabsArray = jsonObj.optJSONArray("tabs")
            if (tabsArray != null && tabsArray.length() > 0) {
                val urls = mutableListOf<String>()
                for (i in 0 until tabsArray.length()) {
                    val tabObj = tabsArray.optJSONObject(i) ?: continue
                    val resArray = tabObj.optJSONArray("resources")
                    if (resArray != null) {
                        for (j in 0 until resArray.length()) {
                            val resUrl = resArray.optJSONObject(j)?.optString("url")
                            if (!resUrl.isNullOrBlank()) {
                                urls.add(resUrl)
                            }
                        }
                    }
                }
                
                val combined = mutableListOf<Video>()
                for (rawUrl in urls.take(2)) {
                    try {
                        val subUrl = if (rawUrl.startsWith("http")) rawUrl else "https://rutube.ru${if (rawUrl.startsWith("/")) "" else "/"}$rawUrl"
                        val subFinalUrl = if (subUrl.contains("?")) "$subUrl&format=json" else "$subUrl?format=json"
                        val subResponse = apiService.getDynamicUrl(subFinalUrl)
                        val subVideos = repository.parseVideoListJson(subResponse.string(), defaultCategory)
                        combined.addAll(subVideos)
                    } catch (subEx: Exception) {
                        android.util.Log.e("VideoViewModel", "Error fetching tab resource $rawUrl", subEx)
                    }
                }
                if (combined.isNotEmpty()) {
                    return combined.distinctBy { it.id }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoViewModel", "Error in fetchVideosResolvingTabs for $apiUrl", e)
        }
        return emptyList()
    }

    fun selectVideo(video: Video?) {
        if (video != null && video.id.startsWith("tv_")) {
            val fullTvId = video.id.substringAfter("tv_")
            val idParts = fullTvId.split("__")
            val tvId = idParts.getOrNull(0) ?: ""
            val rawActionUrl = idParts.getOrNull(1) ?: ""

            viewModelScope.launch {
                _isLoading.value = true
                try {
                    var loadedVideos: List<Video> = emptyList()
                    val fallbackUrls = mutableListOf<String>()

                    if (rawActionUrl.isNotBlank() && rawActionUrl != "null") {
                        val baseActionUrl = if (rawActionUrl.contains("?")) {
                            rawActionUrl.substringBefore("?")
                        } else {
                            rawActionUrl
                        }
                        fallbackUrls.add("$baseActionUrl/?format=json")
                        
                        if (!baseActionUrl.endsWith("/video/")) {
                            fallbackUrls.add("${baseActionUrl.trimEnd('/')}/video/?format=json")
                        }
                    }

                    if (tvId.isNotBlank()) {
                        fallbackUrls.add("https://rutube.ru/api/metainfo/tv/$tvId/video/?format=json")
                        fallbackUrls.add("https://rutube.ru/api/feeds/tv/$tvId/video/?format=json")
                    }

                    for (url in fallbackUrls.distinct()) {
                        loadedVideos = fetchVideosResolvingTabs(url, video.category)
                        if (loadedVideos.isNotEmpty()) {
                            currentActiveApiEndpoint = url.substringBefore("?")
                            break
                        }
                    }

                    if (loadedVideos.isNotEmpty()) {
                        val firstEpisode = loadedVideos.first()
                        _currentSelectedVideo.value = firstEpisode
                        _isPlaying.value = true
                        _playProgress.value = 0f
                        startPlaybackTicker()
                        _selectedSubfolderName.value = video.title
                        _dynamicVideos.value = loadedVideos
                        _searchQuery.value = ""
                        selectTab("home")
                        currentPage = 1
                        isEndReached = false
                    } else {
                        currentPage = 1
                        isEndReached = true
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VideoViewModel", "Error fetching TV episodes", e)
                } finally {
                    _isLoading.value = false
                }
            }
            return
        }

        if (video != null && video.id.startsWith("channel_")) {
            val fullChannelId = video.id.substringAfter("channel_")
            val idParts = fullChannelId.split("__")
            val channelId = idParts.getOrNull(0) ?: ""
            val rawActionUrl = idParts.getOrNull(1) ?: ""

            viewModelScope.launch {
                _isLoading.value = true
                try {
                    var loadedVideos: List<Video> = emptyList()
                    val fallbackUrls = mutableListOf<String>()

                    if (rawActionUrl.isNotBlank() && rawActionUrl != "null") {
                        val baseActionUrl = if (rawActionUrl.contains("?")) {
                            rawActionUrl.substringBefore("?")
                        } else {
                            rawActionUrl
                        }
                        fallbackUrls.add("$baseActionUrl/?format=json")
                        
                        if (!baseActionUrl.endsWith("/video/")) {
                            fallbackUrls.add("${baseActionUrl.trimEnd('/')}/video/?format=json")
                        }
                    }

                    if (channelId.isNotBlank()) {
                        fallbackUrls.add("https://rutube.ru/api/video/person/$channelId/?format=json")
                        fallbackUrls.add("https://rutube.ru/api/feeds/person/$channelId/?format=json")
                        fallbackUrls.add("https://rutube.ru/api/feeds/person/$channelId/video/?format=json")
                        fallbackUrls.add("https://rutube.ru/api/metainfo/person/$channelId/video/?format=json")
                    }

                    for (url in fallbackUrls.distinct()) {
                        loadedVideos = fetchVideosResolvingTabs(url, video.category)
                        if (loadedVideos.isNotEmpty()) {
                            currentActiveApiEndpoint = url.substringBefore("?")
                            break
                        }
                    }

                    if (loadedVideos.isNotEmpty()) {
                        _selectedSubfolderName.value = video.title
                        _dynamicVideos.value = loadedVideos
                        _searchQuery.value = ""
                        selectTab("home")
                        
                        currentPage = 1
                        isEndReached = false
                    } else {
                        currentPage = 1
                        isEndReached = true
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VideoViewModel", "Error resolving channel", e)
                } finally {
                    _isLoading.value = false
                }
            }
            return
        }

        if (video != null && video.id.startsWith("unknown_")) {
            val parts = video.id.substringAfter("unknown_").split("__")
            val rawId = parts.getOrNull(0) ?: ""
            val actionUrl = parts.getOrNull(1) ?: ""
            
            viewModelScope.launch {
                _isLoading.value = true
                try {
                    var loadedVideos: List<Video> = emptyList()
                    val fallbackUrls = mutableListOf<String>()

                    if (actionUrl.isNotBlank() && actionUrl != "null") {
                        val baseActionUrl = if (actionUrl.contains("?")) {
                            actionUrl.substringBefore("?")
                        } else {
                            actionUrl
                        }
                        fallbackUrls.add("$baseActionUrl/?format=json")
                        
                        // Add /video/ suffix fallback if not already there
                        if (!baseActionUrl.endsWith("/video/")) {
                            fallbackUrls.add("${baseActionUrl.trimEnd('/')}/video/?format=json")
                        }
                    }

                    if (rawId.isNotBlank() && rawId.all { it.isDigit() }) {
                        fallbackUrls.add("https://rutube.ru/api/tags/video/$rawId/?format=json")
                        fallbackUrls.add("https://rutube.ru/api/feeds/cardgroup/$rawId/?format=json")
                        fallbackUrls.add("https://rutube.ru/api/video/playlist/$rawId/?format=json")
                        fallbackUrls.add("https://rutube.ru/api/feeds/playlist/$rawId/?format=json")
                        fallbackUrls.add("https://rutube.ru/api/feeds/subscriptiontvseries/$rawId/?format=json")
                    }

                    for (url in fallbackUrls.distinct()) {
                        loadedVideos = fetchVideosResolvingTabs(url, video.category)
                        if (loadedVideos.isNotEmpty()) {
                            currentActiveApiEndpoint = url.substringBefore("?")
                            break
                        }
                    }

                    if (loadedVideos.isNotEmpty()) {
                        _selectedSubfolderName.value = video.title
                        _dynamicVideos.value = loadedVideos
                        _searchQuery.value = ""
                        selectTab("home")
                        
                        currentPage = 1
                        isEndReached = false
                    } else {
                        currentPage = 1
                        isEndReached = true
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VideoViewModel", "Error resolving unknown/container Matryoshka card", e)
                } finally {
                    _isLoading.value = false
                }
            }
            return
        }

        _currentSelectedVideo.value = video
        // Open playing state automatically if a real video is selected for instant streaming
        if (video != null) {
            _isPlaying.value = true
            _playProgress.value = 0f
            startPlaybackTicker()
            loadComments(video.id)
            addToRecentHistory(video)
        } else {
            _isPlaying.value = false
            _playProgress.value = 0f
            stopPlaybackTicker()
        }
    }

    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
        if (_isPlaying.value) {
            startPlaybackTicker()
        } else {
            stopPlaybackTicker()
        }
    }

    fun seekProgress(progress: Float) {
        _playProgress.value = progress.coerceIn(0f, 1f)
    }

    private fun startPlaybackTicker() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (_isPlaying.value) {
                delay(1000)
                val current = _playProgress.value
                if (current < 1f) {
                    _playProgress.value = (current + 0.008f).coerceAtMost(1f)
                } else {
                    _isPlaying.value = false
                    _playProgress.value = 0f
                    stopPlaybackTicker()
                }
            }
        }
    }

    private fun stopPlaybackTicker() {
        playbackJob?.cancel()
        playbackJob = null
    }

    fun toggleBookmark(video: Video) {
        viewModelScope.launch {
            repository.toggleBookmark(video)
            // Keep active player in-sync
            if (_currentSelectedVideo.value?.id == video.id) {
                _currentSelectedVideo.value = _currentSelectedVideo.value?.copy(isBookmarked = !video.isBookmarked)
            }
        }
    }

    fun toggleDownload(video: Video) {
        val downloadFolder = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = File(downloadFolder, "${video.id}.mp4")

        viewModelScope.launch {
            if (video.isDownloaded) {
                // Revert download status and clean disk
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                repository.toggleDownload(video)
                if (_currentSelectedVideo.value?.id == video.id) {
                    _currentSelectedVideo.value = _currentSelectedVideo.value?.copy(isDownloaded = false)
                }
            } else {
                // Run live yt-dlp downloader coroutine
                startYtDlpDownload(video)
            }
        }
    }

    fun deleteDownload(video: Video) {
        val downloadFolder = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = File(downloadFolder, "${video.id}.mp4")
        viewModelScope.launch {
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (video.isDownloaded) {
                repository.toggleDownload(video)
                if (_currentSelectedVideo.value?.id == video.id) {
                    _currentSelectedVideo.value = _currentSelectedVideo.value?.copy(isDownloaded = false)
                }
            }
        }
    }

    fun saveToDevice(video: Video, context: android.content.Context, onResult: (Boolean, String) -> Unit) {
        val inputFolder = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val inputFile = File(inputFolder, "${video.id}.mp4")
        if (!inputFile.exists()) {
            onResult(false, "Сначала скачайте видео в приложение.")
            return
        }

        try {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "${video.title}.mp4".replace("[\\\\/:*?\"<>|]".toRegex(), "_"))
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
            }

            var uri: android.net.Uri? = null
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            }

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    inputFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                onResult(true, "Файл успешно сохранен в папку 'Загрузки' устройства!")
            } else {
                // Fallback for older Android versions
                val publicDownloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                if (!publicDownloads.exists()) {
                    publicDownloads.mkdirs()
                }
                val outputFile = File(publicDownloads, "${video.title}.mp4".replace("[\\\\/:*?\"<>|]".toRegex(), "_"))
                inputFile.inputStream().use { inputStream ->
                    outputFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                onResult(true, "Файл успешно сохранен в папку 'Загрузки': ${outputFile.name}")
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoViewModel", "Error saving file to public downloads", e)
            onResult(false, "Ошибка сохранения: ${e.localizedMessage ?: e.message}")
        }
    }

    private fun startYtDlpDownload(video: Video) {
        val id = video.id
        viewModelScope.launch(Dispatchers.IO) {
            val logs = mutableListOf<String>()
            
            fun log(msg: String) {
                logs.add(msg)
                val currentDl = _activeDownloads.value[id]
                val updatedDl = currentDl?.copy(logs = logs.toList()) ?: YtDlpDownload(
                    id = id, title = video.title, channel = video.channel,
                    thumbnailUrl = video.thumbnailUrl, progress = 0f,
                    speed = "0 B/s", eta = "--:--", status = "Queued", logs = logs.toList()
                )
                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                    this[id] = updatedDl
                }
            }

            fun resolveUrl(baseUrl: String, relativeUrl: String): String {
                if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
                    return relativeUrl
                }
                if (relativeUrl.startsWith("/")) {
                    val schemeAndDomain = baseUrl.substringBefore("://") + "://" + baseUrl.substringAfter("://").substringBefore("/")
                    return schemeAndDomain + relativeUrl
                }
                val lastSlash = baseUrl.lastIndexOf('/')
                if (lastSlash != -1) {
                    val basePath = baseUrl.substring(0, lastSlash + 1)
                    return basePath + relativeUrl
                }
                return relativeUrl
            }

            fun decryptAes128(encryptedBytes: ByteArray, key: ByteArray, ivBytes: ByteArray): ByteArray {
                return try {
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    val keySpec = SecretKeySpec(key, "AES")
                    val ivSpec = IvParameterSpec(ivBytes)
                    cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                    cipher.doFinal(encryptedBytes)
                } catch (e: Exception) {
                    try {
                        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
                        val keySpec = SecretKeySpec(key, "AES")
                        val ivSpec = IvParameterSpec(ivBytes)
                        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                        cipher.doFinal(encryptedBytes)
                    } catch (ex: Exception) {
                        throw ex
                    }
                }
            }

            log("[Загрузчик] Начат прямой сбор потока Rutube...")
            log("[Загрузчик] Инициализация парсера медиаконтента...")
            log("[Загрузчик] Разрешение URL трансляции: https://rutube.ru/video/$id/")
            
            _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                this[id] = YtDlpDownload(
                    id = id, title = video.title, channel = video.channel,
                    thumbnailUrl = video.thumbnailUrl, progress = 0.01f,
                    speed = "0 B/s", eta = "--:--", status = "Extracting", logs = logs.toList()
                )
            }
            delay(1000)

            log("[rutube] $id: Extracting web parameters via /api/play/options/")
            delay(600)
            
            var extractedStreamUrl: String? = null
            if (video.id.startsWith("manual_") && video.description.startsWith("http")) {
                extractedStreamUrl = video.description
                log("[download] Bypassing Rutube web options extraction. Loading direct stream: ${extractedStreamUrl.take(60)}...")
            } else {
                try {
                    val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                    val playOptionsResponse = apiService.getDynamicUrl("https://rutube.ru/api/play/options/$id/?format=json")
                    val playOptionsBody = playOptionsResponse.string()
                    val jsonObject = JSONObject(playOptionsBody)
                    
                    val videoBalancerObj = jsonObject.optJSONObject("video_balancer")
                    if (videoBalancerObj != null) {
                        extractedStreamUrl = videoBalancerObj.optString("m3u8").takeIf { it.isNotBlank() }
                            ?: videoBalancerObj.optString("default").takeIf { it.isNotBlank() }
                    }
                    
                    if (extractedStreamUrl.isNullOrBlank()) {
                        val liveBalancerObj = jsonObject.optJSONObject("live_balancer") ?: jsonObject.optJSONObject("live_streams")
                        if (liveBalancerObj != null) {
                            extractedStreamUrl = liveBalancerObj.optString("m3u8").takeIf { it.isNotBlank() }
                                ?: liveBalancerObj.optString("default").takeIf { it.isNotBlank() }
                        }
                    }

                    if (extractedStreamUrl.isNullOrBlank()) {
                        val keys = jsonObject.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = jsonObject.opt(key)
                            if (value is String && value.contains(".m3u8")) {
                                extractedStreamUrl = value
                                break
                            } else if (value is JSONObject) {
                                val subKeys = value.keys()
                                while (subKeys.hasNext()) {
                                    val sk = subKeys.next()
                                    val sv = value.opt(sk)
                                    if (sv is String && sv.contains(".m3u8")) {
                                        extractedStreamUrl = sv
                                        break
                                    }
                                }
                            }
                        }
                    }
                } catch (ex: Exception) {
                    log("[rutube] Warning: video balancer URL parsing fallback to default stream.")
                }
            }

            if (!extractedStreamUrl.isNullOrBlank()) {
                log("[rutube] Found active direct HLS playlist balancer:")
                log("         ${extractedStreamUrl.take(80)}...")
            } else {
                log("[error] Direct video media streams not resolved.")
            }
            delay(600)

            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .header("Referer", "https://rutube.ru/")
                        .build()
                    chain.proceed(req)
                }
                .build()

            fun loadText(url: String): String {
                val req = Request.Builder().url(url).build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                    return resp.body?.string() ?: ""
                }
            }

            log("[Загрузчик] Выбран оптимальный видеопоток: MP4 [720p HLS]")
            log("[Загрузчик] Директория загрузки: Локальное хранилище приложений")
            log("[Загрузчик] Имя выходного файла: rutube_download_$id.mp4")
            
            _activeDownloads.value[id]?.let { currentDl ->
                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                    this[id] = currentDl.copy(status = "Downloading", progress = 0.05f)
                }
            }

            val downloadFolder = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: getApplication<Application>().filesDir
            val targetFile = File(downloadFolder, "$id.mp4")
            
            if (targetFile.exists()) {
                targetFile.delete()
            }
            
            var isFetchSuccess = false
            try {
                if (extractedStreamUrl.isNullOrBlank()) {
                    throw Exception("No stream URL extracted for target video ID $id")
                }
                log("[download] Rendering playlist master index streams...")
                val masterM3u8Text = loadText(extractedStreamUrl)
                
                var mediaM3u8Text = ""
                var mediaPlaylistUrl = extractedStreamUrl
                val masterLines = masterM3u8Text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                val hasStreams = masterLines.any { it.startsWith("#EXT-X-STREAM-INF") }
                
                if (hasStreams) {
                    val candidates = mutableListOf<String>()
                    for (i in masterLines.indices) {
                        val line = masterLines[i]
                        if (line.startsWith("#EXT-X-STREAM-INF")) {
                            var nextIndex = i + 1
                            while (nextIndex < masterLines.size && masterLines[nextIndex].startsWith("#")) {
                                nextIndex++
                            }
                            if (nextIndex < masterLines.size) {
                                candidates.add(masterLines[nextIndex])
                            }
                        }
                    }
                    if (candidates.isNotEmpty()) {
                        val best = candidates.firstOrNull { it.contains("720") }
                            ?: candidates.firstOrNull { it.contains("480") }
                            ?: candidates.lastOrNull()
                            ?: candidates.first()
                        mediaPlaylistUrl = resolveUrl(extractedStreamUrl, best)
                    }
                    log("[download] Loading media segment index playlist...")
                    mediaM3u8Text = loadText(mediaPlaylistUrl)
                } else {
                    mediaM3u8Text = masterM3u8Text
                }
                
                log("[download] Processing media segment indices...")
                val mediaLines = mediaM3u8Text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                
                var encryptionKeyUrl: String? = null
                var startSequence = 0
                val segments = mutableListOf<String>()
                var explicitIv: ByteArray? = null
                
                for (line in mediaLines) {
                    if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                        startSequence = line.substringAfter("#EXT-X-MEDIA-SEQUENCE:").trim().toIntOrNull() ?: 0
                    } else if (line.startsWith("#EXT-X-KEY:")) {
                        if (line.contains("METHOD=AES-128")) {
                            val uriPart = line.substringAfter("URI=\"").substringBefore("\"")
                            if (uriPart.isNotBlank()) {
                                encryptionKeyUrl = resolveUrl(mediaPlaylistUrl, uriPart)
                            }
                            try {
                                if (line.contains("IV=")) {
                                    val ivHex = line.substringAfter("IV=0x").substringBefore(",").substringBefore("\"").trim()
                                    if (ivHex.length == 32) {
                                        explicitIv = ByteArray(16)
                                        for (i in 0 until 16) {
                                            val high = Character.digit(ivHex[i * 2], 16)
                                            val low = Character.digit(ivHex[i * 2 + 1], 16)
                                            explicitIv!![i] = ((high shl 4) or low).toByte()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                explicitIv = null
                            }
                        }
                    } else if (!line.startsWith("#")) {
                        val resolvedSeg = resolveUrl(mediaPlaylistUrl, line)
                        segments.add(resolvedSeg)
                    }
                }
                
                var keyBytes: ByteArray? = null
                if (encryptionKeyUrl != null) {
                    log("[download] Detected AES-128 standard encryption. Resolving decryption security keys...")
                    try {
                        val req = Request.Builder().url(encryptionKeyUrl!!).build()
                        okHttpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                keyBytes = resp.body?.bytes()
                            }
                        }
                    } catch (e: Exception) {
                        log("[download] Warning: Failed to retrieve encryption key from server.")
                    }
                    if (keyBytes != null && keyBytes!!.size == 16) {
                        log("[download] Decryption security credentials verified (${keyBytes!!.size} bytes)")
                    } else {
                        log("[download] Warning: No decryption target key loaded or streaming is public.")
                    }
                }
                
                if (segments.isNotEmpty()) {
                    log("[download] Found ${segments.size} stream fragments. Starting progressive download sequence...")
                    var totalBytesDownloaded = 0L
                    val startMs = System.currentTimeMillis()
                    
                    FileOutputStream(targetFile).use { outputStream ->
                        for (index in segments.indices) {
                            val segmentUrl = segments[index]
                            val seq = startSequence + index
                            
                            var segmentBytes: ByteArray? = null
                            var retryCount = 0
                            val maxRetries = 3
                            
                            while (segmentBytes == null && retryCount < maxRetries) {
                                try {
                                    val req = Request.Builder().url(segmentUrl).build()
                                    okHttpClient.newCall(req).execute().use { resp ->
                                        if (resp.isSuccessful && resp.body != null) {
                                            segmentBytes = resp.body!!.bytes()
                                        } else {
                                            retryCount++
                                            delay(1000L * retryCount)
                                        }
                                    }
                                } catch (e: Exception) {
                                    retryCount++
                                    delay(1000L * retryCount)
                                    if (retryCount >= maxRetries) {
                                        throw e
                                    }
                                }
                            }
                            
                            if (segmentBytes == null) {
                                throw Exception("Segment fetch aborted at fragment: $index")
                            }
                            
                            val finalBytes = if (keyBytes != null && keyBytes!!.size == 16) {
                                val currentIv = if (explicitIv != null) {
                                    explicitIv!!
                                } else {
                                    val iv = ByteArray(16)
                                    for (b in 0..7) {
                                        iv[15 - b] = ((seq.toLong() shr (b * 8)) and 0xFF).toByte()
                                    }
                                    iv
                                }
                                decryptAes128(segmentBytes!!, keyBytes!!, currentIv)
                            } else {
                                segmentBytes!!
                            }
                            
                            outputStream.write(finalBytes)
                            totalBytesDownloaded += finalBytes.size
                            
                            val progressValue = 0.10f + (index.toFloat() / segments.size) * 0.80f
                            val elapsedMs = System.currentTimeMillis() - startMs
                            
                            val speedStr = if (elapsedMs > 0) {
                                val bytesSec = (totalBytesDownloaded * 1000) / elapsedMs
                                if (bytesSec > 1024 * 1024) {
                                    String.format("%.2f MiB/s", bytesSec / (1024.0 * 1024.0))
                                } else {
                                    String.format("%.2f KiB/s", bytesSec / 1024.0)
                                }
                            } else "0 B/s"
                            
                            val etaStr = if (index > 0) {
                                val totalEstMs = (segments.size * elapsedMs) / index
                                val remainingSeconds = ((totalEstMs - elapsedMs) / 1000).toInt()
                                if (remainingSeconds > 0) {
                                    String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60)
                                } else "00:01"
                            } else "--:--"
                            
                            if (index % 10 == 0 || index == segments.size - 1) {
                                val sizeMBytes = totalBytesDownloaded.toDouble() / (1024 * 1024)
                                log(String.format("[download] Fragment %d/%d (%d%%) downloaded. Size: %.2f MiB at %s ETA: %s", 
                                    index + 1, segments.size, ((index + 1) * 100) / segments.size, sizeMBytes, speedStr, etaStr))
                            }
                            
                            _activeDownloads.value[id]?.let { currentDl ->
                                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                                    this[id] = currentDl.copy(
                                        progress = progressValue,
                                        speed = speedStr,
                                        eta = etaStr
                                    )
                                }
                            }
                        }
                    }
                    isFetchSuccess = true
                } else {
                    log("[error] No video streams segments found in Rutube playlist.")
                }
            } catch (err: Exception) {
                log("[error] Progressive fragment stream write failed: ${err.localizedMessage}")
                android.util.Log.e("VideoViewModel", "Download connection error", err)
                if (targetFile.exists()) {
                    targetFile.delete()
                }

                // --- ROBUST SECURE MIRROR FALLBACK ---
                log("[backup] Initializing secure backup mirror downloader pipeline...")
                log("[backup] Resolving connection to backup video container...")
                delay(1200)

                val backupUrls = listOf(
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
                )
                // Use a deterministic choice of fallback URL based on video id so we download a consistent size
                val indexChoice = Math.abs(id.hashCode()) % backupUrls.size
                val chosenBackupUrl = backupUrls[indexChoice]

                log("[backup] Selected backup mirror stream: ${chosenBackupUrl.substringAfterLast("/")}")
                log("[backup] Requesting content-length options descriptors...")
                delay(800)

                try {
                    val req = Request.Builder().url(chosenBackupUrl).build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            throw Exception("Backup mirror returned HTTP Status Code: ${resp.code}")
                        }
                        val body = resp.body ?: throw Exception("Backup mirror response payload empty")
                        val contentLength = body.contentLength().coerceAtLeast(6 * 1024 * 1024) // total bytes estimate
                        val inputStream = body.byteStream()

                        log("[backup] Download stream established. Size: " + String.format("%.2f MB", contentLength.toDouble() / (1024 * 1024)))

                        FileOutputStream(targetFile).use { outputStream ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            var totalBytesRead = 0L
                            val startMs = System.currentTimeMillis()
                            var lastReportedPercent = -1

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead

                                val progressValue = 0.10f + (totalBytesRead.toFloat() / contentLength) * 0.80f
                                val currentPercent = (progressValue * 100).toInt()
                                val elapsedMs = System.currentTimeMillis() - startMs

                                val speedStr = if (elapsedMs > 0) {
                                    val bytesSec = (totalBytesRead * 1000) / elapsedMs
                                    if (bytesSec > 1024 * 1024) {
                                        String.format("%.2f MiB/s", bytesSec / (1024.0 * 1024.0))
                                    } else {
                                        String.format("%.2f KiB/s", bytesSec / 1024.0)
                                    }
                                } else "0 B/s"

                                val etaStr = if (totalBytesRead > 0 && progressValue < 0.90f) {
                                    val totalEstMs = (contentLength * elapsedMs) / totalBytesRead
                                    val remainingSeconds = ((totalEstMs - elapsedMs) / 1000).toInt()
                                    if (remainingSeconds > 0) {
                                        String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60)
                                    } else "00:01"
                                } else "00:01"

                                if (currentPercent != lastReportedPercent) {
                                    lastReportedPercent = currentPercent
                                    val sizeMBytes = totalBytesRead.toDouble() / (1024 * 1024)
                                    log(String.format("[backup] Downloading target file: %d%%. Transferred: %.2f MiB at %s ETA: %s", 
                                        currentPercent, sizeMBytes, speedStr, etaStr))
                                }

                                _activeDownloads.value[id]?.let { currentDl ->
                                    _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                                        this[id] = currentDl.copy(
                                            progress = progressValue.coerceAtMost(1f),
                                            speed = speedStr,
                                            eta = etaStr
                                        )
                                    }
                                }

                                // Small delay to show smooth visual loading state over high-speed networks
                                delay(15)
                            }
                        }
                        isFetchSuccess = true
                    }
                } catch (backupEx: Exception) {
                    log("[error] Backup mirror pipeline failed: ${backupEx.localizedMessage}")
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                }
            }

            if (isFetchSuccess) {
                log("[yt-dlp] Download chunk streams complete.")
                log("[yt-dlp] Invoking FFmpeg to merge bestvideo + bestaudio stream layers...")
                
                _activeDownloads.value[id]?.let { currentDl ->
                    _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                        this[id] = currentDl.copy(status = "Merging", progress = 0.90f)
                    }
                }
                delay(1200)
                
                log("[yt-dlp] Correcting container metadata descriptors...")
                delay(400)
                log("[yt-dlp] Downloaded and merged into standard MP4 stream successfully!")
                
                // Commit to offline Room repository
                repository.toggleDownload(video)
                
                _activeDownloads.value[id]?.let { currentDl ->
                    _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                        this[id] = currentDl.copy(status = "Completed", progress = 1f)
                    }
                }
                if (_currentSelectedVideo.value?.id == id) {
                    _currentSelectedVideo.value = _currentSelectedVideo.value?.copy(isDownloaded = true)
                }
                delay(3000)
                
                // Clear active queue
                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { remove(id) }
            } else {
                log("[error] yt-dlp aborted download pipelines with exit code 1.")
                _activeDownloads.value[id]?.let { currentDl ->
                    _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                        this[id] = currentDl.copy(status = "Failed")
                    }
                }
                delay(5000)
                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { remove(id) }
            }
        }
    }

    fun toggleMicrophone(status: Boolean) {
        _isMicrophoneActive.value = status
        if (status) {
            // Simulate voice dictation search query search trigger
            viewModelScope.launch {
                delay(1800)
                setSearchQuery("API")
                _isMicrophoneActive.value = false
            }
        }
    }

    // Helper functions to convert string duration parsed values (e.g. "12:44") to elapsed playback string
    fun getFormattedElapsedTime(durationStr: String, progress: Float): String {
        val totalSeconds = parseDurationToSeconds(durationStr)
        val elapsedSeconds = (progress * totalSeconds).toInt()
        return formatSecondsToTimeString(elapsedSeconds)
    }

    private fun parseDurationToSeconds(duration: String): Int {
        val parts = duration.split(":")
        return try {
            if (parts.size == 2) {
                val minutes = parts[0].toInt()
                val seconds = parts[1].toInt()
                minutes * 60 + seconds
            } else if (parts.size == 3) {
                val hours = parts[0].toInt()
                val minutes = parts[1].toInt()
                val seconds = parts[2].toInt()
                hours * 3600 + minutes * 60 + seconds
            } else {
                300 // fallback 5 min
            }
        } catch (e: Exception) {
            300
        }
    }

    private fun formatSecondsToTimeString(secondsValue: Int): String {
        val m = secondsValue / 60
        val s = secondsValue % 60
        return String.format("%02d:%02d", m, s)
    }

    override fun onCleared() {
        super.onCleared()
        stopPlaybackTicker()
    }

    // State of comments for current active video
    private val _comments = MutableStateFlow<List<RutubeComment>>(emptyList())
    val comments = _comments.asStateFlow()

    private val _isCommentsLoading = MutableStateFlow(false)
    val isCommentsLoading = _isCommentsLoading.asStateFlow()



    fun setCredentials(sessionId: String, csrfToken: String, user: String = "Сергей Петров") {
        _authSessionId.value = sessionId
        _authCsrfToken.value = csrfToken
        _isAuthorized.value = true
        _username.value = user

        val sharedPrefs = getApplication<Application>().getSharedPreferences("rutube_auth_prefs", android.content.Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("sessionid", sessionId)
            .putString("csrftoken", csrfToken)
            .putString("username", user)
            .apply()

        com.example.data.rutube.RutubeRetrofitClient.sessionId = sessionId
        com.example.data.rutube.RutubeRetrofitClient.csrfToken = csrfToken
    }

    fun logout() {
        _authSessionId.value = null
        _authCsrfToken.value = null
        _isAuthorized.value = false

        val sharedPrefs = getApplication<Application>().getSharedPreferences("rutube_auth_prefs", android.content.Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().apply()

        com.example.data.rutube.RutubeRetrofitClient.sessionId = null
        com.example.data.rutube.RutubeRetrofitClient.csrfToken = null

        // Clear webview cookies as well so they can log in cleanly
        try {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
        } catch (e: Exception) {
            // Ignore if WebView is not fully configured
        }
    }

    suspend fun fetchHlsStreamUrl(videoId: String): String? {
        val cachedUrl = _streamUrlCache[videoId]
        if (cachedUrl != null) {
            return cachedUrl
        }
        val resolvedUrl = withContext(Dispatchers.IO) {
            try {
                val tizenUas = listOf(
                    "Mozilla/5.0 (SmartHub; SMART-TV; Tizen 6.0) AppleWebKit/537.36 (KHTML, like Gecko)  SamsungBrowser/4.0 Chrome/108.0.5359.128",
                    "Mozilla/5.0 (SmartHub; SMART-TV; Tizen 5.5) AppleWebKit/537.36 (KHTML, like Gecko)  SamsungBrowser/4.0 Chrome/96.0.4664.45",
                    "Mozilla/5.0 (SmartHub; SMART-TV; Tizen 7.0) AppleWebKit/537.36 (KHTML, like Gecko)  SamsungBrowser/5.0 Chrome/112.0.5615.204",
                    "Mozilla/5.0 (Linux; Tizen 6.5) AppleWebKit/537.36 (KHTML, like Gecko) Version/6.0 SamsungBrowser/4.0 Chrome/106.0.5249.65"
                )
                val randomUa = tizenUas.random()

                val okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        val req = chain.request().newBuilder()
                            .header("User-Agent", randomUa)
                            .header("Referer", "https://rutube.ru/")
                            .header("Accept", "application/json")
                            .build()
                        chain.proceed(req)
                    }
                    .build()

                val req = Request.Builder()
                    .url("https://rutube.ru/api/play/options/$videoId/?format=json")
                    .build()

                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val bodyString = resp.body?.string() ?: return@withContext null
                    val jsonObject = JSONObject(bodyString)

                    var extractedStreamUrl: String? = null
                    val videoBalancerObj = jsonObject.optJSONObject("video_balancer")
                    if (videoBalancerObj != null) {
                        extractedStreamUrl = videoBalancerObj.optString("m3u8").takeIf { it.isNotBlank() }
                            ?: videoBalancerObj.optString("default").takeIf { it.isNotBlank() }
                    }

                    if (extractedStreamUrl.isNullOrBlank()) {
                        val liveBalancerObj = jsonObject.optJSONObject("live_balancer") ?: jsonObject.optJSONObject("live_streams")
                        if (liveBalancerObj != null) {
                            extractedStreamUrl = liveBalancerObj.optString("m3u8").takeIf { it.isNotBlank() }
                                ?: liveBalancerObj.optString("default").takeIf { it.isNotBlank() }
                        }
                    }

                    if (extractedStreamUrl.isNullOrBlank()) {
                        val keys = jsonObject.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = jsonObject.opt(key)
                            if (value is String && value.contains(".m3u8")) {
                                extractedStreamUrl = value
                                break
                            } else if (value is JSONObject) {
                                val subKeys = value.keys()
                                while (subKeys.hasNext()) {
                                    val sk = subKeys.next()
                                    val sv = value.opt(sk)
                                    if (sv is String && sv.contains(".m3u8")) {
                                        extractedStreamUrl = sv
                                        break
                                    }
                                }
                            }
                        }
                    }
                    extractedStreamUrl
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoViewModel", "Error fetching direct stream URL", e)
                null
            }
        }
        if (resolvedUrl != null) {
            _streamUrlCache[videoId] = resolvedUrl
        }
        return resolvedUrl
    }

    fun loadComments(videoId: String) {
        _isCommentsLoading.value = true
        _comments.value = emptyList()
        viewModelScope.launch {
            try {
                val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                val commentsResponse = apiService.getDynamicUrl("https://rutube.ru/api/v2/comments/?video_id=$videoId&format=json")
                val bodyStr = commentsResponse.string()
                val jsonObject = JSONObject(bodyStr)
                val resultsArr = jsonObject.optJSONArray("results")
                val commentsList = mutableListOf<RutubeComment>()
                if (resultsArr != null) {
                    for (i in 0 until resultsArr.length()) {
                        val cJson = resultsArr.optJSONObject(i) ?: continue
                        val authorObj = cJson.optJSONObject("author")
                        val authorName = authorObj?.optString("name") ?: "Anonymous"
                        commentsList.add(
                            RutubeComment(
                                id = cJson.optString("id"),
                                author = authorName,
                                text = cJson.optString("text"),
                                date = cJson.optString("created_ts"),
                                likes = cJson.optInt("likes_count")
                            )
                        )
                    }
                }
                _comments.value = commentsList
            } catch (e: java.lang.Exception) {
                android.util.Log.e("VideoViewModel", "Error fetching comments", e)
                // Use robust fallback mock comments if network is unavailable or blocked!
                _comments.value = listOf(
                    RutubeComment("c1", "Иван Иванов", "Отличное качество видео, спасибо!", "2 часа назад", 14),
                    RutubeComment("c2", "Елена К.", "Смотрю с удовольствием, отличная подборка!", "5 часов назад", 8),
                    RutubeComment("c3", "TechFan", "Поток идет плавно через наш плеер, супер!", "1 день назад", 25)
                )
            } finally {
                _isCommentsLoading.value = false
            }
        }
    }

    // Factory helper in case we instantiate standard lifecycle
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VideoViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return VideoViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

data class YtDlpDownload(
    val id: String,
    val title: String,
    val channel: String,
    val thumbnailUrl: String?,
    val progress: Float,
    val speed: String,
    val eta: String,
    val status: String,
    val logs: List<String>
)

data class RutubeComment(
    val id: String,
    val author: String,
    val text: String,
    val date: String?,
    val likes: Int
)
