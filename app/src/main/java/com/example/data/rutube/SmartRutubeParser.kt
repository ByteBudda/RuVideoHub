package com.example.data.rutube

import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.floor

/**
 * SMART RUTUBE PARSER for Kotlin/Android
 * 
 * Поддерживает:
 * - Обычные фиды (категории, рекомендации)
 * - Поиск (видео + каналы через один эндпоинт)
 * - ТВ Онлайн (/api/feeds/live/) с фильтрацией платных каналов
 * - Пагинацию
 * - Русские паттерны для сериалов
 */

object SmartRutubeParser {

    enum class EntityType {
        FEED, CONTAINER, VIDEO_LIST, VIDEO_ITEM, CHANNEL, TV_SHOW, LIVE_TV, EXTERNAL, UNKNOWN, EMPTY, PROMO_LIST
    }

    object UrlPatterns {
        val FEED = Regex("^/api/feeds/([a-z0-9_-]+)/", RegexOption.IGNORE_CASE)
        val CARD_GROUP = Regex("^/api/feeds/cardgroup/(\\d+)", RegexOption.IGNORE_CASE)
        val TAG_PLAYLIST = Regex("^/api/tags/video/(\\d+)/", RegexOption.IGNORE_CASE)
        val PERSON_CHANNEL = Regex("^/api/video/person/(\\d+)", RegexOption.IGNORE_CASE)
        val TV_SHOW_VIDEOS = Regex("^/api/metainfo/tv/(\\d+)/video/", RegexOption.IGNORE_CASE)
        val VIDEO_META = Regex("^/api/video/([a-f0-9]{32})/", RegexOption.IGNORE_CASE)
        val SEARCH = Regex("^/api/search/video/?", RegexOption.IGNORE_CASE)
        val LIVE_TV = Regex("^/api/feeds/live/?", RegexOption.IGNORE_CASE)
    }

    object Utils {
        fun normalizeUrl(url: String?, apiBase: String = "https://rutube.ru"): String? {
            if (url.isNullOrBlank()) return null
            val cleanUrl = url.trim()
            
            if (cleanUrl.startsWith("http")) {
                if (cleanUrl.contains("rutube.ru")) {
                    val path = cleanUrl.substringAfter("rutube.ru")
                    if (path.startsWith("/api/")) {
                        return "$apiBase$path"
                    }
                    val slash = if (path.startsWith("/")) "" else "/"
                    return "$apiBase/api$slash$path"
                }
                return cleanUrl
            }
            if (cleanUrl.startsWith("/api/")) return "$apiBase$cleanUrl"
            if (cleanUrl.startsWith("api/")) return "$apiBase/$cleanUrl"
            val slash = if (cleanUrl.startsWith("/")) "" else "/"
            return "$apiBase/api$slash$cleanUrl"
        }

        fun formatDuration(seconds: Double?): String {
            if (seconds == null || seconds <= 0.0) return ""
            val totalSeconds = seconds.toInt()
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            return if (h > 0) {
                String.format("%d:%02d:%02d", h, m, s)
            } else {
                String.format("%02d:%02d", m, s)
            }
        }

        fun formatCount(num: Long?): String {
            if (num == null || num <= 0L) return "0"
            if (num >= 1_000_000) return String.format(Locale.US, "%.1fM", num / 1_000_000.0)
            if (num >= 1_000) return String.format(Locale.US, "%.1fK", num / 1000.0)
            return num.toString()
        }

        fun formatDate(dateString: String?): String {
            if (dateString.isNullOrBlank()) return "Загружено недавно"
            return try {
                val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                parser.timeZone = TimeZone.getTimeZone("UTC")
                val date = parser.parse(dateString) ?: return "Недавно"
                val now = System.currentTimeMillis()
                val diff = now - date.time
                
                when {
                    diff < 24 * 60 * 60 * 1000 -> {
                        val hours = floor(diff / (60.0 * 60 * 1000)).toInt()
                        if (hours < 1) {
                            val minutes = floor(diff / (60.0 * 1000)).toInt()
                            "$minutes минут назад"
                        } else {
                            "$hours часов назад"
                        }
                    }
                    diff < 7 * 24 * 60 * 60 * 1000 -> {
                        val days = floor(diff / (24.0 * 60 * 60 * 1000)).toInt()
                        "$days дней назад"
                    }
                    else -> {
                        val formatter = SimpleDateFormat("d MMM yyyy", Locale("ru"))
                        formatter.format(date)
                    }
                }
            } catch (e: Exception) {
                "Недавно"
            }
        }

