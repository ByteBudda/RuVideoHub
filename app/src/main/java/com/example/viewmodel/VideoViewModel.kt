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

    private val categorySlugs = mapOf(
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

    private val db = AppDatabase.getDatabase(application)
    private val repository = VideoRepository(db.savedVideoDao())

    // Bottom Navigation tab states: "home", "explore", "downloads", "library"
    private val _currentTab = MutableStateFlow("home")
    val currentTab = _currentTab.asStateFlow()

    // Theme switching state (true for dark, false for light)
    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme = _isDarkTheme.asStateFlow()

    fun toggleTheme() {
        val newValue = !_isDarkTheme.value
        _isDarkTheme.value = newValue
        val sharedPrefs = getApplication<Application>().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("is_dark_theme", newValue).apply()
    }

    // TV Optimization / Low power state for weak devices
    private val _isTvOptimized = MutableStateFlow(false)
    val isTvOptimized = _isTvOptimized.asStateFlow()

    fun toggleTvOptimized() {
        val newValue = !_isTvOptimized.value
        _isTvOptimized.value = newValue
        val sharedPrefs = getApplication<Application>().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("is_tv_optimized", newValue).apply()
    }

    // User Agreement State
    private val _isTermsAgreed = MutableStateFlow(false)
    val isTermsAgreed = _isTermsAgreed.asStateFlow()

    fun agreeToTerms() {
        _isTermsAgreed.value = true
        val sharedPrefs = getApplication<Application>().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("terms_agreed", true).apply()
    }

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

    // TV Mini Player Fullscreen state
    private val _isTvMiniFullscreen = MutableStateFlow(false)
    val isTvMiniFullscreen = _isTvMiniFullscreen.asStateFlow()

    fun setTvMiniFullscreen(fullscreen: Boolean) {
        _isTvMiniFullscreen.value = fullscreen
    }

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

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _realCategories = MutableStateFlow<List<RutubeCategory>>(emptyList())
    val realCategories = _realCategories.asStateFlow()

    private val _isCategoriesLoading = MutableStateFlow(true)
    val isCategoriesLoading = _isCategoriesLoading.asStateFlow()

    private val _feedTabs = MutableStateFlow<List<com.example.data.rutube.SmartRutubeParser.TabInfo>>(emptyList())
    val feedTabs = _feedTabs.asStateFlow()

    private val _selectedFeedTab = MutableStateFlow<com.example.data.rutube.SmartRutubeParser.TabInfo?>(null)
    val selectedFeedTab = _selectedFeedTab.asStateFlow()

    private val _selectedSubfolderName = MutableStateFlow<String?>(null)
    val selectedSubfolderName = _selectedSubfolderName.asStateFlow()

    private val _isChannelView = MutableStateFlow(false)
    val isChannelView = _isChannelView.asStateFlow()

    private val _currentChannelVideo = MutableStateFlow<Video?>(null)
    val currentChannelVideo: StateFlow<Video?> = combine(_currentChannelVideo, repository.getSavedVideosOnly()) { activeChannel, savedList ->
        if (activeChannel == null) return@combine null
        val saved = savedList.firstOrNull { it.id == activeChannel.id }
        activeChannel.copy(
            isDownloaded = saved?.isDownloaded ?: false,
            isBookmarked = saved?.isBookmarked ?: false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _channelVideos = MutableStateFlow<List<Video>>(emptyList())
    val channelVideos: StateFlow<List<Video>> = combine(_channelVideos, repository.getSavedVideosOnly()) { videos, savedList ->
        val savedMap = savedList.associateBy { it.id }
        videos.map { vid ->
            val saved = savedMap[vid.id]
            vid.copy(
                isDownloaded = saved?.isDownloaded ?: false,
                isBookmarked = saved?.isBookmarked ?: false
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _channelPlaylists = MutableStateFlow<List<Video>>(emptyList())
    val channelPlaylists: StateFlow<List<Video>> = combine(_channelPlaylists, repository.getSavedVideosOnly()) { playlists, savedList ->
        val savedMap = savedList.associateBy { it.id }
        playlists.map { pl ->
            val saved = savedMap[pl.id]
            pl.copy(
                isDownloaded = saved?.isDownloaded ?: false,
                isBookmarked = saved?.isBookmarked ?: false
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _channelActiveTab = MutableStateFlow("Видео")
    val channelActiveTab = _channelActiveTab.asStateFlow()
    
    private val _isLoadingPlaylists = MutableStateFlow(false)
    val isLoadingPlaylists = _isLoadingPlaylists.asStateFlow()

    fun setChannelActiveTab(tab: String) {
        _channelActiveTab.value = tab
    }

    data class NavigationSnapshot(
        val tab: String,
        val category: String,
        val feedTab: com.example.data.rutube.SmartRutubeParser.TabInfo?,
        val subfolderName: String?,
        val searchQuery: String,
        val selectedVideo: Video?,
        val isChannelView: Boolean = false,
        val currentChannelVideo: Video? = null,
        val channelVideos: List<Video> = emptyList(),
        val channelPlaylists: List<Video> = emptyList(),
        val channelActiveTab: String = "Видео",
        val dynamicVideos: List<Video> = emptyList(),
        val currentPage: Int = 1,
        val isEndReached: Boolean = false,
        val currentQuery: String? = null,
        val currentCategory: String? = null,
        val currentActiveApiEndpoint: String? = null
    )

    private val navHistory = java.util.Stack<NavigationSnapshot>()

    fun pushToHistory() {
        val currentSnapshot = NavigationSnapshot(
            tab = _currentTab.value,
            category = _selectedCategory.value,
            feedTab = _selectedFeedTab.value,
            subfolderName = _selectedSubfolderName.value,
            searchQuery = _searchQuery.value,
            selectedVideo = _currentSelectedVideo.value,
            isChannelView = _isChannelView.value,
            currentChannelVideo = _currentChannelVideo.value,
            channelVideos = _channelVideos.value,
            channelPlaylists = _channelPlaylists.value,
            channelActiveTab = _channelActiveTab.value,
            dynamicVideos = _dynamicVideos.value,
            currentPage = currentPage,
            isEndReached = isEndReached,
            currentQuery = currentQuery,
            currentCategory = currentCategory,
            currentActiveApiEndpoint = currentActiveApiEndpoint
        )
        if (navHistory.isEmpty() || navHistory.peek() != currentSnapshot) {
            navHistory.push(currentSnapshot)
        }
    }

    fun canNavigateBack(): Boolean {
        if (_currentSelectedVideo.value != null) return true
        if (_searchQuery.value.isNotEmpty()) return true
        return !navHistory.isEmpty()
    }

    fun navigateBack(): Boolean {
        if (_currentSelectedVideo.value != null) {
            _currentSelectedVideo.value = null
            return true
        }

        if (_searchQuery.value.isNotEmpty()) {
            setSearchQuery("")
            return true
        }

        if (!navHistory.isEmpty()) {
            val last = navHistory.pop()
            _currentTab.value = last.tab
            _selectedCategory.value = last.category
            _selectedFeedTab.value = last.feedTab
            _selectedSubfolderName.value = last.subfolderName
            _searchQuery.value = last.searchQuery
            _currentSelectedVideo.value = last.selectedVideo
            _isChannelView.value = last.isChannelView
            _currentChannelVideo.value = last.currentChannelVideo
            _channelVideos.value = last.channelVideos
            _channelPlaylists.value = last.channelPlaylists
            _channelActiveTab.value = last.channelActiveTab
            _dynamicVideos.value = last.dynamicVideos
            
            currentPage = last.currentPage
            isEndReached = last.isEndReached
            currentQuery = last.currentQuery
            currentCategory = last.currentCategory
            currentActiveApiEndpoint = last.currentActiveApiEndpoint

            return true
        }

        if (_currentTab.value != "home") {
            _currentTab.value = "home"
            return true
        }

        return false
    }



    // yt-dlp downloading state parameters
    private val _activeDownloads = MutableStateFlow<Map<String, YtDlpDownload>>(emptyMap())
    val activeDownloads = _activeDownloads.asStateFlow()

    private val _streamUrlCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val _masterUrlCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val _parsedStreamsCache = java.util.concurrent.ConcurrentHashMap<String, List<com.example.data.rutube.HlsStream>>()

    // Quality Selection settings: "Авто", "1080p", "720p", "480p", "360p", "240p"
    private val _playerQuality = MutableStateFlow("Авто")
    val playerQuality = _playerQuality.asStateFlow()

    private val _downloadQuality = MutableStateFlow("720p")
    val downloadQuality = _downloadQuality.asStateFlow()

    // Available qualities for the currently loaded video
    private val _currentAvailableQualities = MutableStateFlow<List<String>>(listOf("Авто"))
    val currentAvailableQualities = _currentAvailableQualities.asStateFlow()

    fun setPlayerQuality(quality: String) {
        _playerQuality.value = quality
        val sharedPrefs = getApplication<Application>().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("player_quality", quality).apply()
    }

    fun setDownloadQuality(quality: String) {
        _downloadQuality.value = quality
        val sharedPrefs = getApplication<Application>().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("download_quality", quality).apply()
    }

    // Expose active loading source: Rutube API Live, Offline database, Built-in hits
    val apiSource = flow {
        while (true) {
            emit(repository.lastFetchSource)
            delay(1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Инициализация...")

    private var currentPage = 1
    private var isEndReached = false
    private var currentQuery: String? = null
    private var currentCategory: String? = "Фильмы"
    private var currentActiveApiEndpoint: String? = null

    private val _isMoreLoading = MutableStateFlow(false)
    val isMoreLoading = _isMoreLoading.asStateFlow()

    init {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val savedIsDarkTheme = sharedPrefs.getBoolean("is_dark_theme", true)
        _isDarkTheme.value = savedIsDarkTheme

        val savedTermsAgreed = sharedPrefs.getBoolean("terms_agreed", false)
        _isTermsAgreed.value = savedTermsAgreed

        // Auto-detect Android TV if UI mode or leanback is active, otherwise default to user preference
        val uiModeManager = getApplication<Application>().getSystemService(android.content.Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        val isDeviceTv = uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
                getApplication<Application>().packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        val savedIsTvOptimized = sharedPrefs.getBoolean("is_tv_optimized", isDeviceTv)
        _isTvOptimized.value = savedIsTvOptimized

        val savedPlayerQuality = sharedPrefs.getString("player_quality", "Авто") ?: "Авто"
        _playerQuality.value = savedPlayerQuality

        val savedDownloadQuality = sharedPrefs.getString("download_quality", "720p") ?: "720p"
        _downloadQuality.value = savedDownloadQuality

        fetchRealVideos()
        fetchRealCategories()
    }

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


    fun selectCategory(category: String, targetUrl: String? = null) {
        pushToHistory()
        _selectedCategory.value = category
        _searchQuery.value = ""
        fetchRealVideos(query = null, category = category, targetUrl = targetUrl)
    }

    fun selectFeedTab(tab: com.example.data.rutube.SmartRutubeParser.TabInfo) {
        selectFeedTabInternal(tab, pushHistory = true)
    }

    private fun selectFeedTabInternal(tab: com.example.data.rutube.SmartRutubeParser.TabInfo, pushHistory: Boolean) {
        if (pushHistory) {
            pushToHistory()
        }
        _selectedFeedTab.value = tab
        _selectedSubfolderName.value = null
        _isChannelView.value = false
        _currentChannelVideo.value = null
        _channelVideos.value = emptyList()
        _channelPlaylists.value = emptyList()
        _channelActiveTab.value = "Видео"
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                // Any tab with more than 1 resource contains subcategories/folders to display
                val isFolderTab = tab.resources.size > 1

                if (isFolderTab) {
                    val folderVideos = tab.resources.map { resource ->
                        mapResourceToVideo(resource, tab.id, _selectedCategory.value)
                    }
                    _dynamicVideos.value = folderVideos
                    currentPage = 1
                    isEndReached = true
                    currentActiveApiEndpoint = null
                } else {
                    val combined = mutableListOf<Video>()
                    for (resource in tab.resources.take(3)) {
                        val rawUrl = resource.url ?: continue
                        val cleanApiUrl = toRutubeApiUrl(rawUrl)
                        val finalUrl = if (cleanApiUrl.contains("?")) {
                            if (cleanApiUrl.contains("format=json")) cleanApiUrl else "$cleanApiUrl&format=json"
                        } else {
                            "$cleanApiUrl?format=json"
                        }
                        
                        try {
                            val response = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(finalUrl)
                            val bodyStr = response.string()
                            val parsedVideos = repository.parseVideoListJson(bodyStr, _selectedCategory.value)
                            combined.addAll(parsedVideos)
                            currentActiveApiEndpoint = toRutubeApiUrl(rawUrl)
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
    private var requestId = 0
    fun fetchRealVideos(query: String? = null, category: String? = null, targetUrl: String? = null) {
        fetchJob?.cancel()
        val currentRequestId = ++requestId
        _selectedSubfolderName.value = null
        _isChannelView.value = false
        _currentChannelVideo.value = null
        _channelVideos.value = emptyList()
        _channelPlaylists.value = emptyList()
        _channelActiveTab.value = "Видео"
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
                    // Check if VK url
                    val parsedVk = com.example.data.vk.VkVideoLoader.parseVideoUrl(q)
                    if (parsedVk != null) {
                        try {
                            val info = com.example.data.vk.VkVideoLoader.getVideoInfo(q)
                            if (info != null) {
                                val vkVideoId = "vk_${info.ownerId}_${info.videoId}"
                                _masterUrlCache[vkVideoId] = info.videoUrl
                                
                                val vkVideo = Video(
                                    id = vkVideoId,
                                    title = info.title,
                                    channel = "VK Видео (id: ${info.ownerId})",
                                    views = "${info.views} просмотров",
                                    timeAgo = "Только что",
                                    duration = info.duration,
                                    category = "VK",
                                    description = "Видео из VK. Воспроизведение и скачивание.",
                                    thumbnailUrl = info.thumbnail,
                                    isDownloaded = false,
                                    isBookmarked = false
                                )
                                val savedMap = repository.getSavedVideosOnly().first().associateBy { it.id }
                                val saved = savedMap[vkVideoId]
                                val finalVideo = vkVideo.copy(
                                    isDownloaded = saved?.isDownloaded ?: false,
                                    isBookmarked = saved?.isBookmarked ?: false
                                )
                                if (currentRequestId == requestId) {
                                    _feedTabs.value = emptyList()
                                    _selectedFeedTab.value = null
                                    _dynamicVideos.value = listOf(finalVideo)
                                }
                            } else {
                                if (currentRequestId == requestId) {
                                    _dynamicVideos.value = emptyList()
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("VideoViewModel", "Error fetching VK video in search", e)
                            if (currentRequestId == requestId) {
                                _dynamicVideos.value = emptyList()
                            }
                        }
                        return@launch
                    }

                    // Check if Rutube url
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
                                    _selectedFeedTab.value = null
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

                    val fetched = repository.fetchRealVideos(q, targetCategory, page = 1)
                    if (currentRequestId == requestId) {
                        _feedTabs.value = emptyList()
                        _selectedFeedTab.value = null
                        _dynamicVideos.value = fetched
                    }
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
                        val cleanApiUrl = toRutubeApiUrl(urlToFetch)
                        val finalUrl = if (cleanApiUrl.contains("?")) {
                            if (cleanApiUrl.contains("format=json")) cleanApiUrl else "$cleanApiUrl&format=json"
                        } else {
                            "$cleanApiUrl?format=json"
                        }
                        
                        val response = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(finalUrl)
                        if (currentRequestId != requestId) return@launch
                        val bodyStr = response.string()
                        val jsonObj = org.json.JSONObject(bodyStr)
                        val parsed = com.example.data.rutube.SmartRutubeParser.ResponseAnalyzer.parse(jsonObj, finalUrl)
                        
                        if (currentRequestId != requestId) return@launch
                        
                        if (parsed.tabs.isNotEmpty()) {
                            _feedTabs.value = parsed.tabs
                            selectFeedTabInternal(parsed.tabs.first(), pushHistory = false)
                        } else {
                            _feedTabs.value = emptyList()
                            _selectedFeedTab.value = null
                            val parsedVideos = repository.parseVideoListJson(bodyStr, targetCategory)
                            if (parsedVideos.isNotEmpty()) {
                                _dynamicVideos.value = parsedVideos
                                currentActiveApiEndpoint = toRutubeApiUrl(urlToFetch)
                            } else {
                                _dynamicVideos.value = repository.fetchRealVideos(null, targetCategory, page = 1)
                            }
                        }
                    } else {
                        if (currentRequestId != requestId) return@launch
                        _feedTabs.value = emptyList()
                        _selectedFeedTab.value = null
                        _dynamicVideos.value = repository.fetchRealVideos(null, targetCategory, page = 1)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoViewModel", "Error in fetchRealVideos with target", e)
                if (currentRequestId == requestId) {
                    _feedTabs.value = emptyList()
                    _selectedFeedTab.value = null
                    _dynamicVideos.value = repository.fetchRealVideos(query, targetCategory, page = 1)
                }
            } finally {
                if (currentRequestId == requestId) {
                    _isLoading.value = false
                }
            }
        }
    }

    fun loadNextPage() {
        if (_isMoreLoading.value || isEndReached || _isLoading.value) return
        
        val snapshotEndpoint = currentActiveApiEndpoint
        val snapshotCategory = currentCategory
        val snapshotQuery = currentQuery
        val snapshotPage = currentPage + 1
        val snapshotChannelView = _isChannelView.value
        val snapshotChannelActiveTab = _channelActiveTab.value
        
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
                    val cleanEndpoint = toRutubeApiUrl(endpoint)
                    val separator = if (cleanEndpoint.contains("?")) "&" else "?"
                    val url = "${cleanEndpoint}${separator}format=json&page=$snapshotPage"
                    val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                    val response = apiService.getDynamicUrl(url)
                    val bodyStr = response.string()
                    repository.parseVideoListJson(bodyStr, snapshotCategory ?: "Фильмы")
                } else if (snapshotEndpoint != null) {
                    val cleanEndpoint = toRutubeApiUrl(snapshotEndpoint)
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
                    _isChannelView.value != snapshotChannelView) {
                    return@launch
                }

                if (newVideos.isEmpty()) {
                    isEndReached = true
                } else {
                    currentPage = snapshotPage
                    if (snapshotChannelView) {
                        if (snapshotChannelActiveTab == "Видео") {
                            _channelVideos.value = (_channelVideos.value + newVideos).distinctBy { it.id }
                        } else {
                            _channelPlaylists.value = (_channelPlaylists.value + newVideos).distinctBy { it.id }
                        }
                    } else {
                        _dynamicVideos.value = (_dynamicVideos.value + newVideos).distinctBy { it.id }
                    }
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
        if (_currentTab.value != tab) {
            _currentTab.value = tab
        }
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
            val parsedFeed = com.example.data.rutube.SmartRutubeParser.ResponseAnalyzer.parse(jsonObj, apiUrl)
            if (parsedFeed.tabs.isNotEmpty()) {
                val urls = mutableListOf<String>()
                for (tab in parsedFeed.tabs) {
                    for (res in tab.resources) {
                        val resUrl = res.url
                        if (!resUrl.isNullOrBlank()) {
                            urls.add(resUrl)
                        }
                    }
                }
                
                val combined = mutableListOf<Video>()
                for (rawUrl in urls.take(2)) {
                    try {
                        val cleanSubUrl = toRutubeApiUrl(rawUrl)
                        val subFinalUrl = if (cleanSubUrl.contains("?")) {
                            if (cleanSubUrl.contains("format=json")) cleanSubUrl else "$cleanSubUrl&format=json"
                        } else {
                            "$cleanSubUrl?format=json"
                        }
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

    private fun cleanRutubeUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (url.contains("?")) {
            url = url.substringBefore("?")
        }
        val trimmed = url.trimEnd('/')
        return if (trimmed.startsWith("http")) trimmed else "https://rutube.ru${if (trimmed.startsWith("/")) "" else "/"}$trimmed"
    }

    private fun toRutubeApiUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (url.isBlank()) return ""
        val absoluteUrl = if (url.startsWith("http")) {
            url
        } else {
            "https://rutube.ru${if (url.startsWith("/")) "" else "/"}$url"
        }
        
        val baseWithoutQuery = absoluteUrl.substringBefore("?")
        val queryPart = if (absoluteUrl.contains("?")) "?" + absoluteUrl.substringAfter("?") else ""
        
        val cleanedBase = baseWithoutQuery.removeSuffix("/")
        val domainPrefix = "https://rutube.ru/"
        if (!cleanedBase.startsWith(domainPrefix)) {
            return absoluteUrl
        }
        
        val path = cleanedBase.substring(domainPrefix.length)
        val apiPath = when {
            path.startsWith("plst/") -> {
                val plId = path.substringAfter("plst/")
                "api/playlist/custom/$plId/videos/"
            }
            path.startsWith("api/video/playlist/") -> {
                val plId = path.substringAfter("api/video/playlist/")
                "api/playlist/custom/$plId/videos/"
            }
            path.startsWith("playlist/") -> {
                val plId = path.substringAfter("playlist/")
                "api/playlist/custom/$plId/videos/"
            }
            path.startsWith("tv/") -> {
                val tvId = path.substringAfter("tv/")
                if (tvId.contains("video")) "api/metainfo/$path" else "api/metainfo/tv/$tvId/video/"
            }
            path.startsWith("series/") -> {
                val seriesId = path.substringAfter("series/")
                if (seriesId.contains("video")) "api/metainfo/$path" else "api/metainfo/tv/$seriesId/video/"
            }
            path.startsWith("brand/") -> {
                val brandId = path.substringAfter("brand/")
                if (brandId.contains("video")) "api/metainfo/$path" else "api/metainfo/tv/$brandId/video/"
            }
            path.startsWith("channel/") -> {
                val chId = path.substringAfter("channel/")
                "api/video/person/$chId/"
            }
            path.startsWith("person/") -> {
                val pId = path.substringAfter("person/")
                "api/video/person/$pId/"
            }
            path.startsWith("video/person/") -> {
                val pId = path.substringAfter("video/person/")
                "api/video/person/$pId/"
            }
            path.startsWith("video/") -> {
                val vidId = path.substringAfter("video/")
                "api/video/$vidId/"
            }
            path.startsWith("api/") -> path
            else -> "api/$path/"
        }
        
        val finalApiUrl = "https://rutube.ru/$apiPath".replace("https://rutube.ru//", "https://rutube.ru/").replace("https://rutube.ru/api/api/", "https://rutube.ru/api/")
        val result = if (queryPart.isNotBlank()) {
            if (finalApiUrl.contains("?")) {
                finalApiUrl + "&" + queryPart.removePrefix("?")
            } else {
                finalApiUrl + queryPart
            }
        } else {
            finalApiUrl
        }
        return result
    }

    fun selectVideo(video: Video?) {
        if (video == null) {
            _currentSelectedVideo.value = null
            _isPlaying.value = false
            stopPlaybackTicker()
            return
        }

        val prefix = when {
            video.id.startsWith("tv_") -> "tv_"
            video.id.startsWith("channel_") -> "channel_"
            video.id.startsWith("playlist_") -> "playlist_"
            video.id.startsWith("promo_") -> "promo_"
            video.id.startsWith("unknown_") -> "unknown_"
            else -> ""
        }
        val fullIdWithUrl = if (prefix.isNotEmpty()) video.id.removePrefix(prefix) else video.id
        val idParts = fullIdWithUrl.split("__")
        val rawId = idParts.getOrNull(0) ?: ""
        val actionUrl = idParts.getOrNull(1) ?: ""
        val finalActionUrl = actionUrl.takeIf { it.isNotBlank() && it != "null" } ?: video.authorActionUrl ?: ""

        val trueType = when {
            video.duration == "КАНАЛ" -> "CHANNEL"
            video.duration == "СЕРИАЛ" -> "TV_SERIES"
            video.duration == "ПЛЕЙЛИСТ" -> "PLAYLIST"
            video.duration in listOf("КАТАЛОГ", "ПАПКА", "ПРОМО") -> "CATALOG"
            
            prefix.isEmpty() -> "VIDEO"
            
            finalActionUrl.contains("/channel/") || finalActionUrl.contains("/person/") || finalActionUrl.contains("/video/person/") -> "CHANNEL"
            finalActionUrl.contains("/tv/") || finalActionUrl.contains("/serial/") || finalActionUrl.contains("/series/") || finalActionUrl.contains("/brand/") || finalActionUrl.contains("/metainfo/tv/") -> "TV_SERIES"
            finalActionUrl.contains("/playlist/") || finalActionUrl.contains("/plst/") -> "PLAYLIST"
            finalActionUrl.contains("/feeds/") || finalActionUrl.contains("/promogroup/") || finalActionUrl.contains("/tags/") -> "CATALOG"
            
            video.id.startsWith("channel_") -> "CHANNEL"
            video.id.startsWith("tv_") -> "TV_SERIES"
            video.id.startsWith("playlist_") -> "PLAYLIST"
            video.id.startsWith("unknown_") || video.id.startsWith("promo_") -> "CATALOG"
            
            else -> "VIDEO"
        }

        if (trueType != "VIDEO") {
            pushToHistory()
        }

        when (trueType) {
            "CHANNEL" -> {
                viewModelScope.launch {
                    _isChannelView.value = true
                    _currentChannelVideo.value = video
                    _selectedSubfolderName.value = video.title
                    _searchQuery.value = ""
                    _channelVideos.value = emptyList()
                    _channelPlaylists.value = emptyList()
                    _channelActiveTab.value = "Видео"
                    selectTab("home")
                    currentPage = 1
                    isEndReached = false

                    val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService

                    launch {
                        try {
                            val searchName = video.title.takeIf { it.isNotBlank() } ?: video.channel
                            if (searchName.isNotBlank()) {
                                val encodedTitle = java.net.URLEncoder.encode(searchName, "UTF-8")
                                val searchPersonUrl = "https://rutube.ru/api/search/person/?query=$encodedTitle&format=json"
                                val response = apiService.getDynamicUrl(searchPersonUrl)
                                val bodyStr = response.string()
                                val channels = repository.parseVideoListJson(bodyStr, video.category, searchPersonUrl)
                                val matchedChannel = channels.firstOrNull { 
                                    it.authorId == rawId || it.title.equals(searchName, ignoreCase = true)
                                } ?: channels.firstOrNull()
                                
                                if (matchedChannel != null) {
                                    val currentVal = _currentChannelVideo.value
                                    if (currentVal != null) {
                                        _currentChannelVideo.value = currentVal.copy(
                                            views = matchedChannel.views.takeIf { it.isNotBlank() } ?: currentVal.views,
                                            timeAgo = matchedChannel.timeAgo.takeIf { it.isNotBlank() } ?: currentVal.timeAgo,
                                            description = matchedChannel.description.takeIf { it.isNotBlank() } ?: currentVal.description,
                                            thumbnailUrl = matchedChannel.thumbnailUrl.takeIf { !it.isNullOrBlank() } ?: currentVal.thumbnailUrl,
                                            authorAvatarUrl = matchedChannel.authorAvatarUrl.takeIf { !it.isNullOrBlank() } ?: currentVal.authorAvatarUrl,
                                            channel = matchedChannel.channel.takeIf { it.isNotBlank() } ?: currentVal.channel
                                        )
                                    }
                                }
                            }
                        } catch (ex: Exception) {
                            android.util.Log.e("VideoViewModel", "Failed to enrich channel metadata", ex)
                        }
                    }

                    launch {
                        _isLoading.value = true
                        try {
                            var loadedVideos: List<Video> = emptyList()
                            val fallbackUrls = mutableListOf<String>()

                            if (finalActionUrl.isNotBlank()) {
                                val cleanApiUrl = toRutubeApiUrl(finalActionUrl)
                                val finalUrl = if (cleanApiUrl.contains("?")) {
                                    if (cleanApiUrl.contains("format=json")) cleanApiUrl else "$cleanApiUrl&format=json"
                                } else {
                                    "$cleanApiUrl?format=json"
                                }
                                fallbackUrls.add(finalUrl)
                            }

                            if (rawId.isNotBlank() && rawId.all { it.isLetterOrDigit() }) {
                                fallbackUrls.add("https://rutube.ru/api/video/person/$rawId/?format=json")
                                fallbackUrls.add("https://rutube.ru/api/feeds/person/$rawId/?format=json")
                                fallbackUrls.add("https://rutube.ru/api/feeds/person/$rawId/video/?format=json")
                                fallbackUrls.add("https://rutube.ru/api/metainfo/person/$rawId/video/?format=json")
                            }

                            for (url in fallbackUrls.distinct()) {
                                val candidates = fetchVideosResolvingTabs(url, video.category)
                                
                                val fetchedChannel = candidates.firstOrNull { it.duration == "КАНАЛ" }
                                if (fetchedChannel != null) {
                                    val currentVal = _currentChannelVideo.value
                                    if (currentVal != null) {
                                        _currentChannelVideo.value = currentVal.copy(
                                            views = fetchedChannel.views.takeIf { it.isNotBlank() } ?: currentVal.views,
                                            timeAgo = fetchedChannel.timeAgo.takeIf { it.isNotBlank() } ?: currentVal.timeAgo,
                                            description = fetchedChannel.description.takeIf { it.isNotBlank() } ?: currentVal.description,
                                            thumbnailUrl = fetchedChannel.thumbnailUrl.takeIf { !it.isNullOrBlank() } ?: currentVal.thumbnailUrl,
                                            authorAvatarUrl = fetchedChannel.authorAvatarUrl.takeIf { !it.isNullOrBlank() } ?: currentVal.authorAvatarUrl,
                                            channel = fetchedChannel.channel.takeIf { it.isNotBlank() } ?: currentVal.channel
                                        )
                                    }
                                }

                                val hasPlayableVideos = candidates.any {
                                    it.duration != "СЕРИАЛ" && it.duration != "КАНАЛ" && 
                                    it.duration != "ПЛЕЙЛИСТ" && it.duration != "ПРОМО" && 
                                    it.duration != "КАТАЛОГ"
                                }
                                if (candidates.isNotEmpty()) {
                                    if (hasPlayableVideos) {
                                        loadedVideos = candidates
                                        currentActiveApiEndpoint = toRutubeApiUrl(url)
                                        break
                                    } else if (loadedVideos.isEmpty()) {
                                        loadedVideos = candidates
                                        currentActiveApiEndpoint = toRutubeApiUrl(url)
                                    }
                                }
                            }
                            
                            _channelVideos.value = loadedVideos

                            val firstVideo = loadedVideos.firstOrNull { !it.authorAvatarUrl.isNullOrBlank() }
                            if (firstVideo != null) {
                                val cur = _currentChannelVideo.value
                                if (cur != null && (cur.thumbnailUrl.isNullOrBlank() || cur.authorAvatarUrl.isNullOrBlank())) {
                                    _currentChannelVideo.value = cur.copy(
                                        thumbnailUrl = firstVideo.authorAvatarUrl,
                                        authorAvatarUrl = firstVideo.authorAvatarUrl
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("VideoViewModel", "Error resolving channel videos", e)
                        } finally {
                            _isLoading.value = false
                        }
                    }

                    if (rawId.isNotBlank() && rawId.all { it.isLetterOrDigit() }) {
                        launch {
                            _isLoadingPlaylists.value = true
                            try {
                                _channelPlaylists.value = fetchVideosResolvingTabs("https://rutube.ru/api/playlist/user/$rawId/?format=json", video.category)
                            } catch (e: Exception) {
                                android.util.Log.e("VideoViewModel", "Error resolving channel playlists", e)
                            } finally {
                                _isLoadingPlaylists.value = false
                            }
                        }
                    }
                }
            }
            "TV_SERIES" -> {
                viewModelScope.launch {
                    _isChannelView.value = false
                    _currentChannelVideo.value = null
                    _channelVideos.value = emptyList()
                    _channelPlaylists.value = emptyList()
                    _channelActiveTab.value = "Видео"
                    _isLoading.value = true
                    try {
                        var loadedVideos: List<Video> = emptyList()
                        val fallbackUrls = mutableListOf<String>()

                        if (finalActionUrl.isNotBlank()) {
                            val cleanApiUrl = toRutubeApiUrl(finalActionUrl)
                            val finalUrl = if (cleanApiUrl.contains("?")) {
                                if (cleanApiUrl.contains("format=json")) cleanApiUrl else "$cleanApiUrl&format=json"
                            } else {
                                "$cleanApiUrl?format=json"
                            }
                            fallbackUrls.add(finalUrl)
                        }

                        if (rawId.isNotBlank() && rawId.all { it.isLetterOrDigit() }) {
                            fallbackUrls.add("https://rutube.ru/api/metainfo/tv/$rawId/video/?format=json")
                            fallbackUrls.add("https://rutube.ru/api/feeds/tv/$rawId/video/?format=json")
                            fallbackUrls.add("https://rutube.ru/api/feeds/subscriptiontvseries/$rawId/video/?format=json")
                            fallbackUrls.add("https://rutube.ru/api/playlist/custom/$rawId/?format=json")
                            fallbackUrls.add("https://rutube.ru/api/playlist/custom/$rawId/videos/?format=json")
                            fallbackUrls.add("https://rutube.ru/api/feeds/playlist/$rawId/?format=json")
                            fallbackUrls.add("https://rutube.ru/api/feeds/person/$rawId/video/?format=json")
                            fallbackUrls.add("https://rutube.ru/api/video/person/$rawId/?format=json")
                            fallbackUrls.add("https://rutube.ru/api/metainfo/tv/$rawId/?format=json")
                            fallbackUrls.add("https://rutube.ru/api/feeds/tv/$rawId/?format=json")
                            fallbackUrls.add("https://rutube.ru/api/feeds/subscriptiontvseries/$rawId/?format=json")
                            fallbackUrls.add("https://rutube.ru/api/feeds/person/$rawId/?format=json")
                        }

                        for (url in fallbackUrls.distinct()) {
                            val candidates = fetchVideosResolvingTabs(url, video.category)
                            val hasPlayableVideos = candidates.any {
                                it.duration != "СЕРИАЛ" && it.duration != "КАНАЛ" && 
                                it.duration != "ПЛЕЙЛИСТ" && it.duration != "ПРОМО" && 
                                it.duration != "КАТАЛОГ"
                            }
                            if (candidates.isNotEmpty()) {
                                if (hasPlayableVideos) {
                                    loadedVideos = candidates
                                    currentActiveApiEndpoint = toRutubeApiUrl(url)
                                    break
                                } else if (loadedVideos.isEmpty()) {
                                    loadedVideos = candidates
                                    currentActiveApiEndpoint = toRutubeApiUrl(url)
                                }
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
                        android.util.Log.e("VideoViewModel", "Error fetching TV episodes", e)
                    } finally {
                        _isLoading.value = false
                    }
                }
            }
            "PLAYLIST" -> {
                viewModelScope.launch {
                    _isChannelView.value = false
                    _currentChannelVideo.value = null
                    _channelVideos.value = emptyList()
                    _channelPlaylists.value = emptyList()
                    _channelActiveTab.value = "Видео"
                    _isLoading.value = true
                    try {
                        var loadedVideos: List<Video> = emptyList()
                        val fallbackUrls = mutableListOf<String>()

                        if (finalActionUrl.isNotBlank()) {
                            val cleanApiUrl = toRutubeApiUrl(finalActionUrl)
                            val finalUrl = if (cleanApiUrl.contains("?")) {
                                if (cleanApiUrl.contains("format=json")) cleanApiUrl else "$cleanApiUrl&format=json"
                            } else {
                                "$cleanApiUrl?format=json"
                            }
                            fallbackUrls.add(finalUrl)
                        }

                        if (rawId.isNotBlank() && rawId.all { it.isLetterOrDigit() }) {
                            fallbackUrls.add("https://rutube.ru/api/playlist/custom/$rawId/videos/?format=json")
                            fallbackUrls.add("https://rutube.ru/api/playlist/custom/$rawId/?format=json")
                            fallbackUrls.add("https://rutube.ru/api/feeds/playlist/$rawId/?format=json")
                        }

                        for (url in fallbackUrls.distinct()) {
                            val candidates = fetchVideosResolvingTabs(url, video.category)
                            if (candidates.isNotEmpty()) {
                                loadedVideos = candidates
                                currentActiveApiEndpoint = toRutubeApiUrl(url)
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
                        android.util.Log.e("VideoViewModel", "Error fetching playlist videos", e)
                    } finally {
                        _isLoading.value = false
                    }
                }
            }
            "CATALOG" -> {
                viewModelScope.launch {
                    _isChannelView.value = false
                    _currentChannelVideo.value = null
                    _channelVideos.value = emptyList()
                    _channelPlaylists.value = emptyList()
                    _channelActiveTab.value = "Видео"
                    _isLoading.value = true
                    try {
                        var loadedVideos: List<Video> = emptyList()
                        val fallbackUrls = mutableListOf<String>()

                        if (finalActionUrl.isNotBlank()) {
                            val cleanApiUrl = toRutubeApiUrl(finalActionUrl)
                            val finalUrl = if (cleanApiUrl.contains("?")) {
                                if (cleanApiUrl.contains("format=json")) cleanApiUrl else "$cleanApiUrl&format=json"
                            } else {
                                "$cleanApiUrl?format=json"
                            }
                            fallbackUrls.add(finalUrl)
                        }

                        if (rawId.isNotBlank() && rawId.all { it.isLetterOrDigit() }) {
                            fallbackUrls.add("https://rutube.ru/api/feeds/promogroup/$rawId/?format=json")
                            fallbackUrls.add("https://rutube.ru/api/v1/feeds/promogroup/$rawId/?format=json")
                            fallbackUrls.add("https://rutube.ru/api/feeds/cardgroup/$rawId/?format=json")
                            fallbackUrls.add("https://rutube.ru/api/tags/video/$rawId/?format=json")
                        }

                        for (url in fallbackUrls.distinct()) {
                            val candidates = fetchVideosResolvingTabs(url, video.category)
                            if (candidates.isNotEmpty()) {
                                loadedVideos = candidates
                                currentActiveApiEndpoint = toRutubeApiUrl(url)
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
                        android.util.Log.e("VideoViewModel", "Error fetching catalog videos", e)
                    } finally {
                        _isLoading.value = false
                    }
                }
            }
            else -> {
                _currentSelectedVideo.value = video
                _isPlaying.value = true
                _playProgress.value = 0f
                startPlaybackTicker()
                addToRecentHistory(video)
                if (_isTvOptimized.value) {
                    _currentTab.value = "tv_mini"
                }
            }
        }
    }

    fun loadVideoByUrlOrId(urlOrId: String) {
        val trimmedUrl = urlOrId.trim()
        if (trimmedUrl.isBlank()) return

        // Check if VK url first
        val parsedVk = com.example.data.vk.VkVideoLoader.parseVideoUrl(trimmedUrl)
        if (parsedVk != null) {
            viewModelScope.launch {
                _isLoading.value = true
                try {
                    val info = com.example.data.vk.VkVideoLoader.getVideoInfo(trimmedUrl)
                    if (info != null) {
                        val vkVideoId = "vk_${info.ownerId}_${info.videoId}"
                        _masterUrlCache[vkVideoId] = info.videoUrl
                        
                        val vkVideo = Video(
                            id = vkVideoId,
                            title = info.title,
                            channel = "VK Видео (id: ${info.ownerId})",
                            views = "${info.views} просмотров",
                            timeAgo = "Только что",
                            duration = info.duration,
                            category = "VK",
                            description = "Видео из VK. Воспроизведение и скачивание.",
                            thumbnailUrl = info.thumbnail,
                            isDownloaded = false,
                            isBookmarked = false
                        )
                        val savedMap = repository.getSavedVideosOnly().first().associateBy { it.id }
                        val saved = savedMap[vkVideoId]
                        val finalVideo = vkVideo.copy(
                            isDownloaded = saved?.isDownloaded ?: false,
                            isBookmarked = saved?.isBookmarked ?: false
                        )
                        selectVideo(finalVideo)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VideoViewModel", "Error resolving VK deep link: $trimmedUrl", e)
                } finally {
                    _isLoading.value = false
                }
            }
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                android.util.Log.d("VideoViewModel", "Handling deep link URL: $trimmedUrl")
                
                // 1. Is it a Channel? E.g., .../video/person/([0-9a-zA-Z_-]+)... or .../channel/([0-9a-zA-Z_-]+)...
                val channelPattern = java.util.regex.Pattern.compile("/(?:video/person|channel)/([0-9a-zA-Z_-]+)")
                val channelMatcher = channelPattern.matcher(trimmedUrl)
                if (channelMatcher.find()) {
                    val channelId = channelMatcher.group(1) ?: ""
                    if (channelId.isNotBlank()) {
                        val dummyChannelVideo = Video(
                            id = "channel_${channelId}__https://rutube.ru/api/video/person/$channelId/",
                            title = "Канал Rutube ($channelId)",
                            channel = "Канал • Загрузка...",
                            views = "",
                            timeAgo = "",
                            duration = "КАНАЛ",
                            category = "Каналы",
                            description = "Загрузка канала из внешнего источника...",
                            thumbnailUrl = ""
                        )
                        selectVideo(dummyChannelVideo)
                        return@launch
                    }
                }

                // 2. Is it a TV series? E.g., .../metainfo/tv/([0-9a-zA-Z_-]+)...
                val tvPattern = java.util.regex.Pattern.compile("/(?:metainfo/tv|brand|series)/([0-9a-zA-Z_-]+)")
                val tvMatcher = tvPattern.matcher(trimmedUrl)
                if (tvMatcher.find()) {
                    val tvId = tvMatcher.group(1) ?: ""
                    if (tvId.isNotBlank()) {
                        val dummyTvVideo = Video(
                            id = "tv_${tvId}__https://rutube.ru/api/metainfo/tv/$tvId/",
                            title = "Передача Rutube ($tvId)",
                            channel = "Шоу • Загрузка...",
                            views = "",
                            timeAgo = "",
                            duration = "СЕРИАЛ",
                            category = "Телепередачи",
                            description = "Загрузка передач из внешнего источника...",
                            thumbnailUrl = ""
                        )
                        selectVideo(dummyTvVideo)
                        return@launch
                    }
                }

                // 2.5 Is it a Playlist? E.g., .../video/playlist/([a-zA-Z0-9_-]+)... or .../playlist/([a-zA-Z0-9_-]+)... or .../plst/([a-zA-Z0-9_-]+)...
                val playlistPattern = java.util.regex.Pattern.compile("/(?:video/playlist|playlist|plst)/([0-9a-zA-Z_-]+)")
                val playlistMatcher = playlistPattern.matcher(trimmedUrl)
                if (playlistMatcher.find()) {
                    val playlistId = playlistMatcher.group(1) ?: ""
                    if (playlistId.isNotBlank()) {
                        val dummyPlaylistVideo = Video(
                            id = "playlist_${playlistId}__https://rutube.ru/api/playlist/custom/$playlistId/",
                            title = "Плейлист Rutube ($playlistId)",
                            channel = "Плейлист • Загрузка...",
                            views = "",
                            timeAgo = "",
                            duration = "ПЛЕЙЛИСТ",
                            category = "Разное",
                            description = "Загрузка плейлиста из внешнего источника...",
                            thumbnailUrl = ""
                        )
                        selectVideo(dummyPlaylistVideo)
                        return@launch
                    }
                }

                // 3. Extract standard video ID
                val videoId = parseVideoIdFromRutubeUrl(trimmedUrl)
                if (videoId.isNotBlank()) {
                    // Try fetching video metadata from api
                    val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                    val apiUrl = "https://rutube.ru/api/video/$videoId/?format=json"
                    val response = apiService.getDynamicUrl(apiUrl)
                    val bodyStr = response.string()
                    val parsedVideoList = repository.parseVideoListJson(bodyStr, "Разное")
                    
                    if (parsedVideoList.isNotEmpty()) {
                        selectVideo(parsedVideoList.first())
                    } else {
                        // Fallback: Create placeholder video if request failed
                        val fallbackVideo = Video(
                            id = videoId,
                            title = "Видео Rutube ($videoId)",
                            channel = "Rutube",
                            views = "",
                            timeAgo = "Только что",
                            duration = "00:00",
                            category = "Разное",
                            description = "Импортировано из внешнего приложения. Приятного просмотра!",
                            thumbnailUrl = ""
                        )
                        selectVideo(fallbackVideo)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoViewModel", "Error resolving deep link URL: $trimmedUrl", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun parseVideoIdFromRutubeUrl(url: String): String {
        if (!url.contains("/")) {
            return url.trim()
        }
        
        // Find 32-character hexadecimal hashes or numeric IDs
        // Handles: /video/79bda66479f64bfcc233d45f3ba1e899/ or /play/embed/12345/
        val generalPattern = java.util.regex.Pattern.compile("/(?:video|play/embed)/([a-zA-Z0-9]+)")
        val matcher = generalPattern.matcher(url)
        if (matcher.find()) {
            return matcher.group(1) ?: ""
        }
        
        // General fallback: last segment that looks like a token
        val cleanUrl = url.substringBefore("?").trimEnd('/')
        val lastSegment = cleanUrl.substringAfterLast("/")
        if (lastSegment.length >= 8 && lastSegment.all { it.isLetterOrDigit() }) {
            return lastSegment
        }
        
        return ""
    }

    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
        if (_isPlaying.value) {
            startPlaybackTicker()
        } else {
            stopPlaybackTicker()
        }
    }

    fun setPlayingState(playing: Boolean) {
        _isPlaying.value = playing
        if (playing) {
            startPlaybackTicker()
        } else {
            stopPlaybackTicker()
        }
    }

    fun seekProgress(progress: Float) {
        _playProgress.value = progress.coerceIn(0f, 1f)
    }

    private fun startPlaybackTicker() {
        // Disabled fake playback ticker because real ExoPlayer handles its own state
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

    private val downloadJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    fun cancelDownload(videoId: String) {
        downloadJobs[videoId]?.cancel()
        downloadJobs.remove(videoId)
        
        _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
            remove(videoId)
        }
        
        val downloadFolder = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = File(downloadFolder, "$videoId.mp4")
        if (targetFile.exists()) {
            targetFile.delete()
        }
        
        if (_currentSelectedVideo.value?.id == videoId) {
            _currentSelectedVideo.value = _currentSelectedVideo.value?.copy(isDownloaded = false)
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

    private suspend fun downloadVkVideoDirect(video: Video) {
        val id = video.id
        val logs = java.util.concurrent.CopyOnWriteArrayList<String>().apply {
            add("[VK Загрузчик] Начат прямой сбор потока VK...")
        }
        
        fun updateDl(progress: Float, speed: String, etaStr: String, status: String) {
            val currentDl = YtDlpDownload(
                id = id,
                title = video.title,
                channel = video.channel,
                thumbnailUrl = video.thumbnailUrl,
                progress = progress,
                speed = speed,
                eta = etaStr,
                status = status,
                logs = logs.toList()
            )
            _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                this[id] = currentDl
            }
        }

        updateDl(0f, "0 B/s", "--:--", "Downloading")
        
        val cachedStreamUrl = _masterUrlCache[id] ?: _streamUrlCache[id] ?: ""
        val videoUrl = if (cachedStreamUrl.isNotBlank()) {
            cachedStreamUrl
        } else {
            // Try resolving on the fly
            logs.add("[VK Загрузчик] Разрешение URL трансляции для $id...")
            val vkId = id.substringAfter("vk_")
            val info = com.example.data.vk.VkVideoLoader.getVideoInfo("https://vk.com/video$vkId")
            if (info != null) {
                _masterUrlCache[id] = info.videoUrl
                info.videoUrl
            } else {
                ""
            }
        }

        if (videoUrl.isBlank()) {
            logs.add("[VK Загрузчик] Ошибка: ссылка на поток не найдена!")
            updateDl(0f, "0 B/s", "--:--", "Error")
            delay(5000)
            _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { remove(id) }
            return
        }

        logs.add("[VK Загрузчик] Начинается скачивание...")
        updateDl(0.01f, "0 B/s", "--:--", "Downloading")

        val downloadFolder = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = File(downloadFolder, "$id.mp4")

        val success = com.example.data.vk.VkVideoLoader.downloadVideo(
            videoUrl = videoUrl,
            targetFile = targetFile,
            onProgress = { progress, speed, etaStr ->
                if ((progress * 100).toInt() % 10 == 0) {
                    logs.add("[VK Загрузчик] Скачано ${(progress * 100).toInt()}% ($speed) ETA: $etaStr")
                }
                updateDl(progress, speed, etaStr, "Downloading")
            }
        )

        if (success) {
            try {
                logs.add("[VK Загрузчик] Файл сохранен в ${targetFile.absolutePath}")
                updateDl(1f, "0 B/s", "00:00", "Finished")
                
                repository.toggleDownload(video)
                if (_currentSelectedVideo.value?.id == id) {
                    _currentSelectedVideo.value = _currentSelectedVideo.value?.copy(isDownloaded = true)
                }
                
                delay(3000)
                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { remove(id) }
            } catch (e: Exception) {
                logs.add("[VK Загрузчик] Ошибка сохранения файла: ${e.message}")
                updateDl(0f, "0 B/s", "--:--", "Error")
                delay(5000)
                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { remove(id) }
            }
        } else {
            logs.add("[VK Загрузчик] Ошибка скачивания видео!")
            updateDl(0f, "0 B/s", "--:--", "Error")
            delay(5000)
            _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { remove(id) }
        }
    }

    private fun startYtDlpDownload(video: Video) {
        val job = viewModelScope.launch {
            try {
                if (video.id.startsWith("vk_")) {
                    downloadVkVideoDirect(video)
                } else {
                    YtDlpDownloader.startYtDlpDownload(
                        getApplication(),
                        video,
                        repository,
                        _activeDownloads,
                        _downloadQuality.value
                    ) { completedId ->
                        if (_currentSelectedVideo.value?.id == completedId) {
                            _currentSelectedVideo.value = _currentSelectedVideo.value?.copy(isDownloaded = true)
                        }
                    }
                }
            } finally {
                downloadJobs.remove(video.id)
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { remove(video.id) }
                }
            }
        }
        downloadJobs[video.id] = job
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

    fun clearHlsCache(videoId: String) {
        _streamUrlCache.remove(videoId)
        _masterUrlCache.remove(videoId)
    }

    suspend fun fetchHlsStreamUrl(videoId: String, quality: String = "Авто"): String? {
        if (videoId.startsWith("vk_")) {
            val cachedMaster = _masterUrlCache[videoId]
            if (cachedMaster != null) {
                return cachedMaster
            }
            return withContext(Dispatchers.IO) {
                try {
                    val vkId = videoId.substringAfter("vk_")
                    val info = com.example.data.vk.VkVideoLoader.getVideoInfo("https://vk.com/video$vkId")
                    if (info != null) {
                        _masterUrlCache[videoId] = info.videoUrl
                        info.videoUrl
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VideoViewModel", "Error fetching HLS for VK video: $videoId", e)
                    null
                }
            }
        }
        val masterUrl = withContext(Dispatchers.IO) {
            val cachedMaster = _masterUrlCache[videoId]
            if (cachedMaster != null) {
                cachedMaster
            } else {
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
                        if (extractedStreamUrl != null) {
                            _masterUrlCache[videoId] = extractedStreamUrl
                        }
                        extractedStreamUrl
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VideoViewModel", "Error fetching direct stream URL", e)
                    null
                }
            }
        }

        if (masterUrl.isNullOrBlank()) {
            return null
        }

        if (quality == "Авто") {
            // Populate available qualities asynchronously in background
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    getOrFetchParsedStreams(masterUrl)
                } catch (e: Exception) {
                    // Ignore
                }
            }
            return masterUrl
        }

        // Specific quality requested
        val streams = getOrFetchParsedStreams(masterUrl)
        if (streams.isEmpty()) {
            return masterUrl
        }

        val selectedStream = streams.firstOrNull { it.resolution.equals(quality, ignoreCase = true) }
            ?: streams.firstOrNull { it.resolution.contains("720") }
            ?: streams.firstOrNull { it.resolution.contains("480") }
            ?: streams.first()

        return selectedStream.url
    }

    private suspend fun getOrFetchParsedStreams(masterUrl: String): List<com.example.data.rutube.HlsStream> {
        val cached = _parsedStreamsCache[masterUrl]
        if (cached != null) {
            _currentAvailableQualities.value = listOf("Авто") + cached.map { it.resolution }.distinct()
            return cached
        }
        return withContext(Dispatchers.IO) {
            try {
                val okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val req = Request.Builder()
                    .url(masterUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Referer", "https://rutube.ru/")
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val text = resp.body?.string() ?: return@withContext emptyList()
                    val parsed = com.example.data.rutube.HlsParser.parseMasterPlaylist(masterUrl, text)
                    if (parsed.isNotEmpty()) {
                        _parsedStreamsCache[masterUrl] = parsed
                        _currentAvailableQualities.value = listOf("Авто") + parsed.map { it.resolution }.distinct()
                    }
                    parsed
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoViewModel", "Error loading/parsing master playlist text", e)
                emptyList()
            }
        }
    }


    private fun mapResourceToVideo(resource: com.example.data.rutube.SmartRutubeParser.ResourceInfo, tabId: Int, categoryName: String): Video {
        val url = resource.url ?: ""
        val extractedId = "\\d+".toRegex().find(url)?.value ?: ""
        
        return when (resource.type) {
            com.example.data.rutube.SmartRutubeParser.EntityType.CHANNEL -> {
                Video(
                    id = "channel_${extractedId}__$url",
                    title = resource.name,
                    channel = "Авторский канал",
                    views = "Открыть канал",
                    timeAgo = "Автор",
                    duration = "КАНАЛ",
                    isPro = false,
                    category = categoryName,
                    description = "Официальный канал: ${resource.name}",
                    thumbnailUrl = null
                )
            }
            com.example.data.rutube.SmartRutubeParser.EntityType.TV_SERIES -> {
                Video(
                    id = "tv_${extractedId}__$url",
                    title = resource.name,
                    channel = "Телешоу / Передача",
                    views = "Смотреть выпуски",
                    timeAgo = "Шоу",
                    duration = "СЕРИАЛ",
                    isPro = false,
                    category = categoryName,
                    description = "Смотрите оригинальные сезоны: ${resource.name}",
                    thumbnailUrl = null
                )
            }
            com.example.data.rutube.SmartRutubeParser.EntityType.PLAYLIST -> {
                Video(
                    id = "playlist_${extractedId}__$url",
                    title = resource.name,
                    channel = "Плейлист • Подборка",
                    views = "Смотреть плейлист",
                    timeAgo = "Плейлист",
                    duration = "ПЛЕЙЛИСТ",
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
                    duration = "ПАПКА",
                    isPro = false,
                    category = categoryName,
                    description = "Коллекция контента из раздела: ${resource.name}",
                    thumbnailUrl = null
                )
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

