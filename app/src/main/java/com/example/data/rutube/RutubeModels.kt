package com.example.data.rutube

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RutubeSearchResponse(
    @Json(name = "results") val results: List<RutubeVideoItem>? = null,
    @Json(name = "next") val next: String? = null
)

@JsonClass(generateAdapter = true)
data class RutubeVideoItem(
    @Json(name = "id") val id: String? = null,
    @Json(name = "video_id") val videoId: String? = null,
    @Json(name = "code") val code: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "original_title") val originalTitle: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @Json(name = "picture") val picture: String? = null,
    @Json(name = "poster_url") val posterUrl: String? = null,
    @Json(name = "user_channel_image") val userChannelImage: String? = null,
    @Json(name = "duration") val duration: Int? = null,
    @Json(name = "views") val views: Int? = null,
    @Json(name = "hits") val hits: Int? = null,
    @Json(name = "views_count") val viewsCount: Int? = null,
    @Json(name = "created_ts") val createdTs: String? = null,
    @Json(name = "publication_ts") val publicationTs: String? = null,
    @Json(name = "author") val author: RutubeAuthor? = null,
    @Json(name = "author_name") val authorName: String? = null,
    @Json(name = "feed_name") val feed_name: String? = null,
    @Json(name = "content_type") val contentType: RutubeContentType? = null,
    
    // Переименовали переменную для удобства Kotlin конвенций, но мапим на "object"
    @Json(name = "object") val nestedObject: RutubeVideoItem? = null
) {
    /**
     * Разворачивает вложенную структуру Rutube API в плоский вид.
     * Если данные лежат внутри "object", подтягивает их оттуда.
     */
    fun getFlatItem(): RutubeFlatData {
        val isNested = contentType != null && nestedObject != null
        val target = if (isNested) nestedObject!! else this

        val finalId = target.id?.takeIf { it.isNotBlank() }
            ?: target.videoId?.takeIf { it.isNotBlank() }
            ?: target.code

        return RutubeFlatData(
            id = finalId,
            title = target.title?.takeIf { it.isNotBlank() } ?: target.name ?: target.originalTitle,
            description = target.description,
            thumbnailUrl = target.thumbnailUrl?.takeIf { it.isNotBlank() } 
                ?: target.picture?.takeIf { it.isNotBlank() } 
                ?: target.posterUrl?.takeIf { it.isNotBlank() } 
                ?: target.userChannelImage,
            duration = target.duration,
            views = target.views ?: target.hits ?: target.viewsCount,
            createdTs = target.createdTs ?: target.publicationTs,
            authorName = target.author?.name?.takeIf { it.isNotBlank() }
                ?: target.authorName?.takeIf { it.isNotBlank() }
                ?: target.feed_name?.takeIf { it.isNotBlank() }
                ?: target.author?.username
        )
    }
}

@JsonClass(generateAdapter = true)
data class RutubeContentType(
    @Json(name = "model") val model: String? = null
)

@JsonClass(generateAdapter = true)
data class RutubeAuthor(
    @Json(name = "name") val name: String? = null,
    @Json(name = "username") val username: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null
)

// Вспомогательный POJO-класс для репозитория
data class RutubeFlatData(
    val id: String?,
    val title: String?,
    val description: String?,
    val thumbnailUrl: String?,
    val duration: Int?,
    val views: Int?,
    val createdTs: String?,
    val authorName: String?
)
