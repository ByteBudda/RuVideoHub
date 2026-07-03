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
                        val mp4Keys = listOf("mp4_1080", "mp4_720", "mp4_480", "mp4_360")
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
        onProgress: (Float, String) -> Unit
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            // Если это HLS плейлист
            if (videoUrl.contains(".m3u8")) {
                return@withContext downloadHlsVideo(videoUrl, onProgress)
            } else {
                // Прямая загрузка MP4
                return@withContext downloadMp4Video(videoUrl, onProgress)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}")
            return@withContext null
        }
    }

    private suspend fun downloadMp4Video(
        url: String,
        onProgress: (Float, String) -> Unit
    ): ByteArray? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://vk.com/")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            
            val body = response.body ?: return@withContext null
            val totalBytes = body.contentLength()
            val inputStream = body.byteStream()
            
            val outputStream = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var downloaded = 0L
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                downloaded += bytesRead
                
                if (totalBytes > 0) {
                    val progress = downloaded.toFloat() / totalBytes
                    val speed = String.format("%.1f MB/s", downloaded / 1024.0 / 1024.0)
                    onProgress(progress, speed)
                }
            }
            
            return@withContext outputStream.toByteArray()
        }
    }

    private suspend fun downloadHlsVideo(
        masterUrl: String,
        onProgress: (Float, String) -> Unit
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            // 1. Получаем мастер плейлист
            val masterRequest = Request.Builder()
                .url(masterUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://vk.com/")
                .build()
            
            val masterText = httpClient.newCall(masterRequest).execute().use {
                it.body?.string() ?: return@withContext null
            }
            
            // 2. Ищем плейлист с качеством
            val qualityPattern = Regex("""RESOLUTION=\d+x(\d+).*\n(.*\.m3u8)""")
            val qualityMatch = qualityPattern.find(masterText)
            val playlistUrl = if (qualityMatch != null) {
                val resolution = qualityMatch.groupValues[1].toIntOrNull() ?: 720
                // Выбираем качество: 1080p > 720p > 480p
                val targetRes = when {
                    resolution >= 1080 -> "1080"
                    resolution >= 720 -> "720"
                    else -> "480"
                }
                val pattern = Regex("""RESOLUTION=\d+x($targetRes).*\n(.*\.m3u8)""")
                pattern.find(masterText)?.groupValues?.get(2) 
                    ?: qualityMatch.groupValues[2]
            } else {
                // Если нет RESOLUTION, берем первый плейлист
                Regex("""\n(.*\.m3u8)""").find(masterText)?.groupValues?.get(1) ?: return@withContext null
            }
            
            val fullPlaylistUrl = if (playlistUrl.startsWith("http")) {
                playlistUrl
            } else {
                val baseUrl = masterUrl.substringBeforeLast("/")
                "$baseUrl/$playlistUrl"
            }
            
            // 3. Получаем плейлист сегментов
            val playlistRequest = Request.Builder()
                .url(fullPlaylistUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://vk.com/")
                .build()
            
            val playlistText = httpClient.newCall(playlistRequest).execute().use {
                it.body?.string() ?: return@withContext null
            }
            
            // 4. Получаем список сегментов
            val segments = playlistText.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
            
            if (segments.isEmpty()) return@withContext null
            
            // 5. Скачиваем сегменты
            val outputStream = java.io.ByteArrayOutputStream()
            val baseUrl = fullPlaylistUrl.substringBeforeLast("/")
            var downloaded = 0
            val total = segments.size
            
            for (segment in segments) {
                val segmentUrl = if (segment.startsWith("http")) {
                    segment
                } else {
                    "$baseUrl/$segment"
                }
                
                val segmentRequest = Request.Builder()
                    .url(segmentUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://vk.com/")
                    .build()
                
                try {
                    httpClient.newCall(segmentRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            val data = response.body?.bytes() ?: byteArrayOf()
                            outputStream.write(data)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Segment download error: ${e.message}")
                }
                
                downloaded++
                val progress = downloaded.toFloat() / total
                val speed = String.format("%.1f%%", progress * 100)
                onProgress(progress, speed)
            }
            
            return@withContext outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "HLS download error: ${e.message}")
            return@withContext null
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
