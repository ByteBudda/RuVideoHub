package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_videos")
data class SavedVideo(
    @PrimaryKey val id: String,
    val title: String,
    val channel: String,
    val views: String,
    val timeAgo: String,
    val duration: String,
    val isPro: Boolean,
    val category: String,
    val isDownloaded: Boolean,
    val isBookmarked: Boolean,
    val thumbnailUrl: String? = null,
    val savedAt: Long = System.currentTimeMillis(),
    val isWatched: Boolean = true,
    val lastProgress: Long = 0L,
    val lastDuration: Long = 0L
)
