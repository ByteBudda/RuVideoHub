package com.example.plugins

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Плагин для интеграции VK Video
 * Поддерживает поиск видео и получение потоков для воспроизведения
 */
class VKVideoPlugin : VideoPlugin {
    
    companion object {
        private const val CLIENT_ID = "52461373"
        private const val API_VERSION = "5.282"
        private const val TIMEOUT_MS = 10000
        private const val CACHE_MS = 10 * 60 * 1000L
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/150.0.0.0 Safari/537.36"
        
        // Кэш для анонимного токена
        private var anonymousToken: String? = null
        private var tokenExpiresAt: Long = 0L
    }
    
    override val name: String = "VK Video"
    override val icon: String = "vk_icon"
    override val baseUrl: String = "https://vkvideo.ru/"
    
    override suspend fun search(query: String, limit: Int): List<VideoItem> {
        return searchVideos(query, limit)
    }
    
    override suspend fun resolveStream(url: String, audioOnly: Boolean): StreamInfo? {
        return null
    }
    
    override suspend fun getVideoInfo(url: String): VideoItem? {
        return fetchVideoInfo(url)
    }
    
    override fun isSupported(url: String): Boolean {
        return url.contains("vkvideo.ru/") || 
               url.contains("vk.com/video") ||
               url.contains("api.vkvideo.ru")
    }
    
    /**
     * Поиск видео через API VK Video
     */
    private suspend fun searchVideos(query: String, limit: Int): List<VideoItem> {
        val result = mutableListOf<VideoItem>()
        
        try {
            val address = "https://api.vkvideo.ru/method/catalog.getVideoSearchWeb2" +
                    "?v=$API_VERSION" +
                    "&client_id=$CLIENT_ID" +
                    "&count=30" +
                    "&q=${Uri.encode(query)}" +
                    "&access_token=${Uri.encode(getAnonymousToken())}"
            
            val root = getJson(address)
            val error = root.optJSONObject("error")
            if (error != null) {
                throw Exception("VK Video: ${error.optString("error_msg", "ошибка поиска")}")
            }
            
            val response = root.optJSONObject("response")
                ?: throw Exception("VK Video не отдал результаты")
            
            // Собираем видео из catalog_videos
            val videos = LinkedHashMap<String, JSONObject>()
            val catalogVideos = response.optJSONArray("catalog_videos")
            if (catalogVideos != null) {
                for (i in 0 until catalogVideos.length()) {
                    val wrapper = catalogVideos.optJSONObject(i)
                    val video = wrapper?.optJSONObject("video")
                    if (video != null) {
                        videos[key(video)] = video
                    }
                }
            }
            
            // Сохраняем порядок из каталога
            val orderedIds = LinkedHashSet<String>()
            val catalog = response.optJSONObject("catalog")
            collectOrderedIds(catalog?.optJSONArray("sections"), orderedIds)
            orderedIds.addAll(videos.keys)
            
            // Формируем результат
            for (id in orderedIds) {
                val video = videos[id] ?: continue
                val ownerId = video.optLong("owner_id")
                val videoId = video.optLong("id")
                val pageUrl = "https://vkvideo.ru/video${ownerId}_${videoId}"
                
                val duration = video.optLong("duration") * 1000L
                val thumbnail = bestImage(video.optJSONArray("image"), 320)
                val title = video.optString("title", "Видео VK")
                val author = video.optString("author_name", "")
                
                result.add(VideoItem(
                    id = "${ownerId}_${videoId}",
                    title = title,
                    author = author,
                    thumbnail = thumbnail,
                    url = pageUrl,
                    duration = duration,
                    views = formatViews(video.optLong("views")),
                    source = name
                ))
                
                if (result.size >= limit) break
            }
        } catch (e: Exception) {
            throw Exception("Ошибка поиска VK Video: ${e.message}")
        }
        
        return result
    }
    
    /**
     * Получение информации о видео по URL
     */
    private suspend fun fetchVideoInfo(url: String): VideoItem? {
        try {
            val info = com.example.data.vk.VkVideoLoader.getVideoInfo(url) ?: return null
            val parts = info.duration.split(":")
            var sec = 0L
            try {
                if (parts.size == 2) {
                    sec = parts[0].toLong() * 60 + parts[1].toLong()
                } else if (parts.size == 3) {
                    sec = parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
                }
            } catch (_: Exception) {}
            val durationMs = sec * 1000L

            return VideoItem(
                id = "${info.ownerId}_${info.videoId}",
                title = info.title,
                author = "VK Video",
                thumbnail = info.thumbnail,
                url = "https://vkvideo.ru/video${info.ownerId}_${info.videoId}",
                duration = durationMs,
                views = info.views,
                source = name
            )
        } catch (e: Exception) {
            android.util.Log.e("VKVideoPlugin", "Error in fetchVideoInfo", e)
            return null
        }
    }
    
    // ============ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ============
    
