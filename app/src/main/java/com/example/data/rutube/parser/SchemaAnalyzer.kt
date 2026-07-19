package com.example.data.rutube.parser

import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object SchemaAnalyzer {

    fun buildSignature(obj: JSONObject, depth: Int = 0): EntitySignature {
        if (depth > 3) return EntitySignature(emptyMap(), emptyList())

        val fieldMap = mutableMapOf<String, MutableList<FieldConfidence>>()
        val nestedArrays = mutableListOf<String>()

        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.opt(key)

            val guesses = HeuristicValueAnalyzer.analyzeValue(key, value, obj)
            for (guess in guesses) {
                fieldMap.getOrPut(guess.canonicalField) { mutableListOf() }
                    .add(FieldConfidence(guess.confidence, key, guess.reason))
            }

            if (value is JSONArray && value.length() > 0) {
                if (value.length() > 0 && value.optJSONObject(0) != null) {
                    nestedArrays.add(key)
                }
            }
        }

        val bestFields = fieldMap.mapValues { (_, confidences) ->
            confidences.maxByOrNull { it.confidence } ?: FieldConfidence(0.0, "", "")
        }

        return EntitySignature(bestFields, nestedArrays)
    }

    fun detectEntityType(signature: EntitySignature, url: String? = null): EntityType {
        val fields = signature.fields
        val has = { key: String -> (fields[key]?.confidence ?: 0.0) > FIELD_CONFIDENCE_THRESHOLD }

        val urlLower = url?.lowercase() ?: ""
        when {
            urlLower.contains("/search/") -> return EntityType.VIDEO_LIST
            urlLower.contains("/feeds/") || urlLower.contains("/promogroup/") -> return EntityType.FEED_CATALOG
            urlLower.contains("/video/") && !urlLower.contains("/api/") -> return EntityType.VIDEO_ITEM
            urlLower.contains("/channel/") || urlLower.contains("/person/") -> return EntityType.CHANNEL
            urlLower.contains("/tv/") || urlLower.contains("/serial/") -> return EntityType.TV_SERIES
            (urlLower.contains("/playlist/") || urlLower.contains("/plst/")) && !urlLower.contains("/videos") -> return EntityType.PLAYLIST
        }

        return when {
            has("seasons") && has("title") -> EntityType.TV_SERIES
            (has("videos") || has("video_count") || has("videos_count") || has("count")) && !has("subscribers") -> EntityType.PLAYLIST
            has("subscribers") && has("avatar") -> EntityType.CHANNEL
            signature.nestedArrays.isNotEmpty() && !has("duration") -> EntityType.CONTAINER
            has("duration") && has("thumbnail") && has("title") -> EntityType.VIDEO_ITEM
            has("title") && has("thumbnail") && !has("subscribers") -> EntityType.PROMO_GROUP
            else -> EntityType.UNKNOWN
        }
    }
}
