package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.json.JSONArray

class VideoRepository(private val dao: SavedVideoDao) {

    var lastFetchSource: String = "Инициализация"
        private set

    private val dynamicCategoryTargets = java.util.concurrent.ConcurrentHashMap<String, String>()

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
        try {
            val jsonObj = JSONObject(bodyString)
            val resultsArray = jsonObj.optJSONArray("results") ?: return emptyList()
            
            for (i in 0 until resultsArray.length()) {
                val rawItem = resultsArray.optJSONObject(i) ?: continue
                var itemObj = rawItem
                var modelType = "video"
                
                // Handle nested resource object wrapper if content_type and object are present
                if (rawItem.has("object") && rawItem.has("content_type")) {
                    val contentTypeObj = rawItem.optJSONObject("content_type")
                    modelType = contentTypeObj?.optString("model") ?: "video"
                    val nestedObj = rawItem.optJSONObject("object")
                    if (nestedObj != null) {
                        itemObj = nestedObj
                    }
                } else if (rawItem.has("type")) {
                    modelType = rawItem.optString("type") ?: "video"
                }

                if (modelType == "userchannel" || itemObj.has("subscribers_count")) {
                    // Channel normalization as per custom parser structure
                    val idVal = itemObj.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val nameVal = itemObj.optString("name").takeIf { it.isNotBlank() } ?: "Авторский канал"
                    val avatarVal = itemObj.optString("avatar_url")
                        .takeIf { it.isNotBlank() }
                        ?: itemObj.optString("user_channel_image")
                        .takeIf { it.isNotBlank() }
                        ?: itemObj.optString("picture")
                        .takeIf { it.isNotBlank() }
                        ?: itemObj.optString("icon")
                        .takeIf { it.isNotBlank() }
                    
                    val subscribersCount = itemObj.optLong("subscribers_count", 0L)
                    val formattedSubs = if (subscribersCount >= 1000000) {
                        String.format("%.1fМ", subscribersCount / 1000000.0)
                    } else if (subscribersCount >= 1000) {
                        "${subscribersCount / 1000}К"
                    } else {
                        "$subscribersCount"
                    }
                    
                    val videoCount = itemObj.optInt("video_count", 0)
                    val descriptionVal = itemObj.optString("description", "Официальный канал в Sleek Video Hub.")
                    
                    mapped.add(
                        Video(
                            id = "channel_$idVal",
                            title = nameVal,
                            channel = "Авторский канал • $formattedSubs подписчиков",
                            views = "$formattedSubs подписчиков",
                            timeAgo = "$videoCount видео",
                            duration = "КАНАЛ",
                            isPro = false,
                            category = defaultCategoryName,
                            description = descriptionVal,
                            thumbnailUrl = avatarVal
                        )
                    )
                } else if (modelType == "tv" || itemObj.has("seasons_count")) {
                    // TV Show / series normalization as per custom parser structure
                    val idVal = itemObj.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val titleVal = itemObj.optString("title")
                        .takeIf { it.isNotBlank() }
                        ?: itemObj.optString("original_title")
                        .takeIf { it.isNotBlank() }
                        ?: itemObj.optString("name")
                        .takeIf { it.isNotBlank() }
                        ?: "Шоу без названия"
                        
                    val posterVal = itemObj.optString("poster_url")
                        .takeIf { it.isNotBlank() }
                        ?: itemObj.optString("thumbnail_url")
                        .takeIf { it.isNotBlank() }
                        ?: itemObj.optJSONArray("images")?.optJSONObject(0)?.optString("image")
                        
                    val seasonsCount = itemObj.optInt("seasons_count", 1)
                    val kpRating = itemObj.optDouble("kinopoisk_rating", 0.0)
                    val ratingStr = if (kpRating > 0.05) " • Кинопоиск: $kpRating" else ""
                    val yearVal = itemObj.optString("year_start")
                        .takeIf { it.isNotBlank() }
                        ?: itemObj.optString("year")
                        .takeIf { it.isNotBlank() }
                        ?: "Передача"
                        
                    val descriptionVal = itemObj.optString("description", "Смотрите оригинальные сезоны и выпуски бесплатно.")
                    
                    mapped.add(
                        Video(
                            id = "tv_$idVal",
                            title = titleVal,
                            channel = "Шоу • $yearVal$ratingStr",
                            views = "$seasonsCount сезонов",
                            timeAgo = "Смотреть выпуски",
                            duration = "СЕРИАЛ",
                            isPro = false,
                            category = defaultCategoryName,
                            description = descriptionVal,
                            thumbnailUrl = posterVal
                        )
                    )
                } else {
                    // Standard Video normalization as per custom parser structure
                    val idVal = itemObj.optString("code")
                        .takeIf { it.isNotBlank() }
                        ?: itemObj.optString("video_id")
                        .takeIf { it.isNotBlank() }
                        ?: itemObj.optString("id")
                        .takeIf { it.isNotBlank() }
                        ?: continue // Required for playback

                    val titleVal = itemObj.optString("title")
                        .takeIf { it.isNotBlank() }
                        ?: itemObj.optString("name", "Без названия")
                    
                    val descVal = itemObj.optString("description", "Описание отсутствует.")
                    
                    val thumbUrl = itemObj.optString("thumbnail_url")
                        .takeIf { it.isNotBlank() }
                        ?: itemObj.optString("poster_url")
                        .takeIf { it.isNotBlank() }
                        ?: itemObj.optString("picture")
                        .takeIf { it.isNotBlank() }
                        ?: itemObj.optString("picture_url")
                    
                    val durationSeconds = itemObj.optDouble("duration", -1.0)
                    val durationStr = if (durationSeconds > 0) {
                        val totalSeconds = durationSeconds.toInt()
                        val h = totalSeconds / 3600
                        val m = (totalSeconds % 3600) / 60
                        val s = totalSeconds % 60
                        if (h > 0) {
                            String.format("%d:%02d:%02d", h, m, s)
                        } else {
                            String.format("%02d:%02d", m, s)
                        }
                    } else {
                        val rawDuration = itemObj.optString("duration", "")
                        if (rawDuration.isNotBlank() && rawDuration.contains(":")) rawDuration else "10:00"
                    }
                    
                    val viewsCountVal = if (itemObj.has("views")) {
                        itemObj.optLong("views", 0L)
                    } else {
                        itemObj.optLong("hits", 0L)
                    }
                    
                    val viewsStr = if (viewsCountVal >= 1000000) {
                        String.format("%.1fМ просмотров", viewsCountVal / 1000000.0)
                    } else if (viewsCountVal >= 1000) {
                        "${viewsCountVal / 1000}К просмотров"
                    } else if (viewsCountVal > 0) {
                        "$viewsCountVal просмотров"
                    } else {
                        "${(1200..340000).random()} просмотров"
                    }
                    
                    val authorObj = itemObj.optJSONObject("author")
                    val channelStr = authorObj?.optString("name")?.takeIf { it.isNotBlank() }
                        ?: authorObj?.optString("username")?.takeIf { it.isNotBlank() }
                        ?: itemObj.optString("feed_name").takeIf { it.isNotBlank() }
                        ?: itemObj.optString("author_name").takeIf { it.isNotBlank() }
                        ?: "Канал Rutube"
                        
                    mapped.add(
                        Video(
                            id = idVal,
                            title = titleVal,
                            channel = channelStr,
                            views = viewsStr,
                            timeAgo = "Загружено недавно",
                            duration = durationStr,
                            isPro = (0..10).random() > 7,
                            category = defaultCategoryName,
                            description = descVal,
                            thumbnailUrl = thumbUrl
                        )
                    )
                }
            }
        } catch (ex: Exception) {
            android.util.Log.e("VideoRepository", "Error parsing results JSON list", ex)
        }
        return mapped
    }

    suspend fun fetchRealVideos(query: String?, category: String?): List<Video> {
        // Try calling the actual Rutube Search or Showcase APIs
        try {
            val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
            val q = query?.trim() ?: ""
            val selectedCategoryName = category ?: "Все"
            
            if (q.isNotEmpty()) {
                val responseBody = apiService.searchVideos(q)
                val bodyString = responseBody.string()
                val results = parseVideoListJson(bodyString, "Поиск: $q")
                if (results.isNotEmpty()) {
                    lastFetchSource = "Rutube LIVE"
                    return results
                }
            } else {
                var categorySlug = categorySlugs[selectedCategoryName]
                if (categorySlug == null) {
                    categorySlug = dynamicCategoryTargets[selectedCategoryName]
                }
                if (categorySlug != null) {
                    try {
                        val feedResponse = apiService.getDynamicUrl("https://rutube.ru/api/feeds/$categorySlug/?format=json")
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
                            try {
                                val resourceResponseBody = apiService.getDynamicUrl(targetResourceUrl)
                                val resourceJsonStr = resourceResponseBody.string()
                                val categoryVideos = parseVideoListJson(resourceJsonStr, selectedCategoryName)
                                parsedResults.addAll(categoryVideos)
                            } catch (resEx: Exception) {
                                android.util.Log.e("VideoRepository", "Error fetching tab resource $targetResourceUrl", resEx)
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
                        val fallbackSearchResponse = apiService.searchVideos(selectedCategoryName)
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
                    val responseBody = apiService.getPopularVideos()
                    val bodyString = responseBody.string()
                    val results = parseVideoListJson(bodyString, "Популярное")
                    if (results.isNotEmpty()) {
                        lastFetchSource = "Rutube LIVE"
                        return results
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoRepository", "Rutube API error, falling back to Gemini", e)
        }

        // If Rutube call fails or empty, call Gemini API fallback loader
        try {
            val geminiResults = com.example.data.gemini.GeminiSearchService.fetchSearchFallback(query, category)
            if (geminiResults.isNotEmpty()) {
                lastFetchSource = "Gemini Hybrid AI"
                return geminiResults
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoRepository", "Gemini API error, falling back to offline database", e)
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
                val matchCat = category.isNullOrBlank() || category == "Все" || video.category.equals(category, ignoreCase = true)
                val matchQuery = query.isNullOrBlank() || 
                        video.title.contains(query, ignoreCase = true) || 
                        video.channel.contains(query, ignoreCase = true)
                matchCat && matchQuery
            }
            return filteredSaved
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

        if (!termBookmark && !termDownload) {
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
                thumbnailUrl = video.thumbnailUrl
            )
            dao.insertOrUpdate(updated)
        }
    }

    suspend fun toggleDownload(video: Video) {
        val saved = dao.getVideoById(video.id)
        val termDownload = !(saved?.isDownloaded ?: false)
        val termBookmark = saved?.isBookmarked ?: false

        if (!termBookmark && !termDownload) {
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
                thumbnailUrl = video.thumbnailUrl
            )
            dao.insertOrUpdate(updated)
        }
    }

    suspend fun fetchRealCategories(): List<RutubeCategory> {
        val categoriesList = mutableListOf<RutubeCategory>()
        try {
            val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
            val response = apiService.getDynamicUrl("https://rutube.ru/api/v1/feeds/promogroup/382/?format=json")
            val bodyStr = response.string()
            val jsonObj = JSONObject(bodyStr)
            val resultsArray = jsonObj.optJSONArray("results")
            if (resultsArray != null) {
                for (i in 0 until resultsArray.length()) {
                    val item = resultsArray.optJSONObject(i) ?: continue
                    val id = item.optInt("id")
                    val title = item.optString("title")
                    val picture = item.optString("picture")
                    val target = item.optString("target")
                    if (title.isNotBlank()) {
                        categoriesList.add(
                            RutubeCategory(
                                id = id,
                                title = title,
                                picture = picture,
                                target = target
                            )
                        )
                        val slug = target.removePrefix("/feeds/").removeSuffix("/")
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

