package com.example.data.rutube.parser

import com.example.data.Video
import com.example.data.SubtitleTrack
import com.example.data.rutube.parser.NormalizedCard
import org.json.JSONObject

class RutubeParser {

    fun parseVideoListJson(bodyString: String, defaultCategoryName: String, url: String? = null): List<Video> {
        val mapped = mutableListOf<Video>()
        val trimmed = bodyString.trim()

        if (!trimmed.startsWith("{")) {
            android.util.Log.w("RutubeParser", "Non-JSON response from $url — likely blocked or HTML error")
            return mapped
        }

        try {
            val jsonObj = JSONObject(trimmed)
            val parsed = ResponseAnalyzer.parse(jsonObj, url)

            for (card in parsed.items) {
                val video = mapNormalizedCardToVideo(card, defaultCategoryName)
                if (!isBlockedContent(video)) {
                    mapped.add(video)
                }
            }

            for (card in parsed.relatedPersons) {
                val video = mapNormalizedCardToVideo(card, defaultCategoryName)
                if (!isBlockedContent(video)) {
                    mapped.add(video)
                }
            }
        } catch (ex: Exception) {
            android.util.Log.e("RutubeParser", "Error parsing $url", ex)
        }
        return mapped
    }

    internal fun isBlockedContent(video: Video): Boolean {
        val checkText = (video.id + " " + video.title + " " + video.channel + " " + video.description + " " + video.category).lowercase()
        return isBlockedText(checkText, includeTvOnline = false)
    }

    internal fun isBlockedText(text: String, includeTvOnline: Boolean = true): Boolean {
        val lower = text.lowercase()
        val baseBlocked = containsPremierBrand(lower) ||
               containsBrandWithBoundaries(lower, "start") ||
               lower.contains("viju") ||
               lower.contains("kion") ||
               lower.contains("кион") ||
               containsBrandWithBoundaries(lower, "ivi") ||
               containsBrandWithBoundaries(lower, "иви") ||
               lower.contains("okko") ||
               lower.contains("окко") ||
               containsBrandWithBoundaries(lower, "wink") ||
               containsBrandWithBoundaries(lower, "винк") ||
               lower.contains("amediateka") ||
               lower.contains("амедиатека") ||
               lower.contains("more.tv") ||
               lower.contains("онлайн-кинотеатр") ||
               lower.contains("онлайн кинотеатр") ||
               lower.contains("онлайн-кинотеатры") ||
               lower.contains("онлайн кинотеатры") ||
               lower.contains("online cinema") ||
               lower.contains("online-cinema") ||
               lower.contains("online-cinemas") ||
               lower.contains("online cinemas") ||
               lower.contains("feeds/kion") ||
               lower.contains("25841652")

        if (baseBlocked) return true

        if (includeTvOnline) {
            return lower.contains("тв онлайн") ||
                   lower.contains("телеканалы") ||
                   lower.contains("тв-каналы") ||
                   lower.contains("тв каналы") ||
                   lower.contains("tvchannels") ||
                   lower.contains("tv-channels") ||
                   lower.contains("онлайн тв") ||
                   lower.contains("онлайн-тв") ||
                   lower.contains("online tv") ||
                   lower.contains("online-tv")
        }
        return false
    }

    private fun containsPremierBrand(lower: String): Boolean {
        var idxEng = lower.indexOf("premier")
        while (idxEng != -1) {
            val nextCharIdx = idxEng + "premier".length
            if (nextCharIdx < lower.length) {
                val nextChar = lower[nextCharIdx]
                if (nextChar == 'e') {
                    idxEng = lower.indexOf("premier", nextCharIdx)
                    continue
                }
            }
            return true
        }

        var idxRus = lower.indexOf("премьер")
        while (idxRus != -1) {
            val nextCharIdx = idxRus + "премьер".length
            if (nextCharIdx < lower.length) {
                val nextChar = lower[nextCharIdx]
                if (nextChar in 'а'..'я' || nextChar == 'ё') {
                    idxRus = lower.indexOf("премьер", nextCharIdx)
                    continue
                }
            }
            return true
        }
        return false
    }

    private fun containsBrandWithBoundaries(lowerText: String, brand: String): Boolean {
        var index = lowerText.indexOf(brand)
        while (index != -1) {
            val prevCharSafe = if (index > 0) lowerText[index - 1] else ' '
            val nextCharSafe = if (index + brand.length < lowerText.length) lowerText[index + brand.length] else ' '
            
            val isPrevAlnum = prevCharSafe in 'a'..'z' || prevCharSafe in '0'..'9' || prevCharSafe in 'а'..'я' || prevCharSafe == 'ё'
            val isNextAlnum = nextCharSafe in 'a'..'z' || nextCharSafe in '0'..'9' || nextCharSafe in 'а'..'я' || nextCharSafe == 'ё'
            
            if (!isPrevAlnum && !isNextAlnum) {
                return true
            }
            index = lowerText.indexOf(brand, index + 1)
        }
        return false
    }