    private suspend fun getAnonymousToken(): String {
        synchronized(this) {
            val now = System.currentTimeMillis() / 1000L
            if (anonymousToken != null && now + 60 < tokenExpiresAt) {
                return anonymousToken!!
            }
            
            val root = postJson("https://login.vk.com/?act=get_anonym_token", "client_id=$CLIENT_ID")
            if (!"okay".equals(root.optString("type"))) {
                throw Exception("VK Video не выдал анонимную сессию")
            }
            
            val data = root.optJSONObject("data")
            anonymousToken = data?.optString("access_token")
            tokenExpiresAt = data?.optLong("expired_at") ?: 0
            
            if (anonymousToken.isNullOrEmpty()) {
                throw Exception("VK Video не выдал анонимную сессию")
            }
            
            return anonymousToken!!
        }
    }
    
    private fun findVkId(url: String): String {
        var from = 0
        while (from < url.length) {
            val marker = url.indexOf("video", from)
            if (marker < 0) break
            
            var start = marker + 5
            var cursor = start
            if (cursor < url.length && url[cursor] == '-') cursor++
            
            val ownerStart = cursor
            while (cursor < url.length && url[cursor].isDigit()) cursor++
            
            if (cursor > ownerStart && cursor < url.length && url[cursor] == '_') {
                cursor++
                val videoStart = cursor
                while (cursor < url.length && url[cursor].isDigit()) cursor++
                if (cursor > videoStart) {
                    return url.substring(start, cursor)
                }
            }
            from = marker + 5
        }
        throw Exception("Не найден ID VK Video")
    }
    
    private fun parseVkId(id: String): Pair<Long, Long> {
        val parts = id.split('_')
        if (parts.size != 2) throw Exception("Неверный ID VK Video")
        return Pair(parts[0].toLong(), parts[1].toLong())
    }
    
    private fun key(video: JSONObject): String {
        return "${video.optLong("owner_id")}_${video.optLong("id")}"
    }
    
    private fun collectOrderedIds(sections: JSONArray?, result: MutableSet<String>) {
        if (sections == null) return
        for (i in 0 until sections.length()) {
            val section = sections.optJSONObject(i)
            val blocks = section?.optJSONArray("blocks") ?: continue
            for (j in 0 until blocks.length()) {
                val block = blocks.optJSONObject(j)
                val ids = block?.optJSONArray("videos_ids") ?: continue
                for (k in 0 until ids.length()) {
                    result.add(ids.optString(k))
                }
            }
        }
    }
    
    private fun bestImage(images: JSONArray?, targetWidth: Int): String {
        if (images == null) return ""
        var smallestSuitable = ""
        var smallestSuitableWidth = Int.MAX_VALUE
        var largestFallback = ""
        var largestFallbackWidth = 0
        
        for (i in 0 until images.length()) {
            val image = images.optJSONObject(i) ?: continue
            val width = image.optInt("width")
            val url = image.optString("url")
            if (url.isEmpty() || width <= 0) continue
            
            if (width > largestFallbackWidth) {
                largestFallbackWidth = width
                largestFallback = url
            }
            if (width >= targetWidth && width < smallestSuitableWidth) {
                smallestSuitableWidth = width
                smallestSuitable = url
            }
        }
        
        return if (smallestSuitable.isNotEmpty()) smallestSuitable else largestFallback
    }
    

    private fun getJson(address: String): JSONObject {
        val connection = configure(URL(address).openConnection() as HttpURLConnection)
        try {
            val code = connection.responseCode
            if (code < 200 || code >= 300) {
                throw Exception("VK Video: HTTP $code")
            }
            return JSONObject(read(connection))
        } finally {
            connection.disconnect()
        }
    }
    
    private fun postJson(address: String, body: String): JSONObject {
        val connection = configure(URL(address).openConnection() as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        connection.setRequestProperty("Origin", "https://vkvideo.ru")
        connection.setRequestProperty("Referer", "https://vkvideo.ru/")
        
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        connection.setFixedLengthStreamingMode(bytes.size)
        
        try {
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            if (code < 200 || code >= 300) {
                throw Exception("VK Video: HTTP $code")
            }
            return JSONObject(read(connection))
        } finally {
            connection.disconnect()
        }
    }
    
    private fun configure(connection: HttpURLConnection): HttpURLConnection {
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/json,*/*")
        connection.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9")
        connection.setRequestProperty("User-Agent", USER_AGENT)
        return connection
    }
    
    private fun read(connection: HttpURLConnection): String {
        connection.inputStream.use { input ->
            ByteArrayOutputStream(128 * 1024).use { output ->
                val buffer = ByteArray(8192)
                var count: Int
                while (input.read(buffer).also { count = it } != -1) {
                    output.write(buffer, 0, count)
                }
                return output.toString(StandardCharsets.UTF_8.name())
            }
        }
    }
    

    private fun formatViews(views: Long): String {
        return when {
            views >= 1_000_000 -> String.format(Locale.US, "%.1fM", views / 1_000_000.0)
            views >= 1_000 -> String.format(Locale.US, "%.1fK", views / 1000.0)
            views > 0 -> views.toString()
            else -> ""
        }
    }
}
