package com.example.manager

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.data.SubtitleTrack
import java.io.File

class ExoPlayerHandler(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null

    fun initialize(
        hlsUrl: String?,
        offlineFile: File,
        subtitles: List<SubtitleTrack>,
        isPlayingState: Boolean,
        initialPosition: Long
    ): ExoPlayer {
        Log.d("ExoPlayerHandler", "Initializing player: hlsUrl=$hlsUrl, offlineFile=${offlineFile.exists()}, isPlaying=$isPlayingState")
        release() // Always release any existing player before creating a new one

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
                    val mimeType = if (track.format.lowercase() == "vtt") MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
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

    private fun getBcp47Language(lang: String): String {
        return when (lang.lowercase()) {
            "русский", "russian", "ru" -> "ru"
            "english", "английский", "en" -> "en"
            else -> "ru"
        }
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
