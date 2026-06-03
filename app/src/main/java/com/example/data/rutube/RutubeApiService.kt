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
