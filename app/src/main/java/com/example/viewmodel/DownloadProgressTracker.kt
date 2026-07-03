package com.example.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object DownloadProgressTracker {
    private val _activeDownloads = MutableStateFlow<Map<String, YtDlpDownload>>(emptyMap())
    val activeDownloads = _activeDownloads.asStateFlow()

    fun updateDownload(id: String, download: YtDlpDownload) {
        _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
            put(id, download)
        }
    }

    fun removeDownload(id: String) {
        _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
            remove(id)
        }
    }
    
    fun setDownloads(downloads: Map<String, YtDlpDownload>) {
        _activeDownloads.value = downloads
    }
}
