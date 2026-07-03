package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.Video
import com.example.data.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File

class DownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DownloadWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val videoId = inputData.getString("videoId") ?: return@withContext Result.failure()
        val title = inputData.getString("title") ?: ""
        val channel = inputData.getString("channel") ?: ""
        val thumbnailUrl = inputData.getString("thumbnailUrl") ?: ""
        val category = inputData.getString("category") ?: ""
        val description = inputData.getString("description") ?: ""

        Log.d(TAG, "Starting background download for video: $videoId")

        val db = AppDatabase.getDatabase(applicationContext)
        val repository = VideoRepository(db.savedVideoDao())

        val video = Video(
            id = videoId,
            title = title,
            channel = channel,
            views = "",
            timeAgo = "",
            duration = "",
            category = category,
            description = description,
            thumbnailUrl = thumbnailUrl,
            isDownloaded = false,
            isBookmarked = false
        )

        val logs = java.util.concurrent.CopyOnWriteArrayList<String>().apply {
            add("[Фоновый Загрузчик] Начат фоновый сбор потока...")
        }

        fun updateDl(progress: Float, speed: String, status: String, completed: Boolean) {
            val currentDl = YtDlpDownload(
                id = videoId,
                title = video.title,
                channel = video.channel,
                thumbnailUrl = video.thumbnailUrl,
                progress = progress,
                speed = speed,
                eta = if (completed) "00:00" else "--:--",
                status = status,
                logs = logs.toList()
            )
            // Update shared tracker
            DownloadProgressTracker.updateDownload(videoId, currentDl)
        }

        updateDl(0.01f, "0 B/s", "Downloading", false)

        try {
            if (videoId.startsWith("vk_")) {
                // Download VK Video
                var videoUrl = ""
                val vkId = videoId.substringAfter("vk_")
                logs.add("[VK Загрузчик] Разрешение URL трансляции для $videoId...")
                
                try {
                    val info = com.example.data.vk.VkVideoLoader.getVideoInfo("https://vk.com/video$vkId")
                    if (info != null) {
                        videoUrl = info.videoUrl
                    }
                } catch (e: Exception) {
                    logs.add("[VK Загрузчик] Ошибка разрешения URL: ${e.message}")
                }

                if (videoUrl.isBlank()) {
                    logs.add("[VK Загрузчик] Ошибка: ссылка на поток не найдена!")
                    updateDl(0f, "0 B/s", "Error", false)
                    return@withContext Result.retry() // Retry on transient resolve failure
                }

                val downloadFolder = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val targetFile = File(downloadFolder, "$videoId.mp4")

                logs.add("[VK Загрузчик] Начинается скачивание...")
                updateDl(0.01f, "0 B/s", "Downloading", false)

                val success = com.example.data.vk.VkVideoLoader.downloadVideo(
                    videoUrl = videoUrl,
                    targetFile = targetFile,
                    onProgress = { progress, speed ->
                        if ((progress * 100).toInt() % 10 == 0) {
                            logs.add("[VK Загрузчик] Скачано ${(progress * 100).toInt()}% ($speed)")
                        }
                        updateDl(progress, speed, "Downloading", false)
                    }
                )

                if (success) {
                    logs.add("[VK Загрузчик] Файл сохранен в ${targetFile.absolutePath}")
                    updateDl(1f, "0 B/s", "Finished", true)
                    repository.toggleDownload(video)
                    
                    // Clear after some delay
                    kotlinx.coroutines.delay(2000)
                    DownloadProgressTracker.removeDownload(videoId)
                    return@withContext Result.success()
                } else {
                    logs.add("[VK Загрузчик] Ошибка скачивания видео!")
                    updateDl(0f, "0 B/s", "Error", false)
                    return@withContext Result.retry() // Retry on failure
                }
            } else {
                // Download Rutube Video
                // We construct a fake activeDownloads StateFlow to pass to YtDlpDownloader
                val activeDownloadsFlow = MutableStateFlow<Map<String, YtDlpDownload>>(emptyMap())
                
                kotlinx.coroutines.coroutineScope {
                    // Collect and mirror back to DownloadProgressTracker
                    val collectJob = launch {
                        activeDownloadsFlow.collect { map ->
                            map[videoId]?.let {
                                DownloadProgressTracker.updateDownload(videoId, it)
                            }
                        }
                    }

                    try {
                        YtDlpDownloader.startYtDlpDownload(
                            applicationContext as Application,
                            video,
                            repository,
                            activeDownloadsFlow,
                            "720p", // default quality
                            onDownloadComplete = {
                                // nothing special
                            }
                        )
                        
                        val finalDl = DownloadProgressTracker.activeDownloads.value[videoId]
                        if (finalDl?.status == "Completed" || finalDl?.status == "Finished") {
                            kotlinx.coroutines.delay(2000)
                            DownloadProgressTracker.removeDownload(videoId)
                            Result.success()
                        } else {
                            Result.retry()
                        }
                    } finally {
                        collectJob.cancel()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in background download", e)
            logs.add("[Фоновый Загрузчик] Ошибка: ${e.message}")
            updateDl(0f, "0 B/s", "Error", false)
            return@withContext Result.retry()
        }
    }
}
