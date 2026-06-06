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
        "Музыка" to "music",
        "Мультфильмы" to "cartoons",
        "Спорт" to "sport",
        "Юмор" to "umor",
        "Видеоигры" to "games",
        "Технологии" to "technologies"
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
            is com.example.data.rutube.SmartRutubeParser.NormalizedCard.TvShowCard -> {
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
                    id = "promo_${card.id}",
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
            is com.example.data.rutube.SmartRutubeParser.NormalizedCard.LiveTvCard -> {
                Video(
                    id = "unknown_${card.id}__${card.apiUrl ?: ""}",
                    title = card.name,
                    channel = "ТВ Эфир • ${if (card.isPaid) "Платный" else "Бесплатный"}",
                    views = "Прямой эфир",
                    timeAgo = if (card.isPaid) "Платный канал" else "Смотреть трансляцию",
                    duration = "ТВ",
                    isPro = card.isPaid,
                    category = defaultCategoryName,
                    description = card.description ?: "Прямая трансляция телеканала на Rutube.",
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
            RutubeCategory(1001, "Фильмы", "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=500", "/api/feeds/movies/"),
            RutubeCategory(1002, "Сериалы", "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=500", "/api/feeds/serials/"),
            RutubeCategory(1003, "Телепередачи", "https://images.unsplash.com/photo-1598257006458-087169a1f08d?w=500", "/api/feeds/tv/"),
            RutubeCategory(1004, "Мультфильмы", "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500", "/api/feeds/cartoons/"),
            RutubeCategory(1005, "Музыка", "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500", "/api/feeds/music/"),
            RutubeCategory(1006, "Спорт", "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?w=500", "/api/feeds/sport/"),
            RutubeCategory(1007, "Юмор", "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=500", "/api/feeds/umor/"),
            RutubeCategory(1008, "Видеоигры", "https://images.unsplash.com/photo-1538481199705-c710c4e965fc?w=500", "/api/feeds/games/"),
            RutubeCategory(1009, "Технологии", "https://images.unsplash.com/photo-1518770660439-4636190af475?w=500", "/api/feeds/technologies/"),
            RutubeCategory(1010, "Блоги", "https://images.unsplash.com/photo-1499750310107-5fef28a66643?w=500", "/api/feeds/blogs/"),
            RutubeCategory(1011, "Новости", "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=500", "/api/feeds/news/"),
            RutubeCategory(1012, "Лайфхаки", "https://images.unsplash.com/photo-1513151233558-d860c5398176?w=500", "/api/feeds/lifehacks/"),
            RutubeCategory(1013, "Детям", "https://images.unsplash.com/photo-1485546246426-74dc88dec4d9?w=500", "/api/feeds/kids/"),
            RutubeCategory(1014, "Авто-мото", "https://images.unsplash.com/photo-1511919884226-fd3cad34687c?w=500", "/api/feeds/auto/"),
            RutubeCategory(1015, "Обучение", "https://images.unsplash.com/photo-1503676260728-1c00da094a0b?w=500", "/api/feeds/education/"),
            RutubeCategory(1016, "Путешествия", "https://images.unsplash.com/photo-1469854523086-cc02fe5d8800?w=500", "/api/feeds/travel/"),
            RutubeCategory(1017, "Кулинария", "https://images.unsplash.com/photo-1556910103-1c02745aae4d?w=500", "/api/feeds/food/"),
            RutubeCategory(1018, "Аниме", "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500", "/api/feeds/anime/"),
            RutubeCategory(1019, "ТВ Каналы", "https://images.unsplash.com/photo-1593305841991-05c297ba4575?w=500", "/api/feeds/live/")
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
            val response = apiService.getDynamicUrl("https://rutube.ru/api/v1/feeds/promogroup/382/?format=json")
            val bodyStr = response.string()
            val jsonObj = JSONObject(bodyStr)
            val parsedResult = com.example.data.rutube.SmartRutubeParser.ResponseAnalyzer.parse(jsonObj, "https://rutube.ru/api/v1/feeds/promogroup/382/")
            
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
                    is com.example.data.rutube.SmartRutubeParser.NormalizedCard.TvShowCard -> {
                        title = card.title
                        thumbnail = card.poster ?: ""
                        actionUrl = "/feeds/tv/"
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

