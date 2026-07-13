package com.example.viewmodel

import com.example.ui.theme.CustomTheme

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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

import com.example.manager.*
import java.util.Stack

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

    // Managers
    val settingsManager = SettingsManager(application)
    val navigationManager = NavigationManager()
    val playerManager = PlayerManager()
    val libraryManager = LibraryManager(repository, viewModelScope)
    val downloadManager = DownloadManager(application, repository, viewModelScope)
    val backupRestoreManager = BackupRestoreManager(repository, settingsManager, libraryManager)
    val mediaResolver = RutubeMediaResolver(playerManager, viewModelScope)

    init {
        com.example.manager.DownloadManager.onDownloadCompletedListener = { completedId ->
            viewModelScope.launch {
                if (playerManager.currentSelectedVideo.value?.id == completedId) {
                    playerManager.selectVideo(playerManager.currentSelectedVideo.value?.copy(isDownloaded = true))
                }
            }
        }
    }

    // Proxies for backward compatibility and UI convenience
    val currentTab = navigationManager.currentTab
    val appTheme = settingsManager.appTheme
    val appEffect = settingsManager.appEffect
    val isDarkTheme = settingsManager.isDarkTheme
    val isTvOptimized = settingsManager.isTvOptimized
    val isLargeCardsMode = settingsManager.isLargeCardsMode
    val isHistoryLargeCardsMode = settingsManager.isHistoryLargeCardsMode
    val isDownloadsLargeCardsMode = settingsManager.isDownloadsLargeCardsMode
    val isTermsAgreed = settingsManager.isTermsAgreed
    val startPageType = settingsManager.startPageType
    val startPageCategory = settingsManager.startPageCategory
    val startPageCustomUrl = settingsManager.startPageCustomUrl
    val startPageFavoriteId = settingsManager.startPageFavoriteId
    val startPageFavoriteTitle = settingsManager.startPageFavoriteTitle
    
    val selectedCategory = navigationManager.selectedCategory
    val searchQuery = navigationManager.searchQuery
    val selectedFeedTab = navigationManager.selectedFeedTab
    val selectedSubfolderName = navigationManager.selectedSubfolderName
    val isChannelView = navigationManager.isChannelView
    val channelActiveTab = navigationManager.channelActiveTab
    
    val currentSelectedVideo = playerManager.currentSelectedVideo
    val isPlaying = playerManager.isPlaying
    val playProgress = playerManager.playProgress
    val currentAvailableQualities = playerManager.currentAvailableQualities
    val activeVideoQuality = playerManager.activeVideoQuality

    val playerQuality = settingsManager.playerQuality
    val downloadQuality = settingsManager.downloadQuality
    val activeDownloads = downloadManager.activeDownloads

    val tvGridColumns = settingsManager.tvGridColumns
    val tvVideoGridColumns = settingsManager.tvVideoGridColumns
    val mobileGridColumns = settingsManager.mobileGridColumns
    val focusStyle = settingsManager.focusStyle

    val customThemes = settingsManager.customThemes
    
    fun toggleTheme() = settingsManager.toggleTheme()
    fun setAppTheme(theme: String) = settingsManager.setAppTheme(theme)
    fun setAppEffect(effect: String) = settingsManager.setAppEffect(effect)
    fun toggleTvOptimized() = settingsManager.toggleTvOptimized()
    fun toggleLargeCardsMode() = settingsManager.toggleLargeCardsMode()
    fun toggleHistoryLargeCardsMode() = settingsManager.toggleHistoryLargeCardsMode()
    fun toggleDownloadsLargeCardsMode() = settingsManager.toggleDownloadsLargeCardsMode()
    fun agreeToTerms() = settingsManager.agreeToTerms()
    fun setStartPageType(type: String) = settingsManager.setStartPageType(type)
    fun setStartPageCategory(category: String) = settingsManager.setStartPageCategory(category)
    fun setStartPageCustomUrl(url: String) = settingsManager.setStartPageCustomUrl(url)
    fun setStartPageFavorite(id: String, title: String) = settingsManager.setStartPageFavorite(id, title)
    fun setPlayerQuality(quality: String) = settingsManager.setPlayerQuality(quality)
    fun setDownloadQuality(quality: String) = settingsManager.setDownloadQuality(quality)
    fun setTvGridColumns(cols: Int) = settingsManager.setTvGridColumns(cols)
    fun setTvVideoGridColumns(cols: Int) = settingsManager.setTvVideoGridColumns(cols)
    fun setMobileGridColumns(cols: Int) = settingsManager.setMobileGridColumns(cols)
    fun setFocusStyle(style: String) = settingsManager.setFocusStyle(style)
    fun addCustomTheme(theme: CustomTheme) = settingsManager.addCustomTheme(theme)
    fun removeCustomTheme(id: String) = settingsManager.removeCustomTheme(id)
    
    fun selectTab(tab: String) = navigationManager.selectTab(tab)
    fun setSearchQuery(query: String) {
        navigationManager.setSearchQuery(query)
        triggerDebouncedSearch(query, navigationManager.selectedCategory.value)
    }

    fun togglePlayPause() = playerManager.togglePlayPause()
    fun setPlayingState(playing: Boolean) = playerManager.setPlayingState(playing)
    fun seekProgress(progress: Float) = playerManager.seekProgress(progress)

    fun toggleBookmark(video: Video) {
        libraryManager.toggleBookmark(video)
        if (playerManager.currentSelectedVideo.value?.id == video.id) {
            playerManager.selectVideo(playerManager.currentSelectedVideo.value?.copy(isBookmarked = !video.isBookmarked))
        }
    }

    fun cancelDownload(videoId: String) {
        downloadManager.cancelDownload(videoId)
        if (playerManager.currentSelectedVideo.value?.id == videoId) {
            playerManager.selectVideo(playerManager.currentSelectedVideo.value?.copy(isDownloaded = false))
        }
    }

    fun toggleDownload(video: Video) {
        if (video.isDownloaded) {
            downloadManager.deleteDownload(video)
            libraryManager.toggleDownloadStateInDb(video)
            if (playerManager.currentSelectedVideo.value?.id == video.id) {
                playerManager.selectVideo(playerManager.currentSelectedVideo.value?.copy(isDownloaded = false))
            }
        } else {
            startYtDlpDownload(video)
        }
    }

    fun deleteDownload(video: Video) {
        downloadManager.deleteDownload(video)
        if (video.isDownloaded) {
            libraryManager.toggleDownloadStateInDb(video)
            if (playerManager.currentSelectedVideo.value?.id == video.id) {
                playerManager.selectVideo(playerManager.currentSelectedVideo.value?.copy(isDownloaded = false))
            }
        }
    }

    fun saveToDevice(video: Video, context: android.content.Context, onResult: (Boolean, String) -> Unit) {
        downloadManager.saveToDevice(video, context, onResult)
    }

    fun toggleMicrophone(status: Boolean) {
        _isMicrophoneActive.value = status
    }

    // Microphoning / Search focused state
    private val _isMicrophoneActive = MutableStateFlow(false)
    val isMicrophoneActive = _isMicrophoneActive.asStateFlow()

    val isTvMiniFullscreen = playerManager.isTvMiniFullscreen
    val isMiniPlayer = playerManager.isMiniPlayer

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode = _isInPipMode.asStateFlow()

    fun setInPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    fun setTvMiniFullscreen(fullscreen: Boolean) {
        playerManager.setTvMiniFullscreen(fullscreen)
    }

    fun setMiniPlayer(isMini: Boolean) {
        playerManager.setMiniPlayer(isMini)
    }

    // Playback progress tracking
    fun saveVideoPosition(videoId: String, position: Long, duration: Long = 0L) {
        playerManager.saveVideoPosition(videoId, position)
        val currentVideo = playerManager.currentSelectedVideo.value
        if (currentVideo != null && currentVideo.id == videoId) {
            viewModelScope.launch {
                repository.saveVideoProgress(currentVideo, position, duration)
            }
        }
    }
    fun getVideoPosition(videoId: String): Long = playerManager.getVideoPosition(videoId)


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

    private val _currentChannelVideo = MutableStateFlow<Video?>(null)
    val currentChannelVideo: StateFlow<Video?> = combine(_currentChannelVideo, repository.getSavedVideosOnly()) { activeChannel, savedList ->
        if (activeChannel == null) return@combine null
        val saved = savedList.firstOrNull { it.id == activeChannel.id }
        activeChannel.copy(
            isDownloaded = saved?.isDownloaded ?: false,
            isBookmarked = saved?.isBookmarked ?: false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _currentSubfolderVideo = MutableStateFlow<Video?>(null)
    val currentSubfolderVideo: StateFlow<Video?> = combine(_currentSubfolderVideo, repository.getSavedVideosOnly()) { activeFolder, savedList ->
        if (activeFolder == null) return@combine null
        val saved = savedList.firstOrNull { it.id == activeFolder.id }
        activeFolder.copy(
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

    private val _isLoadingPlaylists = MutableStateFlow(false)
    val isLoadingPlaylists = _isLoadingPlaylists.asStateFlow()

    fun setChannelActiveTab(tab: String) {
        navigationManager.setChannelActiveTab(tab)
    }

    fun pushToHistory() {
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
            currentSubfolderVideo = _currentSubfolderVideo.value
        )
        navigationManager.pushToHistory(currentSnapshot)
    }

    fun canNavigateBack(): Boolean {
        if (playerManager.currentSelectedVideo.value != null) return true
        if (navigationManager.searchQuery.value.isNotEmpty()) return true
        return navigationManager.canNavigateBack()
    }

    fun navigateBack(): Boolean {
        if (playerManager.currentSelectedVideo.value != null) {
            playerManager.selectVideo(null)
            return true
        }

        if (navigationManager.searchQuery.value.isNotEmpty()) {
            setSearchQuery("")
            return true
        }

        val last = navigationManager.navigateBack()
        if (last != null) {
            navigationManager.restoreFromSnapshot(last)
            _currentChannelVideo.value = last.currentChannelVideo
            _currentSubfolderVideo.value = last.currentSubfolderVideo
            _channelVideos.value = last.channelVideos
            _channelPlaylists.value = last.channelPlaylists
            _dynamicVideos.value = last.dynamicVideos
            
            currentPage = last.currentPage
            isEndReached = last.isEndReached
            currentQuery = last.currentQuery
            currentCategory = last.currentCategory
            currentActiveApiEndpoint = last.currentActiveApiEndpoint

            return true
        }

        if (navigationManager.currentTab.value != "home") {
            navigationManager.selectTab("home")
            return true
        }

        return false
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
        val savedStartPageType = settingsManager.startPageType.value
        val savedStartPageCategory = settingsManager.startPageCategory.value
        val savedStartPageCustomUrl = settingsManager.startPageCustomUrl.value
        val savedStartPageFavoriteId = settingsManager.startPageFavoriteId.value
        val savedStartPageFavoriteTitle = settingsManager.startPageFavoriteTitle.value

        if (savedStartPageType == "favorite" && savedStartPageFavoriteId.isNotBlank()) {
            viewModelScope.launch {
                val saved = repository.getVideoById(savedStartPageFavoriteId)
                if (saved != null) {
                    val videoRuntime = Video(
                        id = saved.id,
                        title = saved.title,
                        channel = saved.channel,
                        views = saved.views,
                        timeAgo = saved.timeAgo,
                        duration = saved.duration,
                        isPro = saved.isPro,
                        category = saved.category,
                        description = "Стартовый экран.",
                        thumbnailUrl = saved.thumbnailUrl,
                        isDownloaded = saved.isDownloaded,
                        isBookmarked = saved.isBookmarked
                    )
                    selectVideo(videoRuntime)
                    navigationManager.clearHistory()
                } else {
                    navigationManager.setCategory("Фильмы")
                    fetchRealVideos()
                }
            }
        } else if (savedStartPageType == "custom_url" && savedStartPageCustomUrl.isNotBlank()) {
            navigationManager.setCategory("Стартовая")
            fetchRealVideos(category = "Стартовая", targetUrl = savedStartPageCustomUrl)
        } else if (savedStartPageType == "category") {
            navigationManager.setCategory(savedStartPageCategory)
            fetchRealVideos(category = savedStartPageCategory)
        } else {
            navigationManager.setCategory("Фильмы")
            fetchRealVideos()
        }

        fetchRealCategories()

        viewModelScope.launch {
            _dynamicVideos.collect { videos ->
                resolveMissingPosters(videos)
            }
        }
        viewModelScope.launch {
            _channelPlaylists.collect { videos ->
                resolveMissingPosters(videos)
            }
        }
        viewModelScope.launch {
            _channelVideos.collect { videos ->
                resolveMissingPosters(videos)
            }
        }
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
        navigationManager.setCategory(category)
        navigationManager.setSearchQuery("")
        fetchRealVideos(query = null, category = category, targetUrl = targetUrl)
    }

    fun selectFeedTab(tab: com.example.data.rutube.SmartRutubeParser.TabInfo) {
        selectFeedTabInternal(tab, pushHistory = true)
    }

    private fun selectFeedTabInternal(tab: com.example.data.rutube.SmartRutubeParser.TabInfo, pushHistory: Boolean) {
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
                            val parsedVideos = repository.parseVideoListJson(bodyStr, navigationManager.selectedCategory.value)
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
                        _dynamicVideos.value = repository.fetchRealVideos(null, navigationManager.selectedCategory.value, page = 1)
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
        navigationManager.setSubfolderName(null)
        navigationManager.setChannelView(false)
        _currentChannelVideo.value = null
        _currentSubfolderVideo.value = null
        _channelVideos.value = emptyList()
        _channelPlaylists.value = emptyList()
        navigationManager.setChannelActiveTab("Видео")
        currentQuery = query
        val targetCategory = category ?: navigationManager.selectedCategory.value
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
                                mediaResolver.masterUrlCache.put(vkVideoId, info.videoUrl)
                                
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
                                    navigationManager.setFeedTab(null)
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

                    val fetched = repository.fetchRealVideos(q, targetCategory, page = 1)
                    if (currentRequestId == requestId) {
                        _feedTabs.value = emptyList()
                        navigationManager.setFeedTab(null)
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
                            navigationManager.setFeedTab(null)
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
                        navigationManager.setFeedTab(null)
                        _dynamicVideos.value = repository.fetchRealVideos(null, targetCategory, page = 1)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoViewModel", "Error in fetchRealVideos with target", e)
                if (currentRequestId == requestId) {
                    _feedTabs.value = emptyList()
                    navigationManager.setFeedTab(null)
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
        val snapshotChannelView = navigationManager.isChannelView.value
        val snapshotChannelActiveTab = navigationManager.channelActiveTab.value
        
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
                    navigationManager.isChannelView.value != snapshotChannelView) {
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
                if (e is retrofit2.HttpException) {
                    if (e.code() == 404) {
                        isEndReached = true
                    } else if (e.code() == 403 || e.code() == 429) {
                        // Rate limit or forbidden - stop paginating aggressively
                        isEndReached = true
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
    val filteredVideos: StateFlow<List<Video>> = combine(allVideos, navigationManager.searchQuery) { videos, query ->
        val trimmed = query.trim()
        val isUrl = trimmed.startsWith("http://") || 
                    trimmed.startsWith("https://") || 
                    trimmed.contains("vk.com") || 
                    trimmed.contains("vkvideo.ru") || 
                    trimmed.contains("rutube.ru") || 
                    trimmed.contains("rutube") ||
                    com.example.data.vk.VkVideoLoader.parseVideoUrl(trimmed) != null

        if (query.isBlank() || isUrl) {
            videos
        } else {
            videos.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.channel.contains(query, ignoreCase = true) 
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val continueWatchingVideos: StateFlow<List<SavedVideo>> = repository.getContinueWatchingVideos()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // List of ONLY downloaded items, taken directly from Room
    val downloadedSavedVideos: StateFlow<List<SavedVideo>> = libraryManager.downloadedVideos

    // List of ONLY bookmarked items, taken directly from Room
    val bookmarkedSavedVideos: StateFlow<List<SavedVideo>> = libraryManager.bookmarkedVideos

    // List of ALL viewed/saved items (Recent/History list), sorted by savedAt DESC
    val recentSavedVideos: StateFlow<List<SavedVideo>> = libraryManager.recentVideos

    fun addToRecentHistory(video: Video) = libraryManager.addToRecentHistory(video)

    fun deleteRecentItem(video: Video) {
        libraryManager.deleteRecentItem(video) { videoId ->
            downloadManager.deleteDownload(video)
            if (playerManager.currentSelectedVideo.value?.id == videoId) {
                playerManager.selectVideo(playerManager.currentSelectedVideo.value?.copy(isDownloaded = false))
            }
        }
    }

    fun exportBookmarksToJson(): String = libraryManager.exportBookmarksToJson()
    suspend fun importBookmarksFromJson(jsonStr: String): Result<Int> = libraryManager.importBookmarksFromJson(jsonStr)

    fun exportBackupToJson(): String = backupRestoreManager.exportBackupToJson()

    suspend fun importBackupFromJson(jsonStr: String): Result<String> = backupRestoreManager.importBackupFromJson(jsonStr)

    /*
    suspend fun oldImportBackupFromJson(jsonStr: String): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val trimmed = jsonStr.trim()
            if (trimmed.isBlank()) return@withContext Result.failure(Exception("Пустая строка"))
            
            val root = org.json.JSONObject(trimmed)
            
            // 1. Settings
            val settingsObj = root.optJSONObject("settings")
            var importedSettingsCount = 0
            if (settingsObj != null) {
                if (settingsObj.has("is_dark_theme")) {
                    val dark = settingsObj.getBoolean("is_dark_theme")
                    if (dark != settingsManager.isDarkTheme.value) settingsManager.toggleTheme()
                    importedSettingsCount++
                }
                if (settingsObj.has("is_tv_optimized")) {
                    val tv = settingsObj.getBoolean("is_tv_optimized")
                    if (tv != settingsManager.isTvOptimized.value) settingsManager.toggleTvOptimized()
                    importedSettingsCount++
                }
                if (settingsObj.has("is_large_cards_mode")) {
                    val large = settingsObj.getBoolean("is_large_cards_mode")
                    if (large != settingsManager.isLargeCardsMode.value) settingsManager.toggleLargeCardsMode()
                    importedSettingsCount++
                }
                if (settingsObj.has("start_page_type")) {
                    settingsManager.setStartPageType(settingsObj.getString("start_page_type"))
                    importedSettingsCount++
                }
                if (settingsObj.has("start_page_category")) {
                    settingsManager.setStartPageCategory(settingsObj.getString("start_page_category"))
                    importedSettingsCount++
                }
                if (settingsObj.has("start_page_custom_url")) {
                    settingsManager.setStartPageCustomUrl(settingsObj.getString("start_page_custom_url"))
                    importedSettingsCount++
                }
                if (settingsObj.has("player_quality")) {
                    settingsManager.setPlayerQuality(settingsObj.getString("player_quality"))
                    importedSettingsCount++
                }
                if (settingsObj.has("download_quality")) {
                    settingsManager.setDownloadQuality(settingsObj.getString("download_quality"))
                    importedSettingsCount++
                }
                if (settingsObj.has("tv_grid_columns")) {
                    settingsManager.setTvGridColumns(settingsObj.getInt("tv_grid_columns"))
                    importedSettingsCount++
                }
                if (settingsObj.has("mobile_grid_columns")) {
                    settingsManager.setMobileGridColumns(settingsObj.getInt("mobile_grid_columns"))
                    importedSettingsCount++
                }
                if (settingsObj.has("focus_style")) {
                    settingsManager.setFocusStyle(settingsObj.getString("focus_style"))
                    importedSettingsCount++
                }
                if (settingsObj.has("app_theme")) {
                    settingsManager.setAppTheme(settingsObj.getString("app_theme"))
                    importedSettingsCount++
                }
            }

            // 1b. Custom Themes
            val customThemesArray = root.optJSONArray("custom_themes")
            var importedThemesCount = 0
            if (customThemesArray != null) {
                for (i in 0 until customThemesArray.length()) {
                    try {
                        val themeObj = customThemesArray.getJSONObject(i)
                        val theme = com.example.ui.theme.CustomTheme.fromJson(themeObj.toString())
                        settingsManager.addCustomTheme(theme)
                        importedThemesCount++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // 2. Bookmarks
            val bookmarksArray = root.optJSONArray("bookmarks")
            var importedBookmarksCount = 0
            if (bookmarksArray != null) {
                for (i in 0 until bookmarksArray.length()) {
                    val obj = bookmarksArray.getJSONObject(i)
                    val id = obj.optString("id") ?: continue
                    if (id.isBlank()) continue
                    
                    val existing = repository.getVideoById(id)
                    val imported = com.example.data.SavedVideo(
                        id = id,
                        title = obj.optString("title", "Без названия"),
                        channel = obj.optString("channel", "Rutube"),
                        views = obj.optString("views", ""),
                        timeAgo = obj.optString("timeAgo", ""),
                        duration = obj.optString("duration", "00:00"),
                        isPro = obj.optBoolean("isPro", false),
                        category = obj.optString("category", "Разное"),
                        thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                        isBookmarked = true,
                        isDownloaded = existing?.isDownloaded ?: false,
                        isWatched = existing?.isWatched ?: false,
                        savedAt = obj.optLong("savedAt", System.currentTimeMillis())
                    )
                    repository.insertOrUpdate(imported)
                    importedBookmarksCount++
                }
            }

            // 3. Recents
            val recentsArray = root.optJSONArray("recents")
            var importedRecentsCount = 0
            if (recentsArray != null) {
                for (i in 0 until recentsArray.length()) {
                    val obj = recentsArray.getJSONObject(i)
                    val id = obj.optString("id") ?: continue
                    if (id.isBlank()) continue
                    
                    val existing = repository.getVideoById(id)
                    val imported = com.example.data.SavedVideo(
                        id = id,
                        title = obj.optString("title", "Без названия"),
                        channel = obj.optString("channel", "Rutube"),
                        views = obj.optString("views", ""),
                        timeAgo = obj.optString("timeAgo", ""),
                        duration = obj.optString("duration", "00:00"),
                        isPro = obj.optBoolean("isPro", false),
                        category = obj.optString("category", "Разное"),
                        thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                        isBookmarked = existing?.isBookmarked ?: false,
                        isDownloaded = existing?.isDownloaded ?: false,
                        isWatched = true,
                        savedAt = obj.optLong("savedAt", System.currentTimeMillis())
                    )
                    repository.insertOrUpdate(imported)
                    importedRecentsCount++
                }
            }

            val customThemeMsg = if (importedThemesCount > 0) ", тем - $importedThemesCount" else ""
            Result.success("Импортировано: настроек - $importedSettingsCount, закладок - $importedBookmarksCount, истории - $importedRecentsCount$customThemeMsg")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    */

    private suspend fun fetchVideosResolvingTabs(apiUrl: String, defaultCategory: String): List<Video> {
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
            val parsedFeed = com.example.data.rutube.SmartRutubeParser.ResponseAnalyzer.parse(jsonObj, apiUrl)
            if (parsedFeed.tabs.isNotEmpty()) {
                android.util.Log.d("PlaylistDebug", "fetchVideosResolvingTabs: Resolving ${parsedFeed.tabs.size} nested tabs from $apiUrl")
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
                val plId = path.substringAfter("plst/").removeSuffix("/")
                "api/playlist/custom/$plId/videos/"
            }
            path.startsWith("api/video/playlist/") -> {
                val plId = path.substringAfter("api/video/playlist/").removeSuffix("/")
                "api/playlist/custom/$plId/videos/"
            }
            path.startsWith("playlist/") -> {
                val plId = path.substringAfter("playlist/").removeSuffix("/")
                "api/playlist/custom/$plId/videos/"
            }
            path.startsWith("api/playlist/custom/") -> {
                if (path.endsWith("/videos") || path.endsWith("/videos/")) {
                    path
                } else {
                    "${path.removeSuffix("/")}/videos/"
                }
            }
            path.startsWith("api/playlist/") && !path.startsWith("api/playlist/user/") -> {
                if (path.endsWith("/videos") || path.endsWith("/videos/")) {
                    path
                } else {
                    "${path.removeSuffix("/")}/videos/"
                }
            }
            path.startsWith("tv/") -> {
                val tvId = path.substringAfter("tv/")
                if (tvId.contains("video")) "api/metainfo/$path" else "api/metainfo/tv/$tvId/video/"
            }
            path.startsWith("metainfo/tv/") -> {
                val tvId = path.substringAfter("metainfo/tv/")
                if (tvId.contains("video")) "api/$path" else "api/metainfo/tv/$tvId/video/"
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
            playerManager.selectVideo(null)
            return
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
                    viewModelScope.launch {
                        _isLoading.value = true
                        _isLoadingPlaylists.value = true
                        try {
                            try {
                                val channelProfileUrl = "https://rutube.ru/api/profile/user/$channelId/?format=json"
                                val profileResponse = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(channelProfileUrl)
                                val profileBody = profileResponse.string()
                                val jsonObject = org.json.JSONObject(profileBody)
                                val name = jsonObject.optString("name", video.channel)
                                val description = jsonObject.optString("description", video.description)
                                val subCount = jsonObject.optInt("subscribers_count", 0)
                                val avatarUrl = jsonObject.optString("avatar_url", video.authorAvatarUrl)
                                val appearance = jsonObject.optJSONObject("appearance")
                                val coverImage = appearance?.optString("cover_image", video.thumbnailUrl) ?: video.thumbnailUrl

                                val updatedVideo = video.copy(
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

                            val vidUrl = "https://rutube.ru/api/video/person/$channelId/?format=json"
                            val plUrl = "https://rutube.ru/api/playlist/user/$channelId/?format=json"
                            
                            val vidResponse = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(vidUrl)
                            val vidBody = vidResponse.string()
                            _channelVideos.value = repository.parseVideoListJson(vidBody, video.category)
                            currentActiveApiEndpoint = vidUrl
                            currentPage = 1
                            isEndReached = false

                            val plResponse = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(plUrl)
                            val plBody = plResponse.string()
                            _channelPlaylists.value = repository.parseVideoListJson(plBody, video.category)
                        } catch (e: Exception) {
                            android.util.Log.e("VideoViewModel", "Channel fetch error", e)
                        } finally {
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
                val apiUrl = toRutubeApiUrl(rawUrl)
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
                                    val infoUrl = "https://rutube.ru/api/playlist/custom/$playlistId/"
                                    val infoResponse = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(infoUrl)
                                    val infoBody = infoResponse.string()
                                    val infoObj = org.json.JSONObject(infoBody)
                                    
                                    val description = infoObj.optString("description", video.description)
                                    val appearance = infoObj.optJSONObject("appearance")
                                    val coverImage = appearance?.optString("cover_image")?.takeIf { it.isNotBlank() && it != "null" }
                                    val picture = infoObj.optString("picture").takeIf { it.isNotBlank() && it != "null" }
                                    val name = infoObj.optString("name", video.title).takeIf { it.isNotBlank() && it != "null" } ?: video.title
                                    val videoCount = infoObj.optInt("video_count", -1)
                                    
                                    _currentSubfolderVideo.value = video.copy(
                                        title = name,
                                        description = description,
                                        thumbnailUrl = coverImage ?: picture ?: video.thumbnailUrl,
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
                                    viewModelScope.async {
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
                    }
                }
            }
            return
        }

        // Regular video selection: play it!
        if (video != null) {
            viewModelScope.launch {
                val dbVideo = repository.getVideoById(video.id)
                if (dbVideo != null && dbVideo.lastProgress > 0) {
                    playerManager.saveVideoPosition(video.id, dbVideo.lastProgress)
                }

                // Intelligently populate dynamicVideos list so that the TV mini player has a valid, interactive playlist
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
                                thumbnailUrl = savedVideo.thumbnailUrl, isDownloaded = savedVideo.isDownloaded, isBookmarked = savedVideo.isBookmarked
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
                                    thumbnailUrl = saved.thumbnailUrl, isDownloaded = saved.isDownloaded, isBookmarked = saved.isBookmarked
                                )
                            }
                        } else {
                            // Default fallback: ensure playlist is not empty by including at least the selected video
                            _dynamicVideos.value = listOf(video)
                        }
                    }
                }

                playerManager.selectVideo(video)
                addToRecentHistory(video)
            }
        } else {
            playerManager.selectVideo(null)
        }
    }

    fun loadVideoByUrlOrId(urlOrId: String) {
        val trimmed = urlOrId.trim()
        if (trimmed.isBlank()) return
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // VK integration
                val parsedVk = com.example.data.vk.VkVideoLoader.parseVideoUrl(trimmed)
                if (parsedVk != null) {
                    val info = com.example.data.vk.VkVideoLoader.getVideoInfo(trimmed)
                    if (info != null) {
                        val vkVideoId = "vk_${info.ownerId}_${info.videoId}"
                        mediaResolver.masterUrlCache.put(vkVideoId, info.videoUrl)
                        val vkVideo = Video(
                            id = vkVideoId,
                            title = info.title,
                            channel = "VK Видео",
                            views = "${info.views} просмотров",
                            timeAgo = "Только что",
                            duration = info.duration,
                            category = "VK",
                            description = "Видео из VK. Импортировано по ссылке.",
                            thumbnailUrl = info.thumbnail,
                            isDownloaded = false,
                            isBookmarked = false
                        )
                        selectVideo(vkVideo)
                    }
                    return@launch
                }

                // Rutube integration
                val resolved = com.example.utils.UrlResolver.resolveUrl(trimmed)
                if (resolved.type != com.example.utils.UrlResolver.EntityType.UNKNOWN) {
                    when (resolved.type) {
                        com.example.utils.UrlResolver.EntityType.VIDEO -> {
                            val rtId = resolved.id
                            val apiUrl = "https://rutube.ru/api/video/$rtId/?format=json"
                            val response = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(apiUrl)
                            val bodyStr = response.string()
                            val list = repository.parseVideoListJson(bodyStr, "Разное")
                            if (list.isNotEmpty()) {
                                selectVideo(list.first())
                            } else {
                                // Minimal video object if list parsing fails but ID was found
                                val minimalVideo = Video(
                                    id = rtId,
                                    title = "Rutube Видео ($rtId)",
                                    channel = "Rutube",
                                    views = "",
                                    timeAgo = "Только что",
                                    duration = "00:00",
                                    category = "Разное",
                                    description = "Импортировано из ссылки Rutube.",
                                    thumbnailUrl = ""
                                )
                                selectVideo(minimalVideo)
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
                            selectVideo(dummySeries)
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
                _isLoading.value = false
            }
        }
    }

    private fun parseVideoIdFromRutubeUrl(url: String): String {
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

    private fun startPlaybackTicker() {
        // Disabled fake playback ticker because real ExoPlayer handles its own state
    }

    private fun stopPlaybackTicker() {
        playbackJob?.cancel()
        playbackJob = null
    }

    private fun startYtDlpDownload(video: Video) {
        downloadManager.startDownload(video, settingsManager.downloadQuality.value) { completedId ->
            if (playerManager.currentSelectedVideo.value?.id == completedId) {
                playerManager.selectVideo(playerManager.currentSelectedVideo.value?.copy(isDownloaded = true))
            }
        }
    }

    // Helper functions to convert string duration parsed values (e.g. "12:44") to elapsed playback string
    fun getFormattedElapsedTime(durationStr: String, progress: Float): String {
        return com.example.utils.VideoDurationFormatter.getFormattedElapsedTime(durationStr, progress)
    }

    override fun onCleared() {
        super.onCleared()
        stopPlaybackTicker()
    }

    fun clearHlsCache(videoId: String) {
        mediaResolver.clearHlsCache(videoId)
    }

    suspend fun fetchSubtitles(videoId: String): List<com.example.data.SubtitleTrack> {
        return mediaResolver.fetchSubtitles(videoId)
    }

    suspend fun fetchHlsStreamUrl(videoId: String, quality: String = "Авто"): String? {
        return mediaResolver.fetchHlsStreamUrl(videoId, quality)
    }

    /*
    suspend fun oldFetchSubtitles(videoId: String): List<com.example.data.SubtitleTrack> {
        return withContext(Dispatchers.IO) {
            try {
                // If it's a VK video or other external, we skip for now
                if (videoId.startsWith("vk_") || videoId.startsWith("channel_") || videoId.startsWith("playlist_")) {
                    return@withContext emptyList()
                }
                
                val response = com.example.data.rutube.RutubeRetrofitClient.apiService.getSubtitles(videoId)
                val bodyStr = response.string()
                val jsonObject = org.json.JSONObject(bodyStr)
                val list = jsonObject.optJSONArray("list") ?: return@withContext emptyList()
                val result = mutableListOf<com.example.data.SubtitleTrack>()
                for (i in 0 until list.length()) {
                    val subObj = list.optJSONObject(i) ?: continue
                    val lang = subObj.optString("langTitle", "Unknown")
                    val format = subObj.optString("format", "srt")
                    val url = subObj.optString("file", "")
                    if (url.isNotBlank()) {
                        result.add(com.example.data.SubtitleTrack(lang, format, url))
                    }
                }
                result
            } catch (e: Exception) {
                android.util.Log.e("VideoViewModel", "Error fetching subtitles", e)
                emptyList()
            }
        }
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
                        _masterUrlCache.put(videoId, info.videoUrl)
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
                            _masterUrlCache.put(videoId, extractedStreamUrl)
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
            playerManager.setActiveVideoQuality("Авто")
            return masterUrl
        }

        // Specific quality requested
        val streams = getOrFetchParsedStreams(masterUrl)
        if (streams.isEmpty()) {
            playerManager.setActiveVideoQuality("Авто")
            return masterUrl
        }

        val selectedStream = streams.firstOrNull { it.resolution.equals(quality, ignoreCase = true) }
            ?: streams.firstOrNull { it.resolution.contains("720") }
            ?: streams.firstOrNull { it.resolution.contains("480") }
            ?: streams.first()

        playerManager.setActiveVideoQuality(selectedStream.resolution)
        return selectedStream.url
    }

    private suspend fun getOrFetchParsedStreams(masterUrl: String): List<com.example.data.rutube.HlsStream> {
        val cached = _parsedStreamsCache[masterUrl]
        if (cached != null) {
            playerManager.setAvailableQualities(listOf("Авто") + cached.map { it.resolution }.distinct())
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
                        _parsedStreamsCache.put(masterUrl, parsed)
                        playerManager.setAvailableQualities(listOf("Авто") + parsed.map { it.resolution }.distinct())
                    }
                    parsed
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoViewModel", "Error loading/parsing master playlist text", e)
                emptyList()
            }
        }
    }
    */


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
                    duration = com.example.utils.VideoType.CHANNEL,
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
                    duration = com.example.utils.VideoType.SERIES,
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

    private val resolvedPostersCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val resolvingVideoIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private fun cleanUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        var u = url.trim()
        if (u.startsWith("//")) {
            u = "https:" + u
        }
        return u
    }

    private fun resolveMissingPosters(videos: List<Video>) {
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
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    val metainfoUrl = "https://rutube.ru/api/metainfo/tv/$objectId/"
                                    val response = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(metainfoUrl)
                                    val body = response.string()
                                    val json = org.json.JSONObject(body)
                                    
                                    val verticalPosterUrl = json.optString("vertical_poster_url").takeIf { it.isNotBlank() }
                                    val posterUrl = json.optString("poster_url").takeIf { it.isNotBlank() }
                                    val appearance = json.optJSONObject("appearance")
                                    val coverImage = appearance?.optString("cover_image")?.takeIf { it.isNotBlank() }
                                    val picture = json.optString("picture").takeIf { it.isNotBlank() }
                                    
                                    val resolvedUrl = verticalPosterUrl ?: posterUrl ?: coverImage ?: picture
                                    if (!resolvedUrl.isNullOrBlank()) {
                                        val clean = cleanUrl(resolvedUrl)
                                        resolvedPostersCache[objectId] = clean
                                        withContext(Dispatchers.Main) {
                                            updateVideoThumbnail(video.id, clean)
                                        }
                                    }
                                } catch (e: java.lang.Exception) {
                                    android.util.Log.e("VideoViewModel", "Failed to fetch metainfo for $objectId", e)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateVideoThumbnail(videoId: String, thumbnailUrl: String) {
        val updater = { list: List<Video> ->
            list.map { if (it.id == videoId) it.copy(thumbnailUrl = thumbnailUrl) else it }
        }
        _dynamicVideos.value = updater(_dynamicVideos.value)
        _channelVideos.value = updater(_channelVideos.value)
        _channelPlaylists.value = updater(_channelPlaylists.value)
        
        // Also update the single current selection or current channel video if matched
        val currentActive = playerManager.currentSelectedVideo.value
        if (currentActive != null && currentActive.id == videoId) {
            playerManager.selectVideo(currentActive.copy(thumbnailUrl = thumbnailUrl))
        }
        val currentChanVid = _currentChannelVideo.value
        if (currentChanVid != null && currentChanVid.id == videoId) {
            _currentChannelVideo.value = currentChanVid.copy(thumbnailUrl = thumbnailUrl)
        }

        // Persistent update in database for bookmarked/history items
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val saved = repository.getVideoById(videoId)
                if (saved != null && saved.thumbnailUrl.isNullOrBlank()) {
                    repository.insertOrUpdate(saved.copy(thumbnailUrl = thumbnailUrl))
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoViewModel", "Failed to update db thumbnail for $videoId", e)
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

