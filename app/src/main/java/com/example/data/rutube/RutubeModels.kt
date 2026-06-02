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
    @Json(name = "description") val description: String? = null,
    @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @Json(name = "duration") val duration: Int? = null,
    @Json(name = "views") val views: Int? = null,
    @Json(name = "hits") val hits: Int? = null,
    @Json(name = "created_ts") val createdTs: String? = null,
    @Json(name = "author") val author: RutubeAuthor? = null,
    @Json(name = "content_type") val contentType: RutubeContentType? = null,
    @Json(name = "object") val `object`: Any? = null
)

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