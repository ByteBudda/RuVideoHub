package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray

/**
 * VideoRepository — универсальный репозиторий с AI-парсером
 *
 * Особенности:
 *  - Автоопределение типа ответа по URL + структуре JSON
 *  - Самообучающийся кэш сопоставлений полей (endpointHint)
 *  - Универсальный parseAnyResponse() для ЛЮБОГО эндпоинта Rutube
 *  - Graceful degradation: если API поменял поля — парсер адаптируется через эвристики
 *  - Рекурсивный поиск карточек на любой глубине вложенности
 */
class VideoRepository(private val dao: SavedVideoDao) {

    @Volatile
    var lastFetchSource: String = "Инициализация"
        private set

    val dynamicCategoryTargets = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val queryCache = java.util.concurrent.ConcurrentHashMap<String, List<Video>>()
    private val queryCacheTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var cachedCategories: List<RutubeCategory>? = null
    private var categoriesCacheTimestamp: Long = 0L

    private val categoriesLoadedMutex = Mutex()
    private var categoriesLoaded = false

    private val defaultCategories = listOf(
        RutubeCategory(1001, "Фильмы", "https://pic.rtbcdn.ru/promoitem/55/c1/55c106e53da7e36f144347990cb39885.png", "/api/feeds/movies/"),
        RutubeCategory(1002, "Сериалы", "https://pic.rtbcdn.ru/promoitem/55/c1/55c106e53da7e36f144347990cb39885.png", "/api/feeds/serials/"),
        RutubeCategory(1003, "Телепередачи", "https://pic.rtbcdn.ru/promoitem/11/4b/114b0e5e339a889c31ee106e9b986c53.png", "/api/feeds/tv/"),
        RutubeCategory(2001, "Онлайн ТВ", "https://pic.rtbcdn.ru/promoitem/3f/e6/3fe614a9cfa0e1c2f25d71102558109d.png", "/api/feeds/tvchannels/"),
        RutubeCategory(2002, "Развлекательные", "https://pic.rtbcdn.ru/promoitem/12/0a/120a7630bcb1ca858abd529d474fc35d.png", "/api/feeds/entertainment/"),
        RutubeCategory(2003, "Реалити", "https://pic.rtbcdn.ru/promoitem/12/0a/120a7630bcb1ca858abd529d474fc35d.png", "/api/feeds/reality/"),
        RutubeCategory(2004, "Ток-шоу", "https://pic.rtbcdn.ru/promoitem/11/4b/114b0e5e339a889c31ee106e9b986c53.png", "/api/feeds/talk-show/"),
        RutubeCategory(1004, "Мультфильмы", "https://pic.rtbcdn.ru/promoitem/2025-06-06/64/73/64734dadd906cefa63183b96cd260e1b.png", "/api/feeds/cartoons/"),
        RutubeCategory(1005, "Music", "https://pic.rtbcdn.ru/promoitem/8c/49/8c49dbbe473765f4c881090860ddee01.png", "/api/feeds/music/"),
        RutubeCategory(1006, "Спорт", "https://pic.rtbcdn.ru/promoitem/dc/04/dc049d8eb246cec4eeb63c615d302bd4.png", "/api/feeds/sport/"),
        RutubeCategory(1007, "Юмор", "https://pic.rtbcdn.ru/promoitem/12/0a/120a7630bcb1ca858abd529d474fc35d.png", "/api/feeds/umor/"),
        RutubeCategory(1008, "Видеоигры", "https://pic.rtbcdn.ru/promoitem/8b/57/8b57e8c2550b1a269b028f6567bbffe6.png", "/api/feeds/games/"),
        RutubeCategory(1009, "Технологии", "https://pic.rtbcdn.ru/promoitem/2025-03-19/a3/c6/a3c653afccb951f5a62bb80c65319a2d.png", "/api/feeds/technologies/"),
        RutubeCategory(1010, "Блоги", "https://pic.rtbcdn.ru/promoitem/ae/62/ae62f83e8cb442661a825819fdf61d8c.png", "/api/feeds/blogs/"),
        RutubeCategory(1011, "Новости", "https://pic.rtbcdn.ru/promoitem/3f/e6/3fe614a9cfa0e1c2f25d71102558109d.png", "/api/feeds/news/"),
        RutubeCategory(1012, "Лайфхаки", "https://pic.rtbcdn.ru/promoitem/2025-03-19/a3/c6/a3c653afccb951f5a62bb80c65319a2d.png", "/api/feeds/lifehacks/"),
        RutubeCategory(1013, "Детям", "https://pic.rtbcdn.ru/promoitem/eb/a3/eba3273e49348d87f585dea09b68327e.png", "/api/feeds/kids/"),
        RutubeCategory(1014, "Авто-мото", "https://pic.rtbcdn.ru/promoitem/d5/e9/d5e9d7e180130a06705106f5f12cbf0f.png", "/api/feeds/auto/"),
        RutubeCategory(1015, "Обучение", "https://pic.rtbcdn.ru/promoitem/15/8d/158d95d4cb03f4187226734c611cfbbe.png", "/api/feeds/education/"),
        RutubeCategory(1016, "Путешествия", "https://pic.rtbcdn.ru/promoitem/24/95/2495ff72ab1d9a70411f2ee8ef6c3b5f.png", "/api/feeds/travel/"),
        RutubeCategory(1017, "Кулинария", "https://pic.rtbcdn.ru/promoitem/02/50/0250d5124179a913e26eb8965f48228e.png", "/api/feeds/food/"),
        RutubeCategory(1018, "Аниме", "https://pic.rtbcdn.ru/promoitem/1d/59/1d59b21c708d89744b20d9f220a47b1a.png", "/api/feeds/anime/")
    )

