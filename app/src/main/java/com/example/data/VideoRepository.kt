package com.example.data

import android.util.Log
import com.example.data.rutube.RutubeApiService
import com.example.data.rutube.RutubeVideoItem
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
            val response = apiService.getDynamicUrl(url)
            val videos = response.results?.mapNotNull { toVideo(it, fallbackCategory) } ?: emptyList()
            videos to response.next
        } catch (e: Exception) {
            Log.e("VideoRepository", "loadPageByUrl error", e)
            emptyList<Video>() to null
        }
    }

    private suspend fun searchVideosPage(query: String, page: Int): Pair<List<Video>, String?> {
        return try {
            val response = apiService.searchVideos(query, page = page)
            val videos = response.results?.mapNotNull { toVideo(it, "Поиск: $query") } ?: emptyList()
            lastFetchSource = "Rutube LIVE (поиск)"
            videos to response.next
        } catch (e: Exception) {
            Log.e("VideoRepository", "searchVideosPage error", e)
            emptyList<Video>() to null
        }
    }

    private suspend fun fetchCategoryPage(slug: String, categoryName: String, page: Int): Pair<List<Video>, String?> {
        return try {
            val url = "https://rutube.ru/api/feeds/$slug/?page=$page&format=json"
            val response = apiService.getDynamicUrl(url)
            val videos = response.results?.mapNotNull { toVideo(it, categoryName) } ?: emptyList()
            lastFetchSource = "Rutube LIVE"
            videos to response.next
        } catch (e: Exception) {
            Log.e("VideoRepository", "fetchCategoryPage error for $slug", e)
            emptyList<Video>() to null
        }
    }

    private suspend fun fetchAllCategoriesVideos(): List<Video> {
        return try {
            val response = apiService.getPopularVideos(page = 1)
            lastFetchSource = "Rutube LIVE (Популярное)"
            response.results?.mapNotNull { toVideo(it, "Все") } ?: emptyList()
        } catch (e: Exception) {
            Log.e("VideoRepository", "fetchAllCategoriesVideos error", e)
            emptyList()
        }
    }

    fun toVideo(rawItem: RutubeVideoItem, categoryName: String): Video? {
        val model = rawItem.contentType?.model ?: "video"
        val flatData = rawItem.getFlatItem()

        val videoId = flatData.id
        if (videoId == null) {
            Log.w("VideoRepository", "Пропущена карточка (пустой ID). Модель: $model")
            return null
        }

        // 1. Обработка Каналов (userchannel)
        if (model == "userchannel") {
            return Video(
                id = videoId,
                title = flatData.title ?: "Безымянный канал",
                channel = "Канал • Rutube",
                views = "Подписчиков: много",
                timeAgo = "Автор",
                duration = "ЛЕНТА",
                isPro = false,
                category = categoryName,
                description = flatData.description ?: "Описание отсутствует",
                thumbnailUrl = flatData.thumbnailUrl ?: "",
                isDownloaded = false,
                isBookmarked = false
            )
        }

        // 2. Обработка Сериалов/Шоу (tv)
        if (model == "tv") {
            return Video(
                id = videoId,
                title = flatData.title ?: "Телепередача",
                channel = "Передача / Шоу",
                views = "Rutube Оригинал",
                timeAgo = "Премьера",
                duration = "ШОУ",
                isPro = false,
                category = categoryName,
                description = flatData.description ?: "Описание отсутствует",
                thumbnailUrl = flatData.thumbnailUrl ?: "",
                isDownloaded = false,
                isBookmarked = false
            )
        }

        // 3. Обработка стандартных Видео
        val title = flatData.title ?: "Без названия"
        val thumbnail = flatData.thumbnailUrl ?: ""
        val description = flatData.description ?: "Описание отсутствует"

        val durationStr = flatData.duration?.let { seconds ->
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
            if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, secs)
            else String.format("%02d:%02d", minutes, secs)
        } ?: "10:00"

        val viewsCount = (flatData.views ?: 0).toLong()
        val viewsStr = when {
            viewsCount >= 1_000_000 -> String.format("%.1fM", viewsCount / 1_000_000.0)
            viewsCount >= 1_000 -> "${viewsCount / 1_000}K"
            viewsCount > 0 -> "$viewsCount"
            else -> "0"
        } + " просмотров"

        return Video(
            id = videoId,
            title = title,
            channel = flatData.authorName ?: "Канал Rutube",
            views = viewsStr,
            timeAgo = if (flatData.createdTs != null) "Опубликовано" else "Недавно",
            duration = durationStr,
            isPro = false,
            category = categoryName,
            description = description,
            thumbnailUrl = thumbnail,
            isDownloaded = false,
            isBookmarked = false
        )
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
