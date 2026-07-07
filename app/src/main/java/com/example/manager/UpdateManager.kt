package com.example.manager

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

object UpdateManager {
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
            downloadManager.enqueue(request)
            // System DownloadManager handles the broadcast to open the APK automatically via the mime type,
            // but we might need to listen to ACTION_DOWNLOAD_COMPLETE to trigger intent.
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start download", e)
        }
    }
}
