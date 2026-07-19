package com.example.data.rutube.parser

import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object PaginationExtractor {
    fun extract(json: JSONObject): ResponsePagination {
        if (json.has("has_next") || json.has("next")) {
            return ResponsePagination(
                hasNext = json.optBoolean("has_next", json.has("next")),
                nextUrl = normalizeUrl(json.optString("next", "")),
                page = json.optInt("page", 1),
                perPage = json.optInt("per_page", 20),
                total = json.optInt("total", 0)
            )
        }
        val pagination = json.optJSONObject("pagination")
        if (pagination != null) {
            return ResponsePagination(
                hasNext = pagination.optBoolean("has_next", false),
                nextUrl = normalizeUrl(pagination.optString("next", "")),
                page = pagination.optInt("page", 1),
                perPage = pagination.optInt("per_page", 20),
                total = pagination.optInt("total", 0)
            )
        }
        return ResponsePagination(nextUrl = null)
    }
}
