package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.json.JSONArray
import com.example.data.rutube.SmartRutubeParser

class VideoRepository(private val dao: SavedVideoDao) {

    var lastFetchSource: String = "Инициализация"
        private set

    // Кэш для динамических разделов (опционально, если нужно хранить слаги)
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
     * 1. ПОЛУЧИТЬ СТРУКТУРУ КАТАЛОГА / КАНАЛА (ФИД С ВКЛАДКАМИ)
     * Вызывай этот метод, когда открываешь категорию или канал автора.
     */
    suspend fun fetchFeedContainer(targetUrl: String): SmartRutubeParser.ParsedResponse? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                val response = apiService.getDynamicUrl(targetUrl)
                val bodyString = response.string()
                
                val jsonObj = JSONObject(bodyString.trim())
                val parsed = SmartRutubeParser.ResponseAnalyzer.parse(jsonObj, targetUrl)
                
                lastFetchSource = "Rutube LIVE (Feed)"
                parsed
            } catch (e: Exception) {
                android.util.Log.e("VideoRepository", "Error fetching feed container from $targetUrl", e)
                null
            }
        }
    }

    /**
     * 2. ЗАГРУЗИТЬ КОНТЕНТ ДЛЯ КОНКРЕТНОЙ ВКЛАДКИ (ПО ЕЁ URL)
     * Возвращает список нормализованных карточек (видео, шоу, каналы)
     */
    suspend fun fetchContentByUrl(resourceUrl: String, page: Int = 1): List<Video> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
                
                // Маппим NormalizedCard в твою UI-модель Video
                parsed.items.map { mapNormalizedCardToVideo(it, "Каталог") }
            } catch (e: Exception) {
                android.util.Log.e("VideoRepository", "Error fetching content for tab $resourceUrl", e)
                emptyList()
            }
        }
    }

    /**
     * Модифицированный старый метод для совместимости со старыми экранами (Поиск, Главная).
     * Теперь он тоже работает через умный парсер!
     */
    suspend fun fetchRealVideos(query: String?, category: String?, page: Int = 1): List<Video> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                val q = query?.trim() ?: ""
                val selectedCategoryName = category ?: "Фильмы"
                
                if (q.isNotEmpty()) {
                    val responseBody = apiService.searchVideos(q, page = page)
                    val jsonObj = JSONObject(responseBody.string().trim())
                    val parsed = SmartRutubeParser.ResponseAnalyzer.parse(jsonObj)
                    return@withContext parsed.items.map { mapNormalizedCardToVideo(it, "Поиск: $q") }
                } else {
                    val categorySlug = categorySlugs[selectedCategoryName] ?: dynamicCategoryTargets[selectedCategoryName]
                    if (categorySlug != null) {
                        val feedUrl = "https://rutube.ru/api/feeds/$categorySlug/?format=json&page=$page"
                        // Если это сложный фид, забираем контент первой вкладки по умолчанию
                        val feedContainer = fetchFeedContainer(feedUrl)
                        val firstTabUrl = feedContainer?.tabs?.firstOrNull()?.resources?.firstOrNull()?.url
                        if (firstTabUrl != null) {
                            return@withContext fetchContentByUrl(firstTabUrl, page)
                        }
                    }
                    
                    // Популярное по дефолту
                    val responseBody = apiService.getPopularVideos(page = page)
                    val parsed = SmartRutubeParser.ResponseAnalyzer.parse(JSONObject(responseBody.string().trim()))
                    return@withContext parsed.items.map { mapNormalizedCardToVideo(it, "Популярное") }
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoRepository", "Rutube API error, falling back to offline", e)
            }

            // Оффлайн фолбек (твой старый код без изменений)
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

    // Твой маппер из Смарт-Карточек в UI-модель Video
    private fun mapNormalizedCardToVideo(card: SmartRutubeParser.NormalizedCard, defaultCategoryName: String): Video {
        return when (card) {
            is SmartRutubeParser.NormalizedCard.VideoCard -> {
                Video(
                    id = card.id,
                    title = card.title,
                    channel = card.channelName,
                    views = card.views,
                    timeAgo = card.published,
                    duration = card.duration,
                    isPro = false,
                    category = defaultCategoryName,
                    description = card.description,
                    thumbnailUrl = card.thumbnail
                )
            }
            is SmartRutubeParser.NormalizedCard.TvShowCard -> {
                val ratingStr = if (card.rating != null && card.rating > 0.05) " • КП: ${card.rating}" else ""
                Video(
                    id = "tv_${card.id}",
                    title = card.title,
                    channel = "Шоу • ${card.year ?: "Передача"}$ratingStr",
                    views = "${card.seasonsCount} сезонов",
                    timeAgo = "Смотреть выпуски",
                    duration = "СЕРИАЛ",
                    isPro = false,
                    category = defaultCategoryName,
                    description = card.description ?: "",
                    thumbnailUrl = card.poster
                )
            }
            is SmartRutubeParser.NormalizedCard.ChannelCard -> {
                Video(
                    id = "channel_${card.id}",
                    title = card.name,
                    channel = "Авторский канал • ${card.subscribers} подписчиков",
                    views = "${card.subscribers} подписчиков",
                    timeAgo = "${card.videosCount} видео",
                    duration = "КАНАЛ",
                    isPro = false,
                    category = defaultCategoryName,
                    description = card.description ?: "",
                    thumbnailUrl = card.avatar
                )
            }
            is SmartRutubeParser.NormalizedCard.PromoCard -> {
                Video(
                    id = "promo_${card.id}",
                    title = card.title,
                    channel = "Реклама",
                    views = "Промо",
                    timeAgo = "Перейти",
                    duration = "ПРОМО",
                    isPro = true,
                    category = defaultCategoryName,
                    description = card.description ?: "",
                    thumbnailUrl = card.thumbnail
                )
            }
            is SmartRutubeParser.NormalizedCard.UnknownCard -> {
                Video(
                    id = "unknown_${Math.random()}",
                    title = card.title,
                    channel = card.rawType ?: "Неизвестно",
                    views = "0 просмотров",
                    timeAgo = "Недавно",
                    duration = "00:00",
                    isPro = false,
                    category = defaultCategoryName,
                    description = "Элемент каталога",
                    thumbnailUrl = card.thumbnail
                )
            }
        }
    }

    // --- Остальные твои методы (getVideosFlow, toggleBookmark, fetchRealCategories и т.д.) оставляешь без изменений ---
}
