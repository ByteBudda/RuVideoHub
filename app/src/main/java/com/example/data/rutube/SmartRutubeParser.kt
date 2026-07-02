package com.example.data.rutube

import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * ULTIMATE RUTUBE PARSER
 * Полностью адаптивный парсер с фильтрацией платного контента
 * 
 * Особенности:
 * - Авто-обнаружение структуры ответа
 * - Фильтрация платного контента (PREMIER, START, IVI, KION и др.)
 * - Поддержка всех типов пагинации
 * - Рекурсивный парсинг вложенных структур
 * - Извлечение связанного контента (related_tv, related_person)
 * - Детекция по кодам подписки (common_subscription_product_codes)
 */
object SmartRutubeParser {

    // ==================== CONFIGURATION ====================
    
    private const val API_BASE = "https://rutube.ru"
    private const val MAX_RECURSION_DEPTH = 3
    private const val PAID_CONFIDENCE_THRESHOLD = 0.7
    
    // ==================== ENUMS ====================
    
    enum class EntityType {
        FEED_CATALOG,      // Каталог с вкладками
        CATEGORY_FEED,     // Простой фид
        CONTAINER,         // Контейнер (cardgroup)
        VIDEO_LIST,        // Список видео
        VIDEO_ITEM,        // Одно видео
        CHANNEL,           // Канал/персона
        TV_SERIES,         // Сериал
        PROMO_GROUP,       // Рекламная группа
        PLAYLIST,          // Плейлист
        LIVE_STREAM,       // Прямой эфир
        EXTERNAL,          // Внешняя ссылка
        UNKNOWN,           // Неизвестный тип
        EMPTY              // Пустой ответ
    }
    
    // ==================== PAID CONTENT DETECTION ====================
    
    /**
     * Платные партнеры (даже если is_paid = false)
     */
    private val PAID_PARTNERS = setOf(
        "PREMIER", "START", "IVI", "KION", "MORE.TV", "OKKO", "WINK", "AMEDIATEKA"
    )
    
    /**
     * Платные коды подписки (из common_subscription_product_codes)
     */
    private val PAID_SUBSCRIPTION_CODES = setOf(
        "PREMIER_RUTUBE_YAPPY", "PREMIER_RUTUBE_START", "PREMIER_RUTUBE_GAZPROM_BONUS",
        "premier-rutube-gazprom-bonus-lite", "START_RUTUBE", "IVI_RUTUBE", "KION_RUTUBE"
    )
    
    /**
     * Результат проверки платности
     */
    data class PaidCheck(
        val isPaid: Boolean,
        val reason: String,
        val confidence: Double,
        val partner: String? = null,
        val requiresSubscription: Boolean = false
    )
    
    // ==================== ADAPTIVE FIELD EXTRACTOR ====================
    
    object AdaptiveExtractor {
        private val fieldAliases = mapOf(
            "id" to listOf("id", "code", "video_id", "content_id", "uuid", "person_id"),
            "title" to listOf("title", "name", "name_displayed", "original_title", "heading"),
            "thumbnail" to listOf("thumbnail_url", "picture_url", "picture", "poster_url", "image", "avatar_url"),
            "duration" to listOf("duration", "length", "video_length"),
            "views" to listOf("views_count", "hits", "views", "watch_count"),
            "author" to listOf("author", "creator", "channel", "owner", "user"),
            "description" to listOf("description", "desc", "about", "content"),
            "created" to listOf("created_ts", "publication_ts", "published_at", "upload_date"),
            "subscribers" to listOf("subscribers_count", "followers", "subs"),
            "videos" to listOf("video_count", "videos_count", "total_videos"),
            "seasons" to listOf("seasons_count", "seasons")
        )
        
        fun makeDeterministicId(prefix: String, title: String, url: String?): String {
            val base = url ?: title
            val hash = base.hashCode().toString().replace("-", "n")
            return "${prefix}_$hash"
        }
        
        fun getString(obj: JSONObject, fieldType: String, default: String = ""): String {
            val aliases = fieldAliases[fieldType] ?: listOf(fieldType)
            for (alias in aliases) {
                if (obj.has(alias)) {
                    val value = obj.opt(alias)
                    when (value) {
                        is String -> if (value.isNotBlank() && value != "null") return value
                        is Number -> return value.toString()
                        is JSONObject -> {
                            val str = value.optString("value", null)
                                ?: value.optString("name", null)
                                ?: value.optString("title", null)
                                ?: value.optString("code", null)
                                ?: value.optString("id", null)
                            if (!str.isNullOrBlank() && str != "null") return str
                        }
                    }
                }
                if (alias.contains(".")) {
                    val parts = alias.split(".")
                    var current: Any? = obj
                    for (part in parts) {
                        current = when (current) {
                            is JSONObject -> current.opt(part)
                            else -> null
                        }
                    }
                    if (current != null && current.toString().isNotBlank() && current.toString() != "null") {
                        return current.toString()
                    }
                }
            }
            return default
        }
        
