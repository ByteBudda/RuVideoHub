package com.example.pluginapi

import androidx.annotation.Keep

/**
 * Информация о потоке, который плеер (ExoPlayer) будет воспроизводить.
 */
@Keep
data class StreamInfo(
    val streamUrl: String,
    val mimeType: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val loadedAt: Long = System.currentTimeMillis(),
    val headers: Map<String, String> = emptyMap()
)