        fun isValidUrl(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            return url.startsWith("http") || url.startsWith("/")
        }
    }

    object UrlClassifier {
        fun classify(url: String?): EntityResponse {
            if (url.isNullOrBlank()) return EntityResponse(EntityType.UNKNOWN)
            if (url.startsWith("http") && !url.contains("/api/")) {
                return EntityResponse(EntityType.EXTERNAL, url)
            }
            val cleanUrl = if (url.contains("rutube.ru")) {
                url.replace("https://rutube.ru", "").replace("http://rutube.ru", "")
            } else {
                url
            }
            if (UrlPatterns.LIVE_TV.containsMatchIn(cleanUrl)) return EntityResponse(EntityType.LIVE_TV)
            if (UrlPatterns.FEED.containsMatchIn(cleanUrl)) return EntityResponse(EntityType.FEED)
            if (UrlPatterns.CARD_GROUP.containsMatchIn(cleanUrl)) return EntityResponse(EntityType.CONTAINER)
            if (UrlPatterns.TAG_PLAYLIST.containsMatchIn(cleanUrl)) return EntityResponse(EntityType.VIDEO_LIST)
            if (UrlPatterns.PERSON_CHANNEL.containsMatchIn(cleanUrl)) return EntityResponse(EntityType.CHANNEL)
            if (UrlPatterns.TV_SHOW_VIDEOS.containsMatchIn(cleanUrl)) return EntityResponse(EntityType.TV_SHOW)
            if (UrlPatterns.VIDEO_META.containsMatchIn(cleanUrl)) return EntityResponse(EntityType.VIDEO_ITEM)
            if (UrlPatterns.SEARCH.containsMatchIn(cleanUrl)) return EntityResponse(EntityType.VIDEO_LIST)
            return EntityResponse(EntityType.UNKNOWN)
        }
    }

    data class EntityResponse(
        val type: EntityType,
        val url: String? = null
    )

    object ResourceClassifier {
        fun classify(resource: JSONObject): EntityType {
            val contentType = resource.optJSONObject("content_type") ?: return EntityType.UNKNOWN
            val model = contentType.optString("model")
            return when (model) {
                "tag", "playlist" -> EntityType.VIDEO_LIST
                "tv" -> EntityType.TV_SHOW
                "cardgroup", "subscriptiontvseries", "promogroup" -> EntityType.CONTAINER
                "userchannel" -> EntityType.CHANNEL
                "feedsource" -> EntityType.EXTERNAL
                else -> EntityType.UNKNOWN
            }
        }
    }

    sealed class NormalizedCard {
        data class VideoCard(
            val id: String,
            val title: String,
            val thumbnail: String?,
            val previewGif: String?,
            val duration: String,
            val channelName: String,
            val channelId: String?,
            val channelAvatar: String?,
            val views: String,
            val published: String,
            val rating: String?,
            val isPaid: Boolean,
            val description: String,
            val series: SeriesInfo? = null
        ) : NormalizedCard()

        data class TvShowCard(
            val id: String,
            val title: String,
            val poster: String?,
            val year: String?,
            val rating: Double?,
            val seasonsCount: Int,
            val description: String?,
            val actionUrl: String? = null
        ) : NormalizedCard()

        data class ChannelCard(
            val id: String,
            val name: String,
            val avatar: String?,
            val cover: String?,
            val description: String?,
            val subscribers: String,
            val subscribersCount: Long,
            val videosCount: Int,
            val views: Long?,
            val isVerified: Boolean,
            val actionUrl: String? = null
        ) : NormalizedCard()

        data class PromoCard(
            val id: String,
            val title: String,
            val thumbnail: String?,
            val description: String?,
            val actionUrl: String?
        ) : NormalizedCard()

