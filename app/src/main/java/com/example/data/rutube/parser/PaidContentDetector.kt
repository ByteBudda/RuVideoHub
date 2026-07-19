package com.example.data.rutube.parser

import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object PaidContentDetector {

    fun check(json: JSONObject, endpointHint: String? = null): PaidCheck {
        val subCodes = AdaptiveExtractor.getArray(json, "common_subscription_product_codes", endpointHint)
        if (subCodes != null && subCodes.length() > 0) {
            val codes = (0 until subCodes.length()).map { subCodes.optString(it) }
            val paidCode = codes.find { code ->
                ParserConfig.PAID_SUBSCRIPTION_CODES.any { code.contains(it, ignoreCase = true) }
            }
            if (paidCode != null) {
                val partner = detectPartnerFromCodes(codes)
                return PaidCheck(true, "Subscription: $paidCode", 0.99, partner, true)
            }
        }

        val authorName = AdaptiveExtractor.getString(json, "author", endpointHint)
        val feedName = AdaptiveExtractor.getString(json, "feed_name", endpointHint)
        val title = AdaptiveExtractor.getString(json, "title", endpointHint).lowercase()
        val desc = AdaptiveExtractor.getString(json, "description", endpointHint).lowercase()

        for (partner in ParserConfig.PAID_PARTNERS) {
            if (authorName.contains(partner, ignoreCase = true) ||
                feedName.contains(partner, ignoreCase = true)) {
                return PaidCheck(true, "Partner: $partner", 0.95, partner, true)
            }
        }

        if (json.optBoolean("is_paid", false)) {
            return PaidCheck(true, "is_paid flag", 0.92)
        }

        if (json.optString("origin_type") == "uma") {
            return PaidCheck(true, "UMA origin", 0.6)
        }

        if (json.optBoolean("is_licensed", false) && json.optBoolean("is_official", false)) {
            return PaidCheck(true, "Licensed official", 0.65)
        }

        if (ParserConfig.PAID_KEYWORDS.any { title.contains(it) || desc.contains(it) }) {
            return PaidCheck(true, "Paid keywords", 0.85)
        }

        val duration = AdaptiveExtractor.getDouble(json, "duration", endpointHint, 0.0)
        val isTvShow = json.has("seasons_count") || json.optJSONObject("content_type")?.optString("model") == "tv"
        if (isTvShow && duration in 1.0..180.0) {
            return PaidCheck(true, "Short TV trailer", 0.8)
        }

        return PaidCheck(false, "Free", 0.95)
    }

    fun shouldExclude(json: JSONObject, endpointHint: String? = null): Boolean {
        val check = check(json, endpointHint)
        return check.isPaid && check.confidence > PAID_CONFIDENCE_THRESHOLD
    }

    private fun detectPartnerFromCodes(codes: List<String>): String? {
        return when {
            codes.any { it.contains("PREMIER", ignoreCase = true) } -> "PREMIER"
            codes.any { it.contains("START", ignoreCase = true) } -> "START"
            codes.any { it.contains("IVI", ignoreCase = true) } -> "IVI"
            codes.any { it.contains("KION", ignoreCase = true) } -> "KION"
            else -> null
        }
    }
}
