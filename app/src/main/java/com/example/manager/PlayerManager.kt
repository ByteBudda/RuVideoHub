package com.example.manager

import com.example.data.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlayerManager {
    private val _currentSelectedVideo = MutableStateFlow<Video?>(null)
    val currentSelectedVideo = _currentSelectedVideo.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playProgress = MutableStateFlow(0f)
    val playProgress = _playProgress.asStateFlow()

    private val _currentAvailableQualities = MutableStateFlow<List<String>>(listOf("Авто"))
    val currentAvailableQualities = _currentAvailableQualities.asStateFlow()

    private val _activeVideoQuality = MutableStateFlow("Авто")
    val activeVideoQuality = _activeVideoQuality.asStateFlow()

    private val _isTvMiniFullscreen = MutableStateFlow(false)
    val isTvMiniFullscreen = _isTvMiniFullscreen.asStateFlow()

    private val _isMiniPlayer = MutableStateFlow(false)
    val isMiniPlayer = _isMiniPlayer.asStateFlow()

    // Playback progress tracking (videoId -> playback position in milliseconds)
    private val _videoPositions = mutableMapOf<String, Long>()

    fun selectVideo(video: Video?) {
        _currentSelectedVideo.value = video
        if (video == null) {
            _isPlaying.value = false
            _isMiniPlayer.value = false
        } else {
            _isPlaying.value = true
            _playProgress.value = 0f
            // _isMiniPlayer remains as is, or reset it to false?
            // When selecting a new video, we should probably expand to full player
            _isMiniPlayer.value = false
        }
    }

    fun setMiniPlayer(isMini: Boolean) {
        _isMiniPlayer.value = isMini
    }

    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
    }

    fun setPlayingState(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun seekProgress(progress: Float) {
        _playProgress.value = progress.coerceIn(0f, 1f)
    }

    fun saveVideoPosition(videoId: String, position: Long) {
        _videoPositions[videoId] = position
    }

    fun getVideoPosition(videoId: String): Long {
        return _videoPositions[videoId] ?: 0L
    }

    fun setAvailableQualities(qualities: List<String>) {
        _currentAvailableQualities.value = qualities
    }

    fun setActiveVideoQuality(quality: String) {
        _activeVideoQuality.value = quality
    }

    fun setTvMiniFullscreen(fullscreen: Boolean) {
        _isTvMiniFullscreen.value = fullscreen
    }
}
