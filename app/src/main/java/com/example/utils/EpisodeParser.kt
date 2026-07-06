package com.example.utils

import com.example.data.Video

data class EpisodeInfo(
    val baseTitle: String,
    val season: Int,
    val episode: Int,
    val rawNum: Int,
    val hasEpisodeInfo: Boolean = false
)

object EpisodeParser {

    fun parseEpisode(title: String): EpisodeInfo {
        val lower = title.lowercase()
        
        var season = 1
        var episode = 1
        var rawNum = -1
        var matched = false
        
        // 1. Unified S01E08, S1 Ep 8, 1x08 patterns
        val sExeRegex = Regex("""s\s*(\d+)\s*e\s*(\d+)""", RegexOption.IGNORE_CASE)
        val sEpRegex = Regex("""s\s*(\d+)\s*ep\s*(\d+)""", RegexOption.IGNORE_CASE)
        val xRegex = Regex("""(\d+)\s*x\s*(\d+)""", RegexOption.IGNORE_CASE)
        
        val sexMatch = sExeRegex.find(lower)
        val sEpMatch = sEpRegex.find(lower)
        val xMatch = xRegex.find(lower)
        
        if (sexMatch != null) {
            season = sexMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            episode = sexMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
            rawNum = episode
            matched = true
        } else if (sEpMatch != null) {
            season = sEpMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            episode = sEpMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
            rawNum = episode
            matched = true
        } else if (xMatch != null) {
            season = xMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            episode = xMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
            rawNum = episode
            matched = true
        }
        
        if (!matched) {
            // 2. Russian Combined patterns:
            val ruComb1 = Regex("""(\d+)\s*(?:-|–|—)?\s*(?:й|ый|ой|го|ий|ая|е|ое)?\s*сезон\w*\s*[,.\s-]*\s*(\d+)\s*(?:-|–|—)?\s*(?:й|ый|ой|го|ий|ая|я|е)?\s*(?:серия|эпизод|выпуск|часть)""")
            val ruComb2 = Regex("""сезон\w*\s*(?:-|–|—)?\s*(\d+)\s*[,.\s-]*\s*(?:серия|эпизод|выпуск|часть)\w*\s*(?:-|–|—)?\s*(\d+)""")
            val ruComb3 = Regex("""(\d+)\s*(?:-|–|—)?\s*(?:й|ый|ой|го|ий|ая|е|ое)?\s*сезон\w*\s*[,.\s-]*\s*(?:серия|эпизод|выпуск|часть)\w*\s*(?:-|–|—)?\s*(\d+)""")
            val ruComb4 = Regex("""сезон\w*\s*(?:-|–|—)?\s*(\d+)\s*[,.\s-]*\s*(\d+)\s*(?:-|–|—)?\s*(?:й|ый|ой|го|ий|ая|я|е)?\s*(?:серия|эпизод|выпуск|часть)""")

            val c1 = ruComb1.find(lower)
            val c2 = ruComb2.find(lower)
            val c3 = ruComb3.find(lower)
            val c4 = ruComb4.find(lower)
            
            if (c1 != null) {
                season = c1.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
                episode = c1.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
                rawNum = episode
                matched = true
            } else if (c2 != null) {
                season = c2.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
                episode = c2.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
                rawNum = episode
                matched = true
            } else if (c3 != null) {
                season = c3.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
                episode = c3.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
                rawNum = episode
                matched = true
            } else if (c4 != null) {
                season = c4.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
                episode = c4.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
                rawNum = episode
                matched = true
            }
        }
        
        if (!matched) {
            val seasonSuffixRegex = Regex("""(\d+)\s*(?:-|–|—)?\s*(?:й|ый|ой|го|ий|ая|е|ое)?\s*сезон""")
            val seasonPrefixRegex = Regex("""сезон\w*\s*(?:-|–|—)?\s*(\d+)\b""")
            
            val sSfxMatch = seasonSuffixRegex.find(lower)
            val sPfxMatch = seasonPrefixRegex.find(lower)
            
            var seasonFound = false
            if (sSfxMatch != null) {
                season = sSfxMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
                seasonFound = true
            } else if (sPfxMatch != null) {
                season = sPfxMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
                seasonFound = true
            }
            
            val epSuffixRegex = Regex("""(\d+)\s*(?:-|–|—)?\s*(?:й|ый|ой|го|ий|ая|я|е)?\s*(?:серия|эпизод|выпуск|часть)""")
            val epPrefixRegex = Regex("""(?:серия|эпизод|выпуск|часть)\w*\s*(?:-|–|—)?\s*(\d+)\b""")
            
            val epSfxMatch = epSuffixRegex.find(lower)
            val epPfxMatch = epPrefixRegex.find(lower)
            
            var episodeFound = false
            if (epSfxMatch != null) {
                episode = epSfxMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
                rawNum = episode
                episodeFound = true
            } else if (epPfxMatch != null) {
                episode = epPfxMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
                rawNum = episode
                episodeFound = true
            }
            
            if (seasonFound || episodeFound) {
                matched = true
            }
            
            if (rawNum == -1) {
                val digitRegex = Regex("""\d+""")
                val matches = digitRegex.findAll(lower).toList()
                if (matches.isNotEmpty()) {
                    val lastNum = matches.last().groupValues.getOrNull(0)?.toIntOrNull() ?: 1
                    if (matches.size == 1 && (sSfxMatch != null || sPfxMatch != null)) {
                        episode = 1
                    } else {
                        episode = lastNum
                        rawNum = lastNum
                    }
                }
            }
        }
        
        var baseTitle = title
            .replace(Regex("""(?i)\bs\d+e\d+\b"""), "")
            .replace(Regex("""(?i)\bs\d+ep\d+\b"""), "")
            .replace(Regex("""(?i)\b\d+x\d+\b"""), "")
            .replace(Regex("""(?i)\b\d+\s*(?:-|–|—)?\s*(?:[а-яА-ЯёЁ]{1,3})?\s*сезон\w*\b"""), "")
            .replace(Regex("""(?i)\bсезон\w*\s*(?:-|–|—)?\s*\d+\b"""), "")
            .replace(Regex("""(?i)\b\d+\s*(?:-|–|—)?\s*(?:[а-яА-ЯёЁ]{1,3})?\s*сери\w*\b"""), "")
            .replace(Regex("""(?i)\bсери\w*\s*(?:-|–|—)?\s*\d+\b"""), "")
            .replace(Regex("""(?i)\b\d+\s*(?:-|–|—)?\s*(?:[а-яА-ЯёЁ]{1,3})?\s*эпизод\w*\b"""), "")
            .replace(Regex("""(?i)\bэпизод\w*\s*(?:-|–|—)?\s*\d+\b"""), "")
            .replace(Regex("""(?i)\b\d+\s*(?:-|–|—)?\s*(?:[а-яА-ЯёЁ]{1,3})?\s*выпуск\w*\b"""), "")
            .replace(Regex("""(?i)\bвыпуск\w*\s*(?:-|–|—)?\s*\d+\b"""), "")
            .replace(Regex("""(?i)\b\d+\s*(?:-|–|—)?\s*(?:[а-яА-ЯёЁ]{1,3})?\s*част\w*\b"""), "")
            .replace(Regex("""(?i)\bчаст\w*\s*(?:-|–|—)?\s*\d+\b"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            
        if (baseTitle.length < 3) {
            baseTitle = title.take(15)
        }
        
        return EpisodeInfo(baseTitle, season, episode, if (rawNum != -1) rawNum else 1, matched)
    }

    fun getSortedEpisodes(currentVideo: Video, allVideos: List<Video>): List<Video> {
        val currentInfo = parseEpisode(currentVideo.title)
        
        val matching = allVideos.filter { item ->
            val itemInfo = parseEpisode(item.title)
            val shareBaseTitle = itemInfo.baseTitle.lowercase().split(" ").filter { it.length > 3 }
                .any { word -> currentInfo.baseTitle.lowercase().contains(word) }
            
            shareBaseTitle || item.channel == currentVideo.channel
        }
        
        val sorted = matching.distinctBy { it.id }.sortedWith(compareBy<Video> { 
            val info = parseEpisode(it.title)
            info.season
        }.thenBy { 
            val info = parseEpisode(it.title)
            info.episode
        })
        
        if (sorted.size > 1) {
            return sorted
        }
        
        val categoryVideos = allVideos.filter { it.category == currentVideo.category }
        if (categoryVideos.size > 1) {
            return categoryVideos
        }
        
        return allVideos.take(10)
    }
}