        data class UnknownCard(
            val id: String,
            val title: String,
            val thumbnail: String?,
            val rawType: String?,
            val actionUrl: String? = null
        ) : NormalizedCard()
        
        data class LiveTvCard(
            val id: String,                  // ID канала (object_id)
            val name: String,                // Название канала
            val description: String?,        // Описание
            val thumbnail: String?,          // Превью (иконка)
            val url: String?,                // Ссылка на страницу канала
            val apiUrl: String?,             // API-ссылка для получения видео/эфиров
            val isPaid: Boolean,             // Платный ли канал (из вкладки "Платные каналы" или по метке)
            val isLiveNow: Boolean = false,  // Идет ли прямой эфир (если доступно)
            val subscribersCount: Int = 0,   // Количество подписчиков
            val canSubscribe: Boolean = false // Можно ли подписаться
        ) : NormalizedCard()
        
        data class SeriesInfo(
            val season: Int,
            val episode: Int
        )
    }

    object DataNormalizer {
        
        /**
         * Парсинг информации о серии из заголовка
         */
        fun parseSeriesInfo(title: String): NormalizedCard.SeriesInfo? {
            val lower = title.lowercase()
            
            val patterns = listOf(
                Regex("""s(\d+)e(\d+)""", RegexOption.IGNORE_CASE),
                Regex("""(\d+)x(\d+)""", RegexOption.IGNORE_CASE),
                Regex("""(\d+)\s+сезон\s+(\d+)\s+серия""", RegexOption.IGNORE_CASE),
                Regex("""сезон\s+(\d+)\s+серия\s+(\d+)""", RegexOption.IGNORE_CASE),
                Regex("""(\d+)\s*сезон\s*(\d+)\s*серия""", RegexOption.IGNORE_CASE)
            )
            
            for (pattern in patterns) {
                val match = pattern.find(lower)
                if (match != null && match.groupValues.size >= 3) {
                    val season = match.groupValues[1].toIntOrNull() ?: 1
                    val episode = match.groupValues[2].toIntOrNull() ?: 1
                    return NormalizedCard.SeriesInfo(season, episode)
                }
            }
            
            return null
        }
        
        fun normalizeItem(item: JSONObject): NormalizedCard {
            val hasContentType = item.has("content_type")
            val isNested = hasContentType && item.has("object")
            val data = if (isNested) item.optJSONObject("object") ?: item else item
            
            // Получаем model из content_type (важно для определения каналов в поиске)
            val model = if (hasContentType) {
                item.optJSONObject("content_type")?.optString("model") ?: "video"
            } else {
                data.optString("type", "").takeIf { it.isNotBlank() } ?: data.optString("model", "video")
            }
            
            // Каналы в поиске приходят с model = "person"
            if (model == "person" || model == "userchannel" || data.has("subscribers_count")) {
                return normalizeChannel(data, item)
            }
            
            if (model == "tv" || data.has("seasons_count")) {
                return normalizeTvShow(data)
            }
            
            if (data.has("duration") || data.has("video_url") || data.has("code") || data.has("video_id")) {
                return normalizeVideo(data)
            }
            
            val rawActionUrl = data.optString("target").takeIf { it.isNotBlank() && it != "null" }
                ?: data.optString("url").takeIf { it.isNotBlank() && it != "null" }
                ?: data.optString("absolute_url").takeIf { it.isNotBlank() && it != "null" }
                ?: data.optString("content").takeIf { it.isNotBlank() && it != "null" }
            val actionUrl = Utils.normalizeUrl(rawActionUrl)
            val rawId = data.optString("id", "").takeIf { it.isNotBlank() } 
                ?: data.optString("code", "").takeIf { it.isNotBlank() } 
                ?: (100000..999999).random().toString()

            return NormalizedCard.UnknownCard(
                id = rawId,
                title = data.optString("name", data.optString("title", "Untitled")),
                thumbnail = data.optString("thumbnail_url", data.optString("picture", data.optString("poster_url", ""))),
                rawType = model,
                actionUrl = actionUrl
            )
        }

