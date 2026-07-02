package com.example.data.rutube

data class HlsStream(
    val resolution: String, // e.g., "2160p","1440p","1080p", "720p", "480p", "360p"
    val url: String,
    val bandwidth: Long
)

object HlsParser {

    fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl
        }
        if (relativeUrl.startsWith("/")) {
            val schemeAndDomain = baseUrl.substringBefore("://") + "://" + baseUrl.substringAfter("://").substringBefore("/")
            return schemeAndDomain + relativeUrl
        }
        val lastSlash = baseUrl.lastIndexOf('/')
        if (lastSlash != -1) {
            val basePath = baseUrl.substring(0, lastSlash + 1)
            return basePath + relativeUrl
        }
        return relativeUrl
    }

    fun parseMasterPlaylist(masterUrl: String, masterText: String): List<HlsStream> {
        val streams = mutableListOf<HlsStream>()
        val lines = masterText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        
        for (i in lines.indices) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                // Extract resolution, e.g., RESOLUTION=1280x720
                var resLabel = ""
                val resMatch = Regex("RESOLUTION=(\\d+)x(\\d+)").find(line)
                if (resMatch != null) {
                    val height = resMatch.groupValues[2]
                    resLabel = "${height}p"
                } else {
                    // Fallback to searching standard resolution numbers
                    val possibleHeights = listOf("2160", "1440", "1080", "720", "480", "360", "240")
                    for (h in possibleHeights) {
                        if (line.contains(h)) {
                            resLabel = "${h}p"
                            break
                        }
                    }
                }
                
                // Extract bandwidth
                var bw = 0L
                val bwMatch = Regex("BANDWIDTH=(\\d+)").find(line)
                if (bwMatch != null) {
                    bw = bwMatch.groupValues[1].toLongOrNull() ?: 0L
                }
                
                // Find next non-comment line for the stream URL
                var nextIndex = i + 1
                while (nextIndex < lines.size && lines[nextIndex].startsWith("#")) {
                    nextIndex++
                }
                if (nextIndex < lines.size) {
                    val rawUrl = lines[nextIndex]
                    val absoluteUrl = resolveUrl(masterUrl, rawUrl)
                    
                    // If resLabel is still empty, let's assign a fallback from the URL or bandwidth
                    if (resLabel.isBlank()) {
                        resLabel = when {
                            absoluteUrl.contains("2160") || absoluteUrl.lowercase().contains("4k") -> "2160p"
                            absoluteUrl.contains("1440") -> "1440p"
                            absoluteUrl.contains("1080") -> "1080p"
                            absoluteUrl.contains("720") -> "720p"
                            absoluteUrl.contains("480") -> "480p"
                            absoluteUrl.contains("360") -> "360p"
                            bw > 10000000 -> "2160p"
                            bw > 6000000 -> "1440p"
                            bw > 3000000 -> "1080p"
                            bw > 1500000 -> "720p"
                            bw > 800000 -> "480p"
                            else -> "360p"
                        }
                    }
                    
                    streams.add(HlsStream(resolution = resLabel, url = absoluteUrl, bandwidth = bw))
                }
            }
        }
        
        // Sort streams by height descending
        return streams.sortedByDescending { stream ->
            stream.resolution.replace("p", "").toIntOrNull() ?: 0
        }
    }
}
