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
     * Универсальный бронебойный парсер списков.
     * Полностью разбирает дерево Rutube: плоские списки, промо-блоки, 
     * вложенные структуры (cardgroup, promogroup) и вытягивает видео, каналы и сериалы.
     */
    fun parseVideoListJson(bodyString: String, defaultCategoryName: String): List<Video> {
        val mapped = mutableListOf<Video>()
        val trimmed = bodyString.trim()
        if (!trimmed.startsWith("{")) {
            Log.w("VideoRepository", "Response body for category '$defaultCategoryName' is not a JSON object.")
            return mapped
        }
        try {
            val jsonObj = JSONObject(trimmed)
            val resultsArray = jsonObj.optJSONArray("results") ?: return emptyList()
            
            for (i in 0 until resultsArray.length()) {
                try {
                    val rawItem = resultsArray.optJSONObject(i) ?: continue
                    
                    // Рекурсивный или сквозной разбор вложенных контейнеров (cardgroup / promogroup)
                    val contentTypeObj = rawItem.optJSONObject("content_type")
                    var modelType = contentTypeObj?.optString("model") 
                        ?: rawItem.optString("type").takeIf { it.isNotBlank() } 
                        ?: "video"

                    var itemObj = rawItem
                    if (rawItem.has("object") && contentTypeObj != null) {
                        val nestedObj = rawItem.optJSONObject("object")
                        if (nestedObj != null) {
                            itemObj = nestedObj
                            // Если внутри контейнера тип модели переопределен
                            val nestedContentType = itemObj.optJSONObject("content_type")?.optString("model")
                            if (!nestedContentType.isNullOrBlank()) {
                                modelType = nestedContentType
                            }
                        }
                    }

                    // Идентификатор (код, видео_ид или обычный ид)
                    val idVal = itemObj.optString("code")
                        .takeIf { it.isNotBlank() && it != "null" }
                        ?: itemObj.optString("video_id")
                        .takeIf { it.isNotBlank() && it != "null" }
                        ?: itemObj.optString("id")
                        .takeIf { it.isNotBlank() && it != "null" }
                        ?: continue

                    // 1. Модель КАНАЛ (userchannel) или наличие признака подписчиков
                    if (modelType == "userchannel" || itemObj.has("subscribers_count")) {
                        val nameVal = itemObj.optString("name").takeIf { it.isNotBlank() } ?: "Авторский канал"
                        val avatarVal = itemObj.optString("avatar_url")
                            .takeIf { it.isNotBlank() }
                            ?: itemObj.optString("user_channel_image")
                            .takeIf { it.isNotBlank() }
                            ?: itemObj.optString("picture")
                            .takeIf { it.isNotBlank() }
                            ?: itemObj.optString("icon")
                            .takeIf { it.isNotBlank() } ?: ""
                        
                        val subscribersCount = itemObj.optLong("subscribers_count", 0L)
                        val formattedSubs = if (subscribersCount >= 1000000) {
                            String.format("%.1fМ", subscribersCount / 1000000.0)
                        } else if (subscribersCount >= 1000) {
                            "${subscribersCount / 1000}К"
                        } else {
                            "$subscribersCount"
                        }
                        
                        val videoCount = itemObj.optInt("video_count", 0)
                        val descriptionVal = itemObj.optString("description", "Официальный канал Rutube.")
                        
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
                    } 
                    // 2. Модель СЕРИАЛ / ТВ-ШОУ (tv) или наличие признака сезонов
                    else if (modelType == "tv" || itemObj.has("seasons_count")) {
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
                            ?: itemObj.optJSONArray("images")?.optJSONObject(0)?.optString("image") ?: ""
                            
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
                } catch (itemEx: Exception) {
                    Log.e("VideoRepository", "Ошибка разбора отдельного элемента дерева", itemEx)
                }
            }
        } catch (ex: Exception) {
            Log.e("VideoRepository", "Error parsing results JSON list", ex)
        }
        return mapped
    }

    suspend fun fetchRealVideos(query: String?, category: String?): List<Video> {
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
                                val categoryVideos = parseVideoListJson(resourceJsonStr, selectedCategoryName)
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
                        Log.e("VideoRepository", "Error fetching showcase $categorySlug, falling back to live search", feedEx)
                    }
                    
                    // Фолбек: поиск по названию категории, если витрина пустая
                    try {
                        val fallbackSearchResponse = apiService.searchVideos(selectedCategoryName)
                        val fallbackSearchBody = fallbackSearchResponse.string()
                        val searchVideos = parseVideoListJson(fallbackSearchBody, selectedCategoryName)
                        if (searchVideos.isNotEmpty()) {
                            lastFetchSource = "Rutube LIVE"
                            return searchVideos
                        }
                    } catch (ex: Exception) {
                        Log.e("VideoRepository", "Fallback search for category $selectedCategoryName failed", ex)
                    }
                } else {
                    // Тренды / Популярное по умолчанию
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
            Log.e("VideoRepository", "Rutube API error, falling back to offline database", e)
        }

        // Офлайн-режим базы Room
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
            if (filteredSaved.isNotEmpty()) {
                return filteredSaved
            } else {
                lastFetchSource = "Встроенные хиты"
                val defaultVideos = listOf(
                    Video(
                        id = "fallback_mock_1",
                        title = "Музыкальный хит: Космическое Путешествие",
                        channel = "Dreamer Records",
                        views = "500К просмотров",
                        timeAgo = "1 день назад",
                        duration = "03:45",
                        isPro = false,
                        category = "Музыка",
                        description = "Красивый успокаивающий инструментальный клип для работы и сна в Sleek Video Hub.",
                        thumbnailUrl = "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?w=500",
                        isDownloaded = false,
                        isBookmarked = false
                    ),
                    Video(
                        id = "fallback_mock_2",
                        title = "Качественные технологии будущего в 2026 году",
                        channel = "TechFocus",
                        views = "1.2М просмотров",
                        timeAgo = "3 дня назад",
                        duration = "10:15",
                        isPro = true,
                        category = "Технологии",
                        description = "Обзор революционных девайсов, инноваций и умной техники в современном разрешении.",
                        thumbnailUrl = "https://images.unsplash.com/photo-1519389950473-47ba0277781c?w=500",
                        isDownloaded = false,
                        isBookmarked = false
                    ),
                    Video(
                        id = "fallback_mock_3",
                        title = "Невероятный юмор: Смешные курьезы из жизни",
                        channel = "SmileTime",
                        views = "350К просмотров",
                        timeAgo = "5 дней назад",
                        duration = "07:20",
                        isPro = false,
                        category = "Юмор",
                        description = "Подборка лучших веселых жизненных моментов, которые поднимут вам настроение на весь день.",
                        thumbnailUrl = "https://images.unsplash.com/photo-1527224857830-43a7acc85260?w=500",
                        isDownloaded = false,
                        isBookmarked = false
                    )
                )
                return defaultVideos.filter { video ->
                    val matchCat = category.isNullOrBlank() || category == "Все" || video.category.equals(category, ignoreCase = true)
                    val matchQuery = query.isNullOrBlank() || 
                            video.title.contains(query, ignoreCase = true) || 
                            video.channel.contains(query, ignoreCase = true)
                    matchCat && matchQuery
                }
            }
        } catch (dbEx: Exception) {
            return emptyList()
        }
    }

    /**
     * ВОЗВРАЩЕНО ДЛЯ СОВМЕСТИМОСТИ С VideoViewModel.kt
     * Закрывает ошибку компиляции на CI сервере Гитхаба ("Unresolved reference 'toVideo'")
     */
    fun toVideo(item: com.example.data.rutube.RutubeVideoItem, categoryName: String): Video? {
        val videoId = item.id?.takeIf { it.isNotBlank() }
            ?: item.videoId?.takeIf { it.isNotBlank() }
            ?: item.code?.takeIf { it.isNotBlank() }

        if (videoId == null) return null

        val title = item.title?.takeIf { it.isNotBlank() } ?: "Без названия"
        val description = item.description ?: "Описание отсутствует"
        val thumbnail = item.thumbnailUrl ?: ""

        val seconds = item.duration ?: -1
        val durationStr = if (seconds > 0) {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
        } else "10:00"

        val viewsCount = (item.views ?: item.hits ?: 0).toLong()
        val viewsStr = when {
            viewsCount >= 1_000_000 -> String.format("%.1fМ просмотров", viewsCount / 1_000_000.0)
            viewsCount >= 1_000 -> "${viewsCount / 1_000}К просмотров"
            viewsCount > 0 -> "$viewsCount просмотров"
            else -> "0 просмотров"
        }

        val channelName = item.author?.name?.takeIf { it.isNotBlank() }
            ?: item.author?.username?.takeIf { it.isNotBlank() }
            ?: "Канал Rutube"

        return Video(
            id = videoId,
            title = title,
            channel = channelName,
            views = viewsStr,
            timeAgo = "Загружено недавно",
            duration = durationStr,
            isPro = false,
            category = categoryName,
            description = description,
            thumbnailUrl = thumbnail,
            isDownloaded = false,
            isBookmarked = false
        )
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
            Log.e("VideoRepository", "Error fetching real categories", ex)
        }
        return categoriesList
    }
}
