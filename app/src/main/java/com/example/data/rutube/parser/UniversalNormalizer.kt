package com.example.data.rutube.parser

import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object UniversalNormalizer {

    fun normalizeOrNull(json: JSONObject, endpointHint: String? = null): NormalizedCard? {
        if (PaidContentDetector.shouldExclude(json, endpointHint)) return null

        val data = if (json.has("object") && json.optJSONObject("object") != null)
            json.optJSONObject("object")!! else json

        val signature = SchemaAnalyzer.buildSignature(data)
        val objectUrl = data.optString("url", null)?.takeIf { it.isNotBlank() }
            ?: data.optString("absolute_url", null)?.takeIf { it.isNotBlank() }
            ?: data.optString("video_url", null)?.takeIf { it.isNotBlank() }
        
        // Only use endpointHint for type detection if the item doesn't have its own URL
        // AND the model is completely unknown. But we must be careful not to label
        // videos as TV_SERIES just because they are in a TV_SERIES endpoint.
        val detectedType = SchemaAnalyzer.detectEntityType(signature, objectUrl)
        val model = extractModel(json, data)

        val isLiveStream = data.optJSONObject("type")?.optInt("id") == 12
                || data.optString("type") == "live"
                || data.optBoolean("is_live", false)
                || json.optBoolean("is_live", false)

        val isTvModel = model in listOf("tv", "show", "serial", "tvshow")
        val isChannelModel = model in listOf("userchannel", "person", "channel", "author", "user")
        val isPlaylistModel = model == "playlist"
        val isPromoModel = model == "promo"
        val isVideoModel = model in listOf("video", "live", "shorts", "episode", "trailer", "movie")

        return when {
            isTvModel -> normalizeTvShow(data, signature, endpointHint)
            isChannelModel -> normalizeChannel(data, signature, endpointHint)
            isPlaylistModel -> normalizePlaylist(data, signature, endpointHint)
            isPromoModel || json.has("button") || json.has("target") -> normalizePromo(json, endpointHint)
            isVideoModel || isLiveStream -> normalizeVideo(data, signature, endpointHint)
            
            // Fallbacks if model is unknown
            detectedType == EntityType.CHANNEL -> normalizeChannel(data, signature, endpointHint)
            detectedType == EntityType.PLAYLIST -> normalizePlaylist(data, signature, endpointHint)
            detectedType == EntityType.PROMO_GROUP -> normalizePromo(json, endpointHint)
            detectedType == EntityType.VIDEO_ITEM -> normalizeVideo(data, signature, endpointHint)
            detectedType == EntityType.TV_SERIES -> normalizeTvShow(data, signature, endpointHint)
            
            // If detectedType is UNKNOWN, try to use endpointHint if it provides a strong clue, 
            // but only if the item doesn't look like a video.
            else -> {
                val endpointType = SchemaAnalyzer.detectEntityType(signature, endpointHint)
                val looksLikeVideo = signature.fields["duration"]?.confidence ?: 0.0 > 0.5
                
                if (looksLikeVideo) {
                    normalizeVideo(data, signature, endpointHint)
                } else when (endpointType) {
                    EntityType.CHANNEL -> normalizeChannel(data, signature, endpointHint)
                    EntityType.PLAYLIST -> normalizePlaylist(data, signature, endpointHint)
                    EntityType.TV_SERIES -> normalizeTvShow(data, signature, endpointHint)
                    else -> normalizeUnknown(data, signature, endpointHint)
                }
            }
        }
    }

    private fun extractModel(outer: JSONObject, inner: JSONObject): String {
        val sources = listOf(
            outer.optJSONObject("content_type")?.optString("model"),
            inner.optJSONObject("content_type")?.optString("model"),
            outer.optJSONObject("type")?.optString("name"),
            inner.optJSONObject("type")?.optString("name"),
            outer.optJSONObject("type")?.optString("code"),
            inner.optJSONObject("type")?.optString("code"),
            outer.optString("type", null),
            inner.optString("type", null),
            outer.optString("model", null),
            inner.optString("model", null)
        )
        return sources.filterNotNull().firstOrNull { it.isNotBlank() } ?: ""
    }

    private fun normalizeVideo(data: JSONObject, sig: EntitySignature, endpointHint: String?): NormalizedCard.VideoCard {
        val id = AdaptiveExtractor.getString(data, "id", endpointHint)
            .takeIf { it.isNotBlank() }
            ?: AdaptiveExtractor.getString(data, "code", endpointHint)
            ?: makeId("video", data, endpointHint)

        val author = AdaptiveExtractor.getObject(data, "author", endpointHint)
        val authorName = if (author != null) {
            AdaptiveExtractor.getString(author, "name", endpointHint).takeIf { it.isNotBlank() }
                ?: AdaptiveExtractor.getString(author, "title", endpointHint).takeIf { it.isNotBlank() }
                ?: "Rutube"
        } else {
            AdaptiveExtractor.getString(data, "feed_name", endpointHint).takeIf { it.isNotBlank() }
                ?: AdaptiveExtractor.getString(data, "author", endpointHint).takeIf { it.isNotBlank() }
                ?: "Rutube"
        }

        val viewsCount = AdaptiveExtractor.getLong(data, "views", endpointHint)
        val durationSeconds = AdaptiveExtractor.getDouble(data, "duration", endpointHint, -1.0)
        val paidCheck = PaidContentDetector.check(data, endpointHint)

        val isLive = data.optJSONObject("type")?.optInt("id") == 12
                || data.optString("type") == "live"
                || data.optBoolean("is_live", false)

        val ageVal = data.optJSONObject("pg_rating")?.opt("age")?.toString() ?: ""
        val rawRating = AdaptiveExtractor.getString(data, "rating", endpointHint).takeIf { it.isNotBlank() }
        val ratingWithAge = if (ageVal.isNotBlank() && ageVal != "null") {
            if (rawRating != null) "$rawRating ($ageVal+)" else "$ageVal+"
        } else rawRating

        val actionUrl = normalizeUrl(
            AdaptiveExtractor.getString(data, "absolute_url", endpointHint)
                .takeIf { it.isNotBlank() }
                ?: AdaptiveExtractor.getString(data, "url", endpointHint)
        )

        return NormalizedCard.VideoCard(
            id = id,
            title = AdaptiveExtractor.getString(data, "title", endpointHint, "Untitled"),
            thumbnail = AdaptiveExtractor.getString(data, "thumbnail", endpointHint).takeIf { it.isNotBlank() },
            previewGif = AdaptiveExtractor.getString(data, "preview_url", endpointHint).takeIf { it.isNotBlank() },
            duration = if (isLive) "ЭФИР" else if (durationSeconds > 0) formatDuration(durationSeconds) else "00:00",
            channelName = authorName,
            channelId = author?.let { AdaptiveExtractor.getString(it, "id", endpointHint) }?.takeIf { it.isNotBlank() }
                ?: AdaptiveExtractor.getString(data, "author_id", endpointHint).takeIf { it.isNotBlank() },
            channelAvatar = author?.let { AdaptiveExtractor.getString(it, "thumbnail", endpointHint) }?.takeIf { it.isNotBlank() }
                ?: AdaptiveExtractor.getString(data, "author_avatar", endpointHint).takeIf { it.isNotBlank() },
            views = formatCount(viewsCount),
            rawViews = viewsCount,
            published = formatDate(AdaptiveExtractor.getString(data, "created", endpointHint)),
            publishedTimestamp = parseTimestamp(AdaptiveExtractor.getString(data, "created", endpointHint)),
            rating = ratingWithAge,
            isPaid = paidCheck.isPaid,
            paidReason = paidCheck.reason.takeIf { paidCheck.isPaid },
            requiresSubscription = paidCheck.requiresSubscription,
            partner = paidCheck.partner,
            description = AdaptiveExtractor.getString(data, "description", endpointHint),
            tags = AdaptiveExtractor.getArray(data, "tags", endpointHint)?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
            } ?: emptyList(),
            actionUrl = actionUrl,
            confidence = sig.fields.values.map { it.confidence }.average().coerceIn(0.0, 1.0)
        )
    }

    private fun normalizeTvShow(data: JSONObject, sig: EntitySignature, endpointHint: String?): NormalizedCard.TvSeriesCard {
        val actionUrl = normalizeUrl(
            AdaptiveExtractor.getString(data, "content", endpointHint).takeIf { it.isNotBlank() }
                ?: AdaptiveExtractor.getString(data, "absolute_url", endpointHint).takeIf { it.isNotBlank() }
                ?: AdaptiveExtractor.getString(data, "url", endpointHint)
        )
        val id = AdaptiveExtractor.getString(data, "id", endpointHint)
            .takeIf { it.isNotBlank() }
            ?: makeId("tv", data, endpointHint)
        val paidCheck = PaidContentDetector.check(data, endpointHint)

        return NormalizedCard.TvSeriesCard(
            id = id,
            title = AdaptiveExtractor.getString(data, "title", endpointHint, "Untitled"),
            originalTitle = AdaptiveExtractor.getString(data, "original_title", endpointHint).takeIf { it.isNotBlank() },
            poster = AdaptiveExtractor.getString(data, "thumbnail", endpointHint).takeIf { it.isNotBlank() },
            year = AdaptiveExtractor.getString(data, "year", endpointHint).takeIf { it.isNotBlank() },
            rating = AdaptiveExtractor.getDouble(data, "rating", endpointHint).takeIf { it > 0 },
            seasonsCount = AdaptiveExtractor.getInt(data, "seasons", endpointHint, 1),
            episodesCount = AdaptiveExtractor.getInt(data, "videos", endpointHint, 0),
            description = AdaptiveExtractor.getString(data, "description", endpointHint).takeIf { it.isNotBlank() },
            isPaid = paidCheck.isPaid,
            paidReason = paidCheck.reason.takeIf { paidCheck.isPaid },
            requiresSubscription = paidCheck.requiresSubscription,
            partner = paidCheck.partner,
            actionUrl = actionUrl,
            confidence = sig.fields.values.map { it.confidence }.average().coerceIn(0.0, 1.0)
        )
    }

    private fun normalizeChannel(data: JSONObject, sig: EntitySignature, endpointHint: String?): NormalizedCard.ChannelCard {
        val url = AdaptiveExtractor.getString(data, "url", endpointHint)
        var id = AdaptiveExtractor.getString(data, "id", endpointHint)
        if (id.isBlank()) {
            val match = Regex("/(?:person|channel)/(\\d+)").find(url)
            id = match?.groupValues?.get(1) ?: makeId("ch", data, endpointHint)
        }
        val subsCount = AdaptiveExtractor.getLong(data, "subscribers", endpointHint)

        return NormalizedCard.ChannelCard(
            id = id,
            name = AdaptiveExtractor.getString(data, "title", endpointHint, "Untitled"),
            avatar = AdaptiveExtractor.getString(data, "thumbnail", endpointHint).takeIf { it.isNotBlank() }
                ?: AdaptiveExtractor.getString(data, "avatar", endpointHint).takeIf { it.isNotBlank() },
            description = AdaptiveExtractor.getString(data, "description", endpointHint).takeIf { it.isNotBlank() },
            subscribers = formatCount(subsCount),
            rawSubscribers = subsCount,
            videosCount = AdaptiveExtractor.getInt(data, "videos", endpointHint, 0),
            actionUrl = normalizeUrl(url),
            confidence = sig.fields.values.map { it.confidence }.average().coerceIn(0.0, 1.0)
        )
    }

    private fun normalizePlaylist(data: JSONObject, sig: EntitySignature, endpointHint: String?): NormalizedCard.PlaylistCard {
        val url = AdaptiveExtractor.getString(data, "url", endpointHint)
        val obj = data.optJSONObject("object")
        val target = obj ?: data

        val id = target.optString("id", null)?.takeIf { it.isNotBlank() }
            ?: data.optString("object_id", null)?.takeIf { it.isNotBlank() }
            ?: AdaptiveExtractor.getString(data, "id", endpointHint).takeIf { it.isNotBlank() }
            ?: AdaptiveExtractor.getString(target, "id", endpointHint).takeIf { it.isNotBlank() }
            ?: makeId("playlist", data, endpointHint) ?: ""

        val vCount = target.optInt("video_count", 0)
            .takeIf { it > 0 }
            ?: target.optInt("videos_count", 0)
            .takeIf { it > 0 }
            ?: target.optInt("video_count", 0)
            .takeIf { it > 0 }
            ?: AdaptiveExtractor.getInt(data, "videos", endpointHint, 0)
            .takeIf { it > 0 }
            ?: data.optInt("videos_count", data.optInt("video_count", 0))

        val contentUrl = target.optString("absolute_url", null)?.takeIf { it.isNotBlank() }
            ?: target.optString("content", null)?.takeIf { it.isNotBlank() }
            ?: data.optString("url", null)?.takeIf { it.isNotBlank() }
            ?: "https://rutube.ru/api/playlist/custom/$id/videos/" ?: ""

        val title = target.optString("name", null)?.takeIf { it.isNotBlank() }
            ?: target.optString("title", null)?.takeIf { it.isNotBlank() } ?: ""
            ?: AdaptiveExtractor.getString(data, "title", endpointHint, "Untitled") ?: ""

        val thumbnail = target.optString("picture", null)?.takeIf { it.isNotBlank() }
            ?: target.optString("thumbnail", null)?.takeIf { it.isNotBlank() }
            ?: AdaptiveExtractor.getString(data, "thumbnail", endpointHint).takeIf { it.isNotBlank() } ?: ""
            ?: AdaptiveExtractor.getString(data, "picture", endpointHint).takeIf { it.isNotBlank() } ?: ""
            ?: AdaptiveExtractor.getString(data, "image", endpointHint) ?: "".takeIf { it.isNotBlank() } ?: ""

        return NormalizedCard.PlaylistCard(
            id = id,
            title = title,
            thumbnail = thumbnail,
            videosCount = vCount,
            actionUrl = normalizeUrl(contentUrl),
            confidence = sig.fields.values.map { it.confidence }.average().coerceIn(0.0, 1.0)
        )
    }

    fun normalizePromo(data: JSONObject, endpointHint: String? = null): NormalizedCard.PromoCard? {
        val button = data.optJSONObject("button")
        val actionUrl = normalizeUrl(
            button?.optString("button_url")?.takeIf { it.isNotBlank() }
                ?: AdaptiveExtractor.getString(data, "target", endpointHint)
                ?: AdaptiveExtractor.getString(data, "url", endpointHint)
        )
        val title = AdaptiveExtractor.getString(data, "title", endpointHint, "Untitled")
        val description = AdaptiveExtractor.getString(data, "description", endpointHint)

        val checkText = (title + " " + description + " " + actionUrl).lowercase()
        if (isBlockedText(checkText)) return null

        val id = AdaptiveExtractor.getString(data, "id", endpointHint)
            .takeIf { it.isNotBlank() }
            ?: makeId("promo", data, endpointHint)

        return NormalizedCard.PromoCard(
            id = id,
            title = title,
            thumbnail = AdaptiveExtractor.getString(data, "thumbnail", endpointHint).takeIf { it.isNotBlank() }
                ?: AdaptiveExtractor.getString(data, "picture", endpointHint).takeIf { it.isNotBlank() } ?: "",
            description = description.takeIf { it.isNotBlank() },
            actionUrl = actionUrl
        )
    }

    private fun normalizeUnknown(data: JSONObject, sig: EntitySignature, endpointHint: String?): NormalizedCard.UnknownCard {
        val url = AdaptiveExtractor.getString(data, "url", endpointHint)
        val id = AdaptiveExtractor.getString(data, "id", endpointHint)
            .takeIf { it.isNotBlank() }
            ?: makeId("unknown", data, endpointHint)

        val extractedFields = mutableMapOf<String, String>()
        for (key in data.keys()) {
            val value = data.opt(key)
            if (value != null && value.toString().isNotBlank() && value.toString().length < 500) {
                extractedFields[key] = value.toString().take(100)
            }
        }

        return NormalizedCard.UnknownCard(
            id = id,
            title = AdaptiveExtractor.getString(data, "title", endpointHint, "Unknown"),
            thumbnail = AdaptiveExtractor.getString(data, "thumbnail", endpointHint).takeIf { it.isNotBlank() },
            rawType = data.optJSONObject("type")?.optString("name") ?: data.optString("type", "unknown"),
            actionUrl = normalizeUrl(url),
            extractedFields = extractedFields,
            confidence = sig.fields.values.map { it.confidence }.average().coerceIn(0.0, 1.0)
        )
    }

    private fun makeId(prefix: String, data: JSONObject, endpointHint: String?): String {
        val title = AdaptiveExtractor.getString(data, "title", endpointHint)
        val url = AdaptiveExtractor.getString(data, "url", endpointHint)
        val base = url.takeIf { it.isNotBlank() } ?: title
        val hash = base.hashCode().toString().replace("-", "n")
        return "${prefix}_$hash"
    }
}