        fun getInt(obj: JSONObject, fieldType: String, default: Int = 0): Int {
            val aliases = fieldAliases[fieldType] ?: listOf(fieldType)
            for (alias in aliases) {
                if (obj.has(alias)) {
                    val value = obj.opt(alias)
                    if (value is Number) return value.toInt()
                    if (value is String) {
                        val parsed = value.toIntOrNull()
                        if (parsed != null) return parsed
                    }
                }
            }
            return default
        }
        
        fun getLong(obj: JSONObject, fieldType: String, default: Long = 0L): Long {
            val aliases = fieldAliases[fieldType] ?: listOf(fieldType)
            for (alias in aliases) {
                if (obj.has(alias)) {
                    val value = obj.opt(alias)
                    if (value is Number) return value.toLong()
                    if (value is String) {
                        val parsed = value.toLongOrNull()
                        if (parsed != null) return parsed
                    }
                }
            }
            return default
        }
        
        fun getDouble(obj: JSONObject, fieldType: String, default: Double = 0.0): Double {
            val aliases = fieldAliases[fieldType] ?: listOf(fieldType)
            for (alias in aliases) {
                if (obj.has(alias)) {
                    val value = obj.opt(alias)
                    if (value is Number) return value.toDouble()
                    if (value is String) {
                        val parsed = value.toDoubleOrNull()
                        if (parsed != null) return parsed
                    }
                }
            }
            return default
        }
        
        fun getBoolean(obj: JSONObject, fieldType: String, default: Boolean = false): Boolean {
            val str = getString(obj, fieldType).lowercase()
            return when (str) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> default
            }
        }
        
        fun getObject(obj: JSONObject, fieldType: String): JSONObject? {
            val aliases = fieldAliases[fieldType] ?: listOf(fieldType)
            for (alias in aliases) {
                val nested = obj.optJSONObject(alias)
                if (nested != null) return nested
            }
            return null
        }
        
        fun getArray(obj: JSONObject, fieldType: String): JSONArray? {
            val aliases = fieldAliases[fieldType] ?: listOf(fieldType)
            for (alias in aliases) {
                val arr = obj.optJSONArray(alias)
                if (arr != null && arr.length() > 0) return arr
            }
            return null
        }
        
