package com.example.data.rutube

import android.webkit.CookieManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RutubeRetrofitClient {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Прямой мост к постоянной базе кук WebView
    private val persistentWebViewCookieJar = object : CookieJar {
        
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            
            cookies.forEach { cookie ->
                // Сохраняем куки, полученные от API запросов, обратно в общую базу WebView
                cookieManager.setCookie(url.toString(), cookie.toString())
            }
            // Принудительно сбрасываем на диск, чтобы сессия сохранялась при закрытии приложения
            cookieManager.flush()
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val cookieManager = CookieManager.getInstance()
            
            // Забираем строку кук прямо из хранилища WebView для конкретного URL
            val cookieString = cookieManager.getCookie(url.toString())
            if (cookieString.isNullOrBlank()) return emptyList()

            // Парсим куки. Просроченные куки Android отсеет сам на основе их Expires/Max-Age
            return cookieString.split(";").mapNotNull {
                Cookie.parse(url, it.trim())
            }
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .cookieJar(persistentWebViewCookieJar) // <--- Автоматически подтягивает и сохраняет куки встроенного браузера
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "https://rutube.ru/")
                .header("Accept", "application/json, text/plain, */*")

            // Rutube для POST/PUT запросов требует дублировать csrftoken в заголовок X-CSRFToken.
            // Достаем его из актуальных кук WebView.
            val cookieManager = CookieManager.getInstance()
            val cookies = cookieManager.getCookie("https://rutube.ru")
            if (!cookies.isNullOrBlank()) {
                cookies.split("; ").forEach { pair ->
                    val parts = pair.split("=")
                    if (parts.size == 2 && parts[0].trim() == "csrftoken") {
                        builder.header("X-CSRFToken", parts[1].trim())
                    }
                }
            }

            chain.proceed(builder.build())
        }
        .build()

    val apiService: RutubeApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://rutube.ru/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RutubeApiService::class.java)
    }
}
