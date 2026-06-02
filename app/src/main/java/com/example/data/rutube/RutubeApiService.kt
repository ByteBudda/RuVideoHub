package com.example.data.rutube

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface RutubeApiService {
    // Поиск видео с пагинацией
    @GET("api/search/video/")
    suspend fun searchVideos(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("format") format: String = "json"
    ): ResponseBody

    // Популярные видео с пагинацией
    @GET("api/feeds/popular/")
    suspend fun getPopularVideos(
        @Query("page") page: Int = 1,
        @Query("format") format: String = "json"
    ): ResponseBody

    // Универсальный метод для любого URL (в том числе для next)
    @GET
    suspend fun getDynamicUrl(
        @Url url: String
    ): ResponseBody
}