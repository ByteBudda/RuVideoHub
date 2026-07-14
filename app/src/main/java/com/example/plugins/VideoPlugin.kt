package com.example.plugins

/**
 * Базовый интерфейс для всех видео-плагинов
 */
interface VideoPlugin {
    val name: String
    val icon: String
    val baseUrl: String
    
    /**
     * Поиск видео
     * @param query поисковый запрос
     * @param limit максимальное количество результатов
     * @return список найденных видео
     */
    suspend fun search(query: String, limit: Int): List<VideoItem>
    
    /**
     * Получение потока для воспроизведения
     * @param url URL видео
     * @param audioOnly только аудио поток
     * @return информация о потоке
     */
    suspend fun resolveStream(url: String, audioOnly: Boolean): StreamInfo?
    
    /**
     * Получение информации о видео
     * @param url URL видео
     * @return информация о видео
     */
    suspend fun getVideoInfo(url: String): VideoItem?
    
    /**
     * Проверка поддержки URL
     * @param url URL для проверки
     * @return true если плагин поддерживает этот URL
     */
    fun isSupported(url: String): Boolean
}

/**
 * Модель видео
 */
data class VideoItem(
    val id: String,
    val title: String,
    val author: String = "",
    val thumbnail: String = "",
    val url: String,
    val duration: Long = 0L, // миллисекунды
    val views: String = "",
    val source: String = "",
    val width: Int = 0,
    val height: Int = 0
)

/**
 * Информация о потоке
 */
data class StreamInfo(
    val streamUrl: String,
    val mimeType: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val loadedAt: Long = System.currentTimeMillis()
)
