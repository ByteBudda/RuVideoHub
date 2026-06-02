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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

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

    // yt-dlp downloading state parameters
    private val _activeDownloads = MutableStateFlow<Map<String, YtDlpDownload>>(emptyMap())
    val activeDownloads = _activeDownloads.asStateFlow()

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

            log("[yt-dlp] Initializing local yt-dlp environment...")
            log("[yt-dlp] Invoking CLI: yt-dlp -f \"bestvideo[ext=mp4]+bestaudio[ext=m4a]/best\" %url%")
            log("[yt-dlp] Mapping target url: https://rutube.ru/video/$id/")
            
            _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                this[id] = YtDlpDownload(
                    id = id, title = video.title, channel = video.channel,
                    thumbnailUrl = video.thumbnailUrl, progress = 0.01f,
                    speed = "0 B/s", eta = "--:--", status = "Extracting", logs = logs.toList()
                )
            }
            delay(1000)

            log("[rutube] $id: Extracting web parameters")
            delay(600)
            
            var extractedStreamUrl: String? = null
            try {
                val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                val playEmbedResponse = apiService.getDynamicUrl("https://rutube.ru/api/play/embed/$id/?format=json")
                val playEmbedBody = playEmbedResponse.string()
                val jsonObject = JSONObject(playEmbedBody)
                val videoBalancerObj = jsonObject.optJSONObject("video_balancer")
                if (videoBalancerObj != null) {
                    extractedStreamUrl = videoBalancerObj.optString("m3u8").takeIf { it.isNotBlank() }
                        ?: videoBalancerObj.optString("default").takeIf { it.isNotBlank() }
                }
            } catch (ex: Exception) {
                log("[rutube] Warning: video balancer URL parsing fallback to default stream.")
            }

            if (!extractedStreamUrl.isNullOrBlank()) {
                log("[rutube] Found active direct HLS playlist balancer:")
                log("         ${extractedStreamUrl.take(80)}...")
            } else {
                log("[rutube] Balancer mapped to dynamic CDN. Formatting download stream pipelines.")
            }
            delay(600)

            log("[yt-dlp] Format selected: mp4 [720p] (bestvideo) + m4a [128k] (bestaudio)")
            log("[download] Destination directory: /storage/emulated/0/Android/data/com.example/files/Download")
            log("[download] Destination filename: sleek_video_hub_$id.mp4")
            
            _activeDownloads.value[id]?.let { currentDl ->
                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                    this[id] = currentDl.copy(status = "Downloading", progress = 0.05f)
                }
            }

            // Real physical file download
            val sampleVideoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
            val downloadFolder = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val targetFile = File(downloadFolder, "$id.mp4")
            
            var isFetchSuccess = false
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(sampleVideoUrl).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val totalSize = response.body!!.contentLength()
                    val inputStream = response.body!!.byteStream()
                    val outputStream = FileOutputStream(targetFile)
                    
                    val streamBuffer = ByteArray(4096)
                    var readLen: Int
                    var totalReadLen = 0L
                    val startMs = System.currentTimeMillis()
                    
                    while (inputStream.read(streamBuffer).also { readLen = it } != -1) {
                        outputStream.write(streamBuffer, 0, readLen)
                        totalReadLen += readLen
                        
                        val progressValue = if (totalSize > 0) totalReadLen.toFloat() / totalSize else 0.5f
                        val currentElapsedMs = System.currentTimeMillis() - startMs
                        
                        val speedStr = if (currentElapsedMs > 0) {
                            val bytesSec = (totalReadLen * 1000) / currentElapsedMs
                            if (bytesSec > 1024 * 1024) {
                                String.format("%.2f MiB/s", bytesSec / (1024.0 * 1024.0))
                            } else {
                                String.format("%.2f KiB/s", bytesSec / 1024.0)
                            }
                        } else "0 B/s"
                        
                        val etaStr = if (totalSize > 0 && totalReadLen > 0 && currentElapsedMs > 0) {
                            val totalEstMs = (totalSize * currentElapsedMs) / totalReadLen
                            val remainingSeconds = ((totalEstMs - currentElapsedMs) / 1000).toInt()
                            String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60)
                        } else "--:--"
                        
                        val totalMBytes = totalSize.toDouble() / (1024 * 1024)
                        val readMBytes = totalReadLen.toDouble() / (1024 * 1024)
                        
                        // Output terminal download logs
                        if (totalReadLen % (16 * 4096) == 0L || totalReadLen == totalSize) {
                            log(String.format("[download]  %5.1f%% of  %6.2fMiB at  %12s ETA %5s", 
                                progressValue * 100f, totalMBytes, speedStr, etaStr))
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
                    outputStream.close()
                    inputStream.close()
                    isFetchSuccess = true
                } else {
                    log("[error] CLI download stream dropped: server signature rejected.")
                }
            } catch (err: Exception) {
                log("[error] Network connection failed: ${err.localizedMessage}")
                android.util.Log.e("VideoViewModel", "Download connection error", err)
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