    internal fun mapNormalizedCardToVideo(
        card: NormalizedCard,
        defaultCategoryName: String
    ): Video {
        return when (card) {
            is NormalizedCard.VideoCard -> {
                Video(
                    id = card.id,
                    title = card.title,
                    channel = card.channelName,
                    views = card.views,
                    timeAgo = card.published,
                    duration = card.duration,
                    isPro = card.isPaid || card.requiresSubscription,
                    category = defaultCategoryName,
                    description = card.description,
                    thumbnailUrl = card.thumbnail,
                    authorId = card.channelId,
                    authorActionUrl = card.channelId?.let { "https://rutube.ru/api/video/person/$it/" },
                    authorAvatarUrl = card.channelAvatar
                )
            }
            is NormalizedCard.TvSeriesCard -> {
                val ratingStr = if (card.rating != null && card.rating > 0.05) " • Кинопоиск: ${card.rating}" else ""
                val yearVal = card.year ?: "Передача"
                val viewsText = if (card.episodesCount > 0 && card.seasonsCount <= 1) "${card.episodesCount} серий" else "${card.seasonsCount} сезонов"
                Video(
                    id = "tv_${card.id}__${card.actionUrl ?: ""}",
                    title = card.title,
                    channel = "Шоу • $yearVal$ratingStr",
                    views = viewsText,
                    timeAgo = "Смотреть выпуски",
                    duration = "СЕРИАЛ",
                    isPro = card.isPaid || card.requiresSubscription,
                    category = defaultCategoryName,
                    description = card.description ?: "Смотрите оригинальные сезоны и выпуски бесплатно.",
                    thumbnailUrl = card.poster
                )
            }
            is NormalizedCard.PlaylistCard -> {
                Video(
                    id = "playlist_${card.id}__${card.actionUrl ?: ""}",
                    title = card.title,
                    channel = "Плейлист • Подборка",
                    views = "${card.videosCount} видео",
                    timeAgo = "Смотреть плейлист",
                    duration = "ПЛЕЙЛИСТ",
                    isPro = false,
                    category = defaultCategoryName,
                    description = "Смотрите полную подборку видео из этого плейлиста.",
                    thumbnailUrl = card.thumbnail
                )
            }
            is NormalizedCard.ChannelCard -> {
                Video(
                    id = "channel_${card.id}__${card.actionUrl ?: ""}",
                    title = card.name,
                    channel = "Авторский канал • ${card.subscribers} подписчиков",
                    views = "${card.subscribers} подписчиков",
                    timeAgo = "${card.videosCount} видео",
                    duration = "КАНАЛ",
                    isPro = false,
                    category = defaultCategoryName,
                    description = card.description ?: "",
                    thumbnailUrl = card.avatar,
                    authorId = card.id,
                    authorAvatarUrl = card.avatar
                )
            }
            is NormalizedCard.PromoCard -> {
                Video(
                    id = "promo_${card.id}__${card.actionUrl ?: ""}",
                    title = card.title,
                    channel = "",
                    views = "",
                    timeAgo = "",
                    duration = "ПРОМО",
                    isPro = true,
                    category = defaultCategoryName,
                    description = card.description ?: "Спонсорский медиаконтент.",
                    thumbnailUrl = card.thumbnail
                )
            }
            is NormalizedCard.UnknownCard -> {
                Video(
                    id = "unknown_${card.id}__${card.actionUrl ?: ""}",
                    title = card.title,
                    channel = card.rawType ?: "Раздел каталога",
                    views = "Коллекция",
                    timeAgo = "Открыть раздел",
                    duration = "КАТАЛОГ",
                    isPro = false,
                    category = defaultCategoryName,
                    description = "Элемент каталога • Нажмите для открытия",
                    thumbnailUrl = card.thumbnail
                )
            }
        }
    }

    data class ParsedChannelProfile(
        val name: String,
        val description: String,
        val subscribersCount: Int,
        val avatarUrl: String,
        val coverImage: String
    )