    init {
        for (defaultCat in defaultCategories) {
            val slug = defaultCat.target
                ?.replace("/api/feeds/", "")
                ?.replace("/feeds/", "")
                ?.replace("/api/v1/feeds/", "")
                ?.trim('/') ?: ""
            if (slug.isNotBlank()) {
                dynamicCategoryTargets[defaultCat.title] = slug
            }
        }
    }

    // ==================== UNIVERSAL PARSER ENTRY ====================

    /**
     * УНИВЕРСАЛЬНЫЙ метод парсинга ЛЮБОГО ответа от Rutube.
     * Не важно, какой эндпоинт — парсер сам поймёт структуру.
     */
    fun parseAnyResponse(bodyString: String, defaultCategoryName: String, url: String? = null): List<Video> {
        val mapped = mutableListOf<Video>()
        val trimmed = bodyString.trim()

        if (!trimmed.startsWith("{")) {
            android.util.Log.w("VideoRepository", "Non-JSON response from $url — likely blocked or HTML error")
            return mapped
        }

        try {
            val jsonObj = JSONObject(trimmed)
            // Используем парсер с endpointHint для самообучения
            val parsed = com.example.data.rutube.SmartRutubeParser.ResponseAnalyzer.parse(jsonObj, url)

            android.util.Log.d("VideoRepository", "Parsed ${parsed.items.size} items from $url (type=${parsed.type})")

            for (card in parsed.items) {
                val video = mapNormalizedCardToVideo(card, defaultCategoryName)
                if (!isBlockedContent(video)) {
                    mapped.add(video)
                }
            }

            for (card in parsed.relatedPersons) {
                val video = mapNormalizedCardToVideo(card, defaultCategoryName)
                if (!isBlockedContent(video)) {
                    mapped.add(video)
                }
            }
        } catch (ex: Exception) {
            android.util.Log.e("VideoRepository", "Error parsing $url", ex)
        }
        return mapped
    }

    /**
     * Проверка на блокируемый контент (платные партнёры)
     */
    private fun isBlockedContent(video: Video): Boolean {
        val checkText = (video.title + " " + video.channel + " " + video.description + " " + video.category).lowercase()
        return checkText.contains("premier") ||
               checkText.contains("start") ||
               checkText.contains("viju") ||
               checkText.contains("премьер") ||
               checkText.contains("вижу")
    }

    // ==================== MAPPING ====================

