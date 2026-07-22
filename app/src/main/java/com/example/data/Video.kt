package com.example.data

import java.io.Serializable

data class Video(
    val id: String,
    val title: String,
    val channel: String,
    val views: String,
    val timeAgo: String,
    val duration: String,
    val isPro: Boolean = false,
    val category: String, // "Новинки", "Топ недели", "Технологии", etc.
    val description: String,
    val thumbnailUrl: String? = null,
    val isDownloaded: Boolean = false,
    val isBookmarked: Boolean = false,
    val isWatched: Boolean = false,
    val playbackProgress: Float = 0f,
    val authorId: String? = null,
    val authorActionUrl: String? = null,
    val authorAvatarUrl: String? = null,
    val pageUrl: String? = null,
    val originType: String? = null,
    val originId: String? = null,
    val originTitle: String? = null,
    val lastProgress: Long = 0L,
    val lastDuration: Long = 0L
) : Serializable {
    fun getShareUrl(): String {
        if (!pageUrl.isNullOrBlank()) {
            return pageUrl
        }
        if (duration == "КАНАЛ") {
            if (!authorActionUrl.isNullOrBlank()) {
                return authorActionUrl
            }
        }
        if (id.startsWith("vk_")) {
            val parts = id.substringAfter("vk_").split("_")
            return if (parts.size >= 2) {
                "https://vkvideo.ru/video${parts[0]}_${parts[1]}"
            } else {
                "https://vkvideo.ru/video$id"
            }
        }
        if (id.startsWith("plugin_Дзен_")) {
            val dzenId = id.substringAfter("plugin_Дзен_")
            return "https://dzen.ru/video/watch/$dzenId"
        }
        if (id.startsWith("plugin_")) {
            val parts = id.split("_")
            if (parts.size >= 3) {
                val pluginName = parts[1]
                val pluginId = id.substringAfter("plugin_${pluginName}_")
                if (pluginName == "Дзен") {
                    return "https://dzen.ru/video/watch/$pluginId"
                } else if (pluginName == "VK_Video" || pluginName == "VKVideo" || pluginName == "VK Video" || pluginName == "VK") {
                    return "https://vkvideo.ru/video$pluginId"
                }
            }
        }
        return "https://rutube.ru/video/$id/"
    }
}
