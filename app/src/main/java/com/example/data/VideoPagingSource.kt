package com.example.data

import androidx.paging.PagingSource
import androidx.paging.PagingState

class VideoPagingSource(
    private val repository: VideoRepository,
    private val query: String?,
    private val category: String?
) : PagingSource<Int, Video>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Video> {
        return try {
            val currentPage = params.key ?: 1
            val videos = repository.fetchRealVideos(query, category, currentPage)
            
            LoadResult.Page(
                data = videos,
                prevKey = if (currentPage == 1) null else currentPage - 1,
                nextKey = if (videos.isEmpty()) null else currentPage + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Video>): Int? {
        return state.anchorPosition?.let { position ->
            state.closestPageToPosition(position)?.nextKey?.minus(1)
        }
    }
}