    fun parseChannelProfile(body: String, fallbackName: String, fallbackDescription: String, fallbackAvatarUrl: String, fallbackCoverUrl: String): ParsedChannelProfile {
        try {
            val jsonObject = JSONObject(body)
            val name = jsonObject.optString("name", fallbackName) ?: ""
            val description = jsonObject.optString("description", fallbackDescription) ?: ""
            val subCount = jsonObject.optInt("subscribers_count", 0)
            val avatarUrl = jsonObject.optString("avatar_url", fallbackAvatarUrl) ?: ""
            val appearance = jsonObject.optJSONObject("appearance")
            val coverImage = appearance?.optString("cover_image", fallbackCoverUrl) ?: fallbackCoverUrl
            return ParsedChannelProfile(name, description, subCount, avatarUrl, coverImage)
        } catch (e: Exception) {
            android.util.Log.e("RutubeParser", "Error parsing channel profile", e)
            return ParsedChannelProfile(fallbackName, fallbackDescription, 0, fallbackAvatarUrl, fallbackCoverUrl)
        }
    }

    data class ParsedTvSeriesMeta(
        val description: String,
        val year: String,
        val bannerUrl: String,
        val posterUrl: String
    )

    fun parseTvSeriesMeta(body: String, fallbackDescription: String, fallbackThumbnailUrl: String?): ParsedTvSeriesMeta {
        try {
            val infoObj = JSONObject(body)
            val description = infoObj.optString("description", fallbackDescription) ?: ""
            val year = infoObj.optString("year") ?: ""
            val picture = infoObj.optString("picture") ?: ""
            val appearance = infoObj.optJSONObject("appearance")
            val coverImage = appearance?.optString("cover_image")?.takeIf { it.isNotBlank() && it != "null" }
            val verticalPoster = infoObj.optString("vertical_poster_url").takeIf { it.isNotBlank() && it != "null" }
            
            val bannerUrl = coverImage ?: picture.takeIf { it.isNotBlank() && it != "null" } ?: fallbackThumbnailUrl ?: ""
            val posterUrl = verticalPoster ?: fallbackThumbnailUrl ?: ""
            return ParsedTvSeriesMeta(description, year, bannerUrl, posterUrl)
        } catch (e: Exception) {
            android.util.Log.e("RutubeParser", "Error parsing tv series meta", e)
            return ParsedTvSeriesMeta(fallbackDescription, "", fallbackThumbnailUrl ?: "", fallbackThumbnailUrl ?: "")
        }
    }

    data class ParsedPlaylistMeta(
        val title: String,
        val description: String,
        val thumbnailUrl: String,
        val videoCount: Int
    )

    fun parsePlaylistMeta(body: String, fallbackTitle: String, fallbackDescription: String, fallbackThumbnailUrl: String?): ParsedPlaylistMeta {
        try {
            val infoObj = JSONObject(body)
            val description = infoObj.optString("description", fallbackDescription) ?: ""
            val appearance = infoObj.optJSONObject("appearance")
            val coverImage = appearance?.optString("cover_image")?.takeIf { it.isNotBlank() && it != "null" }
            val picture = infoObj.optString("picture").takeIf { it.isNotBlank() && it != "null" }
            val thumbnailUrl = infoObj.optString("thumbnail_url").takeIf { it.isNotBlank() && it != "null" }
            val nameFallback = infoObj.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: fallbackTitle
            val name = infoObj.optString("name", nameFallback).takeIf { it.isNotBlank() && it != "null" } ?: nameFallback
            var videoCount = infoObj.optInt("video_count", -1)
            if (videoCount <= 0) videoCount = infoObj.optInt("videos_count", -1)
            
            val resolvedThumbnail = coverImage ?: picture ?: thumbnailUrl ?: fallbackThumbnailUrl ?: ""
            return ParsedPlaylistMeta(name, description, resolvedThumbnail, videoCount)
        } catch (e: Exception) {
            android.util.Log.e("RutubeParser", "Error parsing playlist meta", e)
            return ParsedPlaylistMeta(fallbackTitle, fallbackDescription, fallbackThumbnailUrl ?: "", -1)
        }
    }

    fun parseSingleVideo(body: String, videoId: String): Video? {
        try {
            val jsonObj = JSONObject(body)
            
            val authorObj = jsonObj.optJSONObject("author")
            val authorName = authorObj?.optString("name") ?: "Rutube"
            val authorIdRaw = authorObj?.optString("id") ?: ""
            
            val isLive = jsonObj.optBoolean("is_livestream", false)
            val durationSec = jsonObj.optInt("duration", 0)
            val durStr = if (isLive) {
                "трансляция"
            } else if (durationSec >= 3600) {
                String.format("%d:%02d:%02d", durationSec / 3600, (durationSec % 3600) / 60, durationSec % 60)
            } else if (durationSec > 0) {
                String.format("%02d:%02d", durationSec / 60, durationSec % 60)
            } else {
                "00:00"
            }
            
            val title = jsonObj.optString("title", "Видео")
            val desc = jsonObj.optString("description", "Импортировано из ссылки Rutube.")
            val thumb = jsonObj.optString("thumbnail_url", "")
            
            return Video(
                id = videoId,
                title = title,
                channel = authorName,
                views = "",
                timeAgo = "",
                duration = durStr,
                category = jsonObj.optJSONObject("category")?.optString("name") ?: "Разное",
                description = desc,
                thumbnailUrl = thumb,
                authorId = authorIdRaw,
                authorAvatarUrl = authorObj?.optString("avatar_url", "") ?: "",
                authorActionUrl = authorObj?.optString("site_url", "") ?: ""
            )
        } catch (e: Exception) {
            android.util.Log.e("RutubeParser", "Error parsing single video", e)
            return null
        }
    }

