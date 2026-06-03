package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import com.example.data.rutube.SmartRutubeParser

class VideoRepository(private val dao: SavedVideoDao) {

    var lastFetchSource: String = "Инициализация"
        private set

    private val dynamicCategoryTargets = java.util.concurrent.ConcurrentHashMap<String, String>()

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

    /**
     * Возвращает Flow со списком сохраненных видео из локальной БД Room
     */
    fun getSavedVideosOnly(): Flow<List<SavedVideo>> = dao.getAllSavedVideos()

    /**
     * Парсинг сырой JSON-строки от Rutube через SmartRutubeParser.
     * Используется во ViewModel при обработке динамических URL страниц пагинации.
     */
    fun parseVideoListJson(jsonStr: String, categoryName: String): List<Video> {
        return try {
            val jsonObj = JSONObject(jsonStr.trim())
            val parsed = SmartRutubeParser.ResponseAnalyzer.parse(jsonObj)
            parsed.items.map { mapNormalizedCardToVideo(it, categoryName) }
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error parsing video list JSON", e)
            emptyList()
        }
    }

    /**
     * 1. ПОЛУЧИТЬ СТРУКТУРУ КАТАЛОГА / КАНАЛА (ФИД С ВКЛАДКАМИ)
     */
    suspend fun fetchFeedContainer(targetUrl: String): SmartRutubeParser.ParsedResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                val response = apiService.getDynamicUrl(targetUrl)
                val bodyString = response.string()
                
                val jsonObj = JSONObject(bodyString.trim())
                val parsed = SmartRutubeParser.ResponseAnalyzer.parse(jsonObj, targetUrl)
                
