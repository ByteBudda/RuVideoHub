package com.example.data

import android.util.Log
import com.example.data.rutube.RutubeApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class VideoRepository(
    private val dao: SavedVideoDao,
    private val apiService: RutubeApiService
) {

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
        "Юмор" to "humor",
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

    suspend fun fetchVideosPage(query: String?, category: String?, pageUrl: String?): Pair<List<Video>, String?> {
        return withContext(Dispatchers.IO) {
            val effectiveCategory = category ?: "Все"
            val effectiveQuery = query?.trim() ?: ""

            if (!pageUrl.isNullOrBlank()) {
                return@withContext loadPageByUrl(pageUrl, effectiveCategory)
            }

            if (effectiveQuery.isNotEmpty()) {
                return@withContext searchVideosPage(effectiveQuery, 1)
            }

            val slug = categorySlugs[effectiveCategory] ?: dynamicCategoryTargets[effectiveCategory]
            if (slug != null && effectiveCategory != "Все") {
                return@withContext fetchCategoryPage(slug, effectiveCategory, 1)
            }

            if (effectiveCategory == "Все") {
                val allVideos = fetchAllCategoriesVideos()
                return@withContext allVideos to null
            }

            emptyList<Video>() to null
        }
    }

    private suspend fun loadPageByUrl(url: String, fallbackCategory: String): Pair<List<Video>, String?> {
        return try {
            val responseBody = apiService.getRawDynamicUrl(url)
            return parseRawJsonToVideos(responseBody.string(), fallbackCategory)
        } catch (e: Exception) {
            Log.e("VideoRepository", "loadPageByUrl error", e)
            emptyList<Video>() to null
        }
    }

    private suspend fun searchVideosPage(query: String, page: Int): Pair<List<Video>, String?> {
        return try {
            val url = "https://rutube.ru/api/search/video/?query=$query&page=$page&format=json"
            val responseBody = apiService.getRawDynamicUrl(url)
            lastFetchSource = "Rutube LIVE (поиск)"
            return parseRawJsonToVideos(responseBody.string(), "Поиск: $query")
        } catch (e: Exception) {
            Log.e("VideoRepository", "searchVideosPage error", e)
            emptyList<Video>() to null
        }
    }

    private suspend fun fetchCategoryPage(slug: String, categoryName: String, page: Int): Pair<List<Video>, String?> {
        return try {
            val url = "https://rutube.ru/api/feeds/$slug/?page=$page&format=json"
            val responseBody = apiService.getRawDynamicUrl(url)
            lastFetchSource = "Rutube LIVE"
            return parseRawJsonToVideos(responseBody.string(), categoryName)
        } catch (e: Exception) {
            Log.e("VideoRepository", "fetchCategoryPage error for $slug", e)
            emptyList<Video>() to null
        }
    }

    private suspend fun fetchAllCategoriesVideos(): List<Video> {
        return try {
            val responseBody = apiService.getRawDynamicUrl("https://rutube.ru/api/feeds/popular/?format=json")
            lastFetchSource = "Rutube LIVE (Популярное)"
            val (videos, _) = parseRawJsonToVideos(responseBody.string(), "Все")
            videos
        } catch (e: Exception) {
            Log.e("VideoRepository", "fetchAllCategoriesVideos error", e)
            emptyList()
        }
    }

    /**
     * Прямой порт твоего логики из parser.js для обработки структуры Rutube API.
     */
    private fun parseRawJsonToVideos(jsonStr: String, categoryName: String): Pair<List<Video>, String?> {
        val videosList = mutableListOf<Video>()
        var nextUrl: String? = null

        try {
            val jsonObj = JSONObject(jsonStr)
            nextUrl = jsonObj.optString("next").takeIf { it.isNotBlank() && it != "null" }
            
            val resultsArray = jsonObj.optJSONArray("results")
            if (resultsArray != null) {
                for (i in 0 until resultsArray.length()) {
                    try {
                        val rawItem = resultsArray.optJSONObject(i) ?: continue
                        
                        // Извлекаем content_type.model
                        val contentTypeObj = rawItem.optJSONObject("content_type")
                        val model = contentTypeObj?.optString("model") ?: "video"
                        
                        // Логика isNested: если есть content_type и поле object
                        val nestedObject = rawItem.optJSONObject("object")
                        val item = if (contentTypeObj != null && nestedObject != null) nestedObject else rawItem

                        // Безопасное извлечение ID (число или строка)
                        val videoId = item.optString("id").takeIf { it.isNotBlank() && it != "null" }
                            ?: item.optString("video_id").takeIf { it.isNotBlank() && it != "null" }
                            ?: item.optString("code").takeIf { it.isNotBlank() && it != "null" }

                        if (videoId == null) {
                            continue
                        }

                        // 1. Модель КАНАЛ (userchannel)
                        if (model == "userchannel") {
                            val channelName = item.optString("name").takeIf { it.isNotBlank() } ?: "Безымянный канал"
                            val avatar = item.optString("user_channel_image").takeIf { it.isNotBlank() }
                                ?: item.optString("picture").takeIf { it.isNotBlank() }
                                ?: item.optString("thumbnail_url")
                            
                            videosList.add(
                                Video(
                                    id = videoId,
                                    title = channelName,
                                    channel = "Канал • Rutube",
                                    views = "Подписчиков: много",
                                    timeAgo = "Автор контента",
                                    duration = "ЛЕНТА",
                                    isPro = false,
                                    category = categoryName,
                                    description = item.optString("description", "Описание канала отсутствует"),
                                    thumbnailUrl = avatar ?: "",
                                    isDownloaded = false,
                                    isBookmarked = false
                                )
                            )
                            continue
                        }

                        // 2. Модель СЕРИАЛ / ШОУ (tv)
                        if (model == "tv") {
                            val showTitle = item.optString("title").takeIf { it.isNotBlank() }
                                ?: item.optString("original_title").takeIf { it.isNotBlank() }
                                ?: item.optString("name").takeIf { it.isNotBlank() }
                                ?: "Телепередача"
                            val poster = item.optString("poster_url").takeIf { it.isNotBlank() }
                                ?: item.optString("thumbnail_url").takeIf { it.isNotBlank() }
                                ?: item.optString("picture")

                            videosList.add(
                                Video(
                                    id = videoId,
                                    title = showTitle,
                                    channel = "Передача / Шоу",
                                    views = "Rutube оригинальный контент",
                                    timeAgo = "Премьера",
                                    duration = "ШОУ",
                                    isPro = false,
                                    category = categoryName,
                                    description = item.optString("description", "Описание передачи отсутствует"),
                                    thumbnailUrl = poster ?: "",
                                    isDownloaded = false,
                                    isBookmarked = false
                                )
                            )
                            continue
                        }

                        // 3. Стандартное ВИДЕО
                        val title = item.optString("title").takeIf { it.isNotBlank() } ?: "Без названия"
                        val description = item.optString("description", "Описание отсутствует")
                        
                        val thumbnail = item.optString("thumbnail_url").takeIf { it.isNotBlank() }
                            ?: item.optString("picture_url").takeIf { it.isNotBlank() }
                            ?: item.optString("poster_url") ?: ""

                        val seconds = item.optInt("duration", -1)
                        val durationStr = if (seconds > 0) {
                            val h = seconds / 3600
                            val m = (seconds % 3600) / 60
                            val s = seconds % 60
                            if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
                        } else "10:00"

                        val viewsCount = item.optLong("views", item.optLong("hits", item.optLong("views_count", 0)))
                        val viewsStr = when {
                            viewsCount >= 1_000_000 -> String.format("%.1fM", viewsCount / 1_000_000.0)
                            viewsCount >= 1_000 -> "${viewsCount / 1_000}K"
                            viewsCount > 0 -> "$viewsCount"
                            else -> "0"
                        } + " просмотров"

                        // Парсинг автора
                        val authorObj = item.optJSONObject("author")
                        val channelName = authorObj?.optString("name")?.takeIf { it.isNotBlank() }
                            ?: item.optString("author_name").takeIf { it.isNotBlank() }
                            ?: item.optString("feed_name").takeIf { it.isNotBlank() }
                            ?: authorObj?.optString("username")?.takeIf { it.isNotBlank() }
                            ?: "Канал Rutube"

                        videosList.add(
                            Video(
                                id = videoId,
                                title = title,
                                channel = channelName,
                                views = viewsStr,
                                timeAgo = "Недавно",
                                duration = durationStr,
                                isPro = false,
                                category = categoryName,
                                description = description,
                                thumbnailUrl = thumbnail,
                                isDownloaded = false,
                                isBookmarked = false
                            )
                        )
                    } catch (itemException: Exception) {
                        // Если одна карточка сломалась, не ломаем весь список, а просто скипаем её
                        Log.e("VideoRepository", "Ошибка разбора отдельного элемента", itemException)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VideoRepository", "Ошибка парсинга сырого JSON списка видео", e)
        }

        return videosList to nextUrl
    }

    suspend fun deleteVideoById(videoId: String) {
        dao.deleteById(videoId)
    }

    suspend fun toggleBookmark(video: Video) {
        val saved = dao.getVideoById(video.id)
        val newBookmark = !(saved?.isBookmarked ?: false)
        val download = saved?.isDownloaded ?: false
        if (!newBookmark && !download) {
            dao.deleteById(video.id)
        } else {
            dao.insertOrUpdate(
                SavedVideo(
                    id = video.id,
                    title = video.title,
                    channel = video.channel,
                    views = video.views,
                    timeAgo = video.timeAgo,
                    duration = video.duration,
                    isPro = video.isPro,
                    category = video.category,
                    isDownloaded = download,
                    isBookmarked = newBookmark,
                    thumbnailUrl = video.thumbnailUrl
                )
            )
        }
    }

    suspend fun toggleDownload(video: Video) {
        val saved = dao.getVideoById(video.id)
        val newDownload = !(saved?.isDownloaded ?: false)
        val bookmark = saved?.isBookmarked ?: false
        if (!bookmark && !newDownload) {
            dao.deleteById(video.id)
        } else {
            dao.insertOrUpdate(
                SavedVideo(
                    id = video.id,
                    title = video.title,
                    channel = video.channel,
                    views = video.views,
                    timeAgo = video.timeAgo,
                    duration = video.duration,
                    isPro = video.isPro,
                    category = video.category,
                    isDownloaded = newDownload,
                    isBookmarked = bookmark,
                    thumbnailUrl = video.thumbnailUrl
                )
            )
        }
    }

    suspend fun fetchRealCategories(): List<RutubeCategory> {
        val categoriesList = mutableListOf<RutubeCategory>()
        try {
            val responseBody = apiService.getRawDynamicUrl("https://rutube.ru/api/v1/feeds/promogroup/382/?format=json")
            val jsonStr = responseBody.string() 
            
            val jsonObj = JSONObject(jsonStr)
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
                        val slug = target.removePrefix("/feeds/").removePrefix("feeds/").trim('/')
                        if (slug.isNotBlank()) {
                            dynamicCategoryTargets[title] = slug
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VideoRepository", "fetchRealCategories error", e)
        }
        return categoriesList
    }

    suspend fun fetchRealVideos(query: String?, category: String?, page: Int = 1): List<Video> {
        return withContext(Dispatchers.IO) {
            val effectiveQuery = query?.trim() ?: ""
            val effectiveCategory = category ?: "Все"

            if (effectiveQuery.isNotEmpty()) {
                val (videos, _) = searchVideosPage(effectiveQuery, page)
                return@withContext videos
            }

            val slug = categorySlugs[effectiveCategory] ?: dynamicCategoryTargets[effectiveCategory]
            if (slug != null && effectiveCategory != "Все") {
                val (videos, _) = fetchCategoryPage(slug, effectiveCategory, page)
                return@withContext videos
            }

            if (effectiveCategory == "Все") {
                return@withContext fetchAllCategoriesVideos()
            }

            emptyList()
        }
    }

    fun getSavedVideosOnly(): Flow<List<SavedVideo>> = dao.getAllSavedVideos()
}
