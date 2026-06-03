package com.example.data

import androidx.paging.PagingSource
import androidx.paging.PagingState

class VideoPagingSource(
    private val repository: VideoRepository,
    private val query: String?,
    private val category: String?
) : PagingSource<String, Video>() {

    override suspend fun load(params: LoadParams<String>): LoadResult<String, Video> {
        return try {
            val (videos, nextKey) = repository.fetchVideosPage(query, category, params.key)
            LoadResult.Page(
                data = videos,
                prevKey = null,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<String, Video>): String? {
        return state.anchorPosition?.let { position ->
            state.closestPageToPosition(position)?.nextKey
        }
    }
}