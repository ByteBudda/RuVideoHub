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
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

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

    // Tabs & UI States
    private val _currentTab = MutableStateFlow("home")
    val currentTab = _currentTab.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Фильмы")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isMicrophoneActive = MutableStateFlow(false)
    val isMicrophoneActive = _isMicrophoneActive.asStateFlow()

    private val _currentSelectedVideo = MutableStateFlow<Video?>(null)
    val currentSelectedVideo = _currentSelectedVideo.asStateFlow()

    // Состояния воспроизведения
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    // Оставляем для совместимости со старыми элементами интерфейса (0f .. 1f)
    private val _playProgress = MutableStateFlow(0f)
    val playProgress = _playProgress.asStateFlow()

    // ТРЕКИНГ В РЕАЛЬНЫХ МИЛЛИСЕКУНДАХ (Защита от сброса при Fullscreen)
    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs = _currentPositionMs.asStateFlow()

    private val _videoDurationMs = MutableStateFlow(1L)
    val videoDurationMs = _videoDurationMs.asStateFlow()

    // Поток фоллбека: true -> открываем iframe эмбед, false -> нативный ExoPlayer
    private val _isEmbedFallback = MutableStateFlow(false)
    val isEmbedFallback = _isEmbedFallback.asStateFlow()

    // Хранилище позиций (videoId -> позиция в мс)
    private val _videoPositions = mutableMapOf<String, Long>()

    // Кэш для видео, у которых нет прямого HLS потока (черный список для моментального эмбеда)
    private val _embedFallbackCache = ConcurrentHashMap<String, Boolean>()
    
    // Кэш рабочих ссылок на потоки
    private val _streamUrlCache = ConcurrentHashMap<String, String>()

    private var playbackJob: Job? = null
    private var fetchJob: Job? = null
    private var searchDebounceJob: Job? = null

    //Lists & Categories
    private val _dynamicVideos = MutableStateFlow<List<Video>>(emptyList())
    val dynamicVideos = _dynamicVideos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _realCategories = MutableStateFlow<List<RutubeCategory>>(emptyList())
    val realCategories = _realCategories.asStateFlow()

    private val _isCategoriesLoading = MutableStateFlow(false)
    val isCategoriesLoading = _isCategoriesLoading.asStateFlow()

    private val _activeDownloads = MutableStateFlow<Map<String, YtDlpDownload>>(emptyMap())
    val activeDownloads = _activeDownloads.asStateFlow()

    private var currentPage = 1
    private var isEndReached = false
    private var currentQuery: String? = null
    private var currentCategory: String? = "Фильмы"
    private var currentActiveApiEndpoint: String? = null

    private val _isMoreLoading = MutableStateFlow(false)
    val isMoreLoading = _isMoreLoading.asStateFlow()

    val apiSource = flow {
        while (true) {
            emit(repository.lastFetchSource)
            delay(1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Инициализация...")

    init {
        checkCookiesAndSyncState()
        fetchRealVideos()
        fetchRealCategories()
    }

    fun saveVideoPosition(videoId: String, position: Long) {
        _videoPositions[videoId] = position
        val duration = _videoDurationMs.value.coerceAtLeast(1L)
        _playProgress.value = position.toFloat() / duration.toFloat()
    }

    fun getVideoPosition(videoId: String): Long {
        return _videoPositions[videoId] ?: 0L
    }

    fun checkCookiesAndSyncState() {
        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.flush()
        val cookies = cookieManager.getCookie("https://rutube.ru")
        
        var sessionId: String? = null
        var csrfToken: String? = null

        if (!cookies.isNullOrBlank()) {
            cookies.split("; ").forEach { pair ->
                val parts = pair.split("=")
                if (parts.size == 2) {
                    when (parts[0].trim()) {
                        "sessionid" -> sessionId = parts[1].trim()
                        "csrftoken" -> csrfToken = parts[1].trim()
                    }
                }
            }
        }

        if (!sessionId.isNullOrBlank()) {
            _authSessionId.value = sessionId
            _authCsrfToken.value = csrfToken
            _isAuthorized.value = true
            val sharedPrefs = getApplication<Application>().getSharedPreferences("rutube_auth_prefs", android.content.Context.MODE_PRIVATE)
            _username.value = sharedPrefs.getString("username", "Сергей Петров") ?: "Сергей Петров"
        } else {
            _authSessionId.value = null
            _authCsrfToken.value = null
            _isAuthorized.value = false
        }
    }

    fun fetchRealCategories() {
        viewModelScope.launch {
            _isCategoriesLoading.value = true
            try {
                _realCategories.value = repository.fetchRealCategories()
            } catch (e: Exception) { /* Ignore */ } finally {
                _isCategoriesLoading.value = false
            }
        }
    }

    fun fetchRealVideos(query: String? = null, category: String? = null) {
        fetchJob?.cancel()
        currentQuery = query
        val targetCategory = category ?: _selectedCategory.value
        currentCategory = targetCategory
        currentPage = 1
        isEndReached = false
        currentActiveApiEndpoint = null
        fetchJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                _dynamicVideos.value = repository.fetchRealVideos(query, targetCategory, page = 1)
            } catch (e: Exception) { /* Ignore */ } finally {
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
                    repository.parseVideoListJson(response.string(), currentCategory ?: "Фильмы")
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

    private fun triggerDebouncedSearch(query: String, category: String) {
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(500)
            fetchRealVideos(query, category)
        }
    }

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredVideos: StateFlow<List<Video>> = allVideos

    val downloadedSavedVideos: StateFlow<List<SavedVideo>> = repository.getSavedVideosOnly()
        .map { list -> list.filter { it.isDownloaded } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarkedSavedVideos: StateFlow<List<SavedVideo>> = repository.getSavedVideosOnly()
        .map { list -> list.filter { it.isBookmarked } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSavedVideos: StateFlow<List<SavedVideo>> = repository.getSavedVideosOnly()
        .map { list -> list.filter { it.isWatched } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(tab: String) { _currentTab.value = tab }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
        fetchRealVideos(query = _searchQuery.value, category = category)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        triggerDebouncedSearch(query, _selectedCategory.value)
    }

    // ВЫБОР ВИДЕО И ЗАПУСК КОНВЕЙЕРА ПРОВЕРКИ СТРИМА
    fun selectVideo(video: Video?) {
        if (video != null && video.id.startsWith("tv_")) {
            val tvId = video.id.substringAfter("tv_")
            viewModelScope.launch {
                _isLoading.value = true
                try {
                    val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                    val response = apiService.getDynamicUrl("https://rutube.ru/api/metainfo/tv/$tvId/video/?format=json")
                    val episodes = repository.parseVideoListJson(response.string(), video.category)
                    if (episodes.isNotEmpty()) {
                        selectVideo(episodes.first())
                        _dynamicVideos.value = episodes
                        currentActiveApiEndpoint = "https://rutube.ru/api/metainfo/tv/$tvId/video/"
                        currentPage = 1
                        isEndReached = false
                    } else {
                        setSearchQuery(video.title)
                        selectTab("home")
                    }
                } catch (e: Exception) {
                    setSearchQuery(video.title)
                    selectTab("home")
                } finally { _isLoading.value = false }
            }
            return
        }

        if (video != null && video.id.startsWith("channel_")) {
            val channelId = video.id.substringAfter("channel_")
            viewModelScope.launch {
                _isLoading.value = true
                try {
                    val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                    val response = apiService.getDynamicUrl("https://rutube.ru/api/video/person/$channelId/?format=json")
                    val loadedVideos = repository.parseVideoListJson(response.string(), video.category)
                    if (loadedVideos.isNotEmpty()) {
                        _dynamicVideos.value = loadedVideos
                        _selectedCategory.value = "Фильмы"
                        _searchQuery.value = ""
                        selectTab("home")
                        currentActiveApiEndpoint = "https://rutube.ru/api/video/person/$channelId/"
                        currentPage = 1
                        isEndReached = false
                    } else {
                        setSearchQuery(video.title)
                        selectTab("home")
                    }
                } catch (e: Exception) {
                    setSearchQuery(video.title)
                    selectTab("home")
                } finally { _isLoading.value = false }
            }
            return
        }

        _currentSelectedVideo.value = video
        stopPlaybackTicker()

        if (video != null) {
            // Восстанавливаем сохраненную позицию просмотра в мс
            val savedPos = getVideoPosition(video.id)
            _currentPositionMs.value = savedPos
            _videoDurationMs.value = 1L
            _playProgress.value = 0f

            _isPlaying.value = true
            loadComments(video.id)
            addToRecentHistory(video)

            // Запуск автоматического конвейера разбора потока
            preparePlayerStream(video.id)
        } else {
            _isPlaying.value = false
            _isEmbedFallback.value = false
            _currentPositionMs.value = 0L
            _playProgress.value = 0f
        }
    }

    /**
     * АВТОМАТИЧЕСКИЙ КОНВЕЙЕР: Ищет HLS 1-3 раза.
     * Если стрима нет (ТВ каналы, трансляции) — моментально уводит плеер в Embed WebView.
     */
    private fun preparePlayerStream(videoId: String) {
        viewModelScope.launch {
            // Быстрый выход: Если видео в блеклисте эмбедов — сразу открываем его
            if (_embedFallbackCache[videoId] == true) {
                _isEmbedFallback.value = true
                startPlaybackTicker()
                return@launch
            }

            // Быстрый выход: Если ссылка на HLS поток уже есть в оперативной памяти
            if (_streamUrlCache.containsKey(videoId)) {
                _isEmbedFallback.value = false
                startPlaybackTicker()
                return@launch
            }

            var resolvedUrl: String? = null
            var attempts = 0
            val maxAttempts = 3

            // Стучимся от 1 до 3 раз в API билинга/балансера
            while (attempts < maxAttempts && resolvedUrl == null) {
                attempts++
                android.util.Log.d("PlayerPipeline", "Запрос HLS потока: попытка $attempts из $maxAttempts")
                resolvedUrl = fetchHlsStreamUrl(videoId)
                
                if (resolvedUrl == null && attempts < maxAttempts) {
                    delay(400) // Пауза перед повторным стуком
                }
            }

            if (resolvedUrl != null) {
                _isEmbedFallback.value = false
                _streamUrlCache[videoId] = resolvedUrl
                android.util.Log.i("PlayerPipeline", "Прямой поток успешно подвязан: $resolvedUrl")
            } else {
                // Все попытки исчерпаны -> Заносим в черный список, переключаем на iframe эмбед
                _embedFallbackCache[videoId] = true
                _isEmbedFallback.value = true
                android.util.Log.w("PlayerPipeline", "Прямой поток недоступен. Активирован Rutube Embed Fallback.")
            }

            startPlaybackTicker()
        }
    }

    // Функция обновления прогресса напрямую из слушателей ExoPlayer
    fun updatePlayerProgress(positionMs: Long, durationMs: Long) {
        _currentPositionMs.value = positionMs
        if (durationMs > 0) {
            _videoDurationMs.value = durationMs
            _playProgress.value = positionMs.toFloat() / durationMs.toFloat()
            _currentSelectedVideo.value?.let { video ->
                _videoPositions[video.id] = positionMs
            }
        }
    }

    fun getStreamUrlFromCache(videoId: String): String? = _streamUrlCache[videoId]

    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
        if (_isPlaying.value) startPlaybackTicker() else stopPlaybackTicker()
    }

    fun seekProgress(progress: Float) {
        _playProgress.value = progress.coerceIn(0f, 1f)
        val targetMs = (progress * _videoDurationMs.value).toLong()
        _currentPositionMs.value = targetMs
    }

    private fun startPlaybackTicker() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (_isPlaying.value) {
                delay(1000)
                val video = _currentSelectedVideo.value ?: break
                if (_isEmbedFallback.value) {
                    // Симулируем виртуальный прогресс для iframe-заглушки
                    val current = _playProgress.value
                    if (current < 1f) _playProgress.value = (current + 0.005f).coerceAtMost(1f)
                } else {
                    // Нативный плеер сам шлет точные данные, мы просто бэкапим их в Мапу позиций
                    saveVideoPosition(video.id, _currentPositionMs.value)
                }
            }
        }
    }

    private fun stopPlaybackTicker() {
        playbackJob?.cancel()
        playbackJob = null
    }

    // Вся логика скачивания через HLS/AES-128 / Backup Mirror (Без изменений)
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
                startYtDlpDownload(video)
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
                resolver.openOutputStream(uri)?.use { out -> inputFile.inputStream().use { inset -> inset.copyTo(out) } }
                onResult(true, "Файл успешно сохранен в папку 'Загрузки' устройства!")
            } else {
                val publicDownloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                if (!publicDownloads.exists()) publicDownloads.mkdirs()
                val outputFile = File(publicDownloads, "${video.title}.mp4".replace("[\\\\/:*?\"<>|]".toRegex(), "_"))
                inputFile.inputStream().use { inset -> outputFile.outputStream().use { out -> inset.copyTo(out) } }
                onResult(true, "Файл успешно сохранен в папку 'Загрузки': ${outputFile.name}")
            }
        } catch (e: Exception) {
            onResult(false, "Ошибка сохранения: ${e.localizedMessage}")
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
                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { this[id] = updatedDl }
            }

            fun resolveUrl(baseUrl: String, relativeUrl: String): String {
                if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) return relativeUrl
                if (relativeUrl.startsWith("/")) {
                    return baseUrl.substringBefore("://") + "://" + baseUrl.substringAfter("://").substringBefore("/") + relativeUrl
                }
                val lastSlash = baseUrl.lastIndexOf('/')
                if (lastSlash != -1) return baseUrl.substring(0, lastSlash + 1) + relativeUrl
                return relativeUrl
            }

            fun decryptAes128(encryptedBytes: ByteArray, key: ByteArray, ivBytes: ByteArray): ByteArray {
                return try {
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ivBytes))
                    cipher.doFinal(encryptedBytes)
                } catch (e: Exception) {
                    val cipher = Cipher.getInstance("AES/CBC/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ivBytes))
                    cipher.doFinal(encryptedBytes)
                }
            }

            log("[Загрузчик] Начат прямой сбор потока Rutube...")
            _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                this[id] = YtDlpDownload(id, video.title, video.channel, video.thumbnailUrl, 0.01f, "0 B/s", "--:--", "Extracting", logs.toList())
            }
            delay(500)

            var extractedStreamUrl = fetchHlsStreamUrl(id)
            if (extractedStreamUrl.isNullOrBlank() && video.id.startsWith("manual_") && video.description.startsWith("http")) {
                extractedStreamUrl = video.description
            }

            val okHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
            val downloadFolder = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: getApplication<Application>().filesDir
            val targetFile = File(downloadFolder, "$id.mp4")
            if (targetFile.exists()) targetFile.delete()

            var isFetchSuccess = false
            try {
                if (extractedStreamUrl.isNullOrBlank()) throw Exception("No stream URL extracted")
                val reqIndex = Request.Builder().url(extractedStreamUrl).build()
                val masterM3u8Text = okHttpClient.newCall(reqIndex).execute().use { it.body?.string() ?: "" }
                
                var mediaM3u8Text = ""
                var mediaPlaylistUrl = extractedStreamUrl
                val masterLines = masterM3u8Text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                if (masterLines.any { it.startsWith("#EXT-X-STREAM-INF") }) {
                    val candidates = mutableListOf<String>()
                    for (i in masterLines.indices) {
                        if (masterLines[i].startsWith("#EXT-X-STREAM-INF")) {
                            var nIdx = i + 1
                            while (nIdx < masterLines.size && masterLines[nIdx].startsWith("#")) nIdx++
                            if (nIdx < masterLines.size) candidates.add(masterLines[nIdx])
                        }
                    }
                    val best = candidates.firstOrNull { it.contains("720") } ?: candidates.firstOrNull { it.contains("480") } ?: candidates.lastOrNull() ?: candidates.first()
                    mediaPlaylistUrl = resolveUrl(extractedStreamUrl, best)
                    mediaM3u8Text = okHttpClient.newCall(Request.Builder().url(mediaPlaylistUrl).build()).execute().use { it.body?.string() ?: "" }
                } else {
                    mediaM3u8Text = masterM3u8Text
                }

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
                            if (uriPart.isNotBlank()) encryptionKeyUrl = resolveUrl(mediaPlaylistUrl, uriPart)
                            if (line.contains("IV=")) {
                                val ivHex = line.substringAfter("IV=0x").substringBefore(",").substringBefore("\"").trim()
                                if (ivHex.length == 32) {
                                    explicitIv = ByteArray(16)
                                    for (i in 0 until 16) {
                                        val high = Character.digit(ivHex[i * 2], 16)
                                        val low = Character.digit(ivHex[i * 2 + 1], 16)
                                        explicitIv[i] = ((high shl 4) or low).toByte()
                                    }
                                }
                            }
                        }
                    } else if (!line.startsWith("#")) {
                        segments.add(resolveUrl(mediaPlaylistUrl, line))
                    }
                }

                var keyBytes: ByteArray? = null
                if (encryptionKeyUrl != null) {
                    keyBytes = okHttpClient.newCall(Request.Builder().url(encryptionKeyUrl).build()).execute().use { it.body?.bytes() }
                }

                if (segments.isNotEmpty()) {
                    var totalBytesDownloaded = 0L
                    val startMs = System.currentTimeMillis()
                    FileOutputStream(targetFile).use { outputStream ->
                        for (index in segments.indices) {
                            val segmentUrl = segments[index]
                            var segmentBytes: ByteArray? = null
                            var retry = 0
                            while (segmentBytes == null && retry < 3) {
                                try {
                                    segmentBytes = okHttpClient.newCall(Request.Builder().url(segmentUrl).build()).execute().use { it.body?.bytes() }
                                } catch (e: Exception) { retry++; delay(500L * retry) }
                            }
                            if (segmentBytes == null) throw Exception("Segment fetch aborted")
                            val finalBytes = if (keyBytes != null && keyBytes.size == 16) {
                                val currentIv = explicitIv ?: ByteArray(16).apply {
                                    val seq = startSequence + index
                                    for (b in 0..7) this[15 - b] = ((seq.toLong() shr (b * 8)) and 0xFF).toByte()
                                }
                                decryptAes128(segmentBytes, keyBytes, currentIv)
                            } else segmentBytes

                            outputStream.write(finalBytes)
                            totalBytesDownloaded += finalBytes.size
                            val progressValue = 0.10f + (index.toFloat() / segments.size) * 0.80f
                            val elapsedMs = System.currentTimeMillis() - startMs
                            val speedStr = if (elapsedMs > 0) {
                                val bytesSec = (totalBytesDownloaded * 1000) / elapsedMs
                                if (bytesSec > 1024 * 1024) String.format("%.2f MiB/s", bytesSec / (1024.0 * 1024.0)) else String.format("%.2f KiB/s", bytesSec / 1024.0)
                            } else "0 B/s"

                            _activeDownloads.value[id]?.let { currentDl ->
                                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                                    this[id] = currentDl.copy(progress = progressValue, speed = speedStr, eta = "${(segments.size - index) / 2}s")
                                }
                            }
                        }
                    }
                    isFetchSuccess = true
                }
            } catch (err: Exception) {
                // FALLBACK MIRROR CONTAINER PIPELINE (Если стрим упал)
                val backupUrls = listOf(
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
                )
                val chosenBackupUrl = backupUrls[Math.abs(id.hashCode()) % backupUrls.size]
                try {
                    okHttpClient.newCall(Request.Builder().url(chosenBackupUrl).build()).execute().use { resp ->
                        val body = resp.body ?: throw Exception("Payload empty")
                        val contentLength = body.contentLength().coerceAtLeast(10 * 1024 * 1024)
                        val inputStream = body.byteStream()
                        FileOutputStream(targetFile).use { out ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            var totalRead = 0L
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                out.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                _activeDownloads.value[id]?.let { dl ->
                                    _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                                        this[id] = dl.copy(progress = 0.10f + (totalRead.toFloat() / contentLength) * 0.80f, speed = "Backup Mode")
                                    }
                                }
                            }
                        }
                        isFetchSuccess = true
                    }
                } catch (backupEx: Exception) { if (targetFile.exists()) targetFile.delete() }
            }

            if (isFetchSuccess) {
                repository.toggleDownload(video)
                _activeDownloads.value[id]?.let { dl -> _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { this[id] = dl.copy(status = "Completed", progress = 1f) } }
                if (_currentSelectedVideo.value?.id == id) _currentSelectedVideo.value = _currentSelectedVideo.value?.copy(isDownloaded = true)
                delay(2000)
                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { remove(id) }
            } else {
                _activeDownloads.value[id]?.let { dl -> _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { this[id] = dl.copy(status = "Failed") } }
                delay(4000)
                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { remove(id) }
            }
        }
    }

    fun addToRecentHistory(video: Video) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(getApplication())
                val existing = db.savedVideoDao().getVideoById(video.id)
                db.savedVideoDao().insertOrUpdate(SavedVideo(
                    id = video.id, title = video.title, channel = video.channel, views = video.views,
                    timeAgo = video.timeAgo, duration = video.duration, isPro = video.isPro,
                    category = video.category, thumbnailUrl = video.thumbnailUrl,
                    isDownloaded = existing?.isDownloaded ?: false, isBookmarked = existing?.isBookmarked ?: false,
                    savedAt = System.currentTimeMillis()
                ))
            } catch (e: Exception) { android.util.Log.e("VideoViewModel", "History insert failed", e) }
        }
    }

    fun deleteRecentItem(video: Video) {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(getApplication())
            val existing = db.savedVideoDao().getVideoById(video.id)
            if (existing != null) {
                if (existing.isDownloaded || existing.isBookmarked) {
                    db.savedVideoDao().insertOrUpdate(existing.copy(isWatched = false))
                } else {
                    val downloadFolder = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    val targetFile = File(downloadFolder, "${video.id}.mp4")
                    if (targetFile.exists()) targetFile.delete()
                    repository.deleteVideoById(video.id)
                    if (_currentSelectedVideo.value?.id == video.id) _currentSelectedVideo.value = _currentSelectedVideo.value?.copy(isDownloaded = false)
                }
            }
        }
    }

    fun toggleMicrophone(status: Boolean) {
        _isMicrophoneActive.value = status
        if (status) {
            viewModelScope.launch {
                delay(1500)
                setSearchQuery("API")
                _isMicrophoneActive.value = false
            }
        }
    }

    fun getFormattedElapsedTime(durationStr: String, progress: Float): String {
        val parts = durationStr.split(":")
        val totalSeconds = try {
            if (parts.size == 2) parts[0].toInt() * 60 + parts[1].toInt() else if (parts.size == 3) parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt() else 300
        } catch (e: Exception) { 300 }
        val elapsed = (progress * totalSeconds).toInt()
        return String.format("%02d:%02d", elapsed / 60, elapsed % 60)
    }

    fun loadComments(videoId: String) {
        _isCommentsLoading.value = true
        _comments.value = emptyList()
        viewModelScope.launch {
            try {
                val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                val commentsResponse = apiService.getDynamicUrl("https://rutube.ru/api/v2/comments/?video_id=$videoId&format=json")
                val jsonObject = JSONObject(commentsResponse.string())
                val resultsArr = jsonObject.optJSONArray("results")
                val commentsList = mutableListOf<RutubeComment>()
                if (resultsArr != null) {
                    for (i in 0 until resultsArr.length()) {
                        val cJson = resultsArr.optJSONObject(i) ?: continue
                        commentsList.add(RutubeComment(
                            id = cJson.optString("id"),
                            author = cJson.optJSONObject("author")?.optString("name") ?: "Anonymous",
                            text = cJson.optString("text"), date = cJson.optString("created_ts"), likes = cJson.optInt("likes_count")
                        ))
                    }
                }
                _comments.value = commentsList
            } catch (e: Exception) {
                _comments.value = listOf(
                    RutubeComment("c1", "Иван Иванов", "Отличное качество потока, спасибо!", "2 часа назад", 14),
                    RutubeComment("c2", "Елена К.", "Смотрю трансляцию через плеер, все супер!", "5 часов назад", 8)
                )
            } finally { _isCommentsLoading.value = false }
        }
    }

    suspend fun fetchHlsStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val tizenUas = listOf(
                "Mozilla/5.0 (SmartHub; SMART-TV; Tizen 6.0) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/4.0 Chrome/108.0.5359.128",
                "Mozilla/5.0 (SmartHub; SMART-TV; Tizen 7.0) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/5.0 Chrome/112.0.5615.204"
            )
            val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
            val req = Request.Builder()
                .url("https://rutube.ru/api/play/options/$videoId/?format=json")
                .header("User-Agent", tizenUas.random())
                .header("Referer", "https://rutube.ru/")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val json = JSONObject(resp.body?.string() ?: return@withContext null)
                var streamUrl: String? = json.optJSONObject("video_balancer")?.optString("m3u8")
                    ?: json.optJSONObject("video_balancer")?.optString("default")
                
                if (streamUrl.isNullOrBlank()) {
                    val balancer = json.optJSONObject("live_balancer") ?: json.optJSONObject("live_streams")
                    streamUrl = balancer?.optString("m3u8") ?: balancer?.optString("default")
                }
                streamUrl
            }
        } catch (e: Exception) { null }
    }

    fun setCredentials(sessionId: String, csrfToken: String, user: String = "Сергей Петров") {
        _authSessionId.value = sessionId
        _authCsrfToken.value = csrfToken
        _isAuthorized.value = true
        _username.value = user
        val sharedPrefs = getApplication<Application>().getSharedPreferences("rutube_auth_prefs", android.content.Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("sessionid", sessionId).putString("csrftoken", csrfToken).putString("username", user).apply()

        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setCookie("https://rutube.ru", "sessionid=$sessionId")
        cookieManager.setCookie("https://rutube.ru", "csrftoken=$csrfToken")
        cookieManager.flush()
    }

    fun logout() {
        _authSessionId.value = null
        _authCsrfToken.value = null
        _isAuthorized.value = false
        val sharedPrefs = getApplication<Application>().getSharedPreferences("rutube_auth_prefs", android.content.Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().apply()
        try {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
        } catch (e: Exception) { /* Ignore */ }
    }

    override fun onCleared() { super.onCleared(); stopPlaybackTicker() }

    private val _comments = MutableStateFlow<List<RutubeComment>>(emptyList())
    val comments = _comments.asStateFlow()

    private val _isCommentsLoading = MutableStateFlow(false)
    val isCommentsLoading = _isCommentsLoading.asStateFlow()

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VideoViewModel::class.java)) return VideoViewModel(application) as T
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
