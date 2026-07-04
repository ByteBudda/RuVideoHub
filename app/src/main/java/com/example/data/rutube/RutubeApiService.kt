// RutubeApiService.kt
package com.example.data.rutube

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface RutubeApiService {
    @GET("api/search/video/")
    suspend fun searchVideos(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("format") format: String = "json",
        @Query("content_type") contentType: String? = null
    ): String

    @GET("api/search/combined/cards/list/")
    suspend fun searchCombined(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("format") format: String = "json",
        @Query("fields") fields: String = "video_count"
    ): String

    @GET("api/playlist/user/{channel_id}/")
    suspend fun getChannelPlaylists(
        @Path("channel_id") channelId: String,
        @Query("page") page: Int = 1,
        @Query("format") format: String = "json"
    ): String

    @GET("api/playlist/custom/{playlist_id}/videos/")
    suspend fun getPlaylistVideos(
        @Path("playlist_id") playlistId: String,
        @Query("page") page: Int = 1,
        @Query("format") format: String = "json"
    ): String

    @GET("api/playlist/custom/{playlist_id}/")
    suspend fun getPlaylistInfo(
        @Path("playlist_id") playlistId: String,
        @Query("format") format: String = "json"
    ): String

    @GET("api/video/")
    suspend fun getPopularVideos(
        @Query("page") page: Int = 1,
        @Query("format") format: String = "json"
    ): String

    @GET
    suspend fun getDynamicUrl(
        @Url url: String
    ): String
}