    private fun mapNormalizedCardToVideo(
        card: com.example.data.rutube.SmartRutubeParser.NormalizedCard,
        defaultCategoryName: String
    ): Video {
        return when (card) {
            is com.example.data.rutube.SmartRutubeParser.NormalizedCard.VideoCard -> {
                Video(
                    id = card.id,
                    title = card.title,
                    channel = card.channelName,
                    views = card.views,
                    timeAgo = card.published,
                    duration = card.duration,
                    isPro = card.isPaid || card.requiresSubscription,
                    category = defaultCategoryName,
                    description = card.description,
                    thumbnailUrl = card.thumbnail,
                    authorId = card.channelId,
                    authorActionUrl = card.channelId?.let { "https://rutube.ru/api/video/person/$it/" },
                    authorAvatarUrl = card.channelAvatar
                )
            }
            is com.example.data.rutube.SmartRutubeParser.NormalizedCard.TvSeriesCard -> {
                val ratingStr = if (card.rating != null && card.rating > 0.05) " • Кинопоиск: ${card.rating}" else ""
                val yearVal = card.year ?: "Передача"
                Video(
                    id = "tv_${card.id}__${card.actionUrl ?: ""}",
                    title = card.title,
                    channel = "Шоу • $yearVal$ratingStr",
                    views = "${card.seasonsCount} сезонов",
                    timeAgo = "Смотреть выпуски",
                    duration = "СЕРИАЛ",
                    isPro = card.isPaid || card.requiresSubscription,
                    category = defaultCategoryName,
                    description = card.description ?: "Смотрите оригинальные сезоны и выпуски бесплатно.",
                    thumbnailUrl = card.poster
                )
            }
            is com.example.data.rutube.SmartRutubeParser.NormalizedCard.PlaylistCard -> {
                Video(
                    id = "playlist_${card.id}__${card.actionUrl ?: ""}",
                    title = card.title,
                    channel = "Плейлист • Подборка",
                    views = "${card.videosCount} видео",
                    timeAgo = "Смотреть плейлист",
                    duration = "ПЛЕЙЛИСТ",
                    isPro = false,
                    category = defaultCategoryName,
                    description = "Смотрите полную подборку видео из этого плейлиста.",
                    thumbnailUrl = card.thumbnail
                )
            }
            is com.example.data.rutube.SmartRutubeParser.NormalizedCard.ChannelCard -> {
                Video(
                    id = "channel_${card.id}__${card.actionUrl ?: ""}",
                    title = card.name,
                    channel = "Авторский канал • ${card.subscribers} подписчиков",
                    views = "${card.subscribers} подписчиков",
                    timeAgo = "${card.videosCount} видео",
                    duration = "КАНАЛ",
                    isPro = false,
                    category = defaultCategoryName,
                    description = card.description ?: "",
                    thumbnailUrl = card.avatar,
                    authorId = card.id,
                    authorAvatarUrl = card.avatar
                )
            }
            is com.example.data.rutube.SmartRutubeParser.NormalizedCard.PromoCard -> {
                Video(
                    id = "promo_${card.id}__${card.actionUrl ?: ""}",
                    title = card.title,
                    channel = "Реклама",
                    views = "Промо",
                    timeAgo = "Перейти по ссылке",
                    duration = "ПРОМО",
                    isPro = true,
                    category = defaultCategoryName,
                    description = card.description ?: "Спонсорский медиаконтент.",
                    thumbnailUrl = card.thumbnail
                )
            }
            is com.example.data.rutube.SmartRutubeParser.NormalizedCard.UnknownCard -> {
                Video(
                    id = "unknown_${card.id}__${card.actionUrl ?: ""}",
                    title = card.title,
                    channel = card.rawType ?: "Раздел каталога",
                    views = "Коллекция",
                    timeAgo = "Открыть раздел",
                    duration = "КАТАЛОГ",
                    isPro = false,
                    category = defaultCategoryName,
                    description = "Элемент каталога • Нажмите для открытия",
                    thumbnailUrl = card.thumbnail
                )
            }
        }
    }

    // ==================== FLOW & DB ====================

