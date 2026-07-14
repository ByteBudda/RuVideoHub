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
    val authorId: String? = null,
    val authorActionUrl: String? = null,
    val authorAvatarUrl: String? = null,
    val pageUrl: String? = null
) : Serializable
