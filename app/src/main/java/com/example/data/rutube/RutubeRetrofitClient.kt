package com.example.data.rutube

import android.webkit.CookieManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RutubeRetrofitClient {

    private const val BASE_URL = "https://rutube.ru"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        
        // Отключаем встроенный CookieJar, работаем напрямую с заголовками
        .cookieJar(CookieJar.NO_COOKIES) 
        
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "$BASE_URL/")
                .header("Accept", "application/json, text/plain, */*")

            val cookieManager = CookieManager.getInstance()
            
            // Заставляем CookieManager разрешить куки глобально
            cookieManager.setAcceptCookie(true)

            // Вытаскиваем куки для домена Rutube
            val rawCookies = cookieManager.getCookie(BASE_URL)

            if (!rawCookies.isNullOrBlank()) {
                // Пихаем всю сырую строку кук (включая sessionid), как ты делал руками
                builder.header("Cookie", rawCookies)

                // Дергаем csrftoken для POST/PUT запросов
                rawCookies.split(";").forEach { pair ->
                    val parts = pair.split("=")
                    if (parts.size == 2 && parts[0].trim() == "csrftoken") {
                        builder.header("X-CSRFToken", parts[1].trim())
                    }
                }
            }

            val response = chain.proceed(builder.build())

            // Перехватываем куки, если сам сервер Rutube прислал новые в ответе API
            val responseCookies = response.headers("Set-Cookie")
            if (responseCookies.isNotEmpty()) {
                responseCookies.forEach { cookie ->
                    cookieManager.setCookie(BASE_URL, cookie)
                }
                // КРИТИЧЕСКИ ВАЖНО: Намертво сбрасываем куки из оперативы на диск смартфона.
                // После этой команды Android физически записывает их в постоянную память.
                cookieManager.flush()
            }

            response
        }
        .build()

    val apiService: RutubeApiService by lazy {
        Retrofit.Builder()
            .baseUrl("$BASE_URL/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RutubeApiService::class.java)
    }
}