    fun mapSavedVideoToVideo(saved: SavedVideo): Video {
        return Video(
            id = saved.id,
            title = saved.title,
            channel = saved.channel,
            views = saved.views,
            timeAgo = saved.timeAgo,
            duration = saved.duration,
            isPro = saved.isPro,
            category = saved.category,
            description = "Офлайн-просмотр сохраненного видео",
            thumbnailUrl = saved.thumbnailUrl,
            isDownloaded = saved.isDownloaded,
            isBookmarked = saved.isBookmarked
        )
    }

    fun getVideosFlow(): Flow<List<Video>> {
        return dao.getAllSavedVideos().map { savedList ->
            savedList.map { mapSavedVideoToVideo(it) }
        }
    }

    // ==================== NETWORK FETCHING ====================

    suspend fun fetchRealVideos(
        query: String?,
        category: String?,
        page: Int = 1,
        forceRefresh: Boolean = false
    ): List<Video> = withContext(Dispatchers.IO) {
        ensureCategoriesLoaded()

        val cacheKey = "q=${query.orEmpty()}&cat=${category.orEmpty()}&p=$page"
        val now = System.currentTimeMillis()

        if (!forceRefresh) {
            val cached = queryCache[cacheKey]
            val cachedTime = queryCacheTimestamps[cacheKey] ?: 0L
            if (cached != null && cached.isNotEmpty() && (now - cachedTime < 3600000L)) {
                lastFetchSource = "Rutube LIVE (микрокэш)"
                return@withContext cached
            }
        }

        val result = fetchRealVideosSuspend(query, category, page)
        if (result.isNotEmpty() && lastFetchSource == "Rutube LIVE") {
            queryCache[cacheKey] = result
            queryCacheTimestamps[cacheKey] = now
        }
        result
    }

    private suspend fun fetchRealVideosSuspend(query: String?, category: String?, page: Int = 1): List<Video> {
        var isNetworkError = false
        val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
        val q = query?.trim() ?: ""
        val selectedCategoryName = category ?: "Фильмы"

        try {
            if (q.isNotEmpty()) {
                return fetchSearchResults(q, page, selectedCategoryName)
            } else {
                return fetchCategoryFeed(selectedCategoryName, page)
            }
        } catch (ioEx: java.io.IOException) {
            isNetworkError = true
            android.util.Log.e("VideoRepository", "Primary network failure", ioEx)
        } catch (e: Exception) {
            android.util.Log.e("VideoRepository", "Rutube general error", e)
        }

        // Fallback к локальной БД только при реальной сетевой ошибке
        if (isNetworkError) {
            lastFetchSource = "Локальный оффлайн"
            return fallbackToLocal(query, category)
        }

        return emptyList()
    }

    // ==================== SEARCH ====================

    private suspend fun fetchSearchResults(
        q: String,
        page: Int,
        categoryName: String
    ): List<Video> {
        var isNetworkError = false
        val resultsList = mutableListOf<Video>()

        // 1. Combined search (новый API)
        try {
            val responseBody = com.example.data.rutube.RutubeRetrofitClient.apiService.searchCombined(q, page = page)
            val bodyString = responseBody.string()
            val url = "https://rutube.ru/api/search/combined/cards/list/?query=$q&page=$page"
            resultsList.addAll(parseAnyResponse(bodyString, "Поиск: $q", url))
        } catch (ioEx: java.io.IOException) {
            isNetworkError = true
            android.util.Log.e("VideoRepository", "Network error combined search", ioEx)
        } catch (e: Exception) {
            android.util.Log.e("VideoRepository", "Combined search error", e)
        }

        // 2. Fallback: legacy video search
        if (resultsList.isEmpty() && !isNetworkError) {
            try {
                val responseBody = com.example.data.rutube.RutubeRetrofitClient.apiService.searchVideos(q, page = page)
                val bodyString = responseBody.string()
                val url = "https://rutube.ru/api/search/video/?query=$q&page=$page"
                resultsList.addAll(parseAnyResponse(bodyString, "Поиск: $q", url))
            } catch (ioEx: java.io.IOException) {
                isNetworkError = true
                android.util.Log.e("VideoRepository", "Network error video search", ioEx)
            } catch (e: Exception) {
                android.util.Log.e("VideoRepository", "Video search fallback error", e)
            }
        }

        // 3. Fallback: channel/person search
        if (resultsList.isEmpty() && !isNetworkError) {
            try {
                val encodedQ = java.net.URLEncoder.encode(q, "UTF-8")
                var url = "https://rutube.ru/api/search/channel/?query=$encodedQ&page=$page&format=json"
                var bodyString = ""
                try {
                    bodyString = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(url).string()
                } catch (e: Exception) {
                    url = "https://rutube.ru/api/search/person/?query=$encodedQ&page=$page&format=json"
                    bodyString = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(url).string()
                }
                val parsed = parseAnyResponse(bodyString, "Поиск: $q", url)
                val channelsOnly = parsed.filter { it.id.startsWith("channel_") }
                resultsList.addAll(0, channelsOnly)
            } catch (ioEx: java.io.IOException) {
                isNetworkError = true
                android.util.Log.e("VideoRepository", "Network error channel search", ioEx)
            } catch (e: Exception) {
                android.util.Log.e("VideoRepository", "Channel search fallback error", e)
            }
        }

        if (resultsList.isNotEmpty()) {
            lastFetchSource = "Rutube LIVE"
            return resultsList.distinctBy { it.id }
        }

        if (!isNetworkError) {
            lastFetchSource = "Rutube LIVE (Пусто)"
        }
        return emptyList()
    }

