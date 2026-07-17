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
import com.example.data.rutube.parser.RutubeParser
import com.example.manager.RutubeCategoryManager

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

    private val rutubeParser = RutubeParser()
    private val categoryManager = RutubeCategoryManager()
    fun getCategorySlug(categoryName: String): String? {
        return categoryManager.dynamicCategoryTargets[categoryName]
    }

    fun findCategoryBySlug(slug: String): String? {
        return categoryManager.dynamicCategoryTargets.entries.find {
            it.value.equals(slug, ignoreCase = true)
        }?.key
    }

    fun toRutubeApiUrl(rawUrl: String): String {
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

    fun cleanRutubeUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (url.contains("?")) {
            url = url.substringBefore("?")
        }
        val trimmed = url.trimEnd('/')
        return if (trimmed.startsWith("http")) trimmed else "https://rutube.ru${if (trimmed.startsWith("/")) "" else "/"}$trimmed"
    }
    var lastFetchSource: String = "Инициализация"
        private set

    private val queryCache = java.util.concurrent.ConcurrentHashMap<String, List<Video>>()
    private val queryCacheTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var cachedCategories: List<RutubeCategory>? = null
    private var categoriesCacheTimestamp: Long = 0L

    private val categoriesLoadedMutex = Mutex()
    private var categoriesLoaded = false

    // ==================== UNIVERSAL PARSER ENTRY ====================

    /**
     * УНИВЕРСАЛЬНЫЙ метод парсинга ЛЮБОГО ответа от Rutube.
     * Не важно, какой эндпоинт — парсер сам поймёт структуру.
     */
    fun parseAnyResponse(bodyString: String, defaultCategoryName: String, url: String? = null): List<Video> {
        return rutubeParser.parseVideoListJson(bodyString, defaultCategoryName, url)
    }

    /**
     * Проверка на блокируемый контент (платные партнёры)
     */
    internal fun isBlockedContent(video: Video): Boolean {
        return rutubeParser.isBlockedContent(video)
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
            isBookmarked = saved.isBookmarked,
            authorId = saved.authorId,
            authorAvatarUrl = saved.authorAvatarUrl,
            originType = saved.originType,
            originId = saved.originId,
            originTitle = saved.originTitle,
            pageUrl = saved.pageUrl
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
            val url = "https://rutube.ru/api/search/combined/video_playlist?query=$q&page=$page"
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
        val categorySlug = categoryManager.dynamicCategoryTargets[selectedCategoryName]

        if (categorySlug != null) {
            // Стратегия: загружаем showcase feed, находим ресурсы табов, грузим их
            try {
                val showcaseUrl = "https://rutube.ru/api/feeds/$categorySlug/?format=json&page=$page"
                val feedResponse = com.example.data.rutube.RutubeRetrofitClient.apiService.getDynamicUrl(showcaseUrl)
                val feedJsonStr = feedResponse.string()
                val feedObj = JSONObject(feedJsonStr)
                val parsedFeed = com.example.data.rutube.parser.ResponseAnalyzer.parse(feedObj, showcaseUrl)

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
                if (parsedFeed.items.isNotEmpty()) {
                    parsedFeed.items.forEach { card ->
                        val video = rutubeParser.mapNormalizedCardToVideo(card, selectedCategoryName)
                        if (!isBlockedContent(video)) parsedResults.add(video)
                    }
                }
                
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
        categoriesList.addAll(categoryManager.defaultCategories.filter { 
            !isBlockedText(it.title, includeTvOnline = true) && 
            !isBlockedText(it.target ?: "", includeTvOnline = true)
        })

        // Refresh slug mappings
        for (defaultCat in categoryManager.defaultCategories) {
            val slug = defaultCat.target
                ?.replace("/api/feeds/", "")
                ?.replace("/feeds/", "")
                ?.replace("/api/v1/feeds/", "")
                ?.trim('/') ?: ""
            if (slug.isNotBlank()) {
                categoryManager.dynamicCategoryTargets[defaultCat.title] = slug
            }
        }

        try {
            val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
            val url = "https://rutube.ru/api/v1/feeds/promogroup/382/?format=json&limit=100"
            val bodyStr = apiService.getDynamicUrl(url).string()
            val jsonObj = JSONObject(bodyStr)
            val parsed = com.example.data.rutube.parser.ResponseAnalyzer.parse(jsonObj, url)

            for (card in parsed.items) {
                var title = ""
                var thumbnail = ""
                var actionUrl = ""
                val idVal = card.hashCode()

                when (card) {
                    is com.example.data.rutube.parser.NormalizedCard.PromoCard -> {
                        title = card.title
                        thumbnail = card.thumbnail ?: ""
                        actionUrl = card.actionUrl ?: ""
                    }
                    is com.example.data.rutube.parser.NormalizedCard.UnknownCard -> {
                        title = card.title
                        thumbnail = card.thumbnail ?: ""
                        actionUrl = card.actionUrl ?: ""
                    }
                    is com.example.data.rutube.parser.NormalizedCard.TvSeriesCard -> {
                        title = card.title
                        thumbnail = card.poster ?: ""
                        actionUrl = card.actionUrl ?: ""
                    }
                    is com.example.data.rutube.parser.NormalizedCard.PlaylistCard -> {
                        title = card.title
                        thumbnail = card.thumbnail ?: ""
                        actionUrl = card.actionUrl ?: ""
                    }
                    is com.example.data.rutube.parser.NormalizedCard.VideoCard -> {
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
                        categoryManager.dynamicCategoryTargets[title] = slug
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
                thumbnailUrl = video.thumbnailUrl, isWatched = termWatched,
                originType = saved?.originType ?: video.originType,
                originId = saved?.originId ?: video.originId,
                originTitle = saved?.originTitle ?: video.originTitle,
                description = saved?.description ?: video.description,
                pageUrl = saved?.pageUrl ?: video.pageUrl,
                page = saved?.page ?: 1,
                authorId = saved?.authorId ?: video.authorId,
                authorAvatarUrl = saved?.authorAvatarUrl ?: video.authorAvatarUrl
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
                thumbnailUrl = video.thumbnailUrl, isWatched = termWatched,
                originType = saved?.originType ?: video.originType,
                originId = saved?.originId ?: video.originId,
                originTitle = saved?.originTitle ?: video.originTitle,
                description = saved?.description ?: video.description,
                pageUrl = saved?.pageUrl ?: video.pageUrl,
                page = saved?.page ?: 1,
                authorId = saved?.authorId ?: video.authorId,
                authorAvatarUrl = saved?.authorAvatarUrl ?: video.authorAvatarUrl
            ))
        }
    }

    suspend fun deleteVideoById(id: String) = dao.deleteById(id)
    suspend fun insertOrUpdate(video: SavedVideo) = dao.insertOrUpdate(video)
    suspend fun getVideoById(id: String): SavedVideo? = dao.getVideoById(id)

    fun getContinueWatchingVideos(): Flow<List<SavedVideo>> = dao.getContinueWatchingVideos()

    suspend fun saveVideoProgress(video: Video, position: Long, durationMs: Long) {
        val saved = dao.getVideoById(video.id)
        val termDownload = saved?.isDownloaded ?: false
        val termBookmark = saved?.isBookmarked ?: false
        
        val originType = video.originType ?: saved?.originType
        val originId = video.originId ?: saved?.originId
        val originTitle = video.originTitle ?: saved?.originTitle

        // Fix: preserve previous duration if currently passed duration is 0 or invalid
        val finalDuration = if (durationMs > 0L) {
            durationMs
        } else if (saved != null && saved.lastDuration > 0L) {
            saved.lastDuration
        } else {
            try {
                com.example.utils.VideoDurationFormatter.parseDurationToSeconds(video.duration) * 1000L
            } catch (e: Throwable) {
                0L
            }
        }

        dao.insertOrUpdate(SavedVideo(
            id = video.id, title = video.title, channel = video.channel,
            views = video.views, timeAgo = video.timeAgo, duration = video.duration,
            isPro = video.isPro, category = video.category,
            isDownloaded = termDownload, isBookmarked = termBookmark,
            thumbnailUrl = video.thumbnailUrl, isWatched = true,
            savedAt = System.currentTimeMillis(),
            lastProgress = position,
            lastDuration = finalDuration,
            originType = originType,
            originId = originId,
            originTitle = originTitle,
            description = saved?.description ?: video.description,
            pageUrl = saved?.pageUrl ?: video.pageUrl,
            page = saved?.page ?: 1,
            authorId = saved?.authorId ?: video.authorId,
            authorAvatarUrl = saved?.authorAvatarUrl ?: video.authorAvatarUrl
        ))
    }

    suspend fun saveVideoProgressById(videoId: String, position: Long, durationMs: Long) {
        val saved = dao.getVideoById(videoId) ?: return
        val finalDuration = if (durationMs > 0L) {
            durationMs
        } else if (saved.lastDuration > 0L) {
            saved.lastDuration
        } else {
            0L
        }
        dao.insertOrUpdate(saved.copy(
            lastProgress = position,
            lastDuration = finalDuration,
            savedAt = System.currentTimeMillis()
        ))
    }

    // ==================== СОВМЕСТИМОСТЬ ====================

    fun parseVideoListJson(bodyString: String, defaultCategoryName: String, url: String? = null): List<Video> {
        return rutubeParser.parseVideoListJson(bodyString, defaultCategoryName, url)
    }

    internal fun isBlockedText(text: String, includeTvOnline: Boolean = true): Boolean {
        return rutubeParser.isBlockedText(text, includeTvOnline)
    }
}
