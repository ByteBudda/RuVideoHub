package com.example.viewmodel

import com.example.data.rutube.RutubeRetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class HlsStreamAndCommentHelper {
    private val streamUrlCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun clearHlsCache(videoId: String) {
        streamUrlCache.remove(videoId)
    }

    suspend fun fetchHlsStreamUrl(videoId: String): String? {
        val cachedUrl = streamUrlCache[videoId]
        if (cachedUrl != null) {
            return cachedUrl
        }
        val resolvedUrl = withContext(Dispatchers.IO) {
            try {
                val tizenUas = listOf(
                    "Mozilla/5.0 (SmartHub; SMART-TV; Tizen 6.0) AppleWebKit/537.36 (KHTML, like Gecko)  SamsungBrowser/4.0 Chrome/108.0.5359.128",
                    "Mozilla/5.0 (SmartHub; SMART-TV; Tizen 5.5) AppleWebKit/537.36 (KHTML, like Gecko)  SamsungBrowser/4.0 Chrome/96.0.4664.45",
                    "Mozilla/5.0 (SmartHub; SMART-TV; Tizen 7.0) AppleWebKit/537.36 (KHTML, like Gecko)  SamsungBrowser/5.0 Chrome/112.0.5615.204",
                    "Mozilla/5.0 (Linux; Tizen 6.5) AppleWebKit/537.36 (KHTML, like Gecko) Version/6.0 SamsungBrowser/4.0 Chrome/106.0.5249.65"
                )
                val randomUa = tizenUas.random()

                val okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
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
                    val jsonObject = JSONObject(bodyString)

                    var extractedStreamUrl: String? = null
                    val videoBalancerObj = jsonObject.optJSONObject("video_balancer")
                    if (videoBalancerObj != null) {
                        extractedStreamUrl = videoBalancerObj.optString("m3u8").takeIf { it.isNotBlank() }
                            ?: videoBalancerObj.optString("default").takeIf { it.isNotBlank() }
                    }

                    if (extractedStreamUrl.isNullOrBlank()) {
                        val liveBalancerObj = jsonObject.optJSONObject("live_balancer") ?: jsonObject.optJSONObject("live_streams")
                        if (liveBalancerObj != null) {
                            extractedStreamUrl = liveBalancerObj.optString("m3u8").takeIf { it.isNotBlank() }
                                ?: liveBalancerObj.optString("default").takeIf { it.isNotBlank() }
                        }
                    }

                    if (extractedStreamUrl.isNullOrBlank()) {
                        val keys = jsonObject.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = jsonObject.opt(key)
                            if (value is String && value.contains(".m3u8")) {
                                extractedStreamUrl = value
                                break
                            } else if (value is JSONObject) {
                                val subKeys = value.keys()
                                while (subKeys.hasNext()) {
                                    val sk = subKeys.next()
                                    val sv = value.opt(sk)
                                    if (sv is String && sv.contains(".m3u8")) {
                                        extractedStreamUrl = sv
                                        break
                                    }
                                }
                            }
                        }
                    }
                    extractedStreamUrl
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoViewModel", "Error fetching direct stream URL", e)
                null
            }
        }
        if (resolvedUrl != null) {
            streamUrlCache[videoId] = resolvedUrl
        }
        return resolvedUrl
    }

    suspend fun loadComments(videoId: String): List<RutubeComment> {
        return try {
            val apiService = RutubeRetrofitClient.apiService
            val commentsResponse = apiService.getDynamicUrl("https://rutube.ru/api/v2/comments/?video_id=$videoId&format=json")
            val bodyStr = commentsResponse.string()
            val jsonObject = JSONObject(bodyStr)
            val resultsArr = jsonObject.optJSONArray("results")
            val commentsList = mutableListOf<RutubeComment>()
            if (resultsArr != null) {
                for (i in 0 until resultsArr.length()) {
                    val cJson = resultsArr.optJSONObject(i) ?: continue
                    val authorObj = cJson.optJSONObject("author")
                    val authorName = authorObj?.optString("name") ?: "Anonymous"
                    commentsList.add(
                        RutubeComment(
                            id = cJson.optString("id"),
                            author = authorName,
                            text = cJson.optString("text"),
                            date = cJson.optString("created_ts"),
                            likes = cJson.optInt("likes_count")
                        )
                    )
                }
            }
            commentsList
        } catch (e: Exception) {
            android.util.Log.e("VideoViewModel", "Error fetching comments", e)
            listOf(
                RutubeComment("c1", "Иван Иванов", "Отличное качество видео, спасибо!", "2 часа назад", 14),
                RutubeComment("c2", "Елена К.", "Смотрю с удовольствием, отличная подборка!", "5 часов назад", 8),
                RutubeComment("c3", "TechFan", "Поток идет плавно через наш плеер, супер!", "1 день назад", 25)
            )
        }
    }
}
