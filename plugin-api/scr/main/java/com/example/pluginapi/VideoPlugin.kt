package com.example.pluginapi

import androidx.annotation.Keep

/**
 * Базовый интерфейс видео-плагина.
 *
 * ВАЖНО: этот интерфейс — публичный контракт. Любое изменение сигнатуры ломает
 * совместимость с уже собранными внешними плагинами. Поэтому:
 *   1. Существующие методы не переименовываем и не удаляем.
 *   2. Новые методы добавляем с default-реализацией.
 *   3. При несовместимом изменении — поднимаем [PLUGIN_API_VERSION].
 */
@Keep
interface VideoPlugin {
    /** Отображаемое имя плагина. */
    val name: String

    /** Идентификатор иконки (например, "movie", "vk"). */
    val icon: String

    /** Базовый URL, по которому плагин узнаёт "свои" ссылки. */
    val baseUrl: String

    /** Альтернативные названия для поиска по источнику. */
    val aliases: List<String> get() = emptyList()

    /** Поддерживает ли плагин поиск. */
    val isSearchSupported: Boolean get() = true

    /**
     * Версия Plugin API, под которую собран плагин.
     * Хост отказывается загружать плагин, если его версия не входит в диапазон
     * [MIN_SUPPORTED_API_VERSION]..[PLUGIN_API_VERSION].
     */
    val apiVersion: Int get() = PLUGIN_API_VERSION

    /** Уникальный идентификатор плагина, например "com.example.plugins.liftw". */
    val id: String get() = name.lowercase().replace(" ", ".")

    /** Возвращает true, если плагин умеет работать с данной ссылкой. */
    fun isSupported(url: String): Boolean

    suspend fun search(query: String, limit: Int): List<VideoItem>
    suspend fun resolveStream(url: String, audioOnly: Boolean): StreamInfo?
    suspend fun getVideoInfo(url: String): VideoItem?

    /**
     * Вызывается хостом сразу после создания экземпляра.
     * Плагин получает доступ к логгеру, HTTP-клиенту и кэшу через [PluginContext].
     */
    fun attach(context: PluginContext) = Unit

    /** Вызывается хостом перед выгрузкой плагина. */
    fun detach() = Unit

    companion object {
        const val PLUGIN_API_VERSION: Int = 1
        const val MIN_SUPPORTED_API_VERSION: Int = 1
    }
}