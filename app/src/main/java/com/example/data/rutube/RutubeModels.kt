package com.example.data.rutube

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RutubeSearchResponse(
    @field:Json(name = "results") val results: List<RutubeVideoItem>? = null
)

@JsonClass(generateAdapter = true)
data class RutubeVideoItem(
    @field:Json(name = "id") val id: String? = null,
    @field:Json(name = "video_id") val videoId: String? = null,
    @field:Json(name = "code") val code: String? = null,
    @field:Json(name = "title") val title: String? = null,
    @field:Json(name = "description") val description: String? = null,
    @field:Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @field:Json(name = "duration") val duration: Int? = null,
    @field:Json(name = "views") val views: Int? = null,
    @field:Json(name = "hits") val hits: Int? = null,
    @field:Json(name = "created_ts") val createdTs: String? = null,
    @field:Json(name = "author") val author: RutubeAuthor? = null
)

@JsonClass(generateAdapter = true)
data class RutubeAuthor(
    @field:Json(name = "name") val name: String? = null,
    @field:Json(name = "username") val username: String? = null,
    @field:Json(name = "avatar_url") val avatarUrl: String? = null
)
