package com.example.pluginapi

import androidx.annotation.Keep

/**
 * Контекст, который хост передаёт плагину при вызове [VideoPlugin.attach].
 *
 * Задача — дать плагину сеть/логгер/кэш, но НЕ дать доступ к внутренним классам
 * приложения (PluginManager, Room, Retrofit, Koin).
 */
@Keep
interface PluginContext {
    val http: PluginHttp
    val logger: PluginLogger
    val cache: PluginCache
    val appVersion: String
    val isTvDevice: Boolean
}

@Keep
interface PluginHttp {
    /**
     * Выполняет GET и возвращает тело ответа. Бросает IOException при ошибке.
     */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String

    suspend fun post(
        url: String,
        body: String,
        contentType: String = "application/x-www-form-urlencoded",
        headers: Map<String, String> = emptyMap()
    ): String

    /** Скачивание бинарных данных (субтитры, изображения). */
    suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray
}

@Keep
interface PluginLogger {
    fun d(message: String)
    fun e(message: String, throwable: Throwable? = null)
    fun w(message: String)
}

@Keep
interface PluginCache {
    /** Возвращает значение или null, если ключа нет/истёк TTL. */
    fun get(key: String): String?

    /** Кладёт значение с TTL в миллисекундах. ttlMs <= 0 — навсегда. */
    fun put(key: String, value: String, ttlMs: Long = 0L)

    fun remove(key: String)
    fun clear()
}