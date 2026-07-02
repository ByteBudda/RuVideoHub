package com.example.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.data.Video
import com.example.data.VideoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

class DownloadAndBookmarkHelper(
    private val application: Application,
    private val repository: VideoRepository,
    private val activeDownloads: MutableStateFlow<Map<String, YtDlpDownload>>
) {

    fun toggleBookmark(
        video: Video,
        currentSelectedVideo: MutableStateFlow<Video?>,
        coroutineScope: CoroutineScope
    ) {
        coroutineScope.launch {
            repository.toggleBookmark(video)
            // Keep active player in-sync
            if (currentSelectedVideo.value?.id == video.id) {
                currentSelectedVideo.value = currentSelectedVideo.value?.copy(isBookmarked = !video.isBookmarked)
            }
        }
    }

    fun toggleDownload(
        video: Video,
        currentSelectedVideo: MutableStateFlow<Video?>,
        coroutineScope: CoroutineScope
    ) {
        val downloadFolder = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = File(downloadFolder, "${video.id}.mp4")

        coroutineScope.launch {
            if (video.isDownloaded) {
                // Revert download status and clean disk
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                repository.toggleDownload(video)
                if (currentSelectedVideo.value?.id == video.id) {
                    currentSelectedVideo.value = currentSelectedVideo.value?.copy(isDownloaded = false)
                }
            } else {
                // Run live yt-dlp downloader coroutine
                startYtDlpDownload(video, currentSelectedVideo)
            }
        }
    }

    fun deleteDownload(
        video: Video,
        currentSelectedVideo: MutableStateFlow<Video?>,
        coroutineScope: CoroutineScope
    ) {
        val downloadFolder = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = File(downloadFolder, "${video.id}.mp4")
        coroutineScope.launch {
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (video.isDownloaded) {
                repository.toggleDownload(video)
                if (currentSelectedVideo.value?.id == video.id) {
                    currentSelectedVideo.value = currentSelectedVideo.value?.copy(isDownloaded = false)
                }
            }
        }
    }

    fun saveToDevice(video: Video, context: Context, onResult: (Boolean, String) -> Unit) {
        val inputFolder = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val inputFile = File(inputFolder, "${video.id}.mp4")
        if (!inputFile.exists()) {
            onResult(false, "Сначала скачайте видео в приложение.")
            return
        }

        try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "${video.title}.mp4".replace("[\\\\/:*?\"<>|]".toRegex(), "_"))
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }

            var uri: android.net.Uri? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            }

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    inputFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                onResult(true, "Файл успешно сохранен в папку 'Загрузки' устройства!")
            } else {
                // Fallback for older Android versions
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!publicDownloads.exists()) {
                    publicDownloads.mkdirs()
                }
                val outputFile = File(publicDownloads, "${video.title}.mp4".replace("[\\\\/:*?\"<>|]".toRegex(), "_"))
                inputFile.inputStream().use { inputStream ->
                    outputFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                onResult(true, "Файл успешно сохранен в папку 'Загрузки': ${outputFile.name}")
            }
        } catch (e: Exception) {
            Log.e("DownloadBookmarkHelper", "Error saving file to public downloads", e)
            onResult(false, "Ошибка сохранения: ${e.localizedMessage ?: e.message}")
        }
    }

    private fun startYtDlpDownload(video: Video, currentSelectedVideo: MutableStateFlow<Video?>) {
        val scope = CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
        scope.launch {
            YtDlpDownloader.startYtDlpDownload(
                application,
                video,
                repository,
                activeDownloads
            ) { completedId ->
                if (currentSelectedVideo.value?.id == completedId) {
                    currentSelectedVideo.value = currentSelectedVideo.value?.copy(isDownloaded = true)
                }
            }
        }
    }
}
