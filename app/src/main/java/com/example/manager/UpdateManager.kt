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
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            
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
        } catch (e: Exception) {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions", e)
        }
        return false
    }

    fun startDownloadAndInstall(context: Context, url: String, version: String) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(url)
            val request = DownloadManager.Request(uri).apply {
                setTitle("Загрузка обновления")
                setDescription("Скачивание версии $version")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "RuVideoHub_v$version.apk")
                setMimeType("application/vnd.android.package-archive")
            }
            
            val downloadId = downloadManager.enqueue(request)
            downloadState.value = DownloadState.Downloading(-1f)
            
            CoroutineScope(Dispatchers.IO).launch {
                var isDownloading = true
                while (isDownloading) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        
                        if (statusIndex != -1 && bytesDownloadedIndex != -1 && bytesTotalIndex != -1) {
                            val status = cursor.getInt(statusIndex)
                            val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                            val bytesTotal = cursor.getLong(bytesTotalIndex)
                            
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                isDownloading = false
                                downloadState.value = DownloadState.Finished
                                delay(2000)
                                downloadState.value = DownloadState.Idle
                            } else if (status == DownloadManager.STATUS_FAILED) {
                                isDownloading = false
                                downloadState.value = DownloadState.Error("Download failed")
                                delay(3000)
                                downloadState.value = DownloadState.Idle
                            } else {
                                if (bytesTotal > 0) {
                                    val progress = bytesDownloaded.toFloat() / bytesTotal.toFloat()
                                    downloadState.value = DownloadState.Downloading(progress)
                                } else {
                                    downloadState.value = DownloadState.Downloading(-1f)
                                }
                            }
                        }
                    }
                    cursor?.close()
                    delay(100)
                }
            }

            // System DownloadManager handles the broadcast to open the APK automatically via the mime type,
            // but we might need to listen to ACTION_DOWNLOAD_COMPLETE to trigger intent.
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start download", e)
        }
    }
}
