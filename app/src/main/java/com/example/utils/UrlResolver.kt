package com.example.utils

import com.example.data.Video

object UrlResolver {
    
    enum class EntityType {
        VIDEO,
        CHANNEL,
        PLAYLIST,
        SERIES,
        FEED,
        UNKNOWN
    }
    
    data class ResolvedEntity(
        val type: EntityType,
        val id: String,
        val rawUrl: String? = null
    )

    fun resolveUrl(urlOrId: String): ResolvedEntity {
        val trimmed = urlOrId.trim()
        if (trimmed.isBlank()) return ResolvedEntity(EntityType.UNKNOWN, "")
        
        val cleanUrl = trimmed.substringBefore("?")
        
        // 1. CHANNEL
        val channelPattern = "rutube\\.ru/channel/([^/]+)".toRegex()
        val chMatch = channelPattern.find(cleanUrl)
        if (chMatch != null) {
            return ResolvedEntity(EntityType.CHANNEL, chMatch.groupValues[1], trimmed)
        }
        
        // 2. PLAYLIST
        val plstPattern = "rutube\\.ru/(?:plst|playlist|api/video/playlist|api/playlist/custom)/([^/]+)".toRegex()
        val plMatch = plstPattern.find(cleanUrl)
        if (plMatch != null) {
            return ResolvedEntity(EntityType.PLAYLIST, plMatch.groupValues[1], trimmed)
        }
        
        // 3. SERIES (TV)
        val tvPattern = "rutube\\.ru/(?:tv|metainfo/tv|series)/([^/]+)".toRegex()
        val tvMatch = tvPattern.find(cleanUrl)
        if (tvMatch != null) {
            val tvId = tvMatch.groupValues[1]
            if (tvId != "video") { // avoid matching 'video' as id if path is tv/video
                return ResolvedEntity(EntityType.SERIES, tvId, trimmed)
            }
        }
        
        // 4. FEED
        val feedPattern = "rutube\\.ru/(?:api/)?feeds/([^/]+)".toRegex()
        val feedMatch = feedPattern.find(cleanUrl)
        if (feedMatch != null) {
            return ResolvedEntity(EntityType.FEED, feedMatch.groupValues[1], trimmed)
        }

        // 5. VIDEO
        // Matches https://rutube.ru/video/XXXX/ or private/XXXX/
        val videoPattern = "rutube\\.ru/video/(?:private/)?([a-fA-F0-9]+)".toRegex()
        val match1 = videoPattern.find(cleanUrl)
        if (match1 != null) {
            return ResolvedEntity(EntityType.VIDEO, match1.groupValues[1], trimmed)
        }
        
        // Fallback for ID only:
        val parts = cleanUrl.trimEnd('/').split("/")
        val last = parts.last()
        if (last.length >= 20 && last.matches("[a-fA-F0-9]+".toRegex())) {
            return ResolvedEntity(EntityType.VIDEO, last, trimmed)
        }
        
        // Fallback for numeric ID? E.g. channel ID or playlist ID. We don't know type from just a number,
        // so we can't reliably guess without type prefix. So if it's not a 32-char hex, return UNKNOWN.
        
        return ResolvedEntity(EntityType.UNKNOWN, "", trimmed)
    }
}
