package com.example.data.rutube

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface RutubeApiService {
    @GET("api/search/video/")
    suspend fun searchVideos(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("format") format: String = "json",
        @Query("content_type") contentType: String? = null
    ): ResponseBody

    @GET("api/search/combined/video_playlist/")
    suspend fun searchCombined(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("format") format: String = "json"
    ): ResponseBody

    @GET("api/playlist/user/{channel_id}/")
    suspend fun getChannelPlaylists(
        @retrofit2.http.Path("channel_id") channelId: String,
        @Query("page") page: Int = 1,
        @Query("format") format: String = "json"
    ): ResponseBody

    @GET("api/playlist/custom/{playlist_id}/videos/")
    suspend fun getPlaylistVideos(
        @retrofit2.http.Path("playlist_id") playlistId: String,
        @Query("page") page: Int = 1,
        @Query("format") format: String = "json"
    ): ResponseBody

    @GET("api/playlist/custom/{playlist_id}/")
    suspend fun getPlaylistInfo(
        @retrofit2.http.Path("playlist_id") playlistId: String,
        @Query("format") format: String = "json"
    ): ResponseBody

    @GET("api/video/")
    suspend fun getPopularVideos(
        @Query("page") page: Int = 1,
        @Query("format") format: String = "json"
    ): ResponseBody

    @GET
    suspend fun getDynamicUrl(
        @Url url: String
    ): ResponseBody
}
