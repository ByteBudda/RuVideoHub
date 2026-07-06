package com.example.manager

import android.app.Application
import android.os.Environment
import com.example.data.Video
import com.example.data.VideoRepository
import com.example.viewmodel.YtDlpDownload
import com.example.viewmodel.YtDlpDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class DownloadManager(
    private val application: Application,
    private val repository: VideoRepository,
    private val scope: CoroutineScope
) {
    private val _activeDownloads = MutableStateFlow<Map<String, YtDlpDownload>>(emptyMap())
    val activeDownloads = _activeDownloads.asStateFlow()

    private val downloadJobs = ConcurrentHashMap<String, Job>()

    fun startDownload(video: Video, quality: String, onCompleted: (String) -> Unit) {
        val job = scope.launch {
            try {
                if (video.id.startsWith("vk_")) {
                    downloadVkVideoDirect(video, onCompleted)
                } else {
                    YtDlpDownloader.startYtDlpDownload(
                        application,
                        video,
                        repository,
                        _activeDownloads,
                        quality
                    ) { completedId ->
                        onCompleted(completedId)
                    }
                }
            } finally {
                downloadJobs.remove(video.id)
                withContext(NonCancellable) {
                    _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { remove(video.id) }
                }
            }
        }
        downloadJobs[video.id] = job
    }

    fun cancelDownload(videoId: String) {
        downloadJobs[videoId]?.cancel()
        downloadJobs.remove(videoId)

        _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
            remove(videoId)
        }

        val downloadFolder = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = File(downloadFolder, "$videoId.mp4")
        if (targetFile.exists()) {
            targetFile.delete()
        }
    }

    fun deleteDownload(video: Video) {
        val downloadFolder = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = File(downloadFolder, "${video.id}.mp4")
        if (targetFile.exists()) {
            targetFile.delete()
        }
    }

    fun saveToDevice(video: Video, context: android.content.Context, onResult: (Boolean, String) -> Unit) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val downloadFolder = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val sourceFile = File(downloadFolder, "${video.id}.mp4")
                
                if (!sourceFile.exists()) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onResult(false, "Файл не найден. Сначала скачайте видео.")
                    }
                    return@launch
                }

                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!publicDownloads.exists()) publicDownloads.mkdirs()
                
                val destFile = File(publicDownloads, "${video.title.filter { it.isLetterOrDigit() || it == ' ' }}.mp4")
                
                sourceFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(true, "Файл успешно сохранен в папку Загрузки: ${destFile.name}")
                }
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false, "Ошибка сохранения: ${e.message}")
                }
            }
        }
    }

    private suspend fun downloadVkVideoDirect(video: Video, onCompleted: (String) -> Unit) {
        val id = video.id
        val logs = CopyOnWriteArrayList<String>().apply {
            add("[VK Загрузчик] Начат прямой сбор потока VK...")
        }

        fun updateDl(progress: Float, speed: String, etaStr: String, status: String) {
            val currentDl = YtDlpDownload(
                id = id,
                title = video.title,
                channel = video.channel,
                thumbnailUrl = video.thumbnailUrl,
                progress = progress,
                speed = speed,
                eta = etaStr,
                status = status,
                logs = logs.toList()
            )
            _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                this[id] = currentDl
            }
        }

        updateDl(0f, "0 B/s", "--:--", "Downloading")

        // We'll need access to masterUrlCache if we want to reuse it, or just re-fetch.
        // For now, let's re-fetch as this manager is supposed to be somewhat independent.
        logs.add("[VK Загрузчик] Разрешение URL трансляции для $id...")
        val vkId = id.substringAfter("vk_")
        val info = com.example.data.vk.VkVideoLoader.getVideoInfo("https://vk.com/video$vkId")
        val videoUrl = info?.videoUrl ?: ""

        if (videoUrl.isBlank()) {
            logs.add("[VK Загрузчик] Ошибка: ссылка на поток не найдена!")
            updateDl(0f, "0 B/s", "--:--", "Error")
            delay(5000)
            _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { remove(id) }
            return
        }

        logs.add("[VK Загрузчик] Начинается скачивание...")
        updateDl(0.01f, "0 B/s", "--:--", "Downloading")

        val downloadFolder = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = File(downloadFolder, "$id.mp4")

        val success = com.example.data.vk.VkVideoLoader.downloadVideo(
            videoUrl = videoUrl,
            targetFile = targetFile,
            onProgress = { progress, speed, etaStr ->
                if ((progress * 100).toInt() % 10 == 0) {
                    logs.add("[VK Загрузчик] Скачано ${(progress * 100).toInt()}% ($speed) ETA: $etaStr")
                }
                updateDl(progress, speed, etaStr, "Downloading")
            }
        )

        if (success) {
            try {
                logs.add("[VK Загрузчик] Файл сохранен в ${targetFile.absolutePath}")
                updateDl(1f, "0 B/s", "00:00", "Finished")

                repository.toggleDownload(video)
                onCompleted(id)

                delay(3000)
                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { remove(id) }
            } catch (e: Exception) {
                logs.add("[VK Загрузчик] Ошибка сохранения файла: ${e.message}")
                updateDl(0f, "0 B/s", "--:--", "Error")
                delay(5000)
                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { remove(id) }
            }
        } else {
            logs.add("[VK Загрузчик] Ошибка скачивания видео!")
            updateDl(0f, "0 B/s", "--:--", "Error")
            delay(5000)
            _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { remove(id) }
        }
    }
}
