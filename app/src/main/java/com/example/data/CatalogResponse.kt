package com.example.data

data class CatalogResponse(
    val videos: List<Video> = emptyList(),
    val channels: List<Video> = emptyList(),
    val playlists: List<Video> = emptyList(),
    val tvSeries: List<Video> = emptyList()
) {
    fun getAllPlayableOrFolderVideos(): List<Video> {
        return videos + playlists + tvSeries
    }
}

