package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class VideoRepository(private val dao: SavedVideoDao) {

    var lastFetchSource: String = "Инициализация"
        private set

    private val dynamicCategoryTargets = ConcurrentHashMap<String, String>()

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

    /**
     * Адаптированный парсер на основе JS-логики v4.0.
     * Разворачивает дерево результатов (включая вложенные объекты и промо-баннеры).
     */
    fun parseVideoListJson(bodyString: String, defaultCategoryName: String, contextUrl: String? = null): List<Video> {
        val mapped = mutableListOf<Video>()
        val trimmed = bodyString.trim()
        if (!trimmed.startsWith("{")) {
            Log.w("VideoRepository", "Response body is not a JSON object.")
            return mapped
        }
        try {
            val jsonObj = JSONObject(trimmed)
            val resultsArray = jsonObj.optJSONArray("results") ?: return emptyList()
            
            val isPromoList = contextUrl?.contains("promogroup") == true

            for (i in 0 until resultsArray.length()) {
                try {
                    val rawItem = resultsArray.optJSONObject(i) ?: continue
                    
                    if (isPromoList) {
                        val promoVideo = parsePromoItemAsVideo(rawItem, defaultCategoryName)
                        if (promoVideo != null) mapped.add(promoVideo)
                        continue
                    }

                    val contentTypeObj = rawItem.optJSONObject("content_type")
                    var modelType = contentTypeObj?.optString("model") 
                        ?: rawItem.optString("type").takeIf { it.isNotBlank() } 
                        ?: "video"

                    var itemObj = rawItem
                    if (rawItem.has("object") && contentTypeObj != null) {
                        val nestedObj = rawItem.optJSONObject("object")
                        if (nestedObj != null) {
                            itemObj = nestedObj
                            val nestedModel = itemObj.optJSONObject("content_type")?.optString("model")
                            if (!nestedModel.isNullOrBlank()) {
                                modelType = nestedModel
                            }
                        }
                    }

                    val idVal = itemObj.optString("code")
                        .takeIf { it.isNotBlank() && it != "null" }
                        ?: itemObj.optString("video_id")
                        .takeIf { it.isNotBlank() && it != "null" }
                        ?: itemObj.optString("id")
                        .takeIf { it.isNotBlank() && it != "null" }
                        ?: continue

                    // 1. Нормализация КАНАЛА (userchannel)
                    if (modelType == "userchannel" || itemObj.has("subscribers_count")) {
                        val nameVal = itemObj.optString("name").takeIf { it.isNotBlank() } ?: "Авторский канал"
                        val avatarVal = itemObj.optString("avatar_url")
                            .takeIf { it.isNotBlank() }
                            ?: itemObj.optString("user_channel_image")
                            .takeIf { it.isNotBlank() }
                            ?: itemObj.optString("picture")
                            .takeIf { it.isNotBlank() }
                            ?: itemObj.optString("icon") ?: ""
                        
                        val subscribersCount = itemObj.optLong("subscribers_count", 0L)
                        val formattedSubs = formatCount(subscribersCount)
                        val videoCount = itemObj.optInt("video_count", 0)
                        val descriptionVal = itemObj.optString("description", "Официальный канал Rutube.")
                        
                        mapped.add(
                            Video(
                                id = "channel_$idVal",
                                title = nameVal,
                                channel = "Авторский канал • $formattedSubs подписчиков",
                                views = "$formattedSubs подписчиков",
                                timeAgo = "$videoCount видео",
                                duration = "ЛЕНТА",
                                isPro = false,
                                category = defaultCategoryName,
                                description = descriptionVal,
                                thumbnailUrl = avatarVal
                            )
                        )
                    } 
                    // 2. Нормализация СЕРИАЛА / ТВ-ШОУ (tv)
                    else if (modelType == "tv" || itemObj.has("seasons_count")) {
                        val titleVal = itemObj.optString("title")
                            .takeIf { it.isNotBlank() }
                            ?: itemObj.optString("original_title")
                            .takeIf { it.isNotBlank() }
                            ?: itemObj.optString("name") ?: "Шоу без названия"
                            
                        val posterVal = itemObj.optString("poster_url")
                            .takeIf { it.isNotBlank() }
                            ?: itemObj.optString("thumbnail_url")
                            .takeIf { it.isNotBlank() }
                            ?: itemObj.optJSONArray("images")?.optJSONObject(0)?.optString("image") ?: ""
                            
                        val seasonsCount = itemObj.optInt("seasons_count", 1)
                        val kpRating = itemObj.optDouble("kinopoisk_rating", 0.0)
                        val ratingStr = if (kpRating > 0.05) " • Кинопоиск: $kpRating" else ""
                        val yearVal = itemObj.optString("year_start")
                            .takeIf { it.isNotBlank() }
                            ?: itemObj.optString("year") ?: "Передача"
                            
                        val descriptionVal = itemObj.optString("description", "Смотрите оригинальные сезоны бесплатно.")
                        
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
                    } 
                    // 3. Стандартное ВИДЕО
                    else {
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
                            ?: itemObj.optString("picture_url") ?: ""
                        
                        val durationSeconds = itemObj.optDouble("duration", -1.0)
                        val durationStr = if (durationSeconds > 0) {
                            val totalSeconds = durationSeconds.toInt()
                            val h = totalSeconds / 3600
                            val m = (totalSeconds % 3600) / 60
                            val s = totalSeconds % 60
                            if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
                        } else {
                            val rawDuration = itemObj.optString("duration", "")
                            if (rawDuration.isNotBlank() && rawDuration.contains(":")) rawDuration else "10:00"
                        }
                        
                        val viewsCountVal = if (itemObj.has("views")) {
                            itemObj.optLong("views", 0L)
                        } else {
                            itemObj.optLong("hits", 0L)
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
                                views = "${formatCount(viewsCountVal)} просмотров",
                                timeAgo = "Загружено недавно",
                                duration = durationStr,
                                isPro = (0..10).random() > 7,
                                category = defaultCategoryName,
                                description = descVal,
                                thumbnailUrl = thumbUrl
                            )
                        )
                    }
                } catch (itemEx: Exception) {
                    Log.e("VideoRepository", "Ошибка парсинга элемента дерева", itemEx)
                }
            }
        } catch (ex: Exception) {
            Log.e("VideoRepository", "Error parsing results JSON", ex)
        }
        return mapped
    }

    private fun parsePromoItemAsVideo(data: JSONObject, categoryName: String): Video? {
        val id = data.optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = data.optString("title").takeIf { it.isNotBlank() } ?: "Промо"
        val thumbnail = data.optString("picture")
            .takeIf { it.isNotBlank() }
            ?: data.optString("thumbnail_url")
            .takeIf { it.isNotBlank() }
            ?: data.optString("image") ?: ""
            
        var actionUrl = data.optString("target")
            .takeIf { it.isNotBlank() }
            ?: data.optJSONObject("button")?.optString("button_url")
            ?: data.optString("url") ?: ""

        if (actionUrl.startsWith("/") && !actionUrl.startsWith("/api/")) {
            actionUrl = "/api$actionUrl"
        }

        return Video(
            id = "promo_$id",
            title = title,
            channel = "Рекомендации Rutube",
            views = "Интересное",
            timeAgo = actionUrl,
            duration = "ПРОМО",
            isPro = false,
            category = categoryName,
            description = data.optString("description", "Специальный раздел каталога."),
            thumbnailUrl = thumbnail
        )
    }

    private fun formatCount(num: Long): String {
        return when {
            num >= 1000000 -> String.format("%.1fМ", num / 1000000.0)
            num >= 1000 -> "${num / 1000}К"
            else -> num.toString()
        }
    }

    // Совместимость со старым кодом ViewModel, который может передавать 3 аргумента (включая page)
    suspend fun fetchRealVideos(query: String?, category: String?, page: Int = 1): List<Video> {
        return withContext(Dispatchers.IO) {
            fetchRealVideosSuspend(query, category)
        }
    }

    private suspend fun fetchRealVideosSuspend(query: String?, category: String?): List<Video> {
        try {
            val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
            val q = query?.trim() ?: ""
            val selectedCategoryName = category ?: "Все"
            
            if (q.isNotEmpty()) {
                val combinedResults = mutableListOf<Video>()

                // Поиск каналов
                try {
                    val channelResponse = apiService.getDynamicUrl("https://rutube.ru/api/search/channel/?query=$q&format=json")
                    val channelBodyString = channelResponse.string()
                    val parsedChannels = parseVideoListJson(channelBodyString, "Поиск каналов: $q")
                    combinedResults.addAll(parsedChannels)
                } catch (channelEx: Exception) {
                    Log.e("VideoRepository", "Ошибка поиска каналов", channelEx)
                }

                // Поиск видео
                try {
                    val videoResponse = apiService.searchVideos(q)
                    val videoBodyString = videoResponse.string()
                    val parsedVideos = parseVideoListJson(videoBodyString, "Поиск: $q")
                    combinedResults.addAll(parsedVideos)
                } catch (videoEx: Exception) {
                    Log.e("VideoRepository", "Ошибка поиска видео", videoEx)
                }

                if (combinedResults.isNotEmpty()) {
                    lastFetchSource = "Rutube LIVE (Поиск + Каналы)"
                    return combinedResults.distinctBy { it.id }
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
                        val feedObj = JSONObject(feedJsonStr)
                        val tabsArray = feedObj.optJSONArray("tabs")
                        
                        val resourceUrls = mutableListOf<String>()
                        if (tabsArray != null && tabsArray.length() > 0) {
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
                                val categoryVideos = parseVideoListJson(resourceJsonStr, selectedCategoryName, targetResourceUrl)
                                parsedResults.addAll(categoryVideos)
                            } catch (resEx: Exception) {
                                Log.e("VideoRepository", "Error fetching tab resource $targetResourceUrl", resEx)
                            }
                        }
                        
                        if (parsedResults.isNotEmpty()) {
                            lastFetchSource = "Rutube LIVE"
                            return parsedResults.distinctBy { it.id }
                        }
                    } catch (feedEx: Exception) {
                        Log.e("VideoRepository", "Error fetching showcase $categorySlug", feedEx)
                    }
                    
                    try {
                        val fallbackSearchResponse = apiService.searchVideos(selectedCategoryName)
                        val fallbackSearchBody = fallbackSearchResponse.string()
                        val searchVideos = parseVideoListJson(fallbackSearchBody, selectedCategoryName)
                        if (searchVideos.isNotEmpty()) {
                            lastFetchSource = "Rutube LIVE"
                            return searchVideos
                        }
                    } catch (ex: Exception) {
                        Log.e("VideoRepository", "Fallback search failed", ex)
                    }
                } else {
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
            Log.e("VideoRepository", "Rutube API error, falling back to database", e)
        }

        lastFetchSource = "Локальный оффлайн"
        try {
            val savedList = dao.getAllSavedVideos().first()
            return savedList.map { saved ->
                Video(
                    id = saved.id, title = saved.title, channel = saved.channel,
                    views = saved.views, timeAgo = saved.timeAgo, duration = saved.duration,
                    isPro = saved.isPro, category = saved.category,
                    description = "Офлайн-просмотр сохраненного видео",
                    thumbnailUrl = saved.thumbnailUrl, isDownloaded = saved.isDownloaded,
                    isBookmarked = saved.isBookmarked
                )
            }.filter { video ->
                val matchCat = category.isNullOrBlank() || category == "Все" || video.category.equals(category, ignoreCase = true)
                val matchQuery = query.isNullOrBlank() || 
                        video.title.contains(query, ignoreCase = true) || 
                        video.channel.contains(query, ignoreCase = true)
                matchCat && matchQuery
            }
        } catch (dbEx: Exception) {
            return emptyList()
        }
    }

    fun toVideo(item: com.example.data.rutube.RutubeVideoItem, categoryName: String): Video? {
        val videoId = item.id?.takeIf { it.isNotBlank() }
            ?: item.videoId?.takeIf { it.isNotBlank() }
            ?: item.code?.takeIf { it.isNotBlank() } ?: return null

        val title = item.title?.takeIf { it.isNotBlank() } ?: "Без названия"
        val viewsCount = (item.views ?: item.hits ?: 0).toLong()

        return Video(
            id = videoId, title = title, channel = item.author?.name ?: "Канал Rutube",
            views = "${formatCount(viewsCount)} просмотров", timeAgo = "Загружено недавно",
            duration = "10:00", isPro = false, category = categoryName,
            description = item.description ?: "", thumbnailUrl = item.thumbnailUrl ?: "",
            isDownloaded = false, isBookmarked = false
        )
    }

    fun getSavedVideosOnly(): Flow<List<SavedVideo>> {
        return dao.getAllSavedVideos()
    }

    suspend fun deleteVideoById(id: String) {
        dao.deleteById(id)
    }

    suspend fun toggleBookmark(video: Video) {
        val saved = dao.getVideoById(video.id)
        val termBookmark = !(saved?.isBookmarked ?: false)
        val termDownload = saved?.isDownloaded ?: false

        if (!termBookmark && !termDownload) {
            dao.deleteById(video.id)
        } else {
            val updated = SavedVideo(
                id = video.id, title = video.title, channel = video.channel,
                views = video.views, timeAgo = video.timeAgo, duration = video.duration,
                isPro = video.isPro, category = video.category,
                isDownloaded = termDownload, isBookmarked = termBookmark, thumbnailUrl = video.thumbnailUrl
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
                id = video.id, title = video.title, channel = video.channel,
                views = video.views, timeAgo = video.timeAgo, duration = video.duration,
                isPro = video.isPro, category = video.category,
                isDownloaded = termDownload, isBookmarked = termBookmark, thumbnailUrl = video.thumbnailUrl
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
                        categoriesList.add(RutubeCategory(id, title, picture, target))
                        val slug = target.removePrefix("/feeds/").removeSuffix("/")
                        if (slug.isNotBlank()) {
                            dynamicCategoryTargets[title] = slug
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e("VideoRepository", "Error fetching real categories", ex)
        }
        return categoriesList
    }
}