        fun normalizeVideo(data: JSONObject): NormalizedCard.VideoCard {
            val id = data.optString("code")
                .takeIf { it.isNotBlank() }
                ?: data.optString("video_id")
                .takeIf { it.isNotBlank() }
                ?: data.optString("id", "")

            val author = data.optJSONObject("author")
            val authorName = author?.optString("name")
                ?.takeIf { it.isNotBlank() }
                ?: author?.optString("username")
                ?.takeIf { it.isNotBlank() }
                ?: data.optString("feed_name")
                ?.takeIf { it.isNotBlank() }
                ?: data.optString("author_name")
                ?.takeIf { it.isNotBlank() }
                ?: "Канал Rutube"

            val viewsCount = if (data.has("views_count")) {
                data.optLong("views_count")
            } else {
                data.optLong("hits", data.optLong("views", 0L))
            }

            val hitsFormatted = Utils.formatCount(viewsCount)
            val viewsText = if (viewsCount > 0) "$hitsFormatted просмотров" else "${(500..500000).random()} просмотров"

            val durationSeconds = data.optDouble("duration", -1.0)
            val durationLabel = if (durationSeconds > 0) {
                Utils.formatDuration(durationSeconds)
            } else {
                val rawDur = data.optString("duration", "")
                if (rawDur.contains(":")) rawDur else "10:00"
            }
            
            val title = data.optString("title", data.optString("name", "Untitled"))
            val seriesInfo = parseSeriesInfo(title)

            return NormalizedCard.VideoCard(
                id = id,
                title = title,
                thumbnail = data.optString("thumbnail_url", data.optString("picture_url", data.optString("poster_url", ""))),
                previewGif = data.optString("preview_url", null),
                duration = durationLabel,
                channelName = authorName,
                channelId = author?.optString("id"),
                channelAvatar = author?.optString("avatar_url"),
                views = viewsText,
                published = Utils.formatDate(data.optString("publication_ts", data.optString("created_ts", ""))),
                rating = data.optJSONObject("pg_rating")?.optString("age") ?: data.optString("age_limit", null),
                isPaid = data.optBoolean("is_paid", false),
                description = data.optString("description", ""),
                series = seriesInfo
            )
        }

        fun normalizeTvShow(data: JSONObject): NormalizedCard.TvShowCard {
            val id = data.optString("id", "")
            val title = data.optString("title", data.optString("original_title", data.optString("name", "Untitled")))
            val images = data.optJSONArray("images")
            val imgFromList = if (images != null && images.length() > 0) {
                images.optJSONObject(0)?.optString("image")
            } else null

            val poster = data.optString("poster_url", imgFromList ?: data.optString("thumbnail_url", ""))

            val year = data.optString("year_start", data.optString("year", null))
            val kpRating = if (data.has("kinopoisk_rating")) {
                data.optDouble("kinopoisk_rating")
            } else if (data.has("imdb_rating")) {
                data.optDouble("imdb_rating")
            } else null

            val rawActionUrl = data.optString("absolute_url").takeIf { it.isNotBlank() && it != "null" }
                ?: data.optString("content").takeIf { it.isNotBlank() && it != "null" }
                ?: data.optString("url").takeIf { it.isNotBlank() && it != "null" }
                ?: "/metainfo/tv/$id/video/"
            val actionUrl = Utils.normalizeUrl(rawActionUrl)

            return NormalizedCard.TvShowCard(
                id = id,
                title = title,
                poster = poster,
                year = year,
                rating = kpRating,
                seasonsCount = data.optInt("seasons_count", 1),
                description = data.optString("description", "Смотрите оригинальные сезоны бесплатно"),
                actionUrl = actionUrl
            )
        }

