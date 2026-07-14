package com.example.plugins

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Плагин для интеграции Дзен (dzen.ru)
 * Поддерживает поиск видео и получение потоков для воспроизведения
 */
class DzenPlugin : VideoPlugin {
    
    companion object {
        private const val TIMEOUT_MS = 9000
        private const val CACHE_MS = 10 * 60 * 1000L
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/150.0.0.0 Safari/537.36"
        
        // Кэш для cookies
        private val cookies = ConcurrentHashMap<String, String>()
        
        // Кэш для потоков
        private val streamCache = ConcurrentHashMap<String, StreamInfo>()
    }
    
    override val name: String = "Дзен"
    override val icon: String = "dzen_icon"
    override val baseUrl: String = "https://dzen.ru/"
    
    override suspend fun search(query: String, limit: Int): List<VideoItem> {
        return searchDzen(query, limit)
    }
    
    override suspend fun resolveStream(url: String, audioOnly: Boolean): StreamInfo? {
        return resolveDzenStream(url, audioOnly)
    }
    
    override suspend fun getVideoInfo(url: String): VideoItem? {
        return fetchDzenVideoInfo(url)
    }
    
    override fun isSupported(url: String): Boolean {
        return url.contains("dzen.ru/video/") ||
               url.contains("zen.yandex.ru/video/")
    }
    
    /**
     * Поиск видео через HTML парсинг страницы Дзен
     */
    private suspend fun searchDzen(query: String, limit: Int): List<VideoItem> {
        val result = mutableListOf<VideoItem>()
        
        try {
            val address = "https://dzen.ru/search?query=${Uri.encode(query)}&type_filter=video"
            val html = getPage(address)
            result.addAll(parseCards(html, limit))
            
        } catch (e: Exception) {
            throw Exception("Ошибка поиска Дзен: ${e.message}")
        }
        
        return result
    }
    
