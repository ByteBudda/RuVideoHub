package com.example.manager

import com.example.data.SavedVideo
import com.example.data.Video
import com.example.data.VideoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryManager(
    private val repository: VideoRepository,
    private val scope: CoroutineScope
) {
    val allSavedVideos: Flow<List<SavedVideo>> = repository.getSavedVideosOnly()

    val downloadedVideos: StateFlow<List<SavedVideo>> = repository.getSavedVideosOnly()
        .map { list -> list.filter { it.isDownloaded } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarkedVideos: StateFlow<List<SavedVideo>> = repository.getSavedVideosOnly()
        .map { list -> list.filter { it.isBookmarked } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentVideos: StateFlow<List<SavedVideo>> = repository.getSavedVideosOnly()
        .map { list ->
            list.filter { saved ->
                (saved.isWatched || saved.lastProgress > 0) &&
                saved.duration != com.example.utils.VideoType.CHANNEL &&
                saved.duration != "КАНАЛ" &&
                !saved.id.startsWith("channel_") &&
                saved.duration != com.example.utils.VideoType.FOLDER &&
                saved.duration != "ПАПКА" &&
                saved.duration != com.example.utils.VideoType.CATALOG &&
                saved.duration != "КАТАЛОГ"
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleBookmark(video: Video) {
        scope.launch {
            repository.toggleBookmark(video)
        }
    }

    fun toggleDownloadStateInDb(video: Video) {
        scope.launch {
            repository.toggleDownload(video)
        }
    }

    fun addToRecentHistory(video: Video, page: Int = 1) {
        if (video.duration == com.example.utils.VideoType.CHANNEL ||
            video.duration == "КАНАЛ" ||
            video.id.startsWith("channel_") ||
            video.duration == com.example.utils.VideoType.FOLDER ||
            video.duration == "ПАПКА" ||
            video.duration == com.example.utils.VideoType.CATALOG ||
            video.duration == "КАТАЛОГ"
        ) {
            return
        }
        scope.launch(Dispatchers.IO) {
            val existing = repository.getVideoById(video.id)
            if (video.duration == com.example.utils.VideoType.PLAYLIST ||
                video.duration == "ПЛЕЙЛИСТ" ||
                video.duration == com.example.utils.VideoType.SERIES ||
                video.duration == "СЕРИАЛ"
            ) {
                val hasProgress = (existing != null && (existing.isWatched || existing.lastProgress > 0L)) ||
                        video.isWatched || video.lastProgress > 0L
                if (!hasProgress) {
                    return@launch
                }
            }
            val progressRatio = if ((existing?.lastDuration ?: 0L) > 0L) {
                (existing?.lastProgress ?: 0L).toFloat() / (existing?.lastDuration ?: 1L).toFloat()
            } else if (video.lastDuration > 0L) {
                video.lastProgress.toFloat() / video.lastDuration.toFloat()
            } else 0f
            val isWatchedValue = (existing?.isWatched == true) || video.isWatched || (progressRatio >= 0.85f)

            val toSave = SavedVideo(
                id = video.id,
                title = video.title,
                channel = video.channel,
                views = video.views,
                timeAgo = video.timeAgo,
                duration = video.duration,
                isPro = video.isPro,
                category = video.category,
                thumbnailUrl = video.thumbnailUrl,
                isDownloaded = existing?.isDownloaded ?: false,
                isBookmarked = existing?.isBookmarked ?: false,
                savedAt = System.currentTimeMillis(),
                isWatched = isWatchedValue,
                lastProgress = existing?.lastProgress ?: 0L,
                lastDuration = existing?.lastDuration ?: 0L,
                originType = video.originType ?: existing?.originType,
                originId = video.originId ?: existing?.originId,
                originTitle = video.originTitle ?: existing?.originTitle,
                description = video.description,
                pageUrl = video.pageUrl,
                page = page,
                authorId = video.authorId ?: existing?.authorId,
                authorAvatarUrl = video.authorAvatarUrl ?: existing?.authorAvatarUrl
            )
            repository.insertOrUpdate(toSave)
        }
    }

    fun deleteRecentItem(video: Video, onDeleteFile: (String) -> Unit) {
        scope.launch {
            val existing = repository.getVideoById(video.id)
            if (existing != null) {
                if (existing.isDownloaded || existing.isBookmarked) {
                    val updated = existing.copy(isWatched = false)
                    repository.insertOrUpdate(updated)
                } else {
                    onDeleteFile(video.id)
                    repository.deleteVideoById(video.id)
                }
            }
        }
    }

    fun exportBookmarksToJson(): String {
        val bookmarks = bookmarkedVideos.value
        val jsonArray = org.json.JSONArray()
        for (video in bookmarks) {
            val obj = org.json.JSONObject()
            obj.put("id", video.id)
            obj.put("title", video.title)
            obj.put("channel", video.channel)
            obj.put("views", video.views)
            obj.put("timeAgo", video.timeAgo)
            obj.put("duration", video.duration)
            obj.put("isPro", video.isPro)
            obj.put("category", video.category)
            obj.put("thumbnailUrl", video.thumbnailUrl ?: "")
            obj.put("savedAt", video.savedAt)
            jsonArray.put(obj)
        }
        return jsonArray.toString(4)
    }

    suspend fun importBookmarksFromJson(jsonStr: String): Result<Int> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val trimmed = jsonStr.trim()
            if (trimmed.isBlank()) return@withContext Result.failure(Exception("Пустая строка"))
            val jsonArray = org.json.JSONArray(trimmed)
            var count = 0
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id") ?: continue
                if (id.isBlank()) continue
                
                val existing = repository.getVideoById(id)
                val imported = SavedVideo(
                    id = id,
                    title = obj.optString("title", "Без названия"),
                    channel = obj.optString("channel", "Rutube"),
                    views = obj.optString("views", ""),
                    timeAgo = obj.optString("timeAgo", ""),
                    duration = obj.optString("duration", "00:00"),
                    isPro = obj.optBoolean("isPro", false),
                    category = obj.optString("category", "Разное"),
                    thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                    isBookmarked = true,
                    isDownloaded = existing?.isDownloaded ?: false,
                    isWatched = existing?.isWatched ?: false,
                    savedAt = obj.optLong("savedAt", System.currentTimeMillis()),
                    originType = existing?.originType,
                    originId = existing?.originId,
                    originTitle = existing?.originTitle,
                    description = existing?.description,
                    pageUrl = existing?.pageUrl,
                    page = existing?.page ?: 1
                )
                repository.insertOrUpdate(imported)
                count++
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