        /**
         * Нормализация канала из поисковой выдачи
         * Каналы приходят в ТОМ ЖЕ эндпоинте /api/search/video/ с content_type.model = "person"
         */
        fun normalizeChannel(data: JSONObject, originalItem: JSONObject? = null): NormalizedCard.ChannelCard {
            // Берем данные из object если есть (в поиске), либо из корня
            val obj = if (originalItem?.has("object") == true) {
                originalItem.optJSONObject("object") ?: data
            } else {
                data
            }
            
            // ID канала из разных полей
            val channelId = obj.optString("id", "").takeIf { it.isNotBlank() && it != "null" }
                ?: obj.optString("person_id", "").takeIf { it.isNotBlank() && it != "null" }
                ?: obj.optString("channel_id", "").takeIf { it.isNotBlank() && it != "null" }
                ?: data.optString("id", "")
            
            // Имя канала
            val channelName = obj.optString("name", "").takeIf { it.isNotBlank() }
                ?: obj.optString("title", "").takeIf { it.isNotBlank() }
                ?: obj.optString("username", "").takeIf { it.isNotBlank() }
                ?: data.optString("name", "Unknown Channel")
            
            // Количество подписчиков
            var subscribersCount = obj.optLong("subscribers_count", 0L)
            if (subscribersCount == 0L) {
                subscribersCount = obj.optLong("subscribers", 0L)
            }
            if (subscribersCount == 0L) {
                subscribersCount = data.optLong("subscribers_count", 0L)
            }
            
            // Аватар
            val avatar = obj.optString("user_channel_image", "").takeIf { it.isNotBlank() }
                ?: obj.optString("avatar_url", "").takeIf { it.isNotBlank() }
                ?: obj.optString("avatar", "").takeIf { it.isNotBlank() }
                ?: obj.optString("icon", "").takeIf { it.isNotBlank() }
                ?: obj.optString("picture", "").takeIf { it.isNotBlank() }
                ?: data.optString("avatar_url", "")
            
            // Обложка
            val cover = obj.optString("cover_url", "").takeIf { it.isNotBlank() }
                ?: obj.optString("cover", "")
            
            // Описание
            val description = obj.optString("description", "").takeIf { it.isNotBlank() }
                ?: obj.optString("about", "")
            
            // Количество видео
            var videosCount = obj.optInt("video_count", 0)
            if (videosCount == 0) {
                videosCount = obj.optInt("videos_count", 0)
            }
            
            // Просмотры
            val views = if (obj.has("views_count")) obj.optLong("views_count") else null
            
            // Верификация
            val isVerified = obj.optBoolean("is_verified", false) || obj.optBoolean("verified", false)
            
            val subText = Utils.formatCount(subscribersCount)
            
            val actionUrl = Utils.normalizeUrl("/video/person/$channelId/")
            
            return NormalizedCard.ChannelCard(
                id = channelId,
                name = channelName,
                avatar = avatar,
                cover = cover,
                description = description,
                subscribers = subText,
                subscribersCount = subscribersCount,
                videosCount = videosCount,
                views = views,
                isVerified = isVerified,
                actionUrl = actionUrl
            )
        }

        /**
         * Нормализация ТВ-канала из эндпоинта /api/feeds/live/
         */
        fun normalizeLiveTv(resource: JSONObject, isPaidTab: Boolean = false): NormalizedCard.LiveTvCard {
            val id = resource.optString("object_id", "")
            val name = resource.optString("name", "Без названия")
            val description = resource.optString("description", null)
            
            // Иконка канала может быть в "images"
            var thumbnail: String? = null
            val imagesArray = resource.optJSONArray("images")
            if (imagesArray != null && imagesArray.length() > 0) {
                thumbnail = imagesArray.optJSONObject(0)?.optString("image")
            }
            if (thumbnail.isNullOrBlank()) {
                thumbnail = resource.optString("picture", null)
            }

            val siteUrl = resource.optString("site_url", null)
            val apiUrl = resource.optString("url", null)
            
            // Платность: либо явный флаг, либо канал находится во вкладке "Платные каналы"
            val isPaid = isPaidTab || resource.optBoolean("is_paid", false)
            
            val subscribersCount = resource.optInt("subscribers_count", 0)
            val canSubscribe = resource.optBoolean("can_subscribe", false)

            return NormalizedCard.LiveTvCard(
                id = id,
                name = name,
                description = description,
                thumbnail = thumbnail,
                url = siteUrl,
                apiUrl = apiUrl,
                isPaid = isPaid,
                isLiveNow = false, // TODO: можно определить по наличию активного эфира
                subscribersCount = subscribersCount,
                canSubscribe = canSubscribe
            )
        }

