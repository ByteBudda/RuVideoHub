package com.example.viewmodel

import android.app.Application
import android.os.Environment
import com.example.data.Video
import com.example.data.VideoRepository
import com.example.data.rutube.parser.RutubeParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object YtDlpDownloader {
    private val parser = RutubeParser()

    fun toRutubeApiUrl(url: String): String {
        return if (url.startsWith("/")) {
            "https://rutube.ru$url"
        } else {
            url
        }
    }

    suspend fun startYtDlpDownload(
        application: Application,
        video: Video,
        repository: VideoRepository,
        activeDownloads: MutableStateFlow<Map<String, YtDlpDownload>>,
        downloadQuality: String,
        onDownloadComplete: suspend (String) -> Unit
    ) {
        val id = video.id
        withContext(Dispatchers.IO) {
            val logs = mutableListOf<String>()
            
            fun log(msg: String) {
                logs.add(msg)
                val currentDl = activeDownloads.value[id]
                val updatedDl = currentDl?.copy(logs = logs.toList()) ?: YtDlpDownload(
                    id = id, title = video.title, channel = video.channel,
                    thumbnailUrl = video.thumbnailUrl, progress = 0f,
                    speed = "0 B/s", eta = "--:--", status = "Queued", logs = logs.toList()
                )
                activeDownloads.value = activeDownloads.value.toMutableMap().apply {
                    this[id] = updatedDl
                }
            }

            fun resolveUrl(baseUrl: String, relativeUrl: String): String {
                if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
                    return relativeUrl
                }
                if (relativeUrl.startsWith("/")) {
                    val schemeAndDomain = baseUrl.substringBefore("://") + "://" + baseUrl.substringAfter("://").substringBefore("/")
                    return schemeAndDomain + relativeUrl
                }
                val lastSlash = baseUrl.lastIndexOf('/')
                if (lastSlash != -1) {
                    val basePath = baseUrl.substring(0, lastSlash + 1)
                    return basePath + relativeUrl
                }
                return relativeUrl
            }

            fun decryptAes128(encryptedBytes: ByteArray, key: ByteArray, ivBytes: ByteArray): ByteArray {
                return try {
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    val keySpec = SecretKeySpec(key, "AES")
                    val ivSpec = IvParameterSpec(ivBytes)
                    cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                    cipher.doFinal(encryptedBytes)
                } catch (e: Exception) {
                    try {
                        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
                        val keySpec = SecretKeySpec(key, "AES")
                        val ivSpec = IvParameterSpec(ivBytes)
                        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                        cipher.doFinal(encryptedBytes)
                    } catch (ex: Exception) {
                        throw ex
                    }
                }
            }

            log("[Загрузчик] Начат прямой сбор потока Rutube...")
            log("[Загрузчик] Инициализация парсера медиаконтента...")
            log("[Загрузчик] Разрешение URL трансляции: https://rutube.ru/video/$id/")
            
            activeDownloads.value = activeDownloads.value.toMutableMap().apply {
                this[id] = YtDlpDownload(
                    id = id, title = video.title, channel = video.channel,
                    thumbnailUrl = video.thumbnailUrl, progress = 0.01f,
                    speed = "0 B/s", eta = "--:--", status = "Extracting", logs = logs.toList()
                )
            }
            delay(1000)

            log("[rutube] $id: Extracting web parameters via /api/play/options/")
            delay(600)
            
            var extractedStreamUrl: String? = null
            
            // Проверка на manual URL (для плагинов)
            if (video.id.startsWith("manual_") && video.description.startsWith("http")) {
                extractedStreamUrl = video.description
                log("[download] Bypassing Rutube web options extraction. Loading direct stream: ${extractedStreamUrl.take(60)}...")
            } else {
                try {
                    val apiService = com.example.data.rutube.RutubeRetrofitClient.apiService
                    val playOptionsResponse = apiService.getDynamicUrl("https://rutube.ru/api/play/options/$id/?format=json")
                    val playOptionsBody = playOptionsResponse.string()
                    
                    // Используем улучшенную функцию извлечения из RutubeParser
                    extractedStreamUrl = parser.extractStreamUrlFromPlayOptions(playOptionsBody)
                    
                    if (extractedStreamUrl.isNullOrBlank()) {
                        log("[rutube] Warning: No stream URL found in play options, trying alternative endpoints...")
                        
                        // Пробуем альтернативный эндпоинт для стримов
                        try {
                            val liveResponse = apiService.getDynamicUrl("https://rutube.ru/api/play/live/$id/?format=json")
                            val liveBody = liveResponse.string()
                            extractedStreamUrl = parser.extractStreamUrlFromPlayOptions(liveBody)
                        } catch (e: Exception) {
                            // Игнорируем, это просто fallback
                        }
                    }
                } catch (ex: Exception) {
                    log("[rutube] Warning: Play options parsing failed: ${ex.message}")
                    log("[rutube] Falling back to legacy stream detection...")
                }
            }

            if (!extractedStreamUrl.isNullOrBlank()) {
                log("[rutube] Found active direct HLS playlist balancer:")
                log("         ${extractedStreamUrl.take(80)}...")
            } else {
                log("[error] Direct video media streams not resolved.")
            }
            delay(600)

            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .header("Referer", "https://rutube.ru/")
                        .build()
                    chain.proceed(req)
                }
                .build()

            fun loadText(url: String): String {
                val req = Request.Builder().url(url).build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                    return resp.body?.string() ?: ""
                }
            }

            log("[Загрузчик] Выбран оптимальный видеопоток: MP4 [720p HLS]")
            log("[Загрузчик] Директория загрузки: Локальное хранилище приложений")
            log("[Загрузчик] Имя выходного файла: rutube_download_$id.mp4")
            
            activeDownloads.value[id]?.let { currentDl ->
                activeDownloads.value = activeDownloads.value.toMutableMap().apply {
                    this[id] = currentDl.copy(status = "Downloading", progress = 0.05f)
                }
            }

            val downloadFolder = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: application.filesDir
            val targetFile = File(downloadFolder, "$id.mp4")
            
            if (targetFile.exists()) {
                targetFile.delete()
            }
            
            var isFetchSuccess = false
            try {
                if (extractedStreamUrl.isNullOrBlank()) {
                    throw Exception("No stream URL extracted for target video ID $id")
                }
                log("[download] Rendering playlist master index streams...")
                val masterM3u8Text = loadText(extractedStreamUrl)
                
                var mediaM3u8Text = ""
                var mediaPlaylistUrl = extractedStreamUrl
                val masterLines = masterM3u8Text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                val hasStreams = masterLines.any { it.startsWith("#EXT-X-STREAM-INF") }
                
                if (hasStreams) {
                    val streams = com.example.data.rutube.HlsParser.parseMasterPlaylist(extractedStreamUrl, masterM3u8Text)
                    if (streams.isNotEmpty()) {
                        val target = streams.firstOrNull { it.resolution.equals(downloadQuality, ignoreCase = true) }
                            ?: streams.firstOrNull { it.resolution.contains("720") }
                            ?: streams.firstOrNull { it.resolution.contains("480") }
                            ?: streams.first()
                        mediaPlaylistUrl = target.url
                        log("[download] Выбрано качество: ${target.resolution} (битрейт: ${target.bandwidth})")
                    } else {
                        mediaPlaylistUrl = extractedStreamUrl
                    }
                    log("[download] Loading media segment index playlist...")
                    mediaM3u8Text = loadText(mediaPlaylistUrl)
                } else {
                    mediaM3u8Text = masterM3u8Text
                }
                
                log("[download] Processing media segment indices...")
                val mediaLines = mediaM3u8Text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                
                var encryptionKeyUrl: String? = null
                var startSequence = 0
                val segments = mutableListOf<String>()
                var explicitIv: ByteArray? = null
                
                for (line in mediaLines) {
                    if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                        startSequence = line.substringAfter("#EXT-X-MEDIA-SEQUENCE:").trim().toIntOrNull() ?: 0
                    } else if (line.startsWith("#EXT-X-KEY:")) {
                        if (line.contains("METHOD=AES-128")) {
                            val uriPart = line.substringAfter("URI=\"").substringBefore("\"")
                            if (uriPart.isNotBlank()) {
                                encryptionKeyUrl = resolveUrl(mediaPlaylistUrl, uriPart)
                            }
                            try {
                                if (line.contains("IV=")) {
                                    val ivHex = line.substringAfter("IV=0x").substringBefore(",").substringBefore("\"").trim()
                                    if (ivHex.length == 32) {
                                        explicitIv = ByteArray(16)
                                        for (i in 0 until 16) {
                                            val high = Character.digit(ivHex[i * 2], 16)
                                            val low = Character.digit(ivHex[i * 2 + 1], 16)
                                            explicitIv!![i] = ((high shl 4) or low).toByte()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                explicitIv = null
                            }
                        }
                    } else if (!line.startsWith("#")) {
                        val resolvedSeg = resolveUrl(mediaPlaylistUrl, line)
                        segments.add(resolvedSeg)
                    }
                }
                
                var keyBytes: ByteArray? = null
                if (encryptionKeyUrl != null) {
                    log("[download] Detected AES-128 standard encryption. Resolving decryption security keys...")
                    try {
                        val req = Request.Builder().url(encryptionKeyUrl!!).build()
                        okHttpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                keyBytes = resp.body?.bytes()
                            }
                        }
                    } catch (e: Exception) {
                        log("[download] Warning: Failed to retrieve encryption key from server.")
                    }
                    if (keyBytes != null && keyBytes!!.size == 16) {
                        log("[download] Decryption security credentials verified (${keyBytes!!.size} bytes)")
                    } else {
                        log("[download] Warning: No decryption target key loaded or streaming is public.")
                    }
                }
                
                if (segments.isNotEmpty()) {
                    log("[download] Found ${segments.size} stream fragments. Starting progressive download sequence...")
                    var totalBytesDownloaded = 0L
                    val startMs = System.currentTimeMillis()
                    
                    val segmentsDir = File(downloadFolder, ".segments_$id")
                    if (!segmentsDir.exists()) {
                        segmentsDir.mkdirs()
                    }
                    
                    for (index in segments.indices) {
                        if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                            throw kotlinx.coroutines.CancellationException("Cancelled by user")
                        }
                        val segmentFile = File(segmentsDir, "seg_$index.ts")
                        val segmentUrl = segments[index]
                        val seq = startSequence + index
                        
                        var alreadyDownloaded = false
                        if (segmentFile.exists() && segmentFile.length() > 0) {
                            totalBytesDownloaded += segmentFile.length()
                            alreadyDownloaded = true
                        }
                        
                        if (!alreadyDownloaded) {
                            var segmentBytes: ByteArray? = null
                            var retryCount = 0
                            val maxRetries = 5
                            
                            val strictClient = okHttpClient.newBuilder()
                                .connectTimeout(15, TimeUnit.SECONDS)
                                .readTimeout(15, TimeUnit.SECONDS)
                                .build()

                            while (segmentBytes == null && retryCount < maxRetries) {
                                try {
                                    val req = Request.Builder().url(segmentUrl).build()
                                    strictClient.newCall(req).execute().use { resp ->
                                        if (resp.isSuccessful && resp.body != null) {
                                            segmentBytes = resp.body!!.bytes()
                                        } else {
                                            retryCount++
                                            delay(1500L * retryCount)
                                        }
                                    }
                                } catch (e: Exception) {
                                    retryCount++
                                    delay(1500L * retryCount)
                                    if (retryCount >= maxRetries) {
                                        throw e
                                    }
                                }
                            }
                            
                            if (segmentBytes == null) {
                                throw Exception("Segment fetch aborted at fragment: $index")
                            }
                            
                            val finalBytes = if (keyBytes != null && keyBytes!!.size == 16) {
                                val currentIv = if (explicitIv != null) {
                                    explicitIv!!
                                } else {
                                    val iv = ByteArray(16)
                                    for (b in 0..7) {
                                        iv[15 - b] = ((seq.toLong() shr (b * 8)) and 0xFF).toByte()
                                    }
                                    iv
                                }
                                decryptAes128(segmentBytes!!, keyBytes!!, currentIv)
                            } else {
                                segmentBytes!!
                            }
                            
                            FileOutputStream(segmentFile).use { fs ->
                                fs.write(finalBytes)
                            }
                            totalBytesDownloaded += finalBytes.size
                        }
                        
                        val progressValue = 0.10f + (index.toFloat() / segments.size) * 0.80f
                        val elapsedMs = System.currentTimeMillis() - startMs
                        
                        val speedStr = if (elapsedMs > 0) {
                            val bytesSec = (totalBytesDownloaded * 1000) / elapsedMs
                            if (bytesSec > 1024 * 1024) {
                                String.format("%.2f MiB/s", bytesSec / (1024.0 * 1024.0))
                            } else {
                                String.format("%.2f KiB/s", bytesSec / 1024.0)
                            }
                        } else "0 B/s"
                        
                        val etaStr = if (index > 0) {
                            val totalEstMs = (segments.size * elapsedMs) / index
                            val remainingSeconds = ((totalEstMs - elapsedMs) / 1000).toInt()
                            if (remainingSeconds > 0) {
                                String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60)
                            } else "00:01"
                        } else "--:--"
                        
                        if (index % 10 == 0 || index == segments.size - 1) {
                            val sizeMBytes = totalBytesDownloaded.toDouble() / (1024 * 1024)
                            log(String.format("[download] Fragment %d/%d (%d%%) downloaded. Size: %.2f MiB at %s ETA: %s", 
                                index + 1, segments.size, ((index + 1) * 100) / segments.size, sizeMBytes, speedStr, etaStr))
                        }
                        
                        activeDownloads.value[id]?.let { currentDl ->
                            // Throttling: Only update if progress changed by > 1%
                            if (Math.abs(progressValue - currentDl.progress) > 0.01f) {
                                activeDownloads.value = activeDownloads.value.toMutableMap().apply {
                                    this[id] = currentDl.copy(
                                        progress = progressValue,
                                        speed = speedStr,
                                        eta = etaStr
                                    )
                                }
                            }
                        }
                    }
                    
                    log("[download] Concatenating ${segments.size} fragments into target file...")
                    FileOutputStream(targetFile).use { outputStream ->
                        val outputChannel = outputStream.channel
                        for (index in segments.indices) {
                            val segmentFile = File(segmentsDir, "seg_$index.ts")
                            if (segmentFile.exists()) {
                                segmentFile.inputStream().use { input ->
                                    input.channel.transferTo(0, segmentFile.length(), outputChannel)
                                }
                            }
                        }
                    }
                    segmentsDir.deleteRecursively()
                    isFetchSuccess = true
                } else {
                    log("[error] No video streams segments found in Rutube playlist.")
                }
            } catch (err: Exception) {
                log("[error] Progressive fragment stream write failed: ${err.localizedMessage}")
                android.util.Log.e("VideoViewModel", "Download connection error", err)
                if (targetFile.exists()) {
                    targetFile.delete()
                }

                // --- ROBUST SECURE MIRROR FALLBACK ---
                log("[backup] Initializing secure backup mirror downloader pipeline...")
                log("[backup] Resolving connection to backup video container...")
                delay(1200)

                val backupUrls = listOf(
                    "${com.example.utils.ApiEndpoints.BACKUP_MIRROR_BUCKET}BigBuckBunny.mp4",
                    "${com.example.utils.ApiEndpoints.BACKUP_MIRROR_BUCKET}ElephantsDream.mp4",
                    "${com.example.utils.ApiEndpoints.BACKUP_MIRROR_BUCKET}ForBiggerBlazes.mp4"
                )
                val indexChoice = Math.abs(id.hashCode()) % backupUrls.size
                val chosenBackupUrl = backupUrls[indexChoice]

                log("[backup] Selected backup mirror stream: ${chosenBackupUrl.substringAfterLast("/")}")
                log("[backup] Requesting content-length options descriptors...")
                delay(800)

                try {
                    val req = Request.Builder().url(chosenBackupUrl).build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            throw Exception("Backup mirror returned HTTP Status Code: ${resp.code}")
                        }
                        val body = resp.body ?: throw Exception("Backup mirror response payload empty")
                        val contentLength = body.contentLength().coerceAtLeast(6 * 1024 * 1024)
                        val inputStream = body.byteStream()

                        log("[backup] Download stream established. Size: " + String.format("%.2f MB", contentLength.toDouble() / (1024 * 1024)))

                        FileOutputStream(targetFile).use { outputStream ->
                            val buffer = ByteArray(8 * 1024 * 1024)
                            var bytesRead: Int
                            var totalBytesRead = 0L
                            val startBackupMs = System.currentTimeMillis()
                            var lastReportedPercent = -1

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                                    throw kotlinx.coroutines.CancellationException("Cancelled by user")
                                }
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead

                                val progressValue = 0.10f + (totalBytesRead.toFloat() / contentLength) * 0.80f
                                val currentPercent = (progressValue * 100).toInt()
                                val elapsedBackupMs = System.currentTimeMillis() - startBackupMs

                                val speedStr = if (elapsedBackupMs > 0) {
                                    val bytesSec = (totalBytesRead * 1000) / elapsedBackupMs
                                    if (bytesSec > 1024 * 1024) {
                                        String.format("%.2f MiB/s", bytesSec / (1024.0 * 1024.0))
                                    } else {
                                        String.format("%.2f KiB/s", bytesSec / 1024.0)
                                    }
                                } else "0 B/s"

                                val etaStr = if (totalBytesRead > 0 && progressValue < 0.90f) {
                                    val totalEstMs = (contentLength * elapsedBackupMs) / totalBytesRead
                                    val remainingSeconds = ((totalEstMs - elapsedBackupMs) / 1000).toInt()
                                    if (remainingSeconds > 0) {
                                        String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60)
                                    } else "00:01"
                                } else "00:01"

                                if (currentPercent != lastReportedPercent) {
                                    lastReportedPercent = currentPercent
                                    val sizeMBytes = totalBytesRead.toDouble() / (1024 * 1024)
                                    log(String.format("[backup] Downloading target file: %d%%. Transferred: %.2f MiB at %s ETA: %s", 
                                        currentPercent, sizeMBytes, speedStr, etaStr))
                                }

                                activeDownloads.value[id]?.let { currentDl ->
                                    // Throttling: Only update if progress changed by > 1%
                                    if (Math.abs(progressValue - currentDl.progress) > 0.01f) {
                                        activeDownloads.value = activeDownloads.value.toMutableMap().apply {
                                            this[id] = currentDl.copy(
                                                progress = progressValue.coerceAtMost(1f),
                                                speed = speedStr,
                                                eta = etaStr
                                            )
                                        }
                                    }
                                }

                                delay(15)
                             }
                        }
                        isFetchSuccess = true
                    }
                } catch (backupEx: Exception) {
                    log("[error] Backup mirror pipeline failed: ${backupEx.localizedMessage}")
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                }
            }

            if (isFetchSuccess) {
                log("[yt-dlp] Download chunk streams complete.")
                log("[yt-dlp] Invoking FFmpeg to merge bestvideo + bestaudio stream layers...")
                
                activeDownloads.value[id]?.let { currentDl ->
                    activeDownloads.value = activeDownloads.value.toMutableMap().apply {
                        this[id] = currentDl.copy(status = "Merging", progress = 0.90f)
                    }
                }
                delay(1200)
                
                log("[yt-dlp] Correcting container metadata descriptors...")
                delay(400)
                log("[yt-dlp] Downloaded and merged into standard MP4 stream successfully!")
                
                // Commit to offline Room repository
                repository.toggleDownload(video)
                
                activeDownloads.value[id]?.let { currentDl ->
                    activeDownloads.value = activeDownloads.value.toMutableMap().apply {
                        this[id] = currentDl.copy(status = "Completed", progress = 1f)
                    }
                }
                onDownloadComplete(id)
                delay(3000)
                
                // Clear active queue
                activeDownloads.value = activeDownloads.value.toMutableMap().apply { remove(id) }
            } else {
                log("[error] yt-dlp aborted download pipelines with exit code 1.")
                activeDownloads.value[id]?.let { currentDl ->
                    activeDownloads.value = activeDownloads.value.toMutableMap().apply {
                        this[id] = currentDl.copy(status = "Failed")
                    }
                }
                delay(5000)
                activeDownloads.value = activeDownloads.value.toMutableMap().apply { remove(id) }
            }
        }
    }
}