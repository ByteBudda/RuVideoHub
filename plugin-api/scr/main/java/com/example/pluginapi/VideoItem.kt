package com.example.pluginapi

import androidx.annotation.Keep

@Keep
enum class ContentType {
    UNKNOWN,
    FILM,
    SERIES,
    EPISODE
}

/**
 * Модель видео или эпизода. Используется во всех ответах плагинов.
 */
@Keep
data class VideoItem(
    val id: String,
    val title: String,
    val author: String = "",
    val thumbnail: String = "",
    val url: String,
    val duration: Long = 0L,         // миллисекунды; 1L или 0L + isSeries зарезервировано под "сериал"
    val views: String = "",
    val source: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val description: String = "",
    val isSeries: Boolean = false,
    val contentType: ContentType = ContentType.UNKNOWN,
    val season: Int? = null,
    val episode: Int? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val quality: String = ""
)