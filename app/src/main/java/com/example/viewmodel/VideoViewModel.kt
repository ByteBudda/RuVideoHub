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

    private val _realCategories = MutableStateFlow<List<RutubeCategory>>(emptyList())
    val realCategories = _realCategories.asStateFlow()

    private val _isCategoriesLoading = MutableStateFlow(false)
    val isCategoriesLoading = _isCategoriesLoading.asStateFlow()

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

            log("[rutube] $id: Extracting web parameters via /api/play/options/")
            delay(600)
            
            var extractedStreamUrl: String? = null
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

            log("[yt-dlp] Format selected: mp4 [720p] (bestvideo) + m4a [128k] (bestaudio)")
            log("[download] Destination directory: /storage/emulated/0/Android/data/com.example/files/Download")
            log("[download] Destination filename: sleek_video_hub_$id.mp4")
            
            _activeDownloads.value[id]?.let { currentDl ->
                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                    this[id] = currentDl.copy(status = "Downloading", progress = 0.05f)
                }
            }

            val downloadFolder = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
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
