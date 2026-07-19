package com.example.utils

object VideoDurationFormatter {
    fun getFormattedElapsedTime(durationStr: String, progress: Float): String {
        val totalSeconds = parseDurationToSeconds(durationStr)
        val elapsedSeconds = (progress * totalSeconds).toInt()
        return formatSecondsToTimeString(elapsedSeconds)
    }

    fun parseDurationToSeconds(duration: String): Int {
        val parts = duration.split(":")
        return try {
            if (parts.size == 2) {
                val minutes = parts[0].toInt()
                val seconds = parts[1].toInt()
                minutes * 60 + seconds
            } else if (parts.size == 3) {
                val hours = parts[0].toInt()
                val minutes = parts[1].toInt()
                val seconds = parts[2].toInt()
                hours * 3600 + minutes * 60 + seconds
            } else {
                300 // fallback 5 min
            }
        } catch (e: Exception) {
            300
        }
    }

    fun formatSecondsToTimeString(secondsValue: Int): String {
        val m = secondsValue / 60
        val s = secondsValue % 60
        return String.format("%02d:%02d", m, s)
    }
}
