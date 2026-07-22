package com.example.manager

import android.util.LruCache
import com.example.data.SubtitleTrack
import com.example.data.rutube.HlsParser
import com.example.data.rutube.HlsStream
import com.example.data.rutube.RutubeRetrofitClient
import com.example.data.rutube.parser.RutubeParser
import com.example.data.vk.VkVideoLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class RutubeMediaResolver(
    private val playerManager: PlayerManager,
    private val coroutineScope: CoroutineScope
) {
    private val parser = RutubeParser()

    val streamUrlCache = LruCache<String, String>(100)
    val masterUrlCache = LruCache<String, String>(100)
    val parsedStreamsCache = LruCache<String, List<HlsStream>>(100)

    fun clearHlsCache(videoId: String) {
        streamUrlCache.remove(videoId)
        masterUrlCache.remove(videoId)
    }

    suspend fun fetchSubtitles(videoId: String): List<SubtitleTrack> {
        return withContext(Dispatchers.IO) {
            try {
                // If it's a VK video or other external, we skip for now
                if (videoId.startsWith("vk_") || videoId.startsWith("plugin_") || videoId.startsWith("channel_") || videoId.startsWith("playlist_")) {
                    return@withContext emptyList()
                }
                
                val response = RutubeRetrofitClient.apiService.getSubtitles(videoId)
                val bodyStr = response.string()
                parser.parseSubtitles(bodyStr)
            } catch (e: Exception) {
                android.util.Log.e("RutubeMediaResolver", "Error fetching subtitles", e)
                emptyList()
            }
        }
    }

    suspend fun fetchHlsStreamUrl(videoId: String, quality: String = "Авто"): String? {
        // Plugin video
        if (videoId.startsWith("plugin_")) {
            val cachedMaster = masterUrlCache[videoId]
            if (cachedMaster != null) {
                return cachedMaster
            }
            return withContext(Dispatchers.IO) {
                try {
                    val pageUrl = masterUrlCache[videoId + "_page"] ?: return@withContext null
                    val streamInfo = com.example.plugins.PluginManager.resolveStream(pageUrl, audioOnly = false)
                    val streamUrl = streamInfo?.streamUrl
                    if (streamUrl != null) {
                        masterUrlCache.put(videoId, streamUrl)
                    }
                    streamUrl
                } catch (e: Exception) {
                    android.util.Log.e("RutubeMediaResolver", "Error fetching stream for plugin video: $videoId", e)
                    null
                }
            }
        }

        // VK video
        if (videoId.startsWith("vk_")) {
            val cachedMaster = masterUrlCache[videoId]
            if (cachedMaster != null) {
                return cachedMaster
            }
            return withContext(Dispatchers.IO) {
                try {
                    val vkId = videoId.substringAfter("vk_")
                    val info = VkVideoLoader.getVideoInfo("https://vk.com/video$vkId")
                    if (info != null) {
                        masterUrlCache.put(videoId, info.videoUrl)
                        info.videoUrl
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RutubeMediaResolver", "Error fetching HLS for VK video: $videoId", e)
                    null
                }
            }
        }

        // Rutube video
        val masterUrl = withContext(Dispatchers.IO) {
            val cachedMaster = masterUrlCache[videoId]
            if (cachedMaster != null) {
                cachedMaster
            } else {
                try {
                    val tizenUas = listOf(
                        "Mozilla/5.0 (SmartHub; SMART-TV; Tizen 6.0) AppleWebKit/537.36 (KHTML, like Gecko)  SamsungBrowser/4.0 Chrome/108.0.5359.128",
                        "Mozilla/5.0 (SmartHub; SMART-TV; Tizen 5.5) AppleWebKit/537.36 (KHTML, like Gecko)  SamsungBrowser/4.0 Chrome/96.0.4664.45",
                        "Mozilla/5.0 (SmartHub; SMART-TV; Tizen 7.0) AppleWebKit/537.36 (KHTML, like Gecko)  SamsungBrowser/5.0 Chrome/112.0.5615.204",
                        "Mozilla/5.0 (Linux; Tizen 6.5) AppleWebKit/537.36 (KHTML, like Gecko) Version/6.0 SamsungBrowser/4.0 Chrome/106.0.5249.65"
                    )
                    val randomUa = tizenUas.random()

                    val okHttpClient = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .addInterceptor { chain ->
                            val req = chain.request().newBuilder()
                                .header("User-Agent", randomUa)
                                .header("Referer", "https://rutube.ru/")
                                .header("Accept", "application/json")
                                .build()
                            chain.proceed(req)
                        }
                        .build()

                    val req = Request.Builder()
                        .url("https://rutube.ru/api/play/options/$videoId/?format=json")
                        .build()

                    okHttpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withContext null
                        val bodyString = resp.body?.string() ?: return@withContext null

                        // Используем улучшенную функцию извлечения
                        val extractedStreamUrl = parser.extractStreamUrlFromPlayOptions(bodyString)

                        if (extractedStreamUrl.isNullOrBlank()) {
                            android.util.Log.w("RutubeMediaResolver", "No stream URL found in play options for $videoId")
                        }

                        if (extractedStreamUrl != null) {
                            masterUrlCache.put(videoId, extractedStreamUrl)
                        }
                        extractedStreamUrl
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RutubeMediaResolver", "Error fetching direct stream URL for $videoId", e)
                    null
                }
            }
        }

        if (masterUrl.isNullOrBlank()) {
            return null
        }

        if (quality == "Авто") {
            // Populate available qualities asynchronously in background
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    getOrFetchParsedStreams(masterUrl)
                } catch (e: Exception) {
                    // Ignore
                }
            }
            playerManager.setActiveVideoQuality("Авто")
            return masterUrl
        }

        // Specific quality requested
        val streams = getOrFetchParsedStreams(masterUrl)
        if (streams.isEmpty()) {
            playerManager.setActiveVideoQuality("Авто")
            return masterUrl
        }

        val selectedStream = streams.firstOrNull { it.resolution.equals(quality, ignoreCase = true) }
            ?: streams.firstOrNull { it.resolution.contains("720") }
            ?: streams.firstOrNull { it.resolution.contains("480") }
            ?: streams.first()

        playerManager.setActiveVideoQuality(selectedStream.resolution)
        return selectedStream.url
    }

    private suspend fun getOrFetchParsedStreams(masterUrl: String): List<HlsStream> {
        val cached = parsedStreamsCache[masterUrl]
        if (cached != null) {
            playerManager.setAvailableQualities(listOf("Авто") + cached.map { it.resolution }.distinct())
            return cached
        }
        return withContext(Dispatchers.IO) {
            try {
                val okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()
                val req = Request.Builder()
                    .url(masterUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Referer", "https://rutube.ru/")
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val text = resp.body?.string() ?: return@withContext emptyList()
                    val parsed = HlsParser.parseMasterPlaylist(masterUrl, text)
                    if (parsed.isNotEmpty()) {
                        parsedStreamsCache.put(masterUrl, parsed)
                        playerManager.setAvailableQualities(listOf("Авто") + parsed.map { it.resolution }.distinct())
                    }
                    parsed
                }
            } catch (e: Exception) {
                android.util.Log.e("RutubeMediaResolver", "Error loading/parsing master playlist text", e)
                emptyList()
            }
        }
    }
}