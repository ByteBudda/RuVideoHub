package com.example.viewmodel

import android.app.Application
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.data.*
import com.example.data.rutube.RutubeRetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class VideoViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = VideoRepository(db.savedVideoDao(), RutubeRetrofitClient.apiService)

    private val _currentTab = MutableStateFlow("home")
    val currentTab = _currentTab.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Все")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isMicrophoneActive = MutableStateFlow(false)
    val isMicrophoneActive = _isMicrophoneActive.asStateFlow()

    private val _currentSelectedVideo = MutableStateFlow<Video?>(null)
    val currentSelectedVideo = _currentSelectedVideo.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playProgress = MutableStateFlow(0f)
    val playProgress = _playProgress.asStateFlow()

    private val _videoPositions = mutableMapOf<String, Long>()

    private var playbackJob: Job? = null

    private val _dynamicVideos = MutableStateFlow<List<Video>>(emptyList())
    val dynamicVideos = _dynamicVideos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    val pagingVideos: Flow<PagingData<Video>> = combine(
        _searchQuery.debounce(500),
        _selectedCategory
    ) { query, category ->
        Pair(query, category)
    }.flatMapLatest { (query, category) ->
        Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false, maxSize = 100),
            pagingSourceFactory = { VideoPagingSource(repository, query, category) }
        ).flow.cachedIn(viewModelScope)
    }

    private val _realCategories = MutableStateFlow<List<RutubeCategory>>(emptyList())
    val realCategories = _realCategories.asStateFlow()

    private val _isCategoriesLoading = MutableStateFlow(false)
    val isCategoriesLoading = _isCategoriesLoading.asStateFlow()

    private val _activeDownloads = MutableStateFlow<Map<String, RutubeDownload>>(emptyMap())
    val activeDownloads = _activeDownloads.asStateFlow()

    val apiSource = flow {
        while (true) {
            emit(repository.lastFetchSource)
            delay(1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Инициализация...")

    private var currentPage = 1
    private var isEndReached = false
    private var currentQuery: String? = null
    private var currentCategory: String? = "Все"

    private val _isMoreLoading = MutableStateFlow(false)
    val isMoreLoading = _isMoreLoading.asStateFlow()

    val allVideos: StateFlow<List<Video>> = combine(
        _dynamicVideos,
        repository.getSavedVideosOnly()
    ) { dynamicList: List<Video>, savedList: List<SavedVideo> ->
        val savedMap = savedList.associateBy { it.id }
        dynamicList.map { video ->
            val saved = savedMap[video.id]
            video.copy(
                isDownloaded = saved?.isDownloaded ?: false,
                isBookmarked = saved?.isBookmarked ?: false
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val filteredVideos: StateFlow<List<Video>> = allVideos

    val downloadedSavedVideos: StateFlow<List<SavedVideo>> = repository.getSavedVideosOnly()
        .map { list -> list.filter { it.isDownloaded } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarkedSavedVideos: StateFlow<List<SavedVideo>> = repository.getSavedVideosOnly()
        .map { list -> list.filter { it.isBookmarked } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSavedVideos: StateFlow<List<SavedVideo>> = repository.getSavedVideosOnly()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            } finally {
                _isCategoriesLoading.value = false
            }
        }
    }

    private var fetchJob: Job? = null
    fun fetchRealVideos(query: String? = null, category: String? = null) {
        fetchJob?.cancel()
        currentQuery = query
        currentCategory = category
        currentPage = 1
        isEndReached = false
        fetchJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                _dynamicVideos.value = repository.fetchRealVideos(query, category, page = 1)
            } catch (e: Exception) {
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
                val newVideos = repository.fetchRealVideos(currentQuery, currentCategory, nextPage)
                if (newVideos.isEmpty()) {
                    isEndReached = true
                } else {
                    currentPage = nextPage
                    _dynamicVideos.value = (_dynamicVideos.value + newVideos).distinctBy { it.id }
                }
            } catch (e: Exception) {
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

    fun addToRecentHistory(video: Video) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
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
            }
        }
    }

    fun deleteRecentItem(video: Video) {
        val downloadFolder = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = File(downloadFolder, "${video.id}.mp4")
        viewModelScope.launch {
            if (targetFile.exists()) targetFile.delete()
            repository.deleteVideoById(video.id)
            if (_currentSelectedVideo.value?.id == video.id) {
                _currentSelectedVideo.value = _currentSelectedVideo.value?.copy(isDownloaded = false)
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

    fun selectVideo(video: Video?) {
        if (video != null && video.id.startsWith("tv_")) {
            val tvId = video.id.substringAfter("tv_")
            viewModelScope.launch {
                _isLoading.value = true
                try {
                    val apiService = RutubeRetrofitClient.apiService
                    val response = apiService.getDynamicUrl("https://rutube.ru/api/metainfo/tv/$tvId/video/?format=json")
                    val episodes = response.results?.mapNotNull { repository.toVideo(it, video.category) } ?: emptyList()
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
                    val apiService = RutubeRetrofitClient.apiService
                    var loadedVideos: List<Video> = emptyList()
                    try {
                        val response = apiService.getDynamicUrl("https://rutube.ru/api/video/person/$channelId/?format=json")
                        loadedVideos = response.results?.mapNotNull { repository.toVideo(it, video.category) } ?: emptyList()
                    } catch (ex: Exception) {
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
                    setSearchQuery(video.title)
                    selectTab("home")
                } finally {
                    _isLoading.value = false
                }
            }
            return
        }

        _currentSelectedVideo.value = video
        if (video != null) {
            _isPlaying.value = true
            _playProgress.value = 0f
            startPlaybackTicker()
            loadComments(video.id)
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
                if (targetFile.exists()) targetFile.delete()
                repository.toggleDownload(video)
                if (_currentSelectedVideo.value?.id == video.id) {
                    _currentSelectedVideo.value = _currentSelectedVideo.value?.copy(isDownloaded = false)
                }
            } else {
                startRutubeNativeDownload(video)
            }
        }
    }

    fun deleteDownload(video: Video) {
        val downloadFolder = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = File(downloadFolder, "${video.id}.mp4")
        viewModelScope.launch {
            if (targetFile.exists()) targetFile.delete()
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
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "${video.title}.mp4".replace("[\\\\/:*?\"<>|]".toRegex(), "_"))
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }
            var uri: android.net.Uri? = null
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            }
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    inputFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                onResult(true, "Файл успешно сохранен в папку 'Загрузки' устройства!")
            } else {
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!publicDownloads.exists()) publicDownloads.mkdirs()
                val outputFile = File(publicDownloads, "${video.title}.mp4".replace("[\\\\/:*?\"<>|]".toRegex(), "_"))
                inputFile.inputStream().use { inputStream ->
                    outputFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                onResult(true, "Файл успешно сохранен в папку 'Загрузки': ${outputFile.name}")
            }
        } catch (e: Exception) {
            onResult(false, "Ошибка сохранения: ${e.localizedMessage ?: e.message}")
        }
    }

    private fun startRutubeNativeDownload(video: Video) {
        val id = video.id
        viewModelScope.launch(Dispatchers.IO) {
            val logs = mutableListOf<String>()
            
            fun log(msg: String) {
                logs.add(msg)
                val currentDl = _activeDownloads.value[id]
                val updatedDl = currentDl?.copy(logs = logs.toList()) ?: RutubeDownload(
                    id = id, title = video.title, channel = video.channel,
                    thumbnailUrl = video.thumbnailUrl, progress = 0f,
                    speed = "0 B/s", eta = "--:--", status = "В очереди", logs = logs.toList()
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
                    val cipher = Cipher.getInstance("AES/CBC/NoPadding")
                    val keySpec = SecretKeySpec(key, "AES")
                    val ivSpec = IvParameterSpec(ivBytes)
                    cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                    cipher.doFinal(encryptedBytes)
                }
            }

            log("[Core] Инициализация загрузки видео с Rutube...")
            log("[Core] Идентификатор медиафайла: $id")
            
            _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                this[id] = RutubeDownload(
                    id = id, title = video.title, channel = video.channel,
                    thumbnailUrl = video.thumbnailUrl, progress = 0.01f,
                    speed = "0 B/s", eta = "--:--", status = "Анализ", logs = logs.toList()
                )
            }

            log("[Rutube API] Запрос конфигурации медиа-балансера через /api/play/options/")
            var extractedStreamUrl: String? = null
            if (video.id.startsWith("manual_") && video.description.startsWith("http")) {
                extractedStreamUrl = video.description
                log("[Core] Использование прямого потока из описания: ${extractedStreamUrl.take(60)}...")
            } else {
                try {
                    val apiService = RutubeRetrofitClient.apiService
                    val playOptionsResponse = apiService.getDynamicUrl("https://rutube.ru/api/play/options/$id/?format=json")
                    val playOptionsBody = playOptionsResponse.toString()
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
                    log("[Rutube API] Предупреждение: Ошибка разбора балансера, попытка парсинга структуры по умолчанию.")
                }
            }

            if (!extractedStreamUrl.isNullOrBlank()) {
                log("[Rutube API] Обнаружен рабочий HLS плейлист: ${extractedStreamUrl.take(80)}...")
            } else {
                log("[Ошибка] Не удалось получить ссылки на медиапотоки.")
            }

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

            log("[HTTP] Каталог назначения: /Android/data/${getApplication<Application>().packageName}/files/Download")
            
            _activeDownloads.value[id]?.let { currentDl ->
                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                    this[id] = currentDl.copy(status = "Скачивание", progress = 0.05f)
                }
            }

            val downloadFolder = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: getApplication<Application>().filesDir
            val targetFile = File(downloadFolder, "$id.mp4")
            if (targetFile.exists()) targetFile.delete()
            
            var isFetchSuccess = false
            try {
                if (extractedStreamUrl.isNullOrBlank()) throw Exception("Не найден URL потока для видео $id")
                log("[HLS] Чтение манифеста мастер-плейлиста...")
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
                    log("[HLS] Загрузка плейлиста выбранного профиля качества...")
                    mediaM3u8Text = loadText(mediaPlaylistUrl)
                } else {
                    mediaM3u8Text = masterM3u8Text
                }
                
                log("[HLS] Анализ индексов чанков видеопотока...")
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
                    log("[Защита] Обнаружено шифрование AES-128. Скачивание ключа дешифрации...")
                    try {
                        val req = Request.Builder().url(encryptionKeyUrl!!).build()
                        okHttpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                keyBytes = resp.body?.bytes()
                            }
                        }
                    } catch (e: Exception) {
                        log("[Защита] Предупреждение: Не удалось получить ключ DRM с сервера.")
                    }
                    if (keyBytes != null && keyBytes!!.size == 16) {
                        log("[Защита] Ключ дешифрации успешно загружен и верифицирован.")
                    }
                }
                
                if (segments.isNotEmpty()) {
                    log("[HLS] Найдено фрагментов: ${segments.size}. Запуск конвейера последовательного выкачивания...")
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
                                throw Exception("Сбой загрузки на сегменте номер: $index")
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
                            
                            val progressValue = 0.10f + (index.toFloat() / segments.size) * 0.90f
                            val elapsedMs = System.currentTimeMillis() - startMs
                            
                            val speedStr = if (elapsedMs > 0) {
                                val bytesSec = (totalBytesDownloaded * 1000) / elapsedMs
                                if (bytesSec > 1024 * 1024) {
                                    String.format("%.2f МБ/с", bytesSec / (1024.0 * 1024.0))
                                } else {
                                    String.format("%.2f КБ/с", bytesSec / 1024.0)
                                }
                            } else "0 Б/с"
                            
                            val etaStr = if (index > 0) {
                                val totalEstMs = (segments.size * elapsedMs) / index
                                val remainingSeconds = ((totalEstMs - elapsedMs) / 1000).toInt()
                                if (remainingSeconds > 0) {
                                    String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60)
                                } else "00:01"
                            } else "--:--"
                            
                            if (index % 10 == 0 || index == segments.size - 1) {
                                val sizeMBytes = totalBytesDownloaded.toDouble() / (1024 * 1024)
                                log(String.format("[Поток] Фрагмент %d/%d (%d%%). Получено: %.2f МБ • Скорость: %s • Осталось: %s", 
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
                    log("[Ошибка] Плейлист Rutube не содержит доступных сегментов.")
                }
            } catch (err: Exception) {
                log("[Ошибка] Ошибка записи или скачивания потока данных: ${err.localizedMessage}")
                if (targetFile.exists()) targetFile.delete()
            }

            if (isFetchSuccess) {
                log("[Core] Все фрагменты HLS успешно выкачаны и объединены.")
                repository.toggleDownload(video)
                _activeDownloads.value[id]?.let { currentDl ->
                    _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                        this[id] = currentDl.copy(status = "Готово", progress = 1f)
                    }
                }
                if (_currentSelectedVideo.value?.id == id) {
                    _currentSelectedVideo.value = _currentSelectedVideo.value?.copy(isDownloaded = true)
                }
                delay(2000)
                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { remove(id) }
            } else {
                log("[Ошибка] Процесс обработки прерван из-за критического сбоя сети.")
                _activeDownloads.value[id]?.let { currentDl ->
                    _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                        this[id] = currentDl.copy(status = "Ошибка")
                    }
                }
                delay(4000)
                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { remove(id) }
            }
        }
    }

    fun toggleMicrophone(status: Boolean) {
        _isMicrophoneActive.value = status
        if (status) {
            viewModelScope.launch {
                delay(1800)
                setSearchQuery("API")
                _isMicrophoneActive.value = false
            }
        }
    }

    fun getFormattedElapsedTime(durationStr: String, progress: Float): String {
        val totalSeconds = parseDurationToSeconds(durationStr)
        val elapsedSeconds = (progress * totalSeconds).toInt()
        return formatSecondsToTimeString(elapsedSeconds)
    }

    private fun parseDurationToSeconds(duration: String): Int {
        val parts = duration.split(":")
        return try {
            if (parts.size == 2) {
                parts[0].toInt() * 60 + parts[1].toInt()
            } else if (parts.size == 3) {
                parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
            } else {
                300
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

    private val _comments = MutableStateFlow<List<RutubeComment>>(emptyList())
    val comments = _comments.asStateFlow()

    private val _isCommentsLoading = MutableStateFlow(false)
    val isCommentsLoading = _isCommentsLoading.asStateFlow()

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

    fun setCredentials(sessionId: String, csrfToken: String, user: String = "Сергей Петров") {
        _authSessionId.value = sessionId
        _authCsrfToken.value = csrfToken
        _isAuthorized.value = true
        _username.value = user
    }

    fun logout() {
        _authSessionId.value = null
        _authCsrfToken.value = null
        _isAuthorized.value = false
    }

    suspend fun fetchHlsStreamUrl(videoId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val tizenUas = listOf(
                    "Mozilla/5.0 (SmartHub; SMART-TV; Tizen 6.0) AppleWebKit/537.36 (KHTML, like Gecko)  SamsungBrowser/4.0 Chrome/108.0.5359.128",
                    "Mozilla/5.0 (SmartHub; SMART-TV; Tizen 5.5) AppleWebKit/537.36 (KHTML, like Gecko)  SamsungBrowser/4.0 Chrome/96.0.4664.45",
                    "Mozilla/5.0 (SmartHub; SMART-TV; Tizen 7.0) AppleWebKit/537.36 (KHTML, like Gecko)  SamsungBrowser/5.0 Chrome/112.0.5615.204",
                    "Mozilla/5.0 (Linux; Tizen 6.5) AppleWebKit/537.36 (KHTML, like Gecko) Version/6.0 SamsungBrowser/4.0 Chrome/106.0.5249.65"
                )
                val randomUa = tizenUas.random()
                val okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
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
                null
            }
        }
    }

    fun loadComments(videoId: String) {
        _isCommentsLoading.value = true
        _comments.value = emptyList()
        viewModelScope.launch {
            try {
                val apiService = RutubeRetrofitClient.apiService
                val response = apiService.getDynamicUrl("https://rutube.ru/api/v2/comments/?video_id=$videoId&format=json")
                val jsonStr = response.toString()
                val jsonObject = JSONObject(jsonStr)
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
            } catch (e: Exception) {
                _comments.value = emptyList()
            } finally {
                _isCommentsLoading.value = false
            }
        }
    }

    fun extractRutubeId(url: String): String? {
        if (!url.contains("rutube.ru")) return null
        val regex = "([a-f0-9]{32})".toRegex(RegexOption.IGNORE_CASE)
        return regex.find(url)?.value
    }

    fun startManualYtDlpDownload(url: String, customTitle: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) return
        val rutubeId = extractRutubeId(trimmedUrl)
        if (rutubeId != null) {
            viewModelScope.launch {
                val resolvedTitle = if (customTitle.isNotBlank()) customTitle else "Rutube Видео ($rutubeId)"
                val video = Video(
                    id = rutubeId,
                    title = resolvedTitle,
                    channel = "Ссылка Rutube",
                    views = "Локальная загрузка",
                    timeAgo = "Только что",
                    duration = "--:--",
                    isPro = false,
                    category = "Разное",
                    description = "Видео скачано вручную по ссылке",
                    thumbnailUrl = "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?w=300",
                    isDownloaded = false,
                    isBookmarked = false
                )
                startRutubeNativeDownload(video)
            }
        } else {
            val cleanId = "manual_" + System.currentTimeMillis().toString().takeLast(6)
            val finalTitle = if (customTitle.isNotBlank()) customTitle else "Загрузка ($cleanId)"
            viewModelScope.launch(Dispatchers.IO) {
                val logs = mutableListOf<String>()
                fun log(msg: String) {
                    logs.add(msg)
                    val currentDl = _activeDownloads.value[cleanId]
                    val updatedDl = currentDl?.copy(logs = logs.toList()) ?: RutubeDownload(
                        id = cleanId, title = finalTitle, channel = "Прямой поток",
                        thumbnailUrl = "", progress = 0f,
                        speed = "0 B/s", eta = "--:--", status = "В очереди", logs = logs.toList()
                    )
                    _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                        this[cleanId] = updatedDl
                    }
                }
                log("[Core] Попытка разбора прямой ссылки медиаресурса...")
                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                    this[cleanId] = RutubeDownload(
                        id = cleanId, title = finalTitle, channel = "Прямой поток",
                        thumbnailUrl = "", progress = 0.01f,
                        speed = "0 B/s", eta = "--:--", status = "Анализ", logs = logs.toList()
                    )
                }
                val isM3u8 = trimmedUrl.contains(".m3u8")
                val isDirectMp4 = trimmedUrl.contains(".mp4") || trimmedUrl.contains(".mkv") || trimmedUrl.contains(".mov") || trimmedUrl.contains(".avi")
                val downloadFolder = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val targetFile = File(downloadFolder, "$cleanId.mp4")
                try {
                    if (isM3u8) {
                        log("[HLS] Обнаружен прямой URL стримингового плейлиста HLS/M3U8.")
                        val video = Video(
                            id = cleanId,
                            title = finalTitle,
                            channel = "HLS Стрим",
                            views = "Офлайн-Загрузка",
                            timeAgo = "Только что",
                            duration = "--:--",
                            isPro = false,
                            category = "Разное",
                            description = trimmedUrl,
                            thumbnailUrl = "",
                            isDownloaded = false,
                            isBookmarked = false
                        )
                        startRutubeNativeDownload(video)
                    } else if (isDirectMp4) {
                        log("[HTTP] Обнаружена прямая ссылка на медиафайл MP4/контейнер.")
                        _activeDownloads.value[cleanId]?.let { currentDl ->
                            _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                                this[cleanId] = currentDl.copy(status = "Скачивание", progress = 0.05f)
                            }
                        }
                        val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).build()
                        val request = Request.Builder()
                            .url(trimmedUrl)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                            .header("Accept", "*/*")
                            .header("Referer", "https://rutube.ru/")
                            .build()
                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                            val body = response.body ?: throw Exception("Тело ответа пустое")
                            val contentLength = body.contentLength().coerceAtLeast(1L)
                            body.byteStream().use { inputStream ->
                                FileOutputStream(targetFile).use { outputStream ->
                                    val buffer = ByteArray(16384)
                                    var bytesRead: Int
                                    var totalBytes = 0L
                                    val startMs = System.currentTimeMillis()
                                    var lastLogUpdate = 0L
                                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                        outputStream.write(buffer, 0, bytesRead)
                                        totalBytes += bytesRead
                                        val now = System.currentTimeMillis()
                                        if (now - lastLogUpdate > 600) {
                                            val pct = totalBytes.toFloat() / contentLength.toFloat()
                                            val speed = (totalBytes * 1000) / (now - startMs).coerceAtLeast(1)
                                            val speedStr = if (speed > 1024 * 1024) String.format("%.1f МБ/с", speed.toFloat() / (1024 * 1024)) else "${speed / 1024} КБ/с"
                                            log("[HTTP] Скачано: ${(pct * 100).toInt()}% • Скорость: $speedStr")
                                            _activeDownloads.value[cleanId]?.let { currentDl ->
                                                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                                                    this[cleanId] = currentDl.copy(progress = pct.coerceIn(0f, 0.99f), speed = speedStr, status = "Скачивание")
                                                }
                                            }
                                            lastLogUpdate = now
                                        }
                                    }
                                }
                            }
                        }
                        log("[HTTP] Скачивание файла успешно завершено.")
                        val saved = SavedVideo(
                            id = cleanId,
                            title = finalTitle,
                            channel = "Прямое скачивание",
                            views = "Скачано по ссылке",
                            timeAgo = "Только что",
                            duration = "--:--",
                            isPro = false,
                            category = "Загрузки",
                            isDownloaded = true,
                            isBookmarked = false,
                            thumbnailUrl = ""
                        )
                        db.savedVideoDao().insertOrUpdate(saved)
                        _activeDownloads.value[cleanId]?.let { currentDl ->
                            _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                                this[cleanId] = currentDl.copy(status = "Готово", progress = 1.0f, speed = "0 Б/с")
                            }
                        }
                        delay(1500)
                        _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { remove(cleanId) }
                    } else {
                        throw Exception("Неподдерживаемый тип ссылки или медиа-ресурса. Должен быть Rutube, m3u8 или прямой mp4.")
                    }
                } catch (err: Exception) {
                    log("[Ошибка] Загрузка отменена или произошел сбой: ${err.message}")
                    delay(3000)
                    _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { remove(cleanId) }
                }
            }
        }
    }

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

data class RutubeDownload(
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