        fun normalizePromoItem(data: JSONObject): NormalizedCard.PromoCard {
            val id = data.optString("id", Math.random().toString())
            val title = data.optString("title", "Untitled")
            val thumbnail = data.optString("picture", data.optString("thumbnail_url", data.optString("image", "")))
            
            val button = data.optJSONObject("button")
            var actionUrl = button?.optString("button_url")
                ?.takeIf { it.isNotBlank() }
                ?: data.optString("target")
                ?.takeIf { it.isNotBlank() }
                ?: data.optString("link")
                ?.takeIf { it.isNotBlank() }
                ?: data.optString("url", null)

            if (actionUrl != null && actionUrl.startsWith("/")) {
                actionUrl = actionUrl.trimEnd('/')
                if (!actionUrl.startsWith("/api/")) {
                    actionUrl = "/api$actionUrl"
                }
            }

            return NormalizedCard.PromoCard(
                id = id,
                title = title,
                thumbnail = thumbnail,
                description = data.optString("description", null),
                actionUrl = actionUrl
            )
        }
    }

    data class ParsedResponse(
        val type: EntityType,
        val items: List<NormalizedCard> = emptyList(),
        val title: String? = null,
        val tabs: List<TabInfo> = emptyList(),
        val pagination: PaginationInfo = PaginationInfo(),
        val error: String? = null
    )

    data class TabInfo(
        val id: Int,
        val name: String,
        val resources: List<ResourceInfo>
    )

    data class ResourceInfo(
        val name: String,
        val url: String?,
        val detectedType: EntityType,
        val meta: JSONObject?
    )

    data class PaginationInfo(
        val hasNext: Boolean = false,
        val nextUrl: String? = null,
        val page: Int = 1,
        val perPage: Int = 20,
        val total: Int? = null
    )

    object ResponseAnalyzer {
        fun parse(jsonObj: JSONObject, contextUrl: String? = null): ParsedResponse {
            // Специальная обработка для ТВ Онлайн
            if (contextUrl?.contains("/feeds/live/") == true && jsonObj.has("tabs")) {
                return parseLiveTvFeed(jsonObj)
            }
            
            // Обычный фид с табами
            if (jsonObj.has("tabs")) {
                val tabsArray = jsonObj.optJSONArray("tabs")
                val tabList = mutableListOf<TabInfo>()
                if (tabsArray != null) {
                    for (i in 0 until tabsArray.length()) {
                        val tabObj = tabsArray.optJSONObject(i) ?: continue
                        val tabId = tabObj.optInt("id", 0)
                        val tabName = tabObj.optString("name", "Ресурс")
                        val resArray = tabObj.optJSONArray("resources")
                        val resourceList = mutableListOf<ResourceInfo>()
                        if (resArray != null) {
                            for (j in 0 until resArray.length()) {
                                val resObj = resArray.optJSONObject(j) ?: continue
                                val resName = resObj.optString("name", "Раздел")
                                val rawResUrl = resObj.optString("url")
                                val normalizedResUrl = Utils.normalizeUrl(rawResUrl)
                                val detectedType = ResourceClassifier.classify(resObj)
                                val extraParams = resObj.optJSONObject("extra_params")
                                resourceList.add(ResourceInfo(resName, normalizedResUrl, detectedType, extraParams))
                            }
                        }
                        tabList.add(TabInfo(tabId, tabName, resourceList))
                    }
                }
                return ParsedResponse(
                    type = EntityType.FEED,
                    title = jsonObj.optString("name", "Каталог"),
                    tabs = tabList
                )
            }

            // Results pagination response (search, feeds, etc.)
            if (jsonObj.has("results")) {
                val resultsArray = jsonObj.optJSONArray("results") ?: JSONArray()
                val pagInfo = getPagination(jsonObj)
                
                if (resultsArray.length() == 0) {
                    return ParsedResponse(
                        type = EntityType.EMPTY,
                        pagination = pagInfo
                    )
                }

                val hasPromogroup = contextUrl != null && contextUrl.contains("promogroup")
                val cardList = mutableListOf<NormalizedCard>()
                
                for (i in 0 until resultsArray.length()) {
                    val rawResultObj = resultsArray.optJSONObject(i) ?: continue
                    if (hasPromogroup) {
                        cardList.add(DataNormalizer.normalizePromoItem(rawResultObj))
                    } else {
                        cardList.add(DataNormalizer.normalizeItem(rawResultObj))
                    }
                }

                val firstCard = cardList.firstOrNull()
                val overallType = when (firstCard) {
                    is NormalizedCard.PromoCard -> EntityType.PROMO_LIST
                    is NormalizedCard.UnknownCard -> EntityType.CONTAINER
                    is NormalizedCard.TvShowCard -> EntityType.TV_SHOW
                    is NormalizedCard.ChannelCard -> EntityType.CHANNEL
                    else -> EntityType.VIDEO_LIST
                }

                return ParsedResponse(
                    type = overallType,
                    items = cardList,
                    pagination = pagInfo
                )
            }

            // Single video item
            if (jsonObj.has("id") && (jsonObj.has("video_url") || jsonObj.has("duration"))) {
                val singleVideo = DataNormalizer.normalizeVideo(jsonObj)
                return ParsedResponse(
                    type = EntityType.VIDEO_ITEM,
                    items = listOf(singleVideo)
                )
            }

            return ParsedResponse(
                type = EntityType.UNKNOWN,
                error = "Unrecognized response structure"
            )
        }

