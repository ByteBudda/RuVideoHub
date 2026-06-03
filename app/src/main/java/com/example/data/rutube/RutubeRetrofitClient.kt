package com.example.data.rutube

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RutubeRetrofitClient {

    private const val BASE_URL = "https://rutube.ru/"
    private const val PREFS_NAME = "rutube_auth_prefs"
    private const val KEY_SAVED_COOKIES = "saved_cookies"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private lateinit var prefs: SharedPreferences

    // Метод для инициализации настроек, вызови его в MainActivity.onCreate()
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .cookieJar(CookieJar.NO_COOKIES) 
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", BASE_URL)
                .header("Accept", "application/json, text/plain, */*")

            // 1. Пытаемся достать намертво сохраненные куки из памяти телефона
            var currentCookies = if (::prefs.isInitialized) prefs.getString(KEY_SAVED_COOKIES, null) else null

            // 2. Если в памяти еще ничего нет, пробуем вытащить их из WebView (первый вход)
            if (currentCookies.isNullOrBlank()) {
                val webViewCookies = CookieManager.getInstance().getCookie(BASE_URL)
                
                // Если в WebView куки наконец появились (юзер залогинился) — сохраняем их навсегда
                if (!webViewCookies.isNullOrBlank() && webViewCookies.contains("sessionid")) {
                    currentCookies = webViewCookies
                    if (::prefs.isInitialized) {
                        prefs.edit().putString(KEY_SAVED_COOKIES, webViewCookies).apply()
                    }
                }
            }

            // 3. Если куки есть (хоть из памяти, хоть из WebView) — суем их в заголовок
            if (!currentCookies.isNullOrBlank()) {
                builder.header("Cookie", currentCookies)

                // Вытаскиваем X-CSRFToken для POST запросов
                currentCookies.split(";").forEach { pair ->
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
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RutubeApiService::class.java)
    }
}
