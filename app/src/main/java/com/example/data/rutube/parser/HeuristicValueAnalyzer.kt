package com.example.data.rutube.parser

import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object HeuristicValueAnalyzer {

    // Паттерны для определения типа по значению — ИСПРАВЛЕНЫ
    private val THUMBNAIL_PATTERNS = listOf(
        Pattern.compile("https?://.*\\.(jpg|jpeg|png|webp|gif)(\\?.*)?$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("https?://.*thumb.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("https?://.*pic\\.rtbcdn.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("https?://.*poster.*", Pattern.CASE_INSENSITIVE)
    )

    private val VIDEO_URL_PATTERNS = listOf(
        Pattern.compile("https?://.*rutube\\.ru/video/[a-f0-9]+.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("https?://.*rutube\\.ru/shorts/[a-f0-9]+.*", Pattern.CASE_INSENSITIVE)
    )

    private val DURATION_PATTERN = Pattern.compile("^\\d+(:\\d+)+$")
    private val ISO_DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}.*")
    private val RUTUBE_DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:")

    fun analyzeValue(key: String, value: Any?, parentContext: JSONObject? = null): List<FieldGuess> {
        val guesses = mutableListOf<FieldGuess>()
        if (value == null) return guesses

        val strValue = value.toString()
        val keyLower = key.lowercase()

        if (value is String && strValue.isNotBlank()) {
            if (THUMBNAIL_PATTERNS.any { it.matcher(strValue).matches() }) {
                val confidence = when {
                    keyLower.contains("thumb") || keyLower.contains("pic") ||
                    keyLower.contains("poster") || keyLower.contains("image") ||
                    keyLower.contains("avatar") -> 0.95
                    keyLower.contains("url") -> 0.75
                    else -> 0.65
                }
                guesses.add(FieldGuess("thumbnail", confidence, "url_pattern_match"))
            }

            if (VIDEO_URL_PATTERNS.any { it.matcher(strValue).matches() }) {
                guesses.add(FieldGuess("video_url", 0.9, "rutube_video_pattern"))
            }

            if (DURATION_PATTERN.matcher(strValue).matches()) {
                val confidence = if (keyLower.contains("duration") || keyLower.contains("length")) 0.95 else 0.7
                guesses.add(FieldGuess("duration", confidence, "duration_format"))
            }

            if (RUTUBE_DATE_PATTERN.matcher(strValue).matches() || ISO_DATE_PATTERN.matcher(strValue).matches()) {
                val confidence = if (keyLower.contains("date") || keyLower.contains("created") ||
                                   keyLower.contains("published") || keyLower.contains("time")) 0.95 else 0.7
                guesses.add(FieldGuess("created", confidence, "date_format"))
            }

            if (strValue.length in 3..200 && !strValue.startsWith("http") &&
                !strValue.matches(Regex("^\\d+$"))) {
                val confidence = if (keyLower.contains("title") || keyLower.contains("name") ||
                                   keyLower.contains("heading")) 0.9 else 0.4
                guesses.add(FieldGuess("title", confidence, "text_heuristic"))
            }

            if (strValue.length > 50) {
                val confidence = if (keyLower.contains("desc") || keyLower.contains("about") ||
                                   keyLower.contains("content")) 0.9 else 0.45
                guesses.add(FieldGuess("description", confidence, "long_text"))
            }
        }

        if (value is Number) {
            val num = value.toLong()
            val dbl = value.toDouble()

            if (num > 100) {
                val confidence = if (keyLower.contains("view") || keyLower.contains("hit") ||
                                   keyLower.contains("watch") || keyLower.contains("count")) 0.9 else 0.5
                guesses.add(FieldGuess("views", confidence, "large_number"))
            }

            if (num > 10 && parentContext?.has("avatar") == true) {
                val confidence = if (keyLower.contains("sub") || keyLower.contains("follow")) 0.95 else 0.55
                guesses.add(FieldGuess("subscribers", confidence, "number_with_avatar"))
            }

            if (dbl > 0 && dbl < 86400 && (keyLower.contains("duration") || keyLower.contains("length"))) {
                guesses.add(FieldGuess("duration_seconds", 0.95, "duration_numeric"))
            }

            if (num in 1..50 && (keyLower.contains("season") || keyLower.contains("seas"))) {
                guesses.add(FieldGuess("seasons_count", 0.95, "seasons_numeric"))
            }

            if (num in 1900..2030 && (keyLower.contains("year") || keyLower.contains("release"))) {
                guesses.add(FieldGuess("year", 0.95, "year_numeric"))
            }

            if (dbl in 0.0..10.0 && (keyLower.contains("rating") || keyLower.contains("score") || keyLower.contains("rate"))) {
                guesses.add(FieldGuess("rating", 0.9, "rating_numeric"))
            }
        }

        when {
            keyLower in listOf("id", "code", "uuid", "video_id", "content_id") ->
                guesses.add(FieldGuess("id", 0.9, "exact_key_name"))
            keyLower in listOf("title", "name", "name_displayed", "original_title", "heading") ->
                guesses.add(FieldGuess("title", 0.95, "exact_key_name"))
            keyLower in listOf("thumbnail_url", "picture_url", "picture", "poster_url", "image", "avatar_url", "avatar", "logo") ->
                guesses.add(FieldGuess("thumbnail", 0.95, "exact_key_name"))
            keyLower in listOf("duration", "length", "video_length", "duration_seconds") ->
                guesses.add(FieldGuess("duration", 0.95, "exact_key_name"))
            keyLower in listOf("views_count", "hits", "views", "watch_count", "view_count") ->
                guesses.add(FieldGuess("views", 0.95, "exact_key_name"))
            keyLower in listOf("author", "creator", "channel", "owner", "user") ->
                guesses.add(FieldGuess("author", 0.9, "exact_key_name"))
            keyLower in listOf("description", "desc", "about", "content") ->
                guesses.add(FieldGuess("description", 0.9, "exact_key_name"))
            keyLower in listOf("created_ts", "publication_ts", "published_at", "upload_date", "created") ->
                guesses.add(FieldGuess("created", 0.9, "exact_key_name"))
            keyLower in listOf("subscribers_count", "followers", "subs") ->
                guesses.add(FieldGuess("subscribers", 0.95, "exact_key_name"))
            keyLower in listOf("video_count", "videos_count", "total_videos") ->
                guesses.add(FieldGuess("videos", 0.9, "exact_key_name"))
            keyLower in listOf("seasons_count", "seasons") ->
                guesses.add(FieldGuess("seasons", 0.95, "exact_key_name"))
        }

        return guesses.sortedByDescending { it.confidence }
    }

    data class FieldGuess(
        val canonicalField: String,
        val confidence: Double,
        val reason: String
    )
}