    // ==================== CATEGORY FEED ====================

    private suspend fun fetchCategoryFeed(
        selectedCategoryName: String,
        page: Int
    ): List<Video> {
        var isNetworkError = false
        val categorySlug = dynamicCategoryTargets[selectedCategoryName]

        if (categorySlug != null) {
            // Стратегия: загружаем showcase feed, находим ресурсы табов, грузим их
            try {
                val showcaseUrl = "https://rutube.ru/api/feeds/$categorySlug/?format=json&page=$page"
                val feedResponse = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(showcaseUrl)
                val feedJsonStr = feedResponse.string()
                val feedObj = JSONObject(feedJsonStr)
                val parsedFeed = com.example.data.rutube.SmartRutubeParser.ResponseAnalyzer.parse(feedObj, showcaseUrl)

                val resourceUrls = mutableListOf<String>()
                for (tab in parsedFeed.tabs) {
                    for (res in tab.resources) {
                        val urlVal = res.url
                        if (urlVal != null && urlVal.isNotBlank() && !resourceUrls.contains(urlVal)) {
                            resourceUrls.add(urlVal)
                        }
                    }
                }

                val parsedResults = mutableListOf<Video>()
                val limit = minOf(resourceUrls.size, 3)
                for (idx in 0 until limit) {
                    val endpoint = resourceUrls[idx]
                    val targetUrl = if (endpoint.startsWith("/")) "https://rutube.ru$endpoint" else endpoint
                    val paginatedUrl = if (targetUrl.contains("?")) "$targetUrl&page=$page" else "$targetUrl?page=$page"
                    try {
                        val resourceBody = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(paginatedUrl).string()
                        parsedResults.addAll(parseAnyResponse(resourceBody, selectedCategoryName, paginatedUrl))
                    } catch (ioEx: java.io.IOException) {
                        isNetworkError = true
                        android.util.Log.e("VideoRepository", "Network error tab resource $paginatedUrl", ioEx)
                    } catch (e: Exception) {
                        android.util.Log.e("VideoRepository", "Tab resource error $paginatedUrl", e)
                    }
                }

                if (parsedResults.isNotEmpty()) {
                    lastFetchSource = "Rutube LIVE"
                    return parsedResults.distinctBy { it.id }
                }
            } catch (ioEx: java.io.IOException) {
                isNetworkError = true
                android.util.Log.e("VideoRepository", "Network showcase error $categorySlug", ioEx)
            } catch (e: Exception) {
                android.util.Log.e("VideoRepository", "Showcase error $categorySlug", e)
            }

            // Fallback: search by category name
            if (!isNetworkError) {
                try {
                    val fallbackBody = com.example.data.rutube.RutubeRetrofitClient.apiService.searchVideos(selectedCategoryName, page = page).string()
                    val searchUrl = "https://rutube.ru/api/search/video/?query=${selectedCategoryName}&page=$page"
                    val searchVideos = parseAnyResponse(fallbackBody, selectedCategoryName, searchUrl)
                    if (searchVideos.isNotEmpty()) {
                        lastFetchSource = "Rutube LIVE"
                        return searchVideos
                    }
                } catch (ioEx: java.io.IOException) {
                    isNetworkError = true
                    android.util.Log.e("VideoRepository", "Network fallback search error", ioEx)
                } catch (e: Exception) {
                    android.util.Log.e("VideoRepository", "Fallback search error", e)
                }
            }

            if (!isNetworkError) {
                lastFetchSource = "Rutube LIVE (Пусто)"
                return emptyList()
            }
        }

        // Default: popular videos
        try {
            val popularBody = com.example.data.rutube.RutubeRetrofitClient.apiService.getPopularVideos(page = page).string()
            val popularUrl = "https://rutube.ru/api/video/popular/?page=$page"
            val results = parseAnyResponse(popularBody, "Популярное", popularUrl)
            if (results.isNotEmpty()) {
                lastFetchSource = "Rutube LIVE"
                return results
            }
        } catch (ioEx: java.io.IOException) {
            isNetworkError = true
            android.util.Log.e("VideoRepository", "Network popular videos error", ioEx)
        } catch (e: Exception) {
            android.util.Log.e("VideoRepository", "Popular videos error", e)
        }

        if (!isNetworkError) {
            lastFetchSource = "Rutube LIVE (Пусто)"
        }
        return emptyList()
    }

