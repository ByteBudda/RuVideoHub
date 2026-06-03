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

// Предполагаем, что эти дата-классы объявлены в твоем проекте. 
// Если имена отличаются (например, RutubeCategory), подправь под свою модель.
data class RutubeCategory(val id: Int, val title: String, val picture: String, val target: String)

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
            // Внимание: базовые slug фидов (типа 'movies') отдают структуру разметки страниц, а не списки видео.
            // Для работы напрямую через API, этот URL используется как фоллбек.
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
            // Используем официальную витрину популярного контента для дефолтной страницы
            val response = apiService.getPopularVideos(page = 1)
            lastFetchSource = "Rutube LIVE (Популярное)"
            response.results?.mapNotNull { toVideo(it, "Все") } ?: emptyList()
        } catch (e: Exception) {
            Log.e("VideoRepository", "fetchAllCategoriesVideos (Popular) error", e)
            emptyList()
        }
    }

    fun toVideo(item: RutubeVideoItem, categoryName: String): Video? {
        val videoId = item.id?.takeIf { it.isNotBlank() }
            ?: item.videoId?.takeIf { it.isNotBlank() }
            ?: item.code?.takeIf { it.isNotBlank() }

        if (videoId == null) {
            Log.w("VideoRepository", "Пропущено видео, пустой ID. Модель: ${item.contentType?.model}")
            return null
        }

        val title = item.title?.takeIf { it.isNotBlank() } ?: "Без названия"
        val description = item.description ?: "Описание отсутствует"
        val thumbnail = item.thumbnailUrl ?: ""

        val durationStr = item.duration?.let { seconds ->
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
            if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, secs)
            else String.format("%02d:%02d", minutes, secs)
        } ?: "10:00"

        val viewsCount = (item.views ?: item.hits ?: 0).toLong()
        val viewsStr = when {
            viewsCount >= 1_000_000 -> String.format("%.1fM", viewsCount / 1_000_000.0)
            viewsCount >= 1_000 -> "${viewsCount / 1_000}K"
            viewsCount > 0 -> "$viewsCount"
            else -> "0"
        } + " просмотров"

        val channelName = item.author?.name?.takeIf { it.isNotBlank() }
            ?: item.author?.username?.takeIf { it.isNotBlank() }
            ?: "Канал Rutube"

        return Video(
            id = videoId,
            title = title,
            channel = channelName,
            views = viewsStr,
            timeAgo = item.createdTs?.let { "Опубликовано" } ?: "Недавно",
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
            // Запрашиваем сырой ResponseBody для ручного парсинга нативного JSON
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
                        // Парсим slug: убираем префиксы и слэши по краям
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
