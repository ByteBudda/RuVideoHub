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
import com.example.data.SearchHistory
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

    internal val db = AppDatabase.getDatabase(application)
    internal val repository = VideoRepository(db.savedVideoDao())

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
    val searchHistory: StateFlow<List<SearchHistory>> = db.searchHistoryDao().getAllSearchHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            db.searchHistoryDao().insertOrUpdate(SearchHistory(query = trimmed, timestamp = System.currentTimeMillis()))
        }
    }

    fun deleteSearchQuery(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.searchHistoryDao().deleteByQuery(query)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            db.searchHistoryDao().clearAll()
        }
    }
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

    fun setSearchSource(source: String) {
        _searchSource.value = source
        val q = navigationManager.searchQuery.value
        if (q.isNotEmpty()) {
            triggerDebouncedSearch(q, navigationManager.selectedCategory.value)
        }
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
    internal val _isMicrophoneActive = MutableStateFlow(false)
    val isMicrophoneActive = _isMicrophoneActive.asStateFlow()

    val isTvMiniFullscreen = playerManager.isTvMiniFullscreen
    val isMiniPlayer = playerManager.isMiniPlayer

    internal val _isInPipMode = MutableStateFlow(false)
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
        } else {
            viewModelScope.launch {
                repository.saveVideoProgressById(videoId, position, duration)
            }
        }
    }
    fun getVideoPosition(videoId: String): Long = playerManager.getVideoPosition(videoId)

    fun markAsWatched(video: Video, durationMs: Long = 0L) {
        viewModelScope.launch {
            val actualDuration = if (durationMs > 0L) durationMs else {
                com.example.utils.VideoDurationFormatter.parseDurationToSeconds(video.duration) * 1000L
            }
            repository.saveVideoProgress(video, actualDuration, actualDuration)
        }
    }


    internal var playbackJob: Job? = null

    // Dynamic list of real matching videos from network / offline database/built-in catalog
    internal val _dynamicVideos = MutableStateFlow<List<Video>>(emptyList())
    val dynamicVideos = _dynamicVideos.asStateFlow()

    internal val _searchSource = MutableStateFlow("Rutube")
    val searchSource = _searchSource.asStateFlow()

    internal val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    internal val _realCategories = MutableStateFlow<List<RutubeCategory>>(emptyList())
    val realCategories = _realCategories.asStateFlow()

    internal val _isCategoriesLoading = MutableStateFlow(true)
    val isCategoriesLoading = _isCategoriesLoading.asStateFlow()

    internal val _feedTabs = MutableStateFlow<List<com.example.data.rutube.parser.TabInfo>>(emptyList())
    val feedTabs = _feedTabs.asStateFlow()

    internal val _currentChannelVideo = MutableStateFlow<Video?>(null)
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    internal val _currentSubfolderVideo = MutableStateFlow<Video?>(null)
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    internal val _channelVideos = MutableStateFlow<List<Video>>(emptyList())
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    internal val _channelPlaylists = MutableStateFlow<List<Video>>(emptyList())
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    internal val _isLoadingPlaylists = MutableStateFlow(false)
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

    internal var currentPage = 1
    internal var isEndReached = false
    val isEndReachedPublic: Boolean get() = isEndReached
    internal var currentQuery: String? = null
    internal var currentCategory: String? = "Фильмы"
    internal var currentActiveApiEndpoint: String? = null

    internal val _isMoreLoading = MutableStateFlow(false)
    val isMoreLoading = _isMoreLoading.asStateFlow()

    init {
        viewModelScope.launch {
            _dynamicVideos.collect { list ->
                playerManager.currentPlaylist = list
                val currentVid = playerManager.currentSelectedVideo.value
                if (currentVid != null) {
                    val idx = list.indexOfFirst { it.id == currentVid.id }
                    if (idx != -1) {
                        playerManager.currentIndex = idx
                    }
                }
            }
        }

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






    internal var fetchJob: Job? = null
    internal var requestId = 0


    internal var searchDebounceJob: Job? = null

    // Base video feed matching state changes recursively
    val allVideos: StateFlow<List<Video>> = combine(
        _dynamicVideos,
        repository.getSavedVideosOnly()
    ) { dynamicList, savedList ->
        val savedMap = savedList.associateBy { it.id }
        dynamicList.map { vid ->
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

    fun addToRecentHistory(video: Video) = libraryManager.addToRecentHistory(video, currentPage)

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







    internal fun startPlaybackTicker() {
        // Disabled fake playback ticker because real ExoPlayer handles its own state
    }

    internal fun stopPlaybackTicker() {
        playbackJob?.cancel()
        playbackJob = null
    }

    internal fun startYtDlpDownload(video: Video) {
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

    internal fun formatDurationMs(ms: Long): String {
        if (ms <= 0) return ""
        val totalSecs = ms / 1000
        val h = totalSecs / 3600
        val m = (totalSecs % 3600) / 60
        val s = totalSecs % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
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
    
    fun getPluginPageUrl(videoId: String): String? {
        return mediaResolver.masterUrlCache[videoId + "_page"]
    }



    internal fun mapResourceToVideo(resource: com.example.data.rutube.parser.ResourceInfo, tabId: Int, categoryName: String): Video {
        val url = resource.url ?: ""
        val extractedId = "\\d+".toRegex().find(url)?.value ?: ""
        
        return when (resource.type) {
            com.example.data.rutube.parser.EntityType.CHANNEL -> {
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
            com.example.data.rutube.parser.EntityType.TV_SERIES -> {
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
            com.example.data.rutube.parser.EntityType.PLAYLIST -> {
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

    internal val resolvedPostersCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    internal val resolvingVideoIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    internal fun cleanUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        var u = url.trim()
        if (u.startsWith("//")) {
            u = "https:" + u
        }
        return u
    }

    internal fun resolveMissingPosters(videos: List<Video>) {
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

    internal fun updateVideoThumbnail(videoId: String, thumbnailUrl: String) {
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
                viewModelScope.launch { com.example.manager.ErrorHandler.reportError("Failed to update database: ${e.message}") }
            }
        }
    }

    // Factory helper in case we instantiate standard lifecycle
    class Factory(internal val application: Application) : ViewModelProvider.Factory {
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

