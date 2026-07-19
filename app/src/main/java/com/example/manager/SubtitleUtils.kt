package com.example.manager

import java.util.Locale

object SubtitleUtils {

    fun parseTimestampToMs(timeStr: String): Long {
        val cleanStr = timeStr.trim().replace(',', '.')
        val parts = cleanStr.split(":")
        if (parts.size == 3) {
            val hours = parts[0].toLongOrNull() ?: 0L
            val minutes = parts[1].toLongOrNull() ?: 0L
            val secMs = parts[2].split(".")
            val seconds = secMs[0].toLongOrNull() ?: 0L
            val ms = if (secMs.size > 1) (secMs[1].padEnd(3, '0').take(3).toLongOrNull() ?: 0L) else 0L
            return hours * 3600000L + minutes * 60000L + seconds * 1000L + ms
        } else if (parts.size == 2) {
            val minutes = parts[0].toLongOrNull() ?: 0L
            val secMs = parts[1].split(".")
            val seconds = secMs[0].toLongOrNull() ?: 0L
            val ms = if (secMs.size > 1) (secMs[1].padEnd(3, '0').take(3).toLongOrNull() ?: 0L) else 0L
            return minutes * 60000L + seconds * 1000L + ms
        }
        return 0L
    }

    fun formatMsToTimestamp(msVal: Long, useComma: Boolean): String {
        val nonNegMs = maxOf(0L, msVal)
        val hours = nonNegMs / 3600000L
        val minutes = (nonNegMs % 3600000L) / 60000L
        val seconds = (nonNegMs % 60000L) / 1000L
        val ms = nonNegMs % 1000L
        val separator = if (useComma) "," else "."
        return String.format(Locale.US, "%02d:%02d:%02d%s%03d", hours, minutes, seconds, separator, ms)
    }

    fun shiftSubtitles(input: String, delayMs: Long): String {
        if (delayMs == 0L) return input
        val lines = input.lines()
        val result = StringBuilder()
        val isSrt = !input.contains("WEBVTT", ignoreCase = true)
        
        for (line in lines) {
            if (line.contains(" --> ")) {
                val parts = line.split(" --> ")
                if (parts.size == 2) {
                    val startMs = parseTimestampToMs(parts[0])
                    val endMs = parseTimestampToMs(parts[1])
                    
                    val newStartMs = maxOf(0L, startMs + delayMs)
                    val newEndMs = maxOf(0L, endMs + delayMs)
                    
                    val startStr = formatMsToTimestamp(newStartMs, isSrt)
                    val endStr = formatMsToTimestamp(newEndMs, isSrt)
                    result.append(startStr).append(" --> ").append(endStr).append("\n")
                } else {
                    result.append(line).append("\n")
                }
            } else {
                result.append(line).append("\n")
            }
        }
        return result.toString()
    }
}
