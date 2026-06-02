package com.example.data.gemini

import android.util.Log
import com.example.BuildConfig
import com.example.data.Video
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiSearchService {
    private const val TAG = "GeminiSearchService"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    suspend fun fetchSearchFallback(query: String?, category: String?): List<Video> {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is blank or placeholder")
            return emptyList()
        }

        val requestQuery = when {
            !query.isNullOrBlank() && !category.isNullOrBlank() && category != "Все" -> 
                "теме '$query' в категории '$category'"
            !query.isNullOrBlank() -> "поисковому запросу '$query'"
            !category.isNullOrBlank() && category != "Все" -> "категории '$category'"
            else -> "интересным популярным темам"
        }

        val systemInstruction = """
            Вы — умная поисковая система для Rutube-клиента. Сгенерируйте список из 6-8 реалистичных, качественных, современных видеороликов на русском языке по запросу пользователя.
            Не используйте плейсхолдеры. Обязательно возвращайте валидный JSON-массив объектов.
            Каждое видео должно содержать:
            - id: Реалистичный уникальный 32-символьный хэш-код (например, '0963d76e8a04f4a3c10fb17be3a90342' или '9f5e3c78d4ea93bc01b50cf716f2a034')
            - title: Кликбейт-фри профессиональный заголовок видео
            - channel: Реалистичный рускоязычный канал (например, 'Влад Разработчик', 'ТехноКомпас', 'Мир Кино', 'IT-Новости', 'Кулинарный Шедевр')
            - views: Строка с количеством просмотров на русском (например, '42к просмотров', '1.2м просмотров')
            - timeAgo: Относительная дата загрузки видео (например, '2 часа назад', 'Вчера', '3 дня назад', '1 неделю назад')
            - duration: Длина в формате MM:SS или HH:MM:SS (например, '11:24', '42:15', '07:05')
            - isPro: Boolean (некоторые true, некоторые false, показывая статус премиум)
            - category: Название категории, строго соответствующее одной из: 'Все', 'Фильмы', 'Сериалы', 'Телепередачи', 'Музыка', 'Мультфильмы', 'Спорт', 'Юмор', 'Видеоигры', 'Технологии'
            - description: Развернутое и полезное описание на русском языке (2-3 предложения), детально раскрывающее суть видео.
        """.trimIndent()

        val prompt = "Сгенерируй реалистичные видео для Rutube по $requestQuery."

        // Build Gemini request JSON
        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "System instructions:\n$systemInstruction\n\nPrompt:\n$prompt")
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.7)
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        // Using gemini-3.5-flash as default simple model for search generation, fallback to gemini-2.5-flash if needed
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Gemini call failed with code: ${response.code}")
                    return emptyList()
                }

                val bodyString = response.body?.string() ?: return emptyList()
                val responseJson = JSONObject(bodyString)
                val candidates = responseJson.optJSONArray("candidates")
                val content = candidates?.optJSONObject(0)?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val responseText = parts?.optJSONObject(0)?.optString("text") ?: ""

                if (responseText.isNotBlank()) {
                    val listType = Types.newParameterizedType(List::class.java, Video::class.java)
                    val adapter = moshi.adapter<List<Video>>(listType)
                    adapter.fromJson(responseText) ?: emptyList()
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching search fallback from Gemini", e)
            emptyList()
        }
    }
}
