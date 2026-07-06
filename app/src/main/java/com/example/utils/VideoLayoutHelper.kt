package com.example.utils

import com.example.data.Video

object VideoLayoutHelper {
    fun groupCatalogItems(currentVideos: List<Video>): Map<String, List<Video>> {
        if (currentVideos.firstOrNull()?.duration == VideoType.CATALOG) {
            return currentVideos.groupBy { it.channel }
        } else {
            return emptyMap()
        }
    }

    fun groupFolderItems(currentVideos: List<Video>): List<List<Video>> {
        val list = mutableListOf<List<Video>>()
        val currentPair = mutableListOf<Video>()
        for (video in currentVideos) {
            val hasPreview = !video.thumbnailUrl.isNullOrBlank()
            if (hasPreview) {
                if (currentPair.isNotEmpty()) {
                    list.add(currentPair.toList())
                    currentPair.clear()
                }
                list.add(listOf(video))
            } else {
                currentPair.add(video)
                if (currentPair.size == 2) {
                    list.add(currentPair.toList())
                    currentPair.clear()
                }
            }
        }
        if (currentPair.isNotEmpty()) {
            list.add(currentPair.toList())
        }
        return list
    }
}
