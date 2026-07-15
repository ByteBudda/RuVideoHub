package com.example.data.rutube.parser

import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object AdaptiveExtractor {

    fun extract(obj: JSONObject, canonicalField: String, endpointHint: String? = null): ExtractedValue? {
        if (endpointHint != null) {
            val cached = fieldLearningCache[endpointHint]?.get(canonicalField)
            if (cached != null) {
                val raw = obj.opt(cached.sourcePath)
                if (raw != null && raw.toString().isNotBlank() && raw.toString() != "null") {
                    return ExtractedValue(raw, cached.confidence, "learned_cache")
                }
            }
        }

        val signature = SchemaAnalyzer.buildSignature(obj)
        val bestField = signature.fields[canonicalField]
        if (bestField != null && bestField.confidence > FIELD_CONFIDENCE_THRESHOLD) {
            val raw = obj.opt(bestField.sourcePath)
            if (raw != null && raw.toString().isNotBlank() && raw.toString() != "null") {
                if (endpointHint != null) {
                    val endpointCache = fieldLearningCache.getOrPut(endpointHint) { mutableMapOf() }
                    endpointCache[canonicalField] = FieldMapping(canonicalField, bestField.confidence, bestField.sourcePath)
                    if (endpointCache.size > LEARNING_CACHE_SIZE) {
                        endpointCache.keys.firstOrNull()?.let { endpointCache.remove(it) }
                    }
                }
                return ExtractedValue(raw, bestField.confidence, bestField.reason)
            }
        }

        return extractViaAliases(obj, canonicalField)
    }

    private fun extractViaAliases(obj: JSONObject, canonicalField: String): ExtractedValue? {
        val aliases = when (canonicalField) {
            "id" -> listOf("id", "code", "video_id", "content_id", "uuid", "person_id")
            "title" -> listOf("title", "name", "name_displayed", "original_title", "heading")
            "thumbnail" -> listOf("thumbnail_url", "picture_url", "picture", "poster_url", "image", "avatar_url")
            "duration" -> listOf("duration", "length", "video_length")
            "views" -> listOf("views_count", "hits", "views", "watch_count")
            "author" -> listOf("author", "creator", "channel", "owner", "user")
            "description" -> listOf("description", "desc", "about", "content")
            "created" -> listOf("created_ts", "publication_ts", "published_at", "upload_date")
            "subscribers" -> listOf("subscribers_count", "followers", "subs")
            "videos" -> listOf("video_count", "videos_count", "total_videos")
            "seasons" -> listOf("seasons_count", "seasons")
            "avatar" -> listOf("avatar_url", "avatar", "picture_url", "picture", "logo_url", "logo")
            "url" -> listOf("video_url", "absolute_url", "url", "link")
            else -> listOf(canonicalField)
        }

        for (alias in aliases) {
            if (obj.has(alias)) {
                val value = obj.opt(alias)
                if (value != null && value.toString().isNotBlank() && value.toString() != "null") {
                    return ExtractedValue(value, 0.6, "alias_fallback")
                }
            }
            if (alias.contains(".")) {
                val parts = alias.split(".")
                var current: Any? = obj
                for (part in parts) {
                    current = when (current) {
                        is JSONObject -> current.opt(part)
                        else -> null
                    }
                }
                if (current != null && current.toString().isNotBlank() && current.toString() != "null") {
                    return ExtractedValue(current, 0.55, "nested_alias")
                }
            }
        }
        return null
    }

    fun getString(obj: JSONObject, field: String, endpointHint: String? = null, default: String = ""): String {
        val extracted = extract(obj, field, endpointHint) ?: return default
        return when (val value = extracted.value) {
            is String -> if (value.isNotBlank() && value != "null") value else default
            is Number -> value.toString()
            is JSONObject -> {
                value.optString("value", null as String?)
                    ?: value.optString("name", null as String?)
                    ?: value.optString("title", null as String?)
                    ?: value.optString("code", null as String?)
                    ?: value.optString("id", null as String?)
                    ?: default
            }
            else -> value.toString().takeIf { it.isNotBlank() && it != "null" } ?: default
        }
    }

    fun getInt(obj: JSONObject, field: String, endpointHint: String? = null, default: Int = 0): Int {
        val extracted = extract(obj, field, endpointHint) ?: return default
        return when (val value = extracted.value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: default
            else -> default
        }
    }

    fun getLong(obj: JSONObject, field: String, endpointHint: String? = null, default: Long = 0L): Long {
        val extracted = extract(obj, field, endpointHint) ?: return default
        return when (val value = extracted.value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: default
            else -> default
        }
    }

    fun getDouble(obj: JSONObject, field: String, endpointHint: String? = null, default: Double = 0.0): Double {
        val extracted = extract(obj, field, endpointHint) ?: return default
        return when (val value = extracted.value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: default
            else -> default
        }
    }

    fun getBoolean(obj: JSONObject, field: String, endpointHint: String? = null, default: Boolean = false): Boolean {
        val str = getString(obj, field, endpointHint).lowercase()
        return when (str) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> default
        }
    }

    fun getObject(obj: JSONObject, field: String, endpointHint: String? = null): JSONObject? {
        val extracted = extract(obj, field, endpointHint)
        return extracted?.value as? JSONObject
    }

    fun getArray(obj: JSONObject, field: String, endpointHint: String? = null): JSONArray? {
        val extracted = extract(obj, field, endpointHint)
        return extracted?.value as? JSONArray
    }

    data class ExtractedValue(
        val value: Any,
        val confidence: Double,
        val source: String
    )
}