    // ==================== LOCAL FALLBACK ====================

    private suspend fun fallbackToLocal(query: String?, category: String?): List<Video> {
        return try {
            val savedList = dao.getAllSavedVideos().first()
            savedList.map { mapSavedVideoToVideo(it) }.filter { video ->
                val matchCat = category.isNullOrBlank() || video.category.equals(category, ignoreCase = true)
                val matchQuery = query.isNullOrBlank() ||
                        video.title.contains(query, ignoreCase = true) ||
                        video.channel.contains(query, ignoreCase = true)
                matchCat && matchQuery
            }
        } catch (dbEx: Exception) {
            android.util.Log.e("VideoRepository", "DB fallback error", dbEx)
            emptyList()
        }
    }

    // ==================== CATEGORIES ====================

    suspend fun ensureCategoriesLoaded() {
        withContext(Dispatchers.IO) {
            categoriesLoadedMutex.withLock {
                val now = System.currentTimeMillis()
                if (!categoriesLoaded || (now - categoriesCacheTimestamp >= 3600000L)) {
                    fetchRealCategories()
                    categoriesLoaded = true
                }
            }
        }
    }

    suspend fun fetchRealCategories(forceRefresh: Boolean = false): List<RutubeCategory> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedCategories != null && (now - categoriesCacheTimestamp < 3600000L)) {
            return@withContext cachedCategories!!
        }

        val categoriesList = mutableListOf<RutubeCategory>()
        categoriesList.addAll(defaultCategories)

        // Refresh slug mappings
        for (defaultCat in defaultCategories) {
            val slug = defaultCat.target
                ?.replace("/api/feeds/", "")
                ?.replace("/feeds/", "")
                ?.replace("/api/v1/feeds/", "")
                ?.trim('/') ?: ""
            if (slug.isNotBlank()) {
                dynamicCategoryTargets[defaultCat.title] = slug
            }
        }

        try {
            val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
            val url = "https://rutube.ru/api/v1/feeds/promogroup/382/?format=json&limit=100"
            val bodyStr = apiService.getDynamicUrl(url).string()
            val jsonObj = JSONObject(bodyStr)
            val parsed = com.example.data.rutube.SmartRutubeParser.ResponseAnalyzer.parse(jsonObj, url)

            for (card in parsed.items) {
                var title = ""
                var thumbnail = ""
                var actionUrl = ""
                val idVal = card.hashCode()

                when (card) {
                    is com.example.data.rutube.SmartRutubeParser.NormalizedCard.PromoCard -> {
                        title = card.title
                        thumbnail = card.thumbnail ?: ""
                        actionUrl = card.actionUrl ?: ""
                    }
                    is com.example.data.rutube.SmartRutubeParser.NormalizedCard.UnknownCard -> {
                        title = card.title
                        thumbnail = card.thumbnail ?: ""
                        actionUrl = card.actionUrl ?: ""
                    }
                    is com.example.data.rutube.SmartRutubeParser.NormalizedCard.TvSeriesCard -> {
                        title = card.title
                        thumbnail = card.poster ?: ""
                        actionUrl = card.actionUrl ?: ""
                    }
                    is com.example.data.rutube.SmartRutubeParser.NormalizedCard.PlaylistCard -> {
                        title = card.title
                        thumbnail = card.thumbnail ?: ""
                        actionUrl = card.actionUrl ?: ""
                    }
                    is com.example.data.rutube.SmartRutubeParser.NormalizedCard.VideoCard -> {
                        title = card.title
                        thumbnail = card.thumbnail ?: ""
                        actionUrl = card.actionUrl ?: "/feeds/video/"
                    }
                    else -> {}
                }

                val isBlockedCat = isBlockedText(title.lowercase()) || isBlockedText(actionUrl.lowercase())
                if (!isBlockedCat && title.isNotBlank() && categoriesList.none { it.title.equals(title, ignoreCase = true) }) {
                    categoriesList.add(RutubeCategory(idVal, title, thumbnail, actionUrl))
                    val slug = actionUrl.trim('/')
                        .replace("api/feeds/", "")
                        .replace("feeds/", "")
                        .replace("api/v1/feeds/", "")
                        .trim('/')
                    if (slug.isNotBlank()) {
                        dynamicCategoryTargets[title] = slug
                    }
                }
            }
        } catch (ex: Exception) {
            android.util.Log.e("VideoRepository", "Error fetching categories", ex)
        }

        cachedCategories = categoriesList
        categoriesCacheTimestamp = System.currentTimeMillis()
        categoriesList
    }

    // ==================== SAVED VIDEOS OPERATIONS ====================

    fun getSavedVideosOnly(): Flow<List<SavedVideo>> = dao.getAllSavedVideos()

    suspend fun toggleBookmark(video: Video) {
        val saved = dao.getVideoById(video.id)
        val termBookmark = !(saved?.isBookmarked ?: false)
        val termDownload = saved?.isDownloaded ?: false
        val termWatched = saved?.isWatched ?: false

        if (!termBookmark && !termDownload && !termWatched) {
            dao.deleteById(video.id)
        } else {
            dao.insertOrUpdate(SavedVideo(
                id = video.id, title = video.title, channel = video.channel,
                views = video.views, timeAgo = video.timeAgo, duration = video.duration,
                isPro = video.isPro, category = video.category,
                isDownloaded = termDownload, isBookmarked = termBookmark,
                thumbnailUrl = video.thumbnailUrl, isWatched = termWatched
            ))
        }
    }

    suspend fun toggleDownload(video: Video) {
        val saved = dao.getVideoById(video.id)
        val termDownload = !(saved?.isDownloaded ?: false)
        val termBookmark = saved?.isBookmarked ?: false
        val termWatched = saved?.isWatched ?: false

        if (!termBookmark && !termDownload && !termWatched) {
            dao.deleteById(video.id)
        } else {
            dao.insertOrUpdate(SavedVideo(
                id = video.id, title = video.title, channel = video.channel,
                views = video.views, timeAgo = video.timeAgo, duration = video.duration,
                isPro = video.isPro, category = video.category,
                isDownloaded = termDownload, isBookmarked = termBookmark,
                thumbnailUrl = video.thumbnailUrl, isWatched = termWatched
            ))
        }
    }

    suspend fun deleteVideoById(id: String) = dao.deleteById(id)

    // ==================== СОВМЕСТИМОСТЬ ====================

    fun parseVideoListJson(bodyString: String, defaultCategoryName: String, url: String? = null): List<Video> {
        return parseAnyResponse(bodyString, defaultCategoryName, url)
    }

    private fun isBlockedText(text: String): Boolean {
        return text.contains("premier") ||
               text.contains("start") ||
               text.contains("viju") ||
               text.contains("премьер") ||
               text.contains("вижу")
    }
}