    /**
     * Получение информации о видео по URL
     */
    private suspend fun fetchDzenVideoInfo(url: String): VideoItem? {
        try {
            val id = findDzenId(url)
            val pageUrl = "https://dzen.ru/video/watch/$id"
            val html = getPage(pageUrl)
            
            // Извлекаем метаданные из HTML
            val title = extractTitle(html) ?: "Видео Дзен"
            val thumbnail = extractThumbnail(html) ?: ""
            val duration = extractDuration(html)
            val author = extractAuthor(html) ?: ""
            val views = extractViews(html)
            
            return VideoItem(
                id = id,
                title = title,
                author = author,
                thumbnail = thumbnail,
                url = pageUrl,
                duration = duration,
                views = views,
                source = name
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Получение потока для воспроизведения
     */
    private suspend fun resolveDzenStream(url: String, audioOnly: Boolean): StreamInfo? {
        try {
            val id = findDzenId(url)
            val cacheKey = "dzen:$id${if (audioOnly) ":audio" else ""}"
            
            // Проверяем кэш
            val cached = streamCache[cacheKey]
            if (cached != null && System.currentTimeMillis() - cached.loadedAt < CACHE_MS) {
                return cached
            }
            
            // Загружаем страницу
            val pageUrl = "https://dzen.ru/video/watch/$id"
            val page = getPage(pageUrl)
            
            // Извлекаем данные из JSON в HTML
            val metadataIndex = page.indexOf("\"videoMetaResponse\"")
            if (metadataIndex < 0) {
                throw Exception("Дзен не отдал данные ролика")
            }
            
            val paramsIndex = page.lastIndexOf("var _params", metadataIndex)
            val objectStart = if (paramsIndex < 0) -1 else page.indexOf('{', paramsIndex)
            if (objectStart < 0) {
                throw Exception("Дзен не отдал данные ролика")
            }
            
            val jsonStr = jsonObjectAt(page, objectStart)
            val root = JSONObject(jsonStr)
            val ssr = root.optJSONObject("ssrData")
            val meta = ssr?.optJSONObject("videoMetaResponse")
            val video = meta?.optJSONObject("video")
            
            if (video == null) {
                throw Exception("Дзен не отдал публичный поток")
            }
            
            // Извлекаем потоки
            var hls: String? = null
            var dash: String? = null
            var fallback: String? = null
            var audioFallback: String? = null
            
            val direct = httpUrl(video.optString("id"))
            if (isDash(direct)) dash = direct
            else if (isHls(direct)) hls = direct
            else if (direct != null) {
                fallback = direct
                audioFallback = direct
            }
            
            // Потоки из массива streams
            val streams = video.optJSONArray("streams")
            if (streams != null) {
                for (i in 0 until streams.length()) {
                    val candidate = httpUrl(streams.optString(i)) ?: continue
                    if (dash == null && isDash(candidate)) dash = candidate
                    if (hls == null && isHls(candidate)) hls = candidate
                    if (fallback == null && candidate.contains("ct=0")) fallback = candidate
                    if (candidate.contains("ct=0") && (audioFallback == null || candidate.contains("type=4"))) {
                        audioFallback = candidate
                    }
                }
            }
            
            // Потоки из oneVideoStreams
            val oneVideo = video.optJSONArray("oneVideoStreams")
            if (oneVideo != null) {
                for (i in 0 until oneVideo.length()) {
                    val item = oneVideo.optJSONObject(i)
                    val candidate = httpUrl(item?.optString("url")) ?: continue
                    
                    if (dash == null && ("dash".equals(item?.optString("type")) || isDash(candidate))) {
                        dash = candidate
                    }
                    if (hls == null && ("hls".equals(item?.optString("type")) || isHls(candidate))) {
                        hls = candidate
                    }
                    if (fallback == null && "fullhd".equals(item?.optString("type"))) {
                        fallback = candidate
                    }
                    if (candidate.contains("ct=0") && (audioFallback == null || candidate.contains("type=4"))) {
                        audioFallback = candidate
                    }
                }
            }
            
            // Выбираем поток
            val stream: String?
            val mimeType: String?
            
            if (audioOnly && dash != null && hasDashAudio(dash)) {
                stream = dash
                mimeType = "application/dash+xml"
            } else if (hls != null) {
                val separateAudio = if (audioOnly) findHlsAudioRendition(hls) else null
                stream = separateAudio ?: hls
                mimeType = "application/x-mpegURL"
            } else {
                stream = if (audioOnly) audioFallback else fallback
                mimeType = null
            }
            
            if (stream == null) {
                throw Exception("Нет совместимого потока Дзен")
            }
            
            // Определяем разрешение
            var maxWidth = 0
            var maxHeight = 0
            if (fallback != null && fallback.contains("type=5")) {
                maxWidth = 1920
                maxHeight = 1080
            }
            
            val info = StreamInfo(
                streamUrl = stream,
                mimeType = mimeType,
                width = maxWidth,
                height = maxHeight,
                loadedAt = System.currentTimeMillis()
            )
            
            streamCache[cacheKey] = info
            return info
            
        } catch (e: Exception) {
            throw Exception("Ошибка получения потока Дзен: ${e.message}")
        }
    }
    
    // ============ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ============
    
    suspend fun getPage(address: String): String {
        for (attempt in 0..3) {
            val connection = URL(address).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            
            val cookie = cookieHeader()
            if (cookie.isNotEmpty()) {
                connection.setRequestProperty("Cookie", cookie)
            }
            
            try {
                val code = connection.responseCode
                rememberCookies(connection)
                
                if (code in 300..399) {
                    continue // Следуем редиректам
                }
                if (code < 200 || code >= 300) {
                    throw Exception("Дзен: HTTP $code")
                }
                
                val body = readResponse(connection)
                
                // Проверяем, что получили валидную страницу
                if (body.contains("data-card-type=\"card-video\"") ||
                    body.contains("\"videoMetaResponse\"")) {
                    return body
                }
                
                // Если получили страницу SSO, пробуем ещё раз
                if (!body.contains("sso.dzen.ru") && !body.contains("sso.passport.yandex.ru")) {
                    return body
                }
                
            } finally {
                connection.disconnect()
            }
        }
        throw Exception("Дзен не создал анонимную сессию")
    }
    
    private fun parseCards(html: String, limit: Int): List<VideoItem> {
        val result = mutableListOf<VideoItem>()
        val seen = mutableSetOf<String>()
        val marker = "https://dzen.ru/video/watch/"
        var cursor = 0
        
        while (result.size < limit) {
            val link = html.indexOf(marker, cursor)
            if (link < 0) break
            
            val idStart = link + marker.length
            var idEnd = idStart
            while (idEnd < html.length && isIdChar(html[idEnd])) {
                idEnd++
            }
            
            val id = html.substring(idStart, idEnd)
            cursor = idEnd
            
            if (id.isEmpty() || !seen.add(id)) continue
            
            // Извлекаем карточку
            val articleStart = html.lastIndexOf("<article", link)
            val articleEnd = html.indexOf("</article>", link)
            if (articleStart < 0 || articleEnd < 0 || articleEnd - articleStart > 80000) continue
            
            val card = html.substring(articleStart, articleEnd)
            
            val title = textAfter(card, "data-testid=\"card-part-title\">")
                .ifEmpty { attributeNear(card, "floor-card-video-wrapper-link", "aria-label") }
                .ifEmpty { "Видео Дзен" }
            
            val durationText = textAfter(card, "aria-label=\"Общая длительность видео\">")
            val durationMs = parseDuration(durationText) * 1000L
            
            val thumbnail = between(card, "background-image:url(", ")")
            val page = marker + id
            val author = textAfter(card, "data-testid=\"card-part-author\">")
                .ifEmpty { "" }
            
            result.add(VideoItem(
                id = id,
                title = decode(title),
                author = decode(author),
                thumbnail = decode(thumbnail),
                url = page,
                duration = durationMs,
                source = name
            ))
        }
        
        return result
    }
    
    private fun extractTitle(html: String): String? {
        // Пробуем из meta
        val metaTitle = between(html, "<meta property=\"og:title\" content=\"", "\"")
        if (metaTitle.isNotEmpty()) return decode(metaTitle)
        
        // Пробуем из title
        val titleTag = between(html, "<title>", "</title>")
        if (titleTag.isNotEmpty()) return decode(titleTag)
        
        // Пробуем из JSON
        val dataRegex = "\"title\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val match = dataRegex.find(html)
        return match?.groupValues?.get(1)?.let { decode(it) }
    }
    
    private fun extractThumbnail(html: String): String? {
        // Пробуем из meta
        val metaImage = between(html, "<meta property=\"og:image\" content=\"", "\"")
        if (metaImage.isNotEmpty()) return metaImage
        
        // Пробуем из JSON
        val imageRegex = "\"poster\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val match = imageRegex.find(html)
        return match?.groupValues?.get(1)
    }
    
    private fun extractDuration(html: String): Long {
        // Пробуем из JSON
        val durationRegex = "\"duration\"\\s*:\\s*(\\d+)".toRegex()
        val match = durationRegex.find(html)
        if (match != null) {
            return match.groupValues[1].toLongOrNull() ?: 0L
        }
        
        // Пробуем из текста
        val durationText = textAfter(html, "aria-label=\"Общая длительность видео\">")
        return parseDuration(durationText) * 1000L
    }
    
    private fun extractAuthor(html: String): String? {
        val authorRegex = "\"author\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val match = authorRegex.find(html)
        return match?.groupValues?.get(1)?.let { decode(it) }
    }
    
    private fun extractViews(html: String): String {
        val viewsRegex = "\"views\"\\s*:\\s*(\\d+)".toRegex()
        val match = viewsRegex.find(html)
        if (match != null) {
            val views = match.groupValues[1].toLongOrNull() ?: 0L
            return formatViews(views)
        }
        return ""
    }
    
    private fun findDzenId(url: String): String {
        val marker = "/video/watch/"
        val start = url.indexOf(marker)
        if (start < 0) throw Exception("Не найден ID Дзен")
        
        val idStart = start + marker.length
        var idEnd = idStart
        while (idEnd < url.length && isIdChar(url[idEnd])) {
            idEnd++
        }
        
        if (idEnd == idStart) throw Exception("Не найден ID Дзен")
        return url.substring(idStart, idEnd)
    }
    
    private fun jsonObjectAt(value: String, start: Int): String {
        var depth = 0
        var inString = false
        var escaped = false
        
        for (i in start until value.length) {
            val current = value[i]
            
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (current == '\\') {
                    escaped = true
                } else if (current == '"') {
                    inString = false
                }
                continue
            }
            
            when (current) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return value.substring(start, i + 1)
                    }
                }
            }
        }
        
