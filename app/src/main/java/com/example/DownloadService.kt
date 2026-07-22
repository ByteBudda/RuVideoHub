package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import com.example.data.AppDatabase
import com.example.data.Video
import com.example.data.VideoRepository
import com.example.manager.DownloadManager
import com.example.viewmodel.YtDlpDownload
import com.example.viewmodel.YtDlpDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.io.File
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.CopyOnWriteArrayList

class DownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val downloadSemaphore = Semaphore(1)

    private val repository: VideoRepository by inject()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_STICKY

        when (action) {
            ACTION_START_DOWNLOAD -> {
                val video = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getSerializableExtra("extra_video", Video::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getSerializableExtra("extra_video") as? Video
                }
                val quality = intent.getStringExtra("extra_quality") ?: "720p"

                if (video != null) {
                    startBackgroundDownload(video, quality)
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val videoId = intent.getStringExtra("extra_video_id")
                if (videoId != null) {
                    cancelBackgroundDownload(videoId)
                }
            }
            ACTION_RESUME_ALL -> {
                resumeAllPendingDownloads()
            }
        }

        return START_STICKY
    }

    private fun startBackgroundDownload(video: Video, quality: String) {
        if (DownloadManager.downloadJobs.containsKey(video.id)) return

        // Display initial notification so the service is legally "foreground"
        startForeground(NOTIFICATION_ID, getNotification("Инициализация...", video.title, 0))

        val job = serviceScope.launch {
            downloadSemaphore.withPermit {
                try {
                    if (video.id.startsWith("plugin_")) {
                        downloadPluginVideoDirect(video) { completedId ->
                            val queue = DownloadManager.loadQueue(this@DownloadService).toMutableList()
                            queue.removeAll { it.first.id == completedId }
                            DownloadManager.saveQueue(this@DownloadService, queue)
                            serviceScope.launch { DownloadManager.emitDownloadCompleted(completedId) }
                            showResultNotification(video.title, true)
                        }
                    } else if (video.id.startsWith("vk_")) {
                        downloadVkVideoDirect(video) { completedId ->
                            // Removed from queue upon completion
                            val queue = DownloadManager.loadQueue(this@DownloadService).toMutableList()
                            queue.removeAll { it.first.id == completedId }
                            DownloadManager.saveQueue(this@DownloadService, queue)

                            serviceScope.launch { DownloadManager.emitDownloadCompleted(completedId) }
                            showResultNotification(video.title, true)
                        }
                    } else {
                        YtDlpDownloader.startYtDlpDownload(
                            application,
                            video,
                            repository,
                            DownloadManager._activeDownloads,
                            quality
                        ) { completedId ->
                            // Removed from queue upon completion
                            val queue = DownloadManager.loadQueue(this@DownloadService).toMutableList()
                            queue.removeAll { it.first.id == completedId }
                            DownloadManager.saveQueue(this@DownloadService, queue)

                            serviceScope.launch { DownloadManager.emitDownloadCompleted(completedId) }
                            showResultNotification(video.title, true)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Download failed for ${video.id}", e)
                    showResultNotification(video.title, false)
                } finally {
                    DownloadManager.downloadJobs.remove(video.id)
                    DownloadManager.removeActiveDownload(video.id)
                    
                    // If there are no more active downloads, stop the foreground service
                    if (DownloadManager.downloadJobs.isEmpty()) {
                        @Suppress("DEPRECATION")
                    stopForeground(true)
                        stopSelf()
                    } else {
                        updateNotification()
                    }
                }
            }
        }

        DownloadManager.downloadJobs[video.id] = job

        // Set up periodic notification updates for this download progress
        serviceScope.launch {
            while (DownloadManager.downloadJobs.containsKey(video.id)) {
                delay(1000)
                updateNotification()
            }
        }
    }

    private fun cancelBackgroundDownload(videoId: String) {
        DownloadManager.downloadJobs[videoId]?.cancel()
        DownloadManager.downloadJobs.remove(videoId)
        DownloadManager.removeActiveDownload(videoId)

        val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = File(downloadFolder, "$videoId.mp4")
        if (targetFile.exists()) {
            targetFile.delete()
        }
        val segmentsDir = File(downloadFolder, ".segments_$videoId")
        if (segmentsDir.exists()) {
            segmentsDir.deleteRecursively()
        }

        if (DownloadManager.downloadJobs.isEmpty()) {
            @Suppress("DEPRECATION")
                stopForeground(true)
            stopSelf()
        } else {
            updateNotification()
        }
    }

    private fun resumeAllPendingDownloads() {
        val queue = DownloadManager.loadQueue(this)
        for (item in queue) {
            startBackgroundDownload(item.first, item.second)
        }
    }

    private suspend fun downloadPluginVideoDirect(video: Video, onCompleted: (String) -> Unit) {
        val id = video.id
        val logs = CopyOnWriteArrayList<String>().apply {
            add("[Плагин Загрузчик] Начат прямой сбор потока...")
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
            DownloadManager.updateActiveDownload(id, currentDl)
        }

        updateDl(0f, "0 B/s", "--:--", "Downloading")
        logs.add("[Плагин Загрузчик] Разрешение URL трансляции для $id...")
        
        val pageUrl = video.pageUrl ?: ""
        if (pageUrl.isBlank()) {
            logs.add("[Плагин Загрузчик] Ошибка: ссылка на страницу не найдена в видео!")
            updateDl(0f, "0 B/s", "--:--", "Error")
            delay(5000)
            DownloadManager.removeActiveDownload(id)
            return
        }

        val streamInfo = com.example.plugins.PluginManager.resolveStream(pageUrl, audioOnly = false)
        val streamUrl = streamInfo?.streamUrl ?: ""

        if (streamUrl.isBlank()) {
            logs.add("[Плагин Загрузчик] Ошибка: ссылка на поток не найдена!")
            updateDl(0f, "0 B/s", "--:--", "Error")
            delay(5000)
            DownloadManager.removeActiveDownload(id)
            return
        }

        logs.add("[Плагин Загрузчик] Начинается скачивание...")
        updateDl(0.01f, "0 B/s", "--:--", "Downloading")

        val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = File(downloadFolder, "$id.mp4")

        val success = com.example.data.vk.VkVideoLoader.downloadVideo(
            videoUrl = streamUrl,
            targetFile = targetFile,
            onProgress = { progress, speed, etaStr ->
                if ((progress * 100).toInt() % 10 == 0) {
                    logs.add("[Плагин Загрузчик] Скачано ${(progress * 100).toInt()}% ($speed) ETA: $etaStr")
                }
                updateDl(progress, speed, etaStr, "Downloading")
            }
        )

        if (success) {
            try {
                logs.add("[Плагин Загрузчик] Файл сохранен в ${targetFile.absolutePath}")
                updateDl(1f, "0 B/s", "00:00", "Finished")

                repository.toggleDownload(video)
                onCompleted(id)

                delay(3000)
                DownloadManager.removeActiveDownload(id)
            } catch (e: Exception) {
                logs.add("[Плагин Загрузчик] Ошибка сохранения файла: ${e.message}")
                updateDl(0f, "0 B/s", "--:--", "Error")
                delay(5000)
                DownloadManager.removeActiveDownload(id)
            }
        } else {
            logs.add("[Плагин Загрузчик] Ошибка скачивания видео!")
            updateDl(0f, "0 B/s", "--:--", "Error")
            delay(5000)
            DownloadManager.removeActiveDownload(id)
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
            DownloadManager.updateActiveDownload(id, currentDl)
        }

        updateDl(0f, "0 B/s", "--:--", "Downloading")

        logs.add("[VK Загрузчик] Разрешение URL трансляции для $id...")
        val vkId = id.substringAfter("vk_")
        val info = com.example.data.vk.VkVideoLoader.getVideoInfo("https://vk.com/video$vkId")
        val videoUrl = info?.videoUrl ?: ""

        if (videoUrl.isBlank()) {
            logs.add("[VK Загрузчик] Ошибка: ссылка на поток не найдена!")
            updateDl(0f, "0 B/s", "--:--", "Error")
            delay(5000)
            DownloadManager.removeActiveDownload(id)
            return
        }

        logs.add("[VK Загрузчик] Начинается скачивание...")
        updateDl(0.01f, "0 B/s", "--:--", "Downloading")

        val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
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
                DownloadManager.removeActiveDownload(id)
            } catch (e: Exception) {
                logs.add("[VK Загрузчик] Ошибка сохранения файла: ${e.message}")
                updateDl(0f, "0 B/s", "--:--", "Error")
                delay(5000)
                DownloadManager.removeActiveDownload(id)
            }
        } else {
            logs.add("[VK Загрузчик] Ошибка скачивания видео!")
            updateDl(0f, "0 B/s", "--:--", "Error")
            delay(5000)
            DownloadManager.removeActiveDownload(id)
        }
    }

    private fun updateNotification() {
        val active = DownloadManager.activeDownloads.value.values.firstOrNull { 
            it.status == "Downloading" || it.status == "Extracting" || it.status == "Merging" 
        }
        val title = active?.title ?: "Загрузка видео"
        val progress = active?.progress ?: 0f
        val percent = (progress * 100).toInt()
        val speed = active?.speed ?: "0 B/s"

        val contentText = when (active?.status) {
            "Extracting" -> "Разрешение медиаресурса..."
            "Merging" -> "Завершение загрузки, сборка медиаконтейнера..."
            else -> "Скачано $percent% ($speed)"
        }

        val notification = getNotification(title, contentText, percent)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Загрузки видео",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Отображает ход выполнения фонового скачивания видео"
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun showResultNotification(videoTitle: String, success: Boolean) {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = android.app.PendingIntent.getActivity(
                this, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }
            val title = if (success) "Загрузка завершена" else "Ошибка загрузки"
            val icon = if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error
            val notification = builder
                .setContentTitle(title)
                .setContentText(videoTitle)
                .setSmallIcon(icon)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(videoTitle.hashCode(), notification)
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "Failed to show result notification", e)
        }
    }

    private fun getNotification(title: String, content: String, progress: Int): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress <= 0 || progress >= 95)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
        const val TAG = "DownloadService"
        const val NOTIFICATION_ID = 90210
        const val CHANNEL_ID = "download_service_channel"

        const val ACTION_START_DOWNLOAD = "com.example.action.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.example.action.CANCEL_DOWNLOAD"
        const val ACTION_RESUME_ALL = "com.example.action.RESUME_ALL"
    }
}
