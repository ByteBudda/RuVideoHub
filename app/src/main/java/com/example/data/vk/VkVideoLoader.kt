// VkVideoLoader.kt - РАБОЧАЯ ВЕРСИЯ
package com.example.data.vk

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

object VkVideoLoader {
    private const val TAG = "VkVideoLoader"
    private const val CLIENT_VERSION = "5.199"
    private const val USER_AGENT = "com.vk.vkvideo.prod/1955 (iPhone, iOS 16.7.15)"
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // --- ПОЛУЧЕНИЕ ТОКЕНА ---
    private suspend fun getToken(): String = withContext(Dispatchers.IO) {
        val deviceId = UUID.randomUUID().toString().uppercase()
        val url = "https://api.vk.com/method/auth.getAnonymToken?" +
                "client_id=51552953" +
                "&client_secret=qgr0yWwXCrsxA1jnRtRX" +
                "&device_id=$deviceId" +
                "&v=$CLIENT_VERSION"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Auth error: ${response.code}")
            val json = JSONObject(response.body?.string() ?: "")
            json.getJSONObject("response").getString("token")
        }
    }

    // --- ПАРСИНГ ССЫЛКИ ---
    fun parseVideoUrl(url: String): Triple<String, String, String?>? {
        return try {
            // Форматы:
            // vk.com/video-123456_789
            // vk.com/video123456_789
            // vkvideo.ru/video-123456_789
            // m.vk.com/video-123456_789
            
            val patterns = listOf(
                Regex("""(?:video|clip)(-?\d+)_(\d+)"""),
                Regex("""oid=(-?\d+)&id=(\d+)""")
            )
            
            for (pattern in patterns) {
                val match = pattern.find(url)
                if (match != null) {
                    val ownerId = match.groupValues[1]
                    val videoId = match.groupValues[2]
                    val accessKey = Regex("""access_key=([^&]+)""").find(url)?.groupValues?.get(1)
                    return Triple(ownerId, videoId, accessKey)
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
            null
        }
    }

    // --- ПОЛУЧЕНИЕ ИНФОРМАЦИИ О ВИДЕО ---
    suspend fun getVideoInfo(url: String): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            val parsed = parseVideoUrl(url) ?: return@withContext null
            val (ownerId, videoId, accessKey) = parsed
            val token = getToken()
            
            val videoParam = "${ownerId}_${videoId}${if (accessKey != null) "_$accessKey" else ""}"
            
            val formBody = FormBody.Builder()
                .add("v", CLIENT_VERSION)
                .add("access_token", token)
                .add("videos", videoParam)
                .add("extended", "1")
                .build()

            val request = Request.Builder()
                .url("https://api.vk.com/method/video.get")
                .header("User-Agent", USER_AGENT)
                .post(formBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                
                val json = JSONObject(response.body?.string() ?: "")
                val responseObj = json.optJSONObject("response")
                val items = responseObj?.optJSONArray("items") ?: return@withContext null
                
                if (items.length() == 0) return@withContext null
                
                val video = items.getJSONObject(0)
                
                // Получаем превью
                val imageArray = video.optJSONArray("image")
                val thumbnail = if (imageArray != null && imageArray.length() > 0) {
                    imageArray.getJSONObject(imageArray.length() - 1).optString("url")
                } else {
                    video.optString("first_frame", "")
                }
                
                // Получаем название
                val title = video.optString("title", "Без названия")
                
                // Получаем длительность
                val duration = video.optInt("duration", 0)
                val durationStr = if (duration > 0) {
                    val minutes = duration / 60
                    val seconds = duration % 60
                    String.format("%02d:%02d", minutes, seconds)
                } else {
                    "00:00"
                }
                
                // Получаем ссылку на видео
                val files = video.optJSONObject("files")
                var videoUrl = ""
                
                if (files != null) {
                    // Пробуем HLS
                    if (files.has("hls")) {
                        videoUrl = files.getString("hls")
                        if (videoUrl.startsWith("//")) videoUrl = "https:$videoUrl"
                    } else {
                        // Пробуем MP4
                        val mp4Keys = listOf("mp4_2160", "mp4_1440", "mp4_1080", "mp4_720", "mp4_480", "mp4_360", "mp4_240")
                        for (key in mp4Keys) {
                            if (files.has(key)) {
                                videoUrl = files.getString(key)
                                if (videoUrl.startsWith("//")) videoUrl = "https:$videoUrl"
                                break
                            }
                        }
                        if (videoUrl.isEmpty() && files.has("mp4")) {
                            videoUrl = files.getString("mp4")
                            if (videoUrl.startsWith("//")) videoUrl = "https:$videoUrl"
                        }
                    }
                }
                
                return@withContext VideoInfo(
                    id = "${ownerId}_${videoId}",
                    title = title,
                    thumbnail = thumbnail,
                    duration = durationStr,
                    videoUrl = videoUrl,
                    views = formatViews(video.optInt("views", 0)),
                    ownerId = ownerId,
                    videoId = videoId
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video info: ${e.message}")
            return@withContext null
        }
    }

    // --- СКАЧИВАНИЕ ВИДЕО (если HLS) ---
    suspend fun downloadVideo(
        videoUrl: String,
        targetFile: java.io.File,
        onProgress: (Float, String, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Если это HLS плейлист
            if (videoUrl.contains(".m3u8")) {
                return@withContext downloadHlsVideo(videoUrl, targetFile, onProgress)
            } else {
                // Прямая загрузка MP4
                return@withContext downloadMp4Video(videoUrl, targetFile, onProgress)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}")
            false
        }
    }

    private suspend fun downloadMp4Video(
        url: String,
        targetFile: java.io.File,
        onProgress: (Float, String, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://vk.com/")
            .build()

        try {
            targetFile.parentFile?.mkdirs()
            if (targetFile.exists()) {
                targetFile.delete()
            }

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                
                val body = response.body ?: return@withContext false
                val totalBytes = body.contentLength()
                val inputStream = body.byteStream()
                
                targetFile.outputStream().use { outputStream ->
                    val buffer = ByteArray(8 * 1024 * 1024)
                    var bytesRead: Int
                    var downloaded = 0L
                    val startTimeMs = System.currentTimeMillis()
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        
                        val elapsedMs = System.currentTimeMillis() - startTimeMs
                        val speedStr = if (elapsedMs > 0) {
                            val bytesSec = (downloaded * 1000) / elapsedMs
                            if (bytesSec > 1024 * 1024) {
                                String.format("%.2f MiB/s", bytesSec / (1024.0 * 1024.0))
                            } else {
                                String.format("%.2f KiB/s", bytesSec / 1024.0)
                            }
                        } else "0 B/s"

                        if (totalBytes > 0) {
                            val progress = downloaded.toFloat() / totalBytes
                            val etaStr = if (downloaded > 0) {
                                val totalEstMs = (totalBytes * elapsedMs) / downloaded
                                val remainingSeconds = ((totalEstMs - elapsedMs) / 1000).toInt()
                                if (remainingSeconds > 0) {
                                    String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60)
                                } else "00:01"
                            } else "00:01"
                            onProgress(progress, speedStr, etaStr)
                        } else {
                            onProgress(0.5f, speedStr, "--:--")
                        }
                    }
                }
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "MP4 download error: ${e.message}")
            false
        }
    }

    private fun resolveUrl(base: String, relative: String): String {
        val trimmedRel = relative.trim()
        if (trimmedRel.startsWith("http://") || trimmedRel.startsWith("https://")) {
            return trimmedRel
        }
        if (trimmedRel.startsWith("//")) {
            return "https:$trimmedRel"
        }
        if (trimmedRel.startsWith("/")) {
            try {
                val uri = java.net.URI(base)
                val hostUrl = "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}"
                return "$hostUrl$trimmedRel"
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving domain absolute URL: ${e.message}")
            }
        }
        // Relative to base directory path
        val lastSlash = base.lastIndexOf('/')
        val basePath = if (lastSlash != -1) base.substring(0, lastSlash) else base
        return "$basePath/$trimmedRel"
    }

    private suspend fun downloadHlsVideo(
        masterUrl: String,
        targetFile: java.io.File,
        onProgress: (Float, String, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting HLS download from masterUrl: $masterUrl")
            // 1. Получаем мастер плейлист
            val masterRequest = Request.Builder()
                .url(masterUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://vk.com/")
                .build()
            
            val masterText = httpClient.newCall(masterRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to fetch master playlist, code: ${response.code}")
                    return@withContext false
                }
                response.body?.string() ?: return@withContext false
            }
            
            // 2. Ищем плейлист с наилучшим качеством
            var selectedUrl: String? = null
            var bestResolution = 0
            
            val masterLines = masterText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            var idx = 0
            while (idx < masterLines.size) {
                val line = masterLines[idx]
                if (line.startsWith("#EXT-X-STREAM-INF:")) {
                    // Try to parse RESOLUTION
                    val resolutionMatch = Regex("""RESOLUTION=\d+x(\d+)""").find(line)
                    val res = resolutionMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    
                    if (idx + 1 < masterLines.size) {
                        val nextLine = masterLines[idx + 1]
                        if (!nextLine.startsWith("#")) {
                            // This is the playlist URL
                            // Prefer resolutions up to 720p or 1080p for download
                            if (selectedUrl == null || res > bestResolution) {
                                bestResolution = res
                                selectedUrl = nextLine
                            }
                        }
                    }
                }
                idx++
            }

            if (selectedUrl == null) {
                // Fallback to find any .m3u8 line
                selectedUrl = masterLines.firstOrNull { it.endsWith(".m3u8") && !it.startsWith("#") }
            }

            if (selectedUrl == null) {
                Log.e(TAG, "Could not find any playlist stream URL in master playlist")
                return@withContext false
            }
            
            val fullPlaylistUrl = resolveUrl(masterUrl, selectedUrl)
            Log.d(TAG, "Resolved playlist URL: $fullPlaylistUrl (best resolution: ${bestResolution}p)")
            
            // 3. Получаем плейлист сегментов
            val playlistRequest = Request.Builder()
                .url(fullPlaylistUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://vk.com/")
                .build()
            
            val playlistText = httpClient.newCall(playlistRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to fetch stream playlist, code: ${response.code}")
                    return@withContext false
                }
                response.body?.string() ?: return@withContext false
            }
            
            // 4. Получаем список сегментов
            val segments = playlistText.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
            
            if (segments.isEmpty()) {
                Log.e(TAG, "Parsed segments list is empty")
                return@withContext false
            }
            
            Log.d(TAG, "Found ${segments.size} segments to download")
            
            // 5. Скачиваем сегменты
            targetFile.parentFile?.mkdirs()
            if (targetFile.exists()) {
                targetFile.delete()
            }

            var downloaded = 0
            val total = segments.size
            var totalDownloadedBytes = 0L
            val startTimeMs = System.currentTimeMillis()

            targetFile.outputStream().use { outputStream ->
                for (segment in segments) {
                    val segmentUrl = resolveUrl(fullPlaylistUrl, segment)
                    
                    val segmentRequest = Request.Builder()
                        .url(segmentUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", "https://vk.com/")
                        .build()
                    
                    var success = false
                    var attempts = 0
                    while (!success && attempts < 3) {
                        attempts++
                        try {
                            httpClient.newCall(segmentRequest).execute().use { response ->
                                if (response.isSuccessful) {
                                    val body = response.body
                                    if (body != null) {
                                        val inputStream = body.byteStream()
                                        val buffer = ByteArray(8 * 1024 * 1024)
                                        var bytesRead: Int
                                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                            outputStream.write(buffer, 0, bytesRead)
                                            totalDownloadedBytes += bytesRead
                                        }
                                    }
                                    success = true
                                } else {
                                    Log.e(TAG, "Failed to download segment, code: ${response.code}, attempt: $attempts")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Segment download exception: ${e.message}, attempt: $attempts")
                        }
                    }
                    
                    downloaded++
                    val progress = downloaded.toFloat() / total
                    val elapsedMs = System.currentTimeMillis() - startTimeMs
                    val speedStr = if (elapsedMs > 0) {
                        val bytesSec = (totalDownloadedBytes * 1000) / elapsedMs
                        if (bytesSec > 1024 * 1024) {
                            String.format("%.2f MiB/s", bytesSec / (1024.0 * 1024.0))
                        } else {
                            String.format("%.2f KiB/s", bytesSec / 1024.0)
                        }
                    } else "0 B/s"

                    val etaStr = if (downloaded > 0 && downloaded < total) {
                        val totalEstMs = (total.toLong() * elapsedMs) / downloaded
                        val remainingSeconds = ((totalEstMs - elapsedMs) / 1000).toInt()
                        if (remainingSeconds > 0) {
                            String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60)
                        } else "00:01"
                    } else "00:01"

                    onProgress(progress, speedStr, etaStr)
                }
            }
            
            Log.d(TAG, "HLS stream downloaded successfully, size: $totalDownloadedBytes bytes")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "HLS download error: ${e.message}")
            false
        }
    }

    private fun formatViews(views: Int): String {
        return when {
            views >= 1_000_000 -> String.format("%.1fM", views / 1_000_000.0)
            views >= 1_000 -> String.format("%.1fK", views / 1000.0)
            else -> views.toString()
        }
    }
}

// --- МОДЕЛЬ ДАННЫХ ---
data class VideoInfo(
    val id: String,
    val title: String,
    val thumbnail: String,
    val duration: String,
    val videoUrl: String,
    val views: String,
    val ownerId: String,
    val videoId: String
)
