package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.SavedVideo
import com.example.data.Video
import com.example.data.VideoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VideoViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = VideoRepository(db.savedVideoDao())

    // Bottom Navigation tab states: "home", "explore", "downloads", "library"
    private val _currentTab = MutableStateFlow("home")
    val currentTab = _currentTab.asStateFlow()

    // Navigation Category chips state: "Все", "Новинки", "Топ недели", "Технологии"
    private val _selectedCategory = MutableStateFlow("Все")
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

    private var playbackJob: Job? = null

    // Dynamic list of real matching videos from network / Gemini / local repository
    private val _dynamicVideos = MutableStateFlow<List<Video>>(emptyList())
    val dynamicVideos = _dynamicVideos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // Expose active loading source: Rutube API Live, Gemini AI Fallback, Offline
    val apiSource = flow {
        while (true) {
            emit(repository.lastFetchSource)
            delay(1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Инициализация...")

    init {
        fetchRealVideos()
    }

    private var fetchJob: Job? = null
    fun fetchRealVideos(query: String? = null, category: String? = null) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                _dynamicVideos.value = repository.fetchRealVideos(query, category)
            } catch (e: Exception) {
                // Ignore or log error gracefully
            } finally {
                _isLoading.value = false
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

    fun selectVideo(video: Video?) {
        if (video != null && video.id.startsWith("tv_")) {
            val tvId = video.id.substringAfter("tv_")
            viewModelScope.launch {
                _isLoading.value = true
                try {
                    val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                    val response = apiService.getDynamicUrl("https://rutube.ru/api/metainfo/tv/$tvId/video/?format=json")
                    val bodyStr = response.string()
                    val episodes = repository.parseVideoListJson(bodyStr, video.category)
                    if (episodes.isNotEmpty()) {
                        val firstEpisode = episodes.first()
                        _currentSelectedVideo.value = firstEpisode
                        _isPlaying.value = true
                        _playProgress.value = 0f
                        startPlaybackTicker()
                        _dynamicVideos.value = episodes
                    } else {
                        setSearchQuery(video.title)
                        selectTab("home")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VideoViewModel", "Error fetching TV episodes", e)
                    setSearchQuery(video.title)
                    selectTab("home")
                } finally {
                    _isLoading.value = false
                }
            }
            return
        }

        if (video != null && video.id.startsWith("channel_")) {
            val channelId = video.id.substringAfter("channel_")
            viewModelScope.launch {
                _isLoading.value = true
                try {
                    val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                    var loadedVideos: List<Video> = emptyList()
                    try {
                        val response = apiService.getDynamicUrl("https://rutube.ru/api/video/person/$channelId/?format=json")
                        val bodyStr = response.string()
                        loadedVideos = repository.parseVideoListJson(bodyStr, video.category)
                    } catch (ex: Exception) {
                        android.util.Log.e("VideoViewModel", "Dynamic person load failed, falling back to search", ex)
                    }
                    
                    if (loadedVideos.isNotEmpty()) {
                        _dynamicVideos.value = loadedVideos
                        _selectedCategory.value = "Все"
                        _searchQuery.value = ""
                        selectTab("home")
                    } else {
                        setSearchQuery(video.title)
                        selectTab("home")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VideoViewModel", "Error resolving channel", e)
                    setSearchQuery(video.title)
                    selectTab("home")
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
        viewModelScope.launch {
            repository.toggleDownload(video)
            // Keep active player in-sync
            if (_currentSelectedVideo.value?.id == video.id) {
                _currentSelectedVideo.value = _currentSelectedVideo.value?.copy(isDownloaded = !video.isDownloaded)
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
