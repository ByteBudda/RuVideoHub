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

    fun startDownloadAndInstall(context: Context, urlString: String, version: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                downloadState.value = DownloadState.Downloading(0f)
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    downloadState.value = DownloadState.Error("HTTP error: ${connection.responseCode}")
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
                while (input.read(data).also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) {
                        downloadState.value = DownloadState.Downloading(total.toFloat() / fileLength.toFloat())
                    }
                    output.write(data, 0, count)
                }

                output.flush()
                output.close()
                input.close()

                downloadState.value = DownloadState.Finished
                
                // Trigger install
                installApk(context, apkFile)
                
                delay(2000)
                downloadState.value = DownloadState.Idle
                
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                downloadState.value = DownloadState.Error(e.message ?: "Download failed")
                delay(3000)
                downloadState.value = DownloadState.Idle
            }
        }
    }

    private fun installApk(context: Context, file: File) {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
        }
    }
}