                lastFetchSource = "Rutube LIVE (Feed)"
                parsed
            } catch (e: Exception) {
                Log.e("VideoRepository", "Error fetching feed container from $targetUrl", e)
                null
            }
        }
    }

    /**
     * 2. ЗАГРУЗИТЬ КОНТЕНТ ДЛЯ КОНКРЕТНОЙ ВКЛАДКИ (ПО ЕЁ URL)
     */
    suspend fun fetchContentByUrl(resourceUrl: String, page: Int = 1): List<Video> {
        return withContext(Dispatchers.IO) {
            try {
                val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                val paginatedUrl = if (resourceUrl.contains("?")) {
                    "$resourceUrl&page=$page"
                } else {
                    "$resourceUrl?page=$page"
                }

                val response = apiService.getDynamicUrl(paginatedUrl)
                val bodyString = response.string()
                
                val jsonObj = JSONObject(bodyString.trim())
                val parsed = SmartRutubeParser.ResponseAnalyzer.parse(jsonObj, resourceUrl)
                
                parsed.items.map { mapNormalizedCardToVideo(it, "Каталог") }
            } catch (e: Exception) {
                Log.e("VideoRepository", "Error fetching content for tab $resourceUrl", e)
                emptyList()
            }
        }
    }

    /**
     * Модифицированный метод для поиска и категорий (через SmartRutubeParser)
     */
    suspend fun fetchRealVideos(query: String?, category: String?, page: Int = 1): List<Video> {
        return withContext(Dispatchers.IO) {
            try {
                val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                val q = query?.trim() ?: ""
                val selectedCategoryName = category ?: "Фильмы"
                
                if (q.isNotEmpty()) {
                    val responseBody = apiService.searchVideos(q, page = page)
                    val jsonObj = JSONObject(responseBody.string().trim())
                    val parsed = SmartRutubeParser.ResponseAnalyzer.parse(jsonObj)
                    lastFetchSource = "Rutube API (Поиск)"
                    return@withContext parsed.items.map { mapNormalizedCardToVideo(it, "Поиск: $q") }
                } else {
                    val categorySlug = categorySlugs[selectedCategoryName] ?: dynamicCategoryTargets[selectedCategoryName]
                    if (categorySlug != null) {
                        val feedUrl = "https://rutube.ru/api/feeds/$categorySlug/?format=json&page=$page"
                        val feedContainer = fetchFeedContainer(feedUrl)
                        val firstTabUrl = feedContainer?.tabs?.firstOrNull()?.resources?.firstOrNull()?.url
                        if (firstTabUrl != null) {
                            return@withContext fetchContentByUrl(firstTabUrl, page)
                        }
                    }
                    
                    val responseBody = apiService.getPopularVideos(page = page)
                    val parsed = SmartRutubeParser.ResponseAnalyzer.parse(JSONObject(responseBody.string().trim()))
                    lastFetchSource = "Rutube API (Популярное)"
                    return@withContext parsed.items.map { mapNormalizedCardToVideo(it, "Популярное") }
                }
            } catch (e: Exception) {
                Log.e("VideoRepository", "Rutube API error, falling back to offline", e)
            }

            return@withContext fetchOfflineFallback(query, category)
        }
    }

    private fun fetchOfflineFallback(query: String?, category: String?): List<Video> {
        return try {
            val savedList = dao.getAllSavedVideos().first()
            savedList.map { saved ->
                Video(
                    id = saved.id, title = saved.title, channel = saved.channel,
                    views = saved.views, timeAgo = saved.timeAgo, duration = saved.duration,
                    isPro = saved.isPro, category = saved.category, description = "Офлайн",
                    thumbnailUrl = saved.thumbnailUrl, isDownloaded = saved.isDownloaded, isBookmarked = saved.isBookmarked
                )
            }.filter { video ->
                val matchCat = category.isNullOrBlank() || video.category.equals(category, ignoreCase = true)
                val matchQuery = query.isNullOrBlank() || video.title.contains(query, ignoreCase = true)
                matchCat && matchQuery
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun fetchRealCategories(): List<RutubeCategory> {
        return withContext(Dispatchers.IO) {
            categorySlugs.keys.map { name ->
                RutubeCategory(id = name.hashCode().toString(), name = name, slug = categorySlugs[name] ?: "")
            }
        }
    }

    // --- МЕТОДЫ УПРАВЛЕНИЯ ЛОКАЛЬНОЙ БАЗОЙ ДАННЫХ ДЛЯ VIEWMODEL ---

    suspend fun toggleBookmark(video: Video) = withContext(Dispatchers.IO) {
        val existing = dao.getVideoById(video.id)
        if (existing != null) {
            dao.insertOrUpdate(existing.copy(isBookmarked = !existing.isBookmarked))
        } else {
            dao.insertOrUpdate(
                SavedVideo(
                    id = video.id, title = video.title, channel = video.channel, views = video.views,
                    timeAgo = video.timeAgo, duration = video.duration, isPro = video.isPro,
                    category = video.category, thumbnailUrl = video.thumbnailUrl,
                    isDownloaded = false, isBookmarked = true
                )
            )
        }
    }

    suspend fun toggleDownload(video: Video) = withContext(Dispatchers.IO) {
        val existing = dao.getVideoById(video.id)
        if (existing != null) {
            dao.insertOrUpdate(existing.copy(isDownloaded = !existing.isDownloaded))
        } else {
            dao.insertOrUpdate(
                SavedVideo(
                    id = video.id, title = video.title, channel = video.channel, views = video.views,
                    timeAgo = video.timeAgo, duration = video.duration, isPro = video.isPro,
                    category = video.category, thumbnailUrl = video.thumbnailUrl,
                    isDownloaded = true, isBookmarked = false
                )
            )
        }
    }

    suspend fun deleteVideoById(id: String) = withContext(Dispatchers.IO) {
        val existing = dao.getVideoById(id)
        if (existing != null) {
            // Если видео в закладках, просто снимаем флаг скачивания, иначе удаляем совсем
            if (existing.isBookmarked) {
                dao.insertOrUpdate(existing.copy(isDownloaded = false))
            } else {
                dao.delete(existing)
            }
        }
    }

    private fun mapNormalizedCardToVideo(card: SmartRutubeParser.NormalizedCard, defaultCategoryName: String): Video {
        return when (card) {
            is SmartRutubeParser.NormalizedCard.VideoCard -> {
                Video(
                    id = card.id, title = card.title, channel = card.channelName,
                    views = card.views, timeAgo = card.published, duration = card.duration,
                    isPro = false, category = defaultCategoryName, description = card.description, thumbnailUrl = card.thumbnail
                )
            }
            is SmartRutubeParser.NormalizedCard.TvShowCard -> {
                val ratingStr = if (card.rating != null && card.rating > 0.05) " • КП: ${card.rating}" else ""
                Video(
                    id = "tv_${card.id}", title = card.title, channel = "Шоу • ${card.year ?: "Передача"}$ratingStr",
                    views = "${card.seasonsCount} сезонов", timeAgo = "Смотреть выпуски", duration = "СЕРИАЛ",
                    isPro = false, category = defaultCategoryName, description = card.description ?: "", thumbnailUrl = card.poster
                )
            }
            is SmartRutubeParser.NormalizedCard.ChannelCard -> {
                Video(
                    id = "channel_${card.id}", title = card.name, channel = "Авторский канал • ${card.subscribers} подписчиков",
                    views = "${card.subscribers} подписчиков", timeAgo = "${card.videosCount} видео", duration = "КАНАЛ",
                    isPro = false, category = defaultCategoryName, description = card.description ?: "", thumbnailUrl = card.avatar
                )
            }
            is SmartRutubeParser.NormalizedCard.PromoCard -> {
                Video(
                    id = "promo_${card.id}", title = card.title, channel = "Реклама", views = "Промо",
                    timeAgo = "Перейти", duration = "ПРОМО", isPro = true, category = defaultCategoryName,
                    description = card.description ?: "", thumbnailUrl = card.thumbnail
                )
            }
            is SmartRutubeParser.NormalizedCard.UnknownCard -> {
                Video(
                    id = "unknown_${Math.random()}", title = card.title, channel = card.rawType ?: "Неизвестно",
                    views = "0 просмотров", timeAgo = "Недавно", duration = "00:00", isPro = false,
                    category = defaultCategoryName, description = "Элемент каталога", thumbnailUrl = card.thumbnail
                )
            }
        }
    }
}
