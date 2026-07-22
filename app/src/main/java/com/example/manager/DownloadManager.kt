package com.example.manager

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import com.example.data.Video
import com.example.data.VideoRepository
import com.example.DownloadService
import com.example.viewmodel.YtDlpDownload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DownloadManager(
    private val application: Application,
    private val repository: VideoRepository,
    private val scope: CoroutineScope
) {
    val activeDownloads = DownloadManager.activeDownloads
    val downloadCompleted = DownloadManager.downloadCompleted
    val savingProgress = DownloadManager.savingProgress

    fun startDownload(video: Video, quality: String, onCompleted: (String) -> Unit) {
        // Save to persistent queue
        val currentQueue = loadQueue(application).toMutableList()
        if (currentQueue.none { it.first.id == video.id }) {
            currentQueue.add(video to quality)
            saveQueue(application, currentQueue)
        }


        // Start background service
        val intent = Intent(application, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START_DOWNLOAD
            putExtra("extra_video", video)
            putExtra("extra_quality", quality)
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                application.startForegroundService(intent)
            } else {
                application.startService(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadManager", "Failed to start foreground service", e)
            application.startService(intent)
        }
    }

    fun cancelDownload(videoId: String) {
        // Remove from persistent queue
        val currentQueue = loadQueue(application).toMutableList()
        currentQueue.removeAll { it.first.id == videoId }
        saveQueue(application, currentQueue)

        // Stop in background service
        val intent = Intent(application, DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL_DOWNLOAD
            putExtra("extra_video_id", videoId)
        }
        application.startService(intent)
    }

    fun deleteDownload(video: Video) {
        val downloadFolder = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = File(downloadFolder, "${video.id}.mp4")
        if (targetFile.exists()) {
            targetFile.delete()
        }
    }

    fun saveToDevice(video: Video, context: Context, onResult: (Boolean, String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val downloadFolder = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val sourceFile = File(downloadFolder, "${video.id}.mp4")
                
                if (!sourceFile.exists()) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Файл не найден. Сначала скачайте видео.")
                    }
                    return@launch
                }

                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!publicDownloads.exists()) publicDownloads.mkdirs()
                
                val destFile = File(publicDownloads, "${video.title.filter { it.isLetterOrDigit() || it == ' ' }}.mp4")
                
                val totalBytes = sourceFile.length()
                var bytesCopied = 0L
                val buffer = ByteArray(8192)
                
                sourceFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            bytesCopied += bytesRead
                            val progress = bytesCopied.toFloat() / totalBytes
                            DownloadManager._savingProgress.value = DownloadManager._savingProgress.value.toMutableMap().apply {
                                put(video.id, progress)
                            }
                        }
                    }
                }
                DownloadManager._savingProgress.value = DownloadManager._savingProgress.value.toMutableMap().apply {
                    remove(video.id)
                }

                withContext(Dispatchers.Main) {
                    onResult(true, "Файл успешно сохранен в папку Загрузки: ${destFile.name}")
                }
            } catch (e: Exception) {
                DownloadManager._savingProgress.value = DownloadManager._savingProgress.value.toMutableMap().apply {
                    remove(video.id)
                }
                withContext(Dispatchers.Main) {
                    onResult(false, "Ошибка сохранения: ${e.message}")
                }
            }
        }
    }

    companion object {
        private val _downloadCompleted = MutableSharedFlow<String>()
        val downloadCompleted = _downloadCompleted.asSharedFlow()

        suspend fun emitDownloadCompleted(videoId: String) {
            _downloadCompleted.emit(videoId)
        }

        val _activeDownloads = MutableStateFlow<Map<String, YtDlpDownload>>(emptyMap())
        val activeDownloads = _activeDownloads.asStateFlow()
        val _savingProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
        val savingProgress = _savingProgress.asStateFlow()
        val downloadJobs = ConcurrentHashMap<String, Job>()

        fun updateActiveDownload(id: String, download: YtDlpDownload) {
            _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                this[id] = download
            }
        }

        fun removeActiveDownload(id: String) {
            _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                remove(id)
            }
        }

        @Volatile
        var onDownloadCompletedListener: ((String) -> Unit)? = null

        fun saveQueue(context: Context, queue: List<Pair<Video, String>>) {
            try {
                val array = JSONArray()
                for (item in queue) {
                    val obj = JSONObject()
                    obj.put("id", item.first.id)
                    obj.put("title", item.first.title)
                    obj.put("channel", item.first.channel)
                    obj.put("views", item.first.views)
                    obj.put("timeAgo", item.first.timeAgo)
                    obj.put("duration", item.first.duration)
                    obj.put("isPro", item.first.isPro)
                    obj.put("category", item.first.category)
                    obj.put("description", item.first.description)
                    obj.put("thumbnailUrl", item.first.thumbnailUrl ?: "")
                    obj.put("isDownloaded", item.first.isDownloaded)
                    obj.put("isBookmarked", item.first.isBookmarked)
                    obj.put("authorId", item.first.authorId ?: "")
                    obj.put("authorActionUrl", item.first.authorActionUrl ?: "")
                    obj.put("authorAvatarUrl", item.first.authorAvatarUrl ?: "")
                    obj.put("quality", item.second)
                    array.put(obj)
                }
                val prefs = context.getSharedPreferences("download_queue_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("download_queue", array.toString()).apply()
            } catch (e: Exception) {
                android.util.Log.e("DownloadManager", "Error saving queue", e)
            }
        }

        fun loadQueue(context: Context): List<Pair<Video, String>> {
            val list = mutableListOf<Pair<Video, String>>()
            try {
                val prefs = context.getSharedPreferences("download_queue_prefs", Context.MODE_PRIVATE)
                val jsonStr = prefs.getString("download_queue", null) ?: return list
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val video = Video(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        channel = obj.getString("channel"),
                        views = obj.getString("views"),
                        timeAgo = obj.getString("timeAgo"),
                        duration = obj.getString("duration"),
                        isPro = obj.optBoolean("isPro", false),
                        category = obj.getString("category"),
                        description = obj.getString("description"),
                        thumbnailUrl = obj.optString("thumbnailUrl", "").takeIf { it.isNotBlank() },
                        isDownloaded = obj.optBoolean("isDownloaded", false),
                        isBookmarked = obj.optBoolean("isBookmarked", false),
                        authorId = obj.optString("authorId", "").takeIf { it.isNotBlank() },
                        authorActionUrl = obj.optString("authorActionUrl", "").takeIf { it.isNotBlank() },
                        authorAvatarUrl = obj.optString("authorAvatarUrl", "").takeIf { it.isNotBlank() }
                    )
                    val quality = obj.optString("quality", "720p")
                    list.add(video to quality)
                }
            } catch (e: Exception) {
                android.util.Log.e("DownloadManager", "Error loading queue", e)
            }
            return list
        }

        fun resumeAll(context: Context) {
            val queue = loadQueue(context)
            if (queue.isNotEmpty()) {
                val intent = Intent(context, DownloadService::class.java).apply {
                    action = DownloadService.ACTION_RESUME_ALL
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DownloadManager", "Failed to resume foreground service", e)
                    context.startService(intent)
                }
            }
        }
    }
}