        throw Exception("Повреждены данные ролика Дзен")
    }
    
    private suspend fun hasDashAudio(dashUrl: String): Boolean {
        try {
            val manifest = get(dashUrl)
            return manifest.contains("contentType=\"audio\"") ||
                   manifest.contains("mimeType=\"audio/") ||
                   manifest.contains("<AudioChannelConfiguration")
        } catch (_: Exception) {
            return false
        }
    }
    
    private suspend fun findHlsAudioRendition(hlsUrl: String): String? {
        try {
            val manifest = get(hlsUrl)
            var first: String? = null
            for (line in manifest.split("\\r?\\n".toRegex())) {
                if (!line.startsWith("#EXT-X-MEDIA:") || !line.contains("TYPE=AUDIO")) continue
                val uri = attribute(line, "URI") ?: continue
                val resolved = URL(URL(hlsUrl), uri).toString()
                if (line.contains("DEFAULT=YES")) return resolved
                if (first == null) first = resolved
            }
            return first
        } catch (_: Exception) {
            return null
        }
    }
    
    private fun get(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/json,text/html,*/*")
        connection.setRequestProperty("User-Agent", USER_AGENT)
        
        try {
            val code = connection.responseCode
            if (code < 200 || code >= 300) {
                throw Exception("HTTP $code")
            }
            return readResponse(connection)
        } finally {
            connection.disconnect()
        }
    }
    
    private fun isHls(value: String?): Boolean {
        return value != null && (value.contains(".m3u8") || value.contains("ct=8"))
    }
    
    private fun isDash(value: String?): Boolean {
        return value != null && (value.contains(".mpd") || value.contains("ct=6"))
    }
    
    private fun isIdChar(value: Char): Boolean {
        return value.isLetterOrDigit() || value == '-' || value == '_'
    }
    
    private fun textAfter(value: String, marker: String): String {
        var start = value.indexOf(marker)
        if (start < 0) return ""
        start += marker.length
        val end = value.indexOf('<', start)
        return if (end < 0) "" else value.substring(start, end).trim()
    }
    
    private fun attributeNear(value: String, marker: String, attribute: String): String {
        val markerAt = value.indexOf(marker)
        if (markerAt < 0) return ""
        val tagStart = value.lastIndexOf('<', markerAt)
        val tagEnd = value.indexOf('>', markerAt)
        if (tagStart < 0 || tagEnd < 0) return ""
        return between(value.substring(tagStart, tagEnd), "$attribute=\"", "\"")
    }
    
    private fun between(value: String, before: String, after: String): String {
        val start = value.indexOf(before)
        if (start < 0) return ""
        val begin = start + before.length
        val end = value.indexOf(after, begin)
        return if (end < 0) "" else value.substring(begin, end)
    }
    
    private fun parseDuration(value: String): Int {
        val parts = value.split(":")
        var seconds = 0
        try {
            for (part in parts) {
                seconds = seconds * 60 + part.trim().toInt()
            }
            return seconds
        } catch (_: NumberFormatException) {
            return 0
        }
    }
    
    private fun decode(value: String): String {
        return value.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
    
    private fun httpUrl(value: String?): String? {
        if (value.isNullOrEmpty()) return null
        return if (value.startsWith("//")) "https:$value"
        else if (value.startsWith("http")) value
        else null
    }
    
    private fun attribute(line: String, name: String): String? {
        val marker = "$name=\""
        var start = line.indexOf(marker)
        if (start < 0) return null
        start += marker.length
        val end = line.indexOf('"', start)
        return if (end > start) line.substring(start, end) else null
    }
    
    private fun readResponse(connection: HttpURLConnection): String {
        connection.inputStream.use { input ->
            ByteArrayOutputStream(256 * 1024).use { output ->
                val buffer = ByteArray(16384)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    if (Thread.currentThread().isInterrupted) {
                        throw InterruptedException()
                    }
                    output.write(buffer, 0, read)
                }
                return output.toString(StandardCharsets.UTF_8.name())
            }
        }
    }
    
    private fun cookieHeader(): String {
        synchronized(cookies) {
            return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }
    }
    
    private fun rememberCookies(connection: HttpURLConnection) {
        val headers = connection.headerFields
        for ((key, values) in headers) {
            if (key == null || !key.equals("set-cookie", ignoreCase = true)) continue
            for (value in values) {
                val semicolon = value.indexOf(';')
                val pair = if (semicolon < 0) value else value.substring(0, semicolon)
                val equals = pair.indexOf('=')
                if (equals > 0) {
                    synchronized(cookies) {
                        cookies[pair.substring(0, equals)] = pair.substring(equals + 1)
                    }
                }
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