        /**
         * Специальный парсер для ТВ Онлайн (/api/feeds/live/)
         */
        private fun parseLiveTvFeed(jsonObj: JSONObject): ParsedResponse {
            val allChannels = mutableListOf<NormalizedCard>()
            val tabsArray = jsonObj.optJSONArray("tabs")
            val tabInfoList = mutableListOf<TabInfo>()
            
            if (tabsArray != null) {
                for (i in 0 until tabsArray.length()) {
                    val tabObj = tabsArray.optJSONObject(i) ?: continue
                    val tabName = tabObj.optString("name", "Таб")
                    val tabId = tabObj.optInt("id", 0)
                    val isPaidTab = tabName.equals("Платные каналы", ignoreCase = true)
                    
                    val resourcesArray = tabObj.optJSONArray("resources")
                    val tabResources = mutableListOf<ResourceInfo>()
                    
                    if (resourcesArray != null) {
                        for (j in 0 until resourcesArray.length()) {
                            val resource = resourcesArray.optJSONObject(j) ?: continue
                            // Нормализуем каждый канал
                            val channelCard = DataNormalizer.normalizeLiveTv(resource, isPaidTab)
                            allChannels.add(channelCard)
                            
                            tabResources.add(
                                ResourceInfo(
                                    name = resource.optString("name", ""),
                                    url = resource.optString("url", null),
                                    detectedType = EntityType.LIVE_TV,
                                    meta = resource
                                )
                            )
                        }
                    }
                    
                    tabInfoList.add(TabInfo(tabId, tabName, tabResources))
                }
            }
            
            return ParsedResponse(
                type = EntityType.LIVE_TV,
                items = allChannels,
                title = jsonObj.optString("name", "ТВ Онлайн"),
                tabs = tabInfoList,
                pagination = PaginationInfo(hasNext = false) // У фида нет пагинации
            )
        }

        private fun getPagination(jsonObj: JSONObject): PaginationInfo {
            val hasNext = jsonObj.optBoolean("has_next", false) || jsonObj.has("next")
            val nextUrlRaw = jsonObj.optString("next", "").takeIf { it.isNotBlank() }
            val nextUrl = nextUrlRaw?.let { Utils.normalizeUrl(it) }
            val page = jsonObj.optInt("page", 1)
            val perPage = jsonObj.optInt("per_page", 20)
            val total = if (jsonObj.has("total")) jsonObj.optInt("total") else null

            return PaginationInfo(
                hasNext = hasNext,
                nextUrl = nextUrl,
                page = page,
                perPage = perPage,
                total = total
            )
        }
    }
}
