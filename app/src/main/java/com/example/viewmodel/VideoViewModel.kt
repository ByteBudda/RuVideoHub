package com.example.viewmodel

import com.example.ui.theme.CustomTheme
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.SavedVideo
import com.example.data.Video
import com.example.data.VideoRepository
import com.example.data.SearchHistory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import com.example.manager.*

class VideoViewModel(
    application: Application,
    val repository: VideoRepository,
    val settingsManager: SettingsManager,
    val navigationManager: NavigationManager,
    val playerManager: PlayerManager,
    libraryManagerFactory: (CoroutineScope) -> LibraryManager,
    downloadManagerFactory: (CoroutineScope) -> DownloadManager,
    backupRestoreManagerFactory: (LibraryManager) -> BackupRestoreManager,
    mediaResolverFactory: (CoroutineScope) -> RutubeMediaResolver,
    feedManagerFactory: (CoroutineScope, RutubeMediaResolver) -> RutubeFeedManager
) : AndroidViewModel(application) {

    internal val db = AppDatabase.getDatabase(application)

    // Managers
    val libraryManager = libraryManagerFactory(viewModelScope)
    val downloadManager = downloadManagerFactory(viewModelScope)
    val backupRestoreManager = backupRestoreManagerFactory(libraryManager)
    val mediaResolver = mediaResolverFactory(viewModelScope)

    // Decoupled Feed Manager
    val feedManager = feedManagerFactory(viewModelScope, mediaResolver)

    init {
        // Load saved video progress timestamps from database on startup
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val savedList = repository.getSavedVideosOnly().first()
                withContext(Dispatchers.Main) {
                    savedList.forEach { savedVideo ->
                        if (savedVideo.lastProgress > 0L) {
                            playerManager.saveVideoPosition(savedVideo.id, savedVideo.lastProgress)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoViewModel", "Error restoring initial playback positions", e)
            }
        }

        viewModelScope.launch {
            downloadManager.downloadCompleted.collect { completedId ->
                if (playerManager.currentSelectedVideo.value?.id == completedId) {
                    playerManager.selectVideo(playerManager.currentSelectedVideo.value?.copy(isDownloaded = true))
                }
            }
        }

        // Sync recent watched videos to Android TV "Продолжить просмотр" channel

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

    // Delegated Flow States from feedManager
    val dynamicVideos = feedManager.dynamicVideos
    val searchSource = feedManager.searchSource
    val isLoading = feedManager.isLoading
    val realCategories = feedManager.realCategories
    val isCategoriesLoading = feedManager.isCategoriesLoading
    val feedTabs = feedManager.feedTabs
    val currentChannelVideo = feedManager.currentChannelVideo
    val currentSubfolderVideo = feedManager.currentSubfolderVideo
    val channelVideos = feedManager.channelVideos
    val channelPlaylists = feedManager.channelPlaylists
    val isLoadingPlaylists = feedManager.isLoadingPlaylists
    val isMoreLoading = feedManager.isMoreLoading

    fun setChannelActiveTab(tab: String) {
        navigationManager.setChannelActiveTab(tab)
        if (tab == "Плейлисты" && channelPlaylistsPage == 1 && !channelPlaylistsEndReached) {
            viewModelScope.launch {
                delay(200)
                loadNextPage()
            }
        }
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
            currentChannelVideo = currentChannelVideo.value,
            channelVideos = channelVideos.value,
            channelPlaylists = channelPlaylists.value,
            channelActiveTab = navigationManager.channelActiveTab.value,
            dynamicVideos = dynamicVideos.value,
            currentPage = currentPage,
            isEndReached = isEndReached,
            currentQuery = currentQuery,
            currentCategory = currentCategory,
            currentActiveApiEndpoint = currentActiveApiEndpoint,
            currentSubfolderVideo = currentSubfolderVideo.value,
            channelVideosPage = channelVideosPage,
            channelVideosEndReached = channelVideosEndReached,
            channelPlaylistsPage = channelPlaylistsPage,
            channelPlaylistsEndReached = channelPlaylistsEndReached
        )
        navigationManager.pushToHistory(currentSnapshot)
    }

    fun canNavigateBack(): Boolean {
        if (playerManager.currentSelectedVideo.value != null) return true
        val isDeepView = navigationManager.isChannelView.value || navigationManager.selectedSubfolderName.value != null
        if (isDeepView) {
            if (navigationManager.canNavigateBack()) return true
        }
        if (navigationManager.searchQuery.value.isNotEmpty()) return true
        return navigationManager.canNavigateBack()
    }

    fun navigateBack(): Boolean {
        if (playerManager.currentSelectedVideo.value != null) {
            playerManager.selectVideo(null)
            return true
        }

        val isDeepView = navigationManager.isChannelView.value || navigationManager.selectedSubfolderName.value != null

        if (isDeepView) {
            val last = navigationManager.navigateBack()
            if (last != null) {
                navigationManager.restoreFromSnapshot(last)
                feedManager.restoreFeedState(last)
                return true
            }
        }

        if (navigationManager.searchQuery.value.isNotEmpty()) {
            setSearchQuery("")
            return true
        }

        if (!isDeepView) {
            val last = navigationManager.navigateBack()
            if (last != null) {
                navigationManager.restoreFromSnapshot(last)
                feedManager.restoreFeedState(last)
                return true
            }
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

    // Delegated Properties to feedManager
    var currentPage: Int
        get() = feedManager.currentPage
        set(value) { feedManager.currentPage = value }

    var isEndReached: Boolean
        get() = feedManager.isEndReached
        set(value) { feedManager.isEndReached = value }

    val isEndReachedPublic: Boolean get() = feedManager.isEndReachedPublic

    var channelVideosPage: Int
        get() = feedManager.channelVideosPage
        set(value) { feedManager.channelVideosPage = value }

    var channelVideosEndReached: Boolean
        get() = feedManager.channelVideosEndReached
        set(value) { feedManager.channelVideosEndReached = value }

    var channelPlaylistsPage: Int
        get() = feedManager.channelPlaylistsPage
        set(value) { feedManager.channelPlaylistsPage = value }

    var channelPlaylistsEndReached: Boolean
        get() = feedManager.channelPlaylistsEndReached
        set(value) { feedManager.channelPlaylistsEndReached = value }

    var currentQuery: String?
        get() = feedManager.currentQuery
        set(value) { feedManager.currentQuery = value }

    var currentCategory: String?
        get() = feedManager.currentCategory
        set(value) { feedManager.currentCategory = value }

    var currentActiveApiEndpoint: String?
        get() = feedManager.currentActiveApiEndpoint
        set(value) { feedManager.currentActiveApiEndpoint = value }

    init {
        viewModelScope.launch {
            dynamicVideos.collect { list ->
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
            dynamicVideos.collect { videos ->
                resolveMissingPosters(videos)
            }
        }
        viewModelScope.launch {
            channelPlaylists.collect { videos ->
                resolveMissingPosters(videos)
            }
        }
        viewModelScope.launch {
            channelVideos.collect { videos ->
                resolveMissingPosters(videos)
            }
        }
    }

    // Base video feed matching state changes recursively
    val allVideos: StateFlow<List<Video>> = combine(
        dynamicVideos,
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

    fun setSearchSource(source: String) {
        feedManager.setSearchSource(source)
        val q = navigationManager.searchQuery.value
        if (q.isNotEmpty()) {
            triggerDebouncedSearch(q, navigationManager.selectedCategory.value)
        }
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

    // Delegated functions
    fun fetchRealVideos(query: String? = null, category: String? = null, targetUrl: String? = null) {
        feedManager.fetchRealVideos(query, category, targetUrl)
    }

    fun loadNextPage() = feedManager.loadNextPage()

    fun triggerDebouncedSearch(query: String, category: String) = feedManager.triggerDebouncedSearch(query, category)

    fun fetchRealCategories() = feedManager.fetchRealCategories()

    fun selectCategory(category: String, targetUrl: String? = null) = feedManager.selectCategory(category, targetUrl)

    fun selectFeedTab(tab: com.example.data.rutube.parser.TabInfo) = feedManager.selectFeedTab(tab)

    fun selectVideo(video: Video?) {
        if (video != null) {
            libraryManager.addToRecentHistory(video, currentPage)
        }
        feedManager.selectVideo(video)
    }

    fun loadVideoByUrlOrId(urlOrId: String) = feedManager.loadVideoByUrlOrId(urlOrId)

    fun resolveMissingPosters(videos: List<Video>) = feedManager.resolveMissingPosters(videos)

    fun updateVideoThumbnail(videoId: String, thumbnailUrl: String) = feedManager.updateVideoThumbnail(videoId, thumbnailUrl)

    fun mapResourceToVideo(resource: com.example.data.rutube.parser.ResourceInfo, tabId: Int, categoryName: String): Video {
        return feedManager.mapResourceToVideo(resource, tabId, categoryName)
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
