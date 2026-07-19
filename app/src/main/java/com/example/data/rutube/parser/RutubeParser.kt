package com.example.data.rutube.parser

import com.example.data.Video
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
}