        fun getStringList(obj: JSONObject, fieldType: String): List<String> {
            val arr = getArray(obj, fieldType) ?: return emptyList()
            return (0 until arr.length()).mapNotNull { index ->
                val item = arr.opt(index)
                when (item) {
                    is String -> item.takeIf { it.isNotBlank() && it != "null" }
                    is JSONObject -> item.optString("name", item.optString("title", null))
                    else -> item?.toString()
                }
            }.filter { it != null && it != "null" }
        }
    }
    
    // ==================== PAID CONTENT DETECTOR ====================
    
    object PaidContentDetector {
        
        fun check(json: JSONObject): PaidCheck {
            // 1. Проверка кодов подписки (САМЫЙ ВАЖНЫЙ ПРИЗНАК!)
            val subscriptionCodes = json.optJSONArray("common_subscription_product_codes")
            if (subscriptionCodes != null && subscriptionCodes.length() > 0) {
                val codes = (0 until subscriptionCodes.length()).map { subscriptionCodes.optString(it) }
                val hasPaidCode = codes.any { code ->
                    PAID_SUBSCRIPTION_CODES.any { paidCode -> code.contains(paidCode, ignoreCase = true) }
                }
                if (hasPaidCode) {
                    val partner = when {
                        codes.any { it.contains("PREMIER", ignoreCase = true) } -> "PREMIER"
                        codes.any { it.contains("START", ignoreCase = true) } -> "START"
                        codes.any { it.contains("IVI", ignoreCase = true) } -> "IVI"
                        else -> null
                    }
                    return PaidCheck(true, "Subscription codes: ${codes.joinToString()}", 0.99, partner, true)
                }
            }
            
            // 2. Проверка партнера по имени канала
            val author = AdaptiveExtractor.getObject(json, "author")
            val authorName = AdaptiveExtractor.getString(author ?: json, "name", "")
            val feedName = AdaptiveExtractor.getString(json, "feed_name", "")
            
            for (partner in PAID_PARTNERS) {
                if (authorName.contains(partner, ignoreCase = true) || 
                    feedName.contains(partner, ignoreCase = true)) {
                    return PaidCheck(true, "Paid partner: $partner", 0.95, partner, true)
                }
            }
            
            // 3. Прямой флаг is_paid
            if (json.optBoolean("is_paid", false)) {
                return PaidCheck(true, "Direct is_paid flag", 0.9)
            }
            
            // 4. Проверка origin_type (uma часто платный)
            if (json.optString("origin_type") == "uma") {
                return PaidCheck(true, "UMA content", 0.55)
            }
            
            // 5. Лицензионный контент (может быть платным)
            if (json.optBoolean("is_licensed", false) && json.optBoolean("is_official", false)) {
                return PaidCheck(true, "Licensed official content", 0.6)
            }

            // 6. Дополнительная проверка по ключевым словам в описании/титлах
            val title = AdaptiveExtractor.getString(json, "title").lowercase()
            val desc = AdaptiveExtractor.getString(json, "description").lowercase()
            
            val paidKeywords = listOf("оформить подписку", "только по подписке", "доступно в подписке", "трейлер сериала")
            if (paidKeywords.any { title.contains(it) || desc.contains(it) }) {
                return PaidCheck(true, "Paid keywords matched in text", 0.85)
            }
        
            // 7. Если длительность видео слишком маленькая для фильма/сериала (заглушки-трейлеры)
            val duration = AdaptiveExtractor.getDouble(json, "duration", 0.0)
            val isTvShow = json.has("seasons_count") || json.optJSONObject("content_type")?.optString("model") == "tv"
            if (isTvShow && duration > 0 && duration < 180) { // Меньше 3 минут — явно рекламный трейлер платного сериала
                return PaidCheck(true, "Short trailer duration for TV-show", 0.8)
            }
            
            return PaidCheck(false, "Free content", 0.9)
        }
        
        fun shouldExclude(json: JSONObject): Boolean {
            val check = check(json)
            return check.isPaid && check.confidence > PAID_CONFIDENCE_THRESHOLD
        }
    }
    
    // ==================== NORMALIZED CARDS ====================
    
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
            val rawViews: Long = 0,
            val published: String,
            val publishedTimestamp: Long = 0,
            val rating: String?,
            val isPaid: Boolean,
            val paidReason: String? = null,
            val requiresSubscription: Boolean = false,
            val partner: String? = null,
            val description: String,
            val tags: List<String> = emptyList()
        ) : NormalizedCard()
        
        data class TvSeriesCard(
            val id: String,
            val title: String,
            val originalTitle: String? = null,
            val poster: String?,
            val year: String?,
            val rating: Double?,
            val seasonsCount: Int,
            val description: String?,
            val isPaid: Boolean,
            val paidReason: String? = null,
            val requiresSubscription: Boolean = false,
            val partner: String? = null,
            val actionUrl: String? = null
        ) : NormalizedCard()
        
        data class ChannelCard(
            val id: String,
            val name: String,
            val avatar: String?,
            val description: String?,
            val subscribers: String,
            val rawSubscribers: Long = 0,
            val videosCount: Int,
            val actionUrl: String? = null
        ) : NormalizedCard()
        
        data class PromoCard(
            val id: String,
            val title: String,
            val thumbnail: String?,
            val description: String?,
            val actionUrl: String?
        ) : NormalizedCard()
        
        data class PlaylistCard(
            val id: String,
            val title: String,
            val thumbnail: String?,
            val videosCount: Int,
            val actionUrl: String?
        ) : NormalizedCard()
        
        data class UnknownCard(
            val id: String,
            val title: String,
            val thumbnail: String?,
            val rawType: String?,
            val actionUrl: String? = null
        ) : NormalizedCard()
    }
    
    // ==================== CARD NORMALIZER ====================
    
    object CardNormalizer {
        
        fun normalizeOrNull(json: JSONObject): NormalizedCard? {
            // Фильтрация платного контента
            if (PaidContentDetector.shouldExclude(json)) {
                return null
            }
            
            val hasContentType = json.has("content_type")
            val isNested = hasContentType && json.has("object")
            val data = if (isNested) json.optJSONObject("object") ?: json else json
            
            var model = ""
            if (hasContentType) {
                model = json.optJSONObject("content_type")?.optString("model") ?: ""
            }
            if (model.isBlank()) {
                val typeVal = data.opt("type")
                model = when (typeVal) {
                    is String -> typeVal
                    is JSONObject -> typeVal.optString("name", typeVal.optString("code", ""))
                    else -> ""
                }
            }
            if (model.isBlank()) {
                model = data.optString("model", "")
            }
            
            val urlVal = data.optString("absolute_url", data.optString("url", "")).lowercase()
            if (model.isBlank() && urlVal.isNotBlank()) {
                when {
                    urlVal.contains("/person/") || urlVal.contains("/channel/") || urlVal.contains("/user/") -> model = "channel"
                    urlVal.contains("/tv/") || urlVal.contains("/subscriptiontvseries/") -> model = "tv"
                    urlVal.contains("/playlist/") -> model = "playlist"
                }
            }
            
            val typeObj = data.optJSONObject("type")
            val typeId = typeObj?.optInt("id") ?: data.optInt("type_id", 0)
            val isLive = typeId == 12 || model == "live" || data.optBoolean("is_live", false)
            
            return when {
                // Video / Broadcast / Live Check (Highest priority for playable objects)
                (isLive || data.has("duration") || data.has("video_url") || data.has("video_length") || model == "video") 
                    && !data.has("seasons_count") && !data.has("videos_count") && model != "tv" && model != "serial" -> normalizeVideo(data, isLive)
                
                // Playlist Check
                data.has("videos_count") && !data.has("subscribers_count") && !data.has("duration") -> normalizePlaylist(data)
                
                // TV Show / Series Check
                model == "tv" || model == "show" || model == "serial" || model == "tvshow" || model == "movie" 
                        || data.has("seasons_count") || data.has("seasons") || data.has("genres") -> normalizeTvShow(data)
                
                // Channel / Author/ User / Person Check
                model in listOf("userchannel", "person", "channel", "author", "user") 
                        || data.has("subscribers_count") 
                        || (data.has("avatar") && !data.has("duration") && !data.has("video_url") && !data.has("code")) -> normalizeChannel(data)
                
                // Promo / Landing / Button Check
                json.has("button") || json.has("target") || model == "promo" -> normalizePromo(json)
                
                // Fallback Playlist Check
                data.has("playlist_id") || model == "playlist" -> normalizePlaylist(data)
                
                // Fallback Video Check if it has code / identifier
                data.has("code") || data.has("video_id") -> normalizeVideo(data, isLive)
                
                // Default fallback
                else -> normalizeUnknown(data, model.ifBlank { "unknown" })
            }
        }
        
        private fun normalizeVideo(data: JSONObject, isLiveInput: Boolean = false): NormalizedCard.VideoCard {
            val id = AdaptiveExtractor.getString(data, "code")
                .takeIf { it.isNotBlank() }
                ?: AdaptiveExtractor.getString(data, "video_id")
                .takeIf { it.isNotBlank() }
                ?: AdaptiveExtractor.getString(data, "id", "")
            
            val author = AdaptiveExtractor.getObject(data, "author")
            val authorName = AdaptiveExtractor.getString(author ?: data, "name")
                .takeIf { it.isNotBlank() }
                ?: AdaptiveExtractor.getString(data, "feed_name")
                .takeIf { it.isNotBlank() }
                ?: "Rutube"
            
            val viewsCount = AdaptiveExtractor.getLong(data, "views")
            val durationSeconds = AdaptiveExtractor.getDouble(data, "duration", -1.0)
            val paidCheck = PaidContentDetector.check(data)
            
            val typeObj = data.optJSONObject("type")
            val isLive = isLiveInput || typeObj?.optInt("id") == 12 || data.optString("type") == "live"
            
            val ageObj = data.optJSONObject("pg_rating")
            val ageVal = ageObj?.opt("age")?.toString() ?: ""
            val rawRating = AdaptiveExtractor.getString(data, "rating").takeIf { it.isNotBlank() }
            val ratingWithAge = if (ageVal.isNotBlank() && ageVal != "null") {
                if (rawRating != null) "$rawRating ($ageVal+)" else "$ageVal+"
            } else {
                rawRating
            }
            
            return NormalizedCard.VideoCard(
                id = id,
                title = AdaptiveExtractor.getString(data, "title", "Untitled"),
                thumbnail = AdaptiveExtractor.getString(data, "thumbnail").takeIf { it.isNotBlank() },
                previewGif = AdaptiveExtractor.getString(data, "preview_url").takeIf { it.isNotBlank() },
                duration = if (isLive) "ЭФИР" else if (durationSeconds > 0) formatDuration(durationSeconds) else "00:00",
                channelName = authorName,
                channelId = AdaptiveExtractor.getString(author ?: data, "id").takeIf { it.isNotBlank() },
                channelAvatar = AdaptiveExtractor.getString(author ?: data, "avatar").takeIf { it.isNotBlank() },
                views = formatCount(viewsCount),
                rawViews = viewsCount,
                published = formatDate(AdaptiveExtractor.getString(data, "created")),
                publishedTimestamp = parseTimestamp(AdaptiveExtractor.getString(data, "created")),
                rating = ratingWithAge,
                isPaid = paidCheck.isPaid,
                paidReason = paidCheck.reason.takeIf { paidCheck.isPaid },
                requiresSubscription = paidCheck.requiresSubscription,
                partner = paidCheck.partner,
                description = AdaptiveExtractor.getString(data, "description"),
                tags = AdaptiveExtractor.getStringList(data, "tags")
            )
        }
        
        private fun normalizeTvShow(data: JSONObject): NormalizedCard.TvSeriesCard {
            var id = AdaptiveExtractor.getString(data, "id", "")
            val actionUrl = normalizeUrl(data.optString("absolute_url", data.optString("url", null)))
            if (id.isBlank()) {
                id = AdaptiveExtractor.makeDeterministicId("tv", AdaptiveExtractor.getString(data, "title"), actionUrl)
            }
            val paidCheck = PaidContentDetector.check(data)
            
            return NormalizedCard.TvSeriesCard(
                id = id,
                title = AdaptiveExtractor.getString(data, "title", "Untitled"),
                originalTitle = AdaptiveExtractor.getString(data, "original_title").takeIf { it.isNotBlank() },
                poster = AdaptiveExtractor.getString(data, "thumbnail").takeIf { it.isNotBlank() },
                year = AdaptiveExtractor.getString(data, "year").takeIf { it.isNotBlank() },
                rating = AdaptiveExtractor.getDouble(data, "rating").takeIf { it > 0 },
                seasonsCount = AdaptiveExtractor.getInt(data, "seasons", 1),
                description = AdaptiveExtractor.getString(data, "description").takeIf { it.isNotBlank() },
                isPaid = paidCheck.isPaid,
                paidReason = paidCheck.reason.takeIf { paidCheck.isPaid },
                requiresSubscription = paidCheck.requiresSubscription,
                partner = paidCheck.partner,
                actionUrl = actionUrl
            )
        }
        
        private fun normalizeChannel(data: JSONObject): NormalizedCard.ChannelCard {
            var id = AdaptiveExtractor.getString(data, "id")
            val url = AdaptiveExtractor.getString(data, "url")
            if (id.isBlank()) {
                val match = Regex("/(?:person|channel)/(\\d+)").find(url)
                id = match?.groupValues?.get(1) ?: AdaptiveExtractor.makeDeterministicId("ch", AdaptiveExtractor.getString(data, "title"), url)
            }
            
            val subsCount = AdaptiveExtractor.getLong(data, "subscribers")
            
            return NormalizedCard.ChannelCard(
                id = id,
                name = AdaptiveExtractor.getString(data, "title", "Untitled"),
                avatar = AdaptiveExtractor.getString(data, "avatar").takeIf { it.isNotBlank() },
                description = AdaptiveExtractor.getString(data, "description").takeIf { it.isNotBlank() },
                subscribers = formatCount(subsCount),
                rawSubscribers = subsCount,
                videosCount = AdaptiveExtractor.getInt(data, "videos", 0),
                actionUrl = normalizeUrl(url)
            )
        }
        
        fun normalizePromo(data: JSONObject): NormalizedCard.PromoCard? {
            val button = data.optJSONObject("button")
            val actionUrlCheck = button?.optString("button_url")
                ?.takeIf { it.isNotBlank() }
                ?: AdaptiveExtractor.getString(data, "target")
                .takeIf { it.isNotBlank() }
                ?: AdaptiveExtractor.getString(data, "url")
                .takeIf { it.isNotBlank() }
            val actionUrl = normalizeUrl(actionUrlCheck)
            val title = AdaptiveExtractor.getString(data, "title", "Untitled")
            val description = AdaptiveExtractor.getString(data, "description")
            
            val checkText = (title + " " + description + " " + actionUrl).lowercase()
            val isBlocked = checkText.contains("premier") || 
                            checkText.contains("start") || 
                            checkText.contains("viju") || 
                            checkText.contains("премьер") || 
                            checkText.contains("вижу")
            if (isBlocked) {
                return null
            }
            
            val id = AdaptiveExtractor.getString(data, "id")
                .takeIf { it.isNotBlank() }
                ?: AdaptiveExtractor.makeDeterministicId("promo", title, actionUrl)
            
            return NormalizedCard.PromoCard(
                id = id,
                title = title,
                thumbnail = (AdaptiveExtractor.getString(data, "thumbnail").takeIf { it.isNotBlank() }
                    ?: AdaptiveExtractor.getString(data, "picture").takeIf { it.isNotBlank() }),
                description = description.takeIf { it.isNotBlank() },
                actionUrl = actionUrl
            )
        }
        
        private fun normalizePlaylist(data: JSONObject): NormalizedCard.PlaylistCard {
            val url = AdaptiveExtractor.getString(data, "url")
            val id = AdaptiveExtractor.getString(data, "id")
                .takeIf { it.isNotBlank() }
                ?: AdaptiveExtractor.getString(data, "playlist_id")
                .takeIf { it.isNotBlank() }
                ?: AdaptiveExtractor.getString(data, "playlist_id_string")
                .takeIf { it.isNotBlank() }
                ?: AdaptiveExtractor.makeDeterministicId("playlist", AdaptiveExtractor.getString(data, "title"), url)
            
            val finalUrl = url.takeIf { it.isNotBlank() } ?: "https://rutube.ru/api/playlist/custom/$id/videos/"
            val vCount = if (data.has("videos_count")) {
                data.optInt("videos_count")
            } else if (data.has("video_count")) {
                data.optInt("video_count")
            } else {
                AdaptiveExtractor.getInt(data, "videos", 0)
            }

            return NormalizedCard.PlaylistCard(
                id = id,
                title = AdaptiveExtractor.getString(data, "title", "Untitled"),
                thumbnail = (AdaptiveExtractor.getString(data, "thumbnail").takeIf { it.isNotBlank() }
                    ?: AdaptiveExtractor.getString(data, "picture").takeIf { it.isNotBlank() }
                    ?: AdaptiveExtractor.getString(data, "image").takeIf { it.isNotBlank() }),
                videosCount = vCount,
                actionUrl = normalizeUrl(finalUrl)
            )
        }
        
        private fun normalizeUnknown(data: JSONObject, model: String): NormalizedCard.UnknownCard {
            val url = AdaptiveExtractor.getString(data, "url")
            val id = AdaptiveExtractor.getString(data, "id")
                .takeIf { it.isNotBlank() }
                ?: AdaptiveExtractor.makeDeterministicId("unknown", AdaptiveExtractor.getString(data, "title"), url)
            
            return NormalizedCard.UnknownCard(
                id = id,
                title = AdaptiveExtractor.getString(data, "title", "Unknown"),
                thumbnail = AdaptiveExtractor.getString(data, "thumbnail").takeIf { it.isNotBlank() },
                rawType = model,
                actionUrl = normalizeUrl(url)
            )
        }
    }
    
    // ==================== PAGINATION EXTRACTOR ====================
    
    object PaginationExtractor {
        fun extract(json: JSONObject): PaginationInfo {
            // Паттерн 1: Стандартный
            if (json.has("has_next") || json.has("next")) {
                return PaginationInfo(
                    hasNext = json.optBoolean("has_next", json.has("next")),
                    nextUrl = normalizeUrl(json.optString("next", null)),
                    page = json.optInt("page", 1),
                    perPage = json.optInt("per_page", 20),
                    total = if (json.has("total")) json.getInt("total") else null
                )
            }
            
            // Паттерн 2: pagination объект
            val pagination = json.optJSONObject("pagination")
            if (pagination != null) {
                return PaginationInfo(
                    hasNext = pagination.optBoolean("has_next", false),
                    nextUrl = normalizeUrl(pagination.optString("next", null)),
                    page = pagination.optInt("page", 1),
                    perPage = pagination.optInt("per_page", 20),
                    total = if (pagination.has("total")) pagination.getInt("total") else null
                )
            }
            
            return PaginationInfo()
        }
    }
    
    // ==================== RESPONSE ANALYZER ====================
    
    object ResponseAnalyzer {
        
        fun parse(jsonObj: JSONObject, isPromoGroup: Boolean = false, url: String? = null): ParsedResponse {
            val actualPromoGroup = isPromoGroup || (url?.contains("/promogroup/") == true)
            // Каталог с вкладками
            if (jsonObj.has("tabs")) {
                return parseCatalogFeed(jsonObj)
            }
            
            // Пагинированный ответ или список видео/элементов
            if (jsonObj.has("results") || jsonObj.has("videos") || jsonObj.has("items")) {
                return parsePaginatedResponse(jsonObj, actualPromoGroup)
            }
            
            // Одиночный плейлист
            if (jsonObj.has("title") && (jsonObj.has("playlist_id") || jsonObj.has("playlist_id_string") || jsonObj.has("videos_count") || jsonObj.has("video_count"))) {
                val playlist = CardNormalizer.normalizeOrNull(jsonObj)
                return ParsedResponse(
                    type = if (playlist != null) EntityType.PLAYLIST else EntityType.EMPTY,
                    items = playlist?.let { listOf(it) } ?: emptyList()
                )
            }
            
            // Одиночное видео
            if (jsonObj.has("id") && (jsonObj.has("video_url") || jsonObj.has("duration"))) {
                val video = CardNormalizer.normalizeOrNull(jsonObj)
                return ParsedResponse(
                    type = if (video != null) EntityType.VIDEO_ITEM else EntityType.EMPTY,
                    items = video?.let { listOf(it) } ?: emptyList()
                )
            }
            
            // Сериал
            if (jsonObj.has("id") && jsonObj.has("seasons_count")) {
                val series = CardNormalizer.normalizeOrNull(jsonObj)
                return ParsedResponse(
                    type = if (series != null) EntityType.TV_SERIES else EntityType.EMPTY,
                    items = series?.let { listOf(it) } ?: emptyList()
                )
            }
            
            return ParsedResponse(
                type = EntityType.UNKNOWN,
                error = "Unrecognized response structure"
            )
        }
        
        private fun parseCatalogFeed(jsonObj: JSONObject): ParsedResponse {
            val tabsArray = jsonObj.optJSONArray("tabs") ?: JSONArray()
            val tabList = mutableListOf<TabInfo>()
            var totalResources = 0
            
            for (i in 0 until tabsArray.length()) {
                val tabObj = tabsArray.optJSONObject(i) ?: continue
                val resourcesArray = tabObj.optJSONArray("resources") ?: JSONArray()
                val resourceList = mutableListOf<ResourceInfo>()
                
                for (j in 0 until resourcesArray.length()) {
                    val resObj = resourcesArray.optJSONObject(j) ?: continue
                    val contentType = resObj.optJSONObject("content_type")
                    val model = contentType?.optString("model") ?: "unknown"
                    val url = resObj.optString("url", "").lowercase()
                    
                    var detectedType = when (model) {
                        "tag", "playlist" -> EntityType.VIDEO_LIST
                        "tv" -> EntityType.TV_SERIES
                        "cardgroup", "subscriptiontvseries", "promogroup" -> EntityType.CONTAINER
                        "userchannel", "person", "channel", "author" -> EntityType.CHANNEL
                        else -> EntityType.UNKNOWN
                    }
                    
                    if (detectedType == EntityType.UNKNOWN && url.isNotBlank()) {
                        detectedType = when {
                            url.contains("/person/") || url.contains("/channel/") || url.contains("/userchannel/") -> EntityType.CHANNEL
                            url.contains("/tv/") || url.contains("/show/") || url.contains("/serial/") || url.contains("/subscriptiontvseries/") -> EntityType.TV_SERIES
                            url.contains("/playlist/") -> EntityType.PLAYLIST
                            else -> EntityType.UNKNOWN
                        }
                    }
                    
                    val nameVal = resObj.optString("name", "Раздел")
                    val normUrl = normalizeUrl(resObj.optString("url"))
                    val checkText = (nameVal + " " + normUrl).lowercase()
                    val isBlocked = checkText.contains("premier") || 
                                    checkText.contains("start") || 
                                    checkText.contains("viju") || 
                                    checkText.contains("премьер") || 
                                    checkText.contains("вижу")
                    
                    if (!isBlocked) {
                        resourceList.add(
                            ResourceInfo(
                                name = nameVal,
                                url = normUrl,
                                type = detectedType,
                                extraParams = resObj.optJSONObject("extra_params")
                            )
                        )
                        totalResources++
                    }
                }
                
                val tabName = tabObj.optString("name", "Вкладка")
                val tabNameLower = tabName.lowercase()
                val isBlockedTab = tabNameLower.contains("premier") || 
                                   tabNameLower.contains("start") || 
                                   tabNameLower.contains("viju") || 
                                   tabNameLower.contains("премьер") || 
                                   tabNameLower.contains("вижу")
                
                if (!isBlockedTab) {
                    tabList.add(
                        TabInfo(
                            id = tabObj.optInt("id", 0),
                            name = tabName,
                            resources = resourceList
                        )
                    )
                }
            }
            
            // Извлечение связанного контента
            val relatedTv = mutableListOf<NormalizedCard.TvSeriesCard>()
            val relatedTvArray = jsonObj.optJSONArray("related_tv")
            if (relatedTvArray != null) {
                for (i in 0 until relatedTvArray.length()) {
                    relatedTvArray.optJSONObject(i)?.let {
                        CardNormalizer.normalizeOrNull(it)?.let { card ->
                            if (card is NormalizedCard.TvSeriesCard) relatedTv.add(card)
                        }
                    }
                }
            } else {
                val relatedTvObj = jsonObj.optJSONObject("related_tv")
                if (relatedTvObj != null) {
                    CardNormalizer.normalizeOrNull(relatedTvObj)?.let { card ->
                        if (card is NormalizedCard.TvSeriesCard) relatedTv.add(card)
                    }
                }
            }
            
            val relatedPersons = mutableListOf<NormalizedCard.ChannelCard>()
            val relatedPersonsArray = jsonObj.optJSONArray("related_person")
            if (relatedPersonsArray != null) {
                for (i in 0 until relatedPersonsArray.length()) {
                    relatedPersonsArray.optJSONObject(i)?.let {
                        CardNormalizer.normalizeOrNull(it)?.let { card ->
                            if (card is NormalizedCard.ChannelCard) relatedPersons.add(card)
                        }
                    }
                }
            } else {
                val relatedPersonsObj = jsonObj.optJSONObject("related_person")
                if (relatedPersonsObj != null) {
                    CardNormalizer.normalizeOrNull(relatedPersonsObj)?.let { card ->
                        if (card is NormalizedCard.ChannelCard) relatedPersons.add(card)
                    }
                }
            }
            
            return ParsedResponse(
                type = EntityType.FEED_CATALOG,
                title = AdaptiveExtractor.getString(jsonObj, "title", "Каталог"),
                description = AdaptiveExtractor.getString(jsonObj, "description").takeIf { it.isNotBlank() },
                tabs = tabList,
                relatedTv = relatedTv,
                relatedPersons = relatedPersons,
                metadata = ResponseMetadata(totalResources = totalResources)
            )
        }
        
        private fun parsePaginatedResponse(jsonObj: JSONObject, isPromoGroup: Boolean): ParsedResponse {
            val resultsArray = jsonObj.optJSONArray("results") 
                ?: jsonObj.optJSONArray("videos")
                ?: jsonObj.optJSONArray("items")
                ?: JSONArray()
            val pagination = PaginationExtractor.extract(jsonObj)
            
            if (resultsArray.length() == 0) {
                return ParsedResponse(type = EntityType.EMPTY, pagination = pagination)
            }
            
            val items = mutableListOf<NormalizedCard>()
            var filteredCount = 0
            
            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.optJSONObject(i) ?: continue
                
                // Unpack widget structures (e.g. widget_type: "tv_channel_cards")
                val nestedCards = item.optJSONArray("cards") 
                    ?: item.optJSONArray("items") 
                    ?: item.optJSONArray("results") 
                    ?: item.optJSONArray("videos")
                
                if (nestedCards != null && nestedCards.length() > 0) {
                    for (j in 0 until nestedCards.length()) {
                        val nestedItem = nestedCards.optJSONObject(j) ?: continue
                        val normalized = if (isPromoGroup) {
                            CardNormalizer.normalizePromo(nestedItem)
                        } else {
                            CardNormalizer.normalizeOrNull(nestedItem)
                        }
                        if (normalized != null) {
                            items.add(normalized)
                        } else {
                            filteredCount++
                        }
                    }
                } else {
                    val normalized = if (isPromoGroup) {
                        CardNormalizer.normalizePromo(item)
                    } else {
                        CardNormalizer.normalizeOrNull(item)
                    }
                    if (normalized != null) {
                        items.add(normalized)
                    } else {
                        filteredCount++
                    }
                }
            }
            
            val firstCard = items.firstOrNull()
            val overallType = when (firstCard) {
                is NormalizedCard.PromoCard -> EntityType.PROMO_GROUP
                is NormalizedCard.TvSeriesCard -> EntityType.TV_SERIES
                is NormalizedCard.ChannelCard -> EntityType.CHANNEL
                is NormalizedCard.PlaylistCard -> EntityType.PLAYLIST
                else -> EntityType.VIDEO_LIST
            }
            
            return ParsedResponse(
                type = overallType,
                items = items,
                pagination = pagination,
                metadata = ResponseMetadata(
                    filteredPaidCount = filteredCount,
                    totalOriginalCount = resultsArray.length()
                )
            )
        }
    }
    
    // ==================== DATA CLASSES ====================
    
    data class ParsedResponse(
        val type: EntityType,
        val items: List<NormalizedCard> = emptyList(),
        val title: String? = null,
        val description: String? = null,
        val tabs: List<TabInfo> = emptyList(),
        val relatedTv: List<NormalizedCard.TvSeriesCard> = emptyList(),
        val relatedPersons: List<NormalizedCard.ChannelCard> = emptyList(),
        val pagination: PaginationInfo = PaginationInfo(),
        val metadata: ResponseMetadata = ResponseMetadata(),
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
        val type: EntityType,
        val extraParams: JSONObject?
    )
    
    data class PaginationInfo(
        val hasNext: Boolean = false,
        val nextUrl: String? = null,
        val page: Int = 1,
        val perPage: Int = 20,
        val total: Int? = null
    )
    
    data class ResponseMetadata(
        val filteredPaidCount: Int = 0,
        val totalOriginalCount: Int = 0,
        val totalResources: Int = 0
    )
    
    // ==================== HELPER FUNCTIONS ====================
    
    private fun formatDuration(seconds: Double): String {
        val totalSeconds = seconds.toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }
    
    private fun formatCount(count: Long): String {
        return when {
            count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1000.0)
            count > 0 -> count.toString()
            else -> "0"
        }
    }
    
    private fun formatDate(dateString: String?): String {
        if (dateString.isNullOrBlank()) return "Недавно"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date = parser.parse(dateString) ?: return "Недавно"
            val formatter = SimpleDateFormat("d MMM yyyy", Locale("ru"))
            formatter.format(date)
        } catch (e: Exception) {
            "Недавно"
        }
    }
    
    private fun parseTimestamp(dateString: String?): Long {
        if (dateString.isNullOrBlank()) return 0L
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            parser.parse(dateString)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun normalizeUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> "https://rutube.ru$trimmed"
            trimmed.contains("rutube.ru") -> {
                "https://" + trimmed.removePrefix("http://").removePrefix("https://").removePrefix("//")
            }
            else -> "https://rutube.ru/$trimmed"
        }
    }
}
