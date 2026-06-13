package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.json.JSONArray

class VideoRepository(private val dao: SavedVideoDao) {

    var lastFetchSource: String = "Инициализация"
        private set

    val dynamicCategoryTargets = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun getVideosFlow(): Flow<List<Video>> {
        return dao.getAllSavedVideos().map { savedList ->
            savedList.map { saved ->
                Video(
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
        }
    }

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

    fun parseVideoListJson(bodyString: String, defaultCategoryName: String): List<Video> {
        val mapped = mutableListOf<Video>()
        val trimmed = bodyString.trim()
        if (!trimmed.startsWith("{")) {
            android.util.Log.w("VideoRepository", "Response body for category '$defaultCategoryName' is not a JSON object, search query might be blocked or HTML error was returned.")
            return mapped
        }
        try {
            val jsonObj = JSONObject(trimmed)
            val parsed = com.example.data.rutube.SmartRutubeParser.ResponseAnalyzer.parse(jsonObj, defaultCategoryName)
            for (card in parsed.items) {
                mapped.add(mapNormalizedCardToVideo(card, defaultCategoryName))
            }
        } catch (ex: Exception) {
            android.util.Log.e("VideoRepository", "Error parsing results JSON list via SmartRutubeParser", ex)
        }
        return mapped
    }

    private fun mapNormalizedCardToVideo(card: com.example.data.rutube.SmartRutubeParser.NormalizedCard, defaultCategoryName: String): Video {
        return when (card) {
            is com.example.data.rutube.SmartRutubeParser.NormalizedCard.VideoCard -> {
                Video(
                    id = card.id,
                    title = card.title,
                    channel = card.channelName,
                    views = card.views,
                    timeAgo = card.published,
                    duration = card.duration,
                    isPro = (0..10).random() > 8,
                    category = defaultCategoryName,
                    description = card.description,
                    thumbnailUrl = card.thumbnail,
                    authorId = card.channelId,
                    authorActionUrl = card.channelId?.let { "https://rutube.ru/api/video/person/$it/" }
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
                    isPro = false,
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
                    description = card.description ?: "Официальный канал в Sleek Video Hub.",
                    thumbnailUrl = card.avatar
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

    suspend fun fetchRealVideos(query: String?, category: String?, page: Int = 1): List<Video> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            fetchRealVideosSuspend(query, category, page)
        }
    }

    private suspend fun fetchRealVideosSuspend(query: String?, category: String?, page: Int = 1): List<Video> {
        // Try calling the actual Rutube Search or Showcase APIs
        try {
            val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
            val q = query?.trim() ?: ""
            val selectedCategoryName = category ?: "Фильмы"
            
            if (q.isNotEmpty()) {
                val resultsList = mutableListOf<Video>()
                
                // Try fetching videos search results
                try {
                    val responseBody = apiService.searchVideos(q, page = page)
                    val bodyString = responseBody.string()
                    val parsed = parseVideoListJson(bodyString, "Поиск: $q")
                    resultsList.addAll(parsed)
                } catch (e: Exception) {
                    android.util.Log.e("VideoRepository", "Error searching videos", e)
                }

                // Try fetching channels search results
                try {
                    val encodedQ = java.net.URLEncoder.encode(q, "UTF-8")
                    val personResponse = apiService.getDynamicUrl("https://rutube.ru/api/search/person/?query=$encodedQ&page=$page&format=json")
                    val bodyString = personResponse.string()
                    val parsedChannels = parseVideoListJson(bodyString, "Поиск: $q")
                    
                    val channelsOnly = parsedChannels.filter { it.id.startsWith("channel_") }
                    resultsList.addAll(0, channelsOnly)
                } catch (e: Exception) {
                    android.util.Log.e("VideoRepository", "Error searching channels via person endpoint", e)
                }

                if (resultsList.isNotEmpty()) {
                    lastFetchSource = "Rutube LIVE"
                    return resultsList.distinctBy { it.id }
                }
            } else {
                var categorySlug = categorySlugs[selectedCategoryName]
                if (categorySlug == null) {
                    categorySlug = dynamicCategoryTargets[selectedCategoryName]
                }
                if (categorySlug != null) {
                    try {
                        val feedResponse = apiService.getDynamicUrl("https://rutube.ru/api/feeds/$categorySlug/?format=json&page=$page")
                        val feedJsonStr = feedResponse.string()
                        val feedObj = org.json.JSONObject(feedJsonStr)
                        val tabsArray = feedObj.optJSONArray("tabs")
                        
                        val resourceUrls = mutableListOf<String>()
                        if (tabsArray != null && tabsArray.length() > 0) {
                            // Find all resource URLs from tabs in the category showcase feed
                            for (t in 0 until tabsArray.length()) {
                                val tab = tabsArray.optJSONObject(t) ?: continue
                                val resourcesArray = tab.optJSONArray("resources")
                                if (resourcesArray != null) {
                                    for (r in 0 until resourcesArray.length()) {
                                        val res = resourcesArray.optJSONObject(r) ?: continue
                                        val urlVal = res.optString("url")
                                        if (urlVal.isNotBlank() && !resourceUrls.contains(urlVal)) {
                                            resourceUrls.add(urlVal)
                                        }
                                    }
                                }
                            }
                        }
                        
                        val parsedResults = mutableListOf<Video>()
                        // Load top 3 showcases to form a rich category feed list
                        val limit = minOf(resourceUrls.size, 3)
                        for (idx in 0 until limit) {
                            val endpoint = resourceUrls[idx]
                            val targetResourceUrl = if (endpoint.startsWith("/")) {
                                "https://rutube.ru$endpoint"
                            } else {
                                endpoint
                            }
                            val paginatedResourceUrl = if (targetResourceUrl.contains("?")) {
                                "$targetResourceUrl&page=$page"
                            } else {
                                "$targetResourceUrl?page=$page"
                            }
                            try {
                                val resourceResponseBody = apiService.getDynamicUrl(paginatedResourceUrl)
                                val resourceJsonStr = resourceResponseBody.string()
                                val categoryVideos = parseVideoListJson(resourceJsonStr, selectedCategoryName)
                                parsedResults.addAll(categoryVideos)
                            } catch (resEx: Exception) {
                                android.util.Log.e("VideoRepository", "Error fetching tab resource $paginatedResourceUrl", resEx)
                            }
                        }
                        
                        if (parsedResults.isNotEmpty()) {
                            lastFetchSource = "Rutube LIVE"
                            return parsedResults.distinctBy { it.id }
                        }
                    } catch (feedEx: Exception) {
                        android.util.Log.e("VideoRepository", "Error fetching showcase $categorySlug, falling back to live search", feedEx)
                    }
                    
                    // Fallback to active searching for the category name itself
                    try {
                        val fallbackSearchResponse = apiService.searchVideos(selectedCategoryName, page = page)
                        val fallbackSearchBody = fallbackSearchResponse.string()
                        val searchVideos = parseVideoListJson(fallbackSearchBody, selectedCategoryName)
                        if (searchVideos.isNotEmpty()) {
                            lastFetchSource = "Rutube LIVE"
                            return searchVideos
                        }
                    } catch (ex: Exception) {
                        android.util.Log.e("VideoRepository", "Fallback search for category $selectedCategoryName failed", ex)
                    }
                } else {
                    // Default to popular videos if "Все" or mapping not found
                    val responseBody = apiService.getPopularVideos(page = page)
                    val bodyString = responseBody.string()
                    val results = parseVideoListJson(bodyString, "Популярное")
                    if (results.isNotEmpty()) {
                        lastFetchSource = "Rutube LIVE"
                        return results
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoRepository", "Rutube API error, falling back to offline database", e)
        }

        // Fallback to Room DB saved offline videos to prevent complete empty stubs
        lastFetchSource = "Локальный оффлайн"
        try {
            val savedList = dao.getAllSavedVideos().first()
            val filteredSaved = savedList.map { saved ->
                Video(
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
            }.filter { video ->
                val matchCat = category.isNullOrBlank() || video.category.equals(category, ignoreCase = true)
                val matchQuery = query.isNullOrBlank() || 
                        video.title.contains(query, ignoreCase = true) || 
                        video.channel.contains(query, ignoreCase = true)
                matchCat && matchQuery
            }
            if (filteredSaved.isNotEmpty()) {
                return filteredSaved
            } else {
                return emptyList()
            }
        } catch (dbEx: Exception) {
            return emptyList()
        }
    }

    fun getSavedVideosOnly(): Flow<List<SavedVideo>> {
        return dao.getAllSavedVideos()
    }

    suspend fun toggleBookmark(video: Video) {
        val saved = dao.getVideoById(video.id)
        val termBookmark = !(saved?.isBookmarked ?: false)
        val termDownload = saved?.isDownloaded ?: false
        val termWatched = saved?.isWatched ?: false

        if (!termBookmark && !termDownload && !termWatched) {
            dao.deleteById(video.id)
        } else {
            val updated = SavedVideo(
                id = video.id,
                title = video.title,
                channel = video.channel,
                views = video.views,
                timeAgo = video.timeAgo,
                duration = video.duration,
                isPro = video.isPro,
                category = video.category,
                isDownloaded = termDownload,
                isBookmarked = termBookmark,
                thumbnailUrl = video.thumbnailUrl,
                isWatched = termWatched
            )
            dao.insertOrUpdate(updated)
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
            val updated = SavedVideo(
                id = video.id,
                title = video.title,
                channel = video.channel,
                views = video.views,
                timeAgo = video.timeAgo,
                duration = video.duration,
                isPro = video.isPro,
                category = video.category,
                isDownloaded = termDownload,
                isBookmarked = termBookmark,
                thumbnailUrl = video.thumbnailUrl,
                isWatched = termWatched
            )
            dao.insertOrUpdate(updated)
        }
    }

    suspend fun deleteVideoById(id: String) {
        dao.deleteById(id)
    }

    suspend fun fetchRealCategories(): List<RutubeCategory> {
        val defaultCategories = listOf(
    RutubeCategory(1001, "Фильмы", "https://pic.rtbcdn.ru/promoitem/55/c1/55c106e53da7e36f144347990cb39885.png", "/api/feeds/movies/"),
    RutubeCategory(1002, "Сериалы", "https://pic.rtbcdn.ru/promoitem/55/c1/55c106e53da7e36f144347990cb39885.png", "/api/feeds/serials/"),
    RutubeCategory(1003, "Телепередачи", "https://pic.rtbcdn.ru/promoitem/11/4b/114b0e5e339a889c31ee106e9b986c53.png", "/api/feeds/tv/"),
    RutubeCategory(1004, "Мультфильмы", "https://pic.rtbcdn.ru/promoitem/2025-06-06/64/73/64734dadd906cefa63183b96cd260e1b.png", "/api/feeds/cartoons/"),
    RutubeCategory(1005, "Музыка", "https://pic.rtbcdn.ru/promoitem/8c/49/8c49dbbe473765f4c881090860ddee01.png", "/api/feeds/music/"),
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

        val categoriesList = mutableListOf<RutubeCategory>()
        categoriesList.addAll(defaultCategories)

        for (defaultCat in defaultCategories) {
            val slug = defaultCat.target?.replace("/api/feeds/", "")?.replace("/feeds/", "")?.replace("/api/v1/feeds/", "")?.trim('/') ?: ""
            if (slug.isNotBlank()) {
                dynamicCategoryTargets[defaultCat.title] = slug
            }
        }

        try {
            val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
            val response = apiService.getDynamicUrl("https://rutube.ru/api/v1/feeds/promogroup/382/?format=json&limit=100")
            val bodyStr = response.string()
            val jsonObj = JSONObject(bodyStr)
            val parsedResult = com.example.data.rutube.SmartRutubeParser.ResponseAnalyzer.parse(jsonObj, "https://rutube.ru/api/v1/feeds/promogroup/382/?format=json&limit=1")
            
            for (card in parsedResult.items) {
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
                        actionUrl = "/feeds/video/"
                    }
                    else -> {}
                }

                if (title.isNotBlank() && categoriesList.none { it.title.equals(title, ignoreCase = true) }) {
                    categoriesList.add(
                        RutubeCategory(
                            id = idVal,
                            title = title,
                            picture = thumbnail,
                            target = actionUrl
                        )
                    )
                    val slug = actionUrl.replace("/api/feeds/", "").replace("/feeds/", "").replace("/api/v1/feeds/", "").trim('/')
                    if (slug.isNotBlank()) {
                        dynamicCategoryTargets[title] = slug
                    }
                }
            }

            val resultsArray = jsonObj.optJSONArray("results")
            if (resultsArray != null) {
                for (i in 0 until resultsArray.length()) {
                    val item = resultsArray.optJSONObject(i) ?: continue
                    val id = item.optInt("id", item.hashCode())
                    val title = item.optString("title")
                    val picture = item.optString("picture", item.optString("image", ""))
                    val target = item.optString("target", item.optString("url", ""))
                    if (title.isNotBlank() && categoriesList.none { it.title.equals(title, ignoreCase = true) }) {
                        categoriesList.add(
                            RutubeCategory(
                                id = id,
                                title = title,
                                picture = picture,
                                target = target
                            )
                        )
                        val slug = target.replace("/api/feeds/", "").replace("/feeds/", "").replace("/api/v1/feeds/", "").trim('/')
                        if (slug.isNotBlank()) {
                            dynamicCategoryTargets[title] = slug
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            android.util.Log.e("VideoRepository", "Error fetching real categories", ex)
        }

        return categoriesList
    }
}

