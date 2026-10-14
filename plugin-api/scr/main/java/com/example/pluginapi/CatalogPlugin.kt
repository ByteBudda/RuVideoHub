package com.example.pluginapi

import androidx.annotation.Keep

/**
 * Расширенный интерфейс для плагинов с каталогом (фильмы, сериалы, аниме).
 * Не все плагины обязаны его реализовывать — VK/Dzen остаются простыми [VideoPlugin].
 */
@Keep
interface CatalogPlugin : VideoPlugin {
    /** Заголовок вкладки в Explore. По умолчанию "Каталог $name". */
    val catalogTitle: String get() = "Каталог $name"

    suspend fun getCatalogCategories(): List<CatalogCategory>
    suspend fun getCatalogItems(category: String, page: Int = 1): List<VideoItem>

    /**
     * Для сериалов: список серий по URL сериала.
     * Плагины фильмов возвращают null.
     */
    suspend fun getEpisodes(seriesUrl: String): List<VideoItem>? = null
}