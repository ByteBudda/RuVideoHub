package com.example.data.rutube.parser

import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

// ==================== CONFIGURATION ====================
const val MAX_RECURSION_DEPTH = 5
const val PAID_CONFIDENCE_THRESHOLD = 0.7
const val FIELD_CONFIDENCE_THRESHOLD = 0.5
const val LEARNING_CACHE_SIZE = 500

data class FieldMapping(
    val canonicalName: String,
    val confidence: Double,
    val sourcePath: String
)

enum class EntityType {
    FEED_CATALOG, CATEGORY_FEED, CONTAINER, VIDEO_LIST, VIDEO_ITEM,
    CHANNEL, TV_SERIES, PROMO_GROUP, PLAYLIST, LIVE_STREAM, EXTERNAL, UNKNOWN, EMPTY
}

enum class EndpointPattern {
    SEARCH_COMBINED, SEARCH_VIDEO, SEARCH_CHANNEL, SEARCH_PERSON,
    FEEDS_SHOWCASE, FEEDS_CATEGORY, FEEDS_PROMOGROUP, FEEDS_POPULAR,
    VIDEO_DETAIL, CHANNEL_DETAIL, PLAYLIST_DETAIL, TV_DETAIL, UNKNOWN
}

data class PaidCheck(
    val isPaid: Boolean,
    val reason: String,
    val confidence: Double,
    val partner: String? = null,
    val requiresSubscription: Boolean = false
)

data class FieldGuess(
    val canonicalField: String,
    val confidence: Double,
    val reason: String
)

data class FieldConfidence(
    val confidence: Double,
    val sourcePath: String,
    val reason: String
)

data class EntitySignature(
    val fields: Map<String, FieldConfidence>,
    val nestedArrays: List<String> = emptyList(),
    val type: EntityType = EntityType.UNKNOWN
)

sealed class NormalizedCard {
    abstract val id: String
    abstract val title: String
    abstract val actionUrl: String?
    abstract val confidence: Double

    data class VideoCard(
        override val id: String,
        override val title: String,
        val thumbnail: String?,
        val previewGif: String?,
        val duration: String,
        val channelName: String,
        val channelId: String?,
        val channelAvatar: String?,
        val views: String,
        val rawViews: Long,
        val published: String,
        val publishedTimestamp: Long,
        val rating: String?,
        val isPaid: Boolean,
        val paidReason: String?,
        val requiresSubscription: Boolean,
        val partner: String?,
        val description: String,
        val tags: List<String>,
        override val actionUrl: String?,
        override val confidence: Double
    ) : NormalizedCard()

    data class TvSeriesCard(
        override val id: String,
        override val title: String,
        val originalTitle: String?,
        val poster: String?,
        val year: String?,
        val rating: Double?,
        val seasonsCount: Int,
        val episodesCount: Int,
        val description: String?,
        val isPaid: Boolean,
        val paidReason: String?,
        val requiresSubscription: Boolean,
        val partner: String?,
        override val actionUrl: String?,
        override val confidence: Double
    ) : NormalizedCard()

    data class ChannelCard(
        override val id: String,
        val name: String,
        val avatar: String?,
        val description: String?,
        val subscribers: String,
        val rawSubscribers: Long,
        val videosCount: Int,
        override val actionUrl: String?,
        override val confidence: Double
    ) : NormalizedCard() {
        override val title: String get() = name
    }

    data class PlaylistCard(
        override val id: String,
        override val title: String,
        val thumbnail: String?,
        val videosCount: Int,
        override val actionUrl: String?,
        override val confidence: Double
    ) : NormalizedCard()

    data class PromoCard(
        override val id: String,
        override val title: String,
        val thumbnail: String?,
        val description: String?,
        override val actionUrl: String?,
        override val confidence: Double = 1.0
    ) : NormalizedCard()

    data class UnknownCard(
        override val id: String,
        override val title: String,
        val thumbnail: String?,
        val rawType: String,
        val extractedFields: Map<String, Any?>,
        override val actionUrl: String?,
        override val confidence: Double
    ) : NormalizedCard()
}

data class ResourceInfo(
    val name: String,
    val url: String,
    val type: EntityType,
    val extraParams: JSONObject? = null
)

data class TabInfo(val id: Int, val name: String, val resources: List<ResourceInfo>, val url: String = "", val isSelected: Boolean = false)

data class ResponsePagination(val nextUrl: String?, val hasNext: Boolean = false, val page: Int = 1, val perPage: Int = 20, val total: Int = 0)

data class ResponseMetadata(val filteredPaidCount: Int = 0, val totalOriginalCount: Int = 0, val totalResources: Int = 0)

data class ParsedResponse(
    val type: EntityType,
    val items: List<NormalizedCard> = emptyList(),
    val resources: List<ResourceInfo> = emptyList(),
    val relatedTv: List<NormalizedCard.TvSeriesCard> = emptyList(),
    val relatedPersons: List<NormalizedCard.ChannelCard> = emptyList(),
    val pagination: ResponsePagination? = null,
    val metadata: ResponseMetadata? = null,
    val error: String? = null,
    val tabs: List<TabInfo> = emptyList(),
    val title: String? = null,
    val description: String? = null
)

// Helper functions that were originally in SmartRutubeParser
fun normalizeUrl(url: String?): String {
    if (url.isNullOrBlank()) return ""
    return if (url.startsWith("//")) "https:$url"
    else if (url.startsWith("/")) "https://rutube.ru$url"
    else url
}

fun formatDuration(seconds: Double): String {
    val sec = seconds.toInt()
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}

fun formatCount(count: Long): String {
    if (count < 1000) return count.toString()
    if (count < 1000000) return String.format("%.1fK", count / 1000.0).replace(".0K", "K")
    return String.format("%.1fM", count / 1000000.0).replace(".0M", "M")
}

fun formatDate(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return ""
    return try {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        val date = format.parse(dateStr)
        val outFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
        outFormat.format(date!!)
    } catch (e: Exception) {
        dateStr.substringBefore("T")
    }
}

fun parseTimestamp(dateStr: String?): Long {
    if (dateStr.isNullOrBlank()) return 0L
    return try {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        format.parse(dateStr)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}

fun isBlockedText(text: String?): Boolean {
    if (text == null) return false
    val lower = text.lowercase()
    return lower.contains("заблокирован") || lower.contains("blocked")
}

object ParserConfig {
    val PAID_PARTNERS = setOf(
        "PREMIER", "START", "IVI", "KION", "MORE.TV", "OKKO", "WINK", "AMEDIATEKA",
        "PREMIER.RUTUBE", "START.RUTUBE", "IVI.RUTUBE", "KION.RUTUBE"
    )
    val PAID_SUBSCRIPTION_CODES = setOf(
        "PREMIER_RUTUBE_YAPPY", "PREMIER_RUTUBE_START", "PREMIER_RUTUBE_GAZPROM_BONUS",
        "premier-rutube-gazprom-bonus-lite", "START_RUTUBE", "IVI_RUTUBE", "KION_RUTUBE",
        "PREMIER", "START", "IVI", "KION"
    )
    val PAID_KEYWORDS = listOf(
        "оформить подписку", "только по подписке", "доступно в подписке",
        "трейлер сериала", "подписка", "премиум", "premium", "subscription only"
    )
}

val fieldLearningCache = ConcurrentHashMap<String, MutableMap<String, FieldMapping>>()

data class ExtractedValue(val value: Any?, val confidence: Double = 1.0, val reason: String = "")