    fun parseMissingPosterUrl(body: String): String? {
        try {
            val json = JSONObject(body)
            val verticalPosterUrl = json.optString("vertical_poster_url").takeIf { it.isNotBlank() }
            val posterUrl = json.optString("poster_url").takeIf { it.isNotBlank() }
            val appearance = json.optJSONObject("appearance")
            val coverImage = appearance?.optString("cover_image")?.takeIf { it.isNotBlank() }
            val picture = json.optString("picture").takeIf { it.isNotBlank() }
            
            return verticalPosterUrl ?: posterUrl ?: coverImage ?: picture
        } catch (e: Exception) {
            android.util.Log.e("RutubeParser", "Error parsing missing poster url", e)
            return null
        }
    }

    fun parseResponse(bodyString: String, url: String? = null): ParsedResponse {
        val jsonObj = JSONObject(bodyString)
        return ResponseAnalyzer.parse(jsonObj, url)
    }

    fun extractStreamUrlFromPlayOptions(body: String): String? {
        try {
            val json = JSONObject(body)
            return extractStreamUrlFromPlayOptions(json)
        } catch (e: Exception) {
            android.util.Log.e("RutubeParser", "Error parsing play options JSON", e)
            return null
        }
    }

    fun extractStreamUrlFromPlayOptions(json: JSONObject): String? {
        // 1. Проверяем live_streams (для прямых эфиров)
        json.optJSONObject("live_streams")?.let { liveStreams ->
            // Пробуем hls массив
            liveStreams.optJSONArray("hls")?.let { hlsArray ->
                if (hlsArray.length() > 0) {
                    val firstHls = hlsArray.getJSONObject(0)
                    firstHls.optString("url").takeIf { it.isNotBlank() }?.let { return it }
                }
            }
            // Пробуем прямые поля
            listOf("hls", "m3u8", "url", "default").forEach { key ->
                liveStreams.optString(key).takeIf { it.isNotBlank() }?.let { return it }
            }
        }

        // 2. Проверяем live_balancer (альтернативный формат)
        json.optJSONObject("live_balancer")?.let { liveBalancer ->
            listOf("hls", "m3u8", "url", "default").forEach { key ->
                liveBalancer.optString(key).takeIf { it.isNotBlank() }?.let { return it }
            }
        }

        // 3. Проверяем video_balancer (для обычных видео)
        json.optJSONObject("video_balancer")?.let { vb ->
            listOf("m3u8", "hls", "default", "url").forEach { key ->
                vb.optString(key).takeIf { it.isNotBlank() }?.let { return it }
            }
        }

        // 4. Проверяем корневые поля
        listOf("hls_url", "stream_url", "m3u8", "url", "video_url").forEach { key ->
            json.optString(key).takeIf { it.isNotBlank() }?.let { return it }
        }

        // 5. Рекурсивный поиск по всем полям JSON (запасной вариант)
        fun searchInJson(obj: JSONObject, depth: Int = 0): String? {
            if (depth > 3) return null
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.opt(key)
                when (value) {
                    is String -> {
                        if (value.contains(".m3u8") && value.startsWith("http")) {
                            return value
                        }
                    }
                    is JSONObject -> {
                        searchInJson(value, depth + 1)?.let { return it }
                    }
                }
            }
            return null
        }
        return searchInJson(json)
    }

    fun parseSubtitles(body: String): List<SubtitleTrack> {
        try {
            val jsonObject = JSONObject(body)
            val list = jsonObject.optJSONArray("list") ?: return emptyList()
            val result = mutableListOf<SubtitleTrack>()
            for (i in 0 until list.length()) {
                val subObj = list.optJSONObject(i) ?: continue
                val lang = subObj.optString("langTitle", "Unknown")
                val format = subObj.optString("format", "srt")
                val url = subObj.optString("file", "")
                if (url.isNotBlank()) {
                    result.add(SubtitleTrack(lang, format, url))
                }
            }
            return result
        } catch (e: Exception) {
            android.util.Log.e("RutubeParser", "Error parsing subtitles JSON", e)
            return emptyList()
        }
    }
}
