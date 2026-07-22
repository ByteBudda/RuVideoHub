package com.example.manager

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String
)


sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    object Finished : DownloadState()
    data class Error(val message: String) : DownloadState()
}

object UpdateManager {
    val downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)

    private const val GITHUB_API_URL = "https://api.github.com/repos/ByteBudda/RuVideoHub/releases/latest"
    private const val TAG = "UpdateManager"

    suspend fun checkForUpdates(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) RuVideoHub")
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                
                var latestVersion = json.getString("tag_name")
                if (latestVersion.startsWith("v")) {
                    latestVersion = latestVersion.substring(1)
                }
                
                val releaseNotes = json.optString("body", "Нет описания обновления.")
                
                var downloadUrl = ""
                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }
                
                if (downloadUrl.isNotEmpty()) {
                    val isNewer = isVersionNewer(currentVersion, latestVersion)
                    
                    if (isNewer) {
                        return@withContext UpdateInfo(true, latestVersion, downloadUrl, releaseNotes)
                    } else {
                        return@withContext UpdateInfo(false, latestVersion, "", "")
                    }
                }
            } else {
                Log.e(TAG, "Failed to check for updates. Response code: ${connection.responseCode}")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error checking for updates", e)
        }
        null
    }

    private fun isVersionNewer(current: String, latest: String): Boolean {
        try {
            val currParts = current.replace(Regex("[^0-9.]"), "").split(".").map { it.toIntOrNull() ?: 0 }
            val latestParts = latest.replace(Regex("[^0-9.]"), "").split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(currParts.size, latestParts.size)
            for (i in 0 until maxLen) {
                val pCurr = currParts.getOrElse(i) { 0 }
                val pLatest = latestParts.getOrElse(i) { 0 }
                if (pLatest > pCurr) return true
                if (pLatest < pCurr) return false
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error comparing versions", e)
        }
        return false
    }

    private const val UPDATE_CHANNEL_ID = "update_channel"
    private const val UPDATE_NOTIFICATION_ID = 9991

    private fun showUpdateNotification(context: Context, title: String, content: String, progress: Int = -1, finishedFile: File? = null) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    UPDATE_CHANNEL_ID,
                    "Обновления приложения",
                    android.app.NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(channel)
            }

            val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.Notification.Builder(context, UPDATE_CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(context)
            }

            builder.setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.stat_sys_download)

            if (progress >= 0) {
                builder.setProgress(100, progress, false)
            } else if (progress == -2) {
                builder.setProgress(100, 0, true)
            }

            if (finishedFile != null) {
                try {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        finishedFile
                    )
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        context,
                        0,
                        intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.setContentIntent(pendingIntent)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setAutoCancel(true)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to create pending intent for update notification", e)
                }
            }

            notificationManager.notify(UPDATE_NOTIFICATION_ID, builder.build())
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to show update notification", e)
        }
    }

    fun startDownloadAndInstall(context: Context, urlString: String, version: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                downloadState.value = DownloadState.Downloading(0f)
                showUpdateNotification(context, "Обновление RuVideoHub v$version", "Подключение...", -2)

                var currentUrl = urlString
                var connection: HttpURLConnection? = null
                var redirectCount = 0
                while (redirectCount < 10) {
                    val url = URL(currentUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = false
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                    conn.connect()

                    val status = conn.responseCode
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                        status == HttpURLConnection.HTTP_MOVED_PERM ||
                        status == HttpURLConnection.HTTP_SEE_OTHER ||
                        status == 307 || status == 308
                    ) {
                        val newUrl = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (!newUrl.isNullOrEmpty()) {
                            currentUrl = newUrl
                            redirectCount++
                            continue
                        }
                    }
                    connection = conn
                    break
                }

                if (connection == null || connection.responseCode != HttpURLConnection.HTTP_OK) {
                    val errCode = connection?.responseCode ?: -1
                    downloadState.value = DownloadState.Error("HTTP error: $errCode")
                    showUpdateNotification(context, "Ошибка обновления", "Код ошибки: $errCode")
                    delay(3000)
                    downloadState.value = DownloadState.Idle
                    return@launch
                }

                val fileLength = connection.contentLength
                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir != null && !downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val apkFile = File(downloadsDir, "RuVideoHub_v$version.apk")
                if (apkFile.exists()) apkFile.delete()

                val input = connection.inputStream
                val output = java.io.FileOutputStream(apkFile)

                val data = ByteArray(8192)
                var total: Long = 0
                var count: Int
                var lastNotifyTime = 0L
                while (input.read(data).also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) {
                        val progressFloat = total.toFloat() / fileLength.toFloat()
                        downloadState.value = DownloadState.Downloading(progressFloat)
                        val now = System.currentTimeMillis()
                        if (now - lastNotifyTime > 800L) {
                            lastNotifyTime = now
                            val pct = (progressFloat * 100).toInt()
                            showUpdateNotification(context, "Загрузка обновления v$version", "$pct%", pct)
                        }
                    }
                    output.write(data, 0, count)
                }

                output.flush()
                output.close()
                input.close()

                // Validate APK signature (PK header) and file length
                var isValidApk = false
                if (apkFile.exists() && apkFile.length() > 100_000L) {
                    try {
                        java.io.FileInputStream(apkFile).use { fis ->
                            val b1 = fis.read()
                            val b2 = fis.read()
                            if (b1 == 0x50 && b2 == 0x4B) { // 'P' 'K'
                                isValidApk = true
                            }
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Error validating APK signature", e)
                    }
                }

                if (!isValidApk) {
                    if (apkFile.exists()) apkFile.delete()
                    downloadState.value = DownloadState.Error("Скачанный файл поврежден или не является APK")
                    showUpdateNotification(context, "Ошибка обновления", "Скачанный файл не является корректным APK пакетом")
                    delay(3000)
                    downloadState.value = DownloadState.Idle
                    return@launch
                }

                downloadState.value = DownloadState.Finished
                showUpdateNotification(context, "Обновление скачано", "Нажмите для установки v$version", 100, apkFile)
                
                // Trigger install
                installApk(context, apkFile)
                
                delay(2000)
                downloadState.value = DownloadState.Idle
                
            } catch (e: Throwable) {
                Log.e(TAG, "Download failed", e)
                downloadState.value = DownloadState.Error(e.message ?: "Download failed")
                showUpdateNotification(context, "Ошибка скачивания обновления", e.message ?: "Неизвестная ошибка")
                delay(3000)
                downloadState.value = DownloadState.Idle
            }
        }
    }

    private fun installApk(context: Context, file: File) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    android.widget.Toast.makeText(context, "Разрешите установку обновлений из неизвестных источников", android.widget.Toast.LENGTH_LONG).show()
                }
            }

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Throwable) {
            Log.e(TAG, "Install failed", e)
            android.widget.Toast.makeText(context, "Ошибка установки APK: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
