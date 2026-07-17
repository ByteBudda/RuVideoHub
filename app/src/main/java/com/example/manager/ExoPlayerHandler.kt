package com.example.manager

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.data.SubtitleTrack
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ExoPlayerHandler(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private var originalHlsUrl: String? = null
    private var originalOfflineFile: File? = null
    private var originalSubtitles: List<SubtitleTrack> = emptyList()
    private var currentSubtitleDelayMs: Long = 0L

    private val scope = CoroutineScope(Dispatchers.Main)
    private val okHttpClient = OkHttpClient()
    private var shiftingJob: Job? = null

    fun initialize(
        hlsUrl: String?,
        offlineFile: File,
        subtitles: List<SubtitleTrack>,
        isPlayingState: Boolean,
        initialPosition: Long
    ): ExoPlayer {
        Log.d("ExoPlayerHandler", "Initializing player: hlsUrl=$hlsUrl, offlineFile=${offlineFile.exists()}, isPlaying=$isPlayingState")
        release() // Always release any existing player before creating a new one

        this.originalHlsUrl = hlsUrl
        this.originalOfflineFile = offlineFile
        this.originalSubtitles = subtitles
        this.currentSubtitleDelayMs = 0L

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(32000, 120000, 2500, 5000)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            setEnableDecoderFallback(false)
        }

        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setForceHighestSupportedBitrate(true)
                .build()
        }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(mapOf(
                "Accept" to "*/*",
                "Referer" to "https://rutube.ru/"
            ))
            .setConnectTimeoutMs(5000)
            .setReadTimeoutMs(5000)
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .build().apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setForceHighestSupportedBitrate(true)
                    .build()
                playWhenReady = isPlayingState
                val uri = if (offlineFile.exists()) {
                    Uri.fromFile(offlineFile)
                } else {
                    Uri.parse(hlsUrl)
                }

                val subtitleConfigs = subtitles.map { track ->
                    val isVtt = track.format.lowercase().contains("vtt") || track.url.lowercase().contains(".vtt")
                    val mimeType = if (isVtt) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.url))
                        .setMimeType(mimeType)
                        .setLanguage(getBcp47Language(track.language))
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                }

                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(uri)
                    .setSubtitleConfigurations(subtitleConfigs)

                if (!offlineFile.exists() && hlsUrl!!.contains(".m3u8")) {
                    mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                }

                setMediaItem(mediaItemBuilder.build())
                prepare()
                play()
                if (initialPosition > 0L) {
                    seekTo(initialPosition)
                }
            }
        return exoPlayer!!
    }

    fun setSubtitleTrack(track: SubtitleTrack?) {
        exoPlayer?.let { player ->
            if (track == null) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            } else {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setPreferredTextLanguage(getBcp47Language(track.language))
                    .build()
            }
        }
    }

    fun setSubtitleDelayMs(delayMs: Long) {
        currentSubtitleDelayMs = delayMs
        shiftingJob?.cancel()
        shiftingJob = scope.launch {
            val player = exoPlayer ?: return@launch
            val currentPosition = player.currentPosition
            
            val newSubtitleConfigs = withContext(Dispatchers.IO) {
                originalSubtitles.map { track ->
                    val content = if (track.url.startsWith("http")) {
                        try {
                            okHttpClient.newCall(Request.Builder().url(track.url).build()).execute().body?.string()
                        } catch (e: Exception) {
                            Log.e("ExoPlayerHandler", "Error fetching subtitle: ${track.url}", e)
                            null
                        }
                    } else {
                        try {
                            File(track.url).readText()
                        } catch (e: Exception) {
                            Log.e("ExoPlayerHandler", "Error reading subtitle file: ${track.url}", e)
                            null
                        }
                    } ?: ""
                    
                    val shiftedContent = SubtitleUtils.shiftSubtitles(content, delayMs)
                    Log.d("ExoPlayerHandler", "Shifted subtitle content for ${track.url} with delay $delayMs: ${shiftedContent.take(100)}")
                    val tempFile = File.createTempFile("sub", if (track.format.lowercase().contains("vtt")) ".vtt" else ".srt", context.cacheDir)
                    tempFile.writeText(shiftedContent)
                    Log.d("ExoPlayerHandler", "Temp subtitle file created: ${tempFile.absolutePath}")
                    
                    val isVtt = track.format.lowercase().contains("vtt") || track.url.lowercase().contains(".vtt")
                    val mimeType = if (isVtt) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
                    MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(tempFile))
                        .setMimeType(mimeType)
                        .setLanguage(getBcp47Language(track.language))
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                }
            }
            
            val uri = if (originalOfflineFile?.exists() == true) {
                Uri.fromFile(originalOfflineFile)
            } else {
                Uri.parse(originalHlsUrl)
            }
            
            val mediaItemBuilder = MediaItem.Builder()
                .setUri(uri)
                .setSubtitleConfigurations(newSubtitleConfigs)
            
            if (originalOfflineFile?.exists() != true && originalHlsUrl?.contains(".m3u8") == true) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            
            player.setMediaItem(mediaItemBuilder.build())
            player.prepare()
            player.seekTo(currentPosition)
            player.play()
        }
    }

    private fun getBcp47Language(lang: String): String {
        return when (val l = lang.lowercase().trim()) {
            "русский", "russian", "ru", "rus" -> "ru"
            "english", "английский", "en", "eng" -> "en"
            "français", "french", "французский", "fr", "fra" -> "fr"
            "español", "spanish", "испанский", "es", "spa" -> "es"
            "deutsch", "german", "немецкий", "de", "deu" -> "de"
            "italiano", "italian", "итальянский", "it", "ita" -> "it"
            "português", "portuguese", "португальский", "pt", "por" -> "pt"
            "türkçe", "turkish", "турецкий", "tr", "tur" -> "tr"
            "中文", "chinese", "китайский", "zh", "chi" -> "zh"
            "日本語", "japanese", "японский", "ja", "jpn" -> "ja"
            "한국어", "korean", "корейский", "ko", "kor" -> "ko"
            else -> {
                if (l.length in 2..3) l else "ru"
            }
        }
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
