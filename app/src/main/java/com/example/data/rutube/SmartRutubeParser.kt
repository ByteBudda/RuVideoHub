package com.example.data.rutube

import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * SMART RUTUBE PARSER V2 — AI-парсер с эвристическим пониманием схемы
 *
 * Принцип: не полагаться на жёсткие имена полей, а "понимать" JSON по:
 *  1. Сигнатуре объекта (какие поля присутствуют и их типы)
 *  2. Значениям полей (URL картинки, длительность в секундах, счётчики)
 *  3. Контексту эндпоинта (что ожидать от /search/ vs /feeds/)
 *  4. Рекурсивному поиску массивов карточек на любой глубине
 */
object SmartRutubeParser {

    // ==================== CONFIGURATION ====================

    private const val MAX_RECURSION_DEPTH = 5
    private const val PAID_CONFIDENCE_THRESHOLD = 0.7
    private const val FIELD_CONFIDENCE_THRESHOLD = 0.5
    private const val LEARNING_CACHE_SIZE = 500

    // ==================== SELF-LEARNING CACHE ====================

    /**
     * Кэш сопоставлений: endpointPattern -> (jsonPath -> FieldMapping)
     * Позволяет "запоминать" структуру конкретных эндпоинтов
     */
    private val fieldLearningCache = ConcurrentHashMap<String, MutableMap<String, FieldMapping>>()
    private val schemaSignatureCache = ConcurrentHashMap<String, EntitySignature>()

    data class FieldMapping(
        val canonicalName: String,
        val confidence: Double,
        val sourcePath: String
    )

    // ==================== ENUMS ====================

    enum class EntityType {
        FEED_CATALOG, CATEGORY_FEED, CONTAINER, VIDEO_LIST, VIDEO_ITEM,
        CHANNEL, TV_SERIES, PROMO_GROUP, PLAYLIST, LIVE_STREAM, EXTERNAL, UNKNOWN, EMPTY
    }

    enum class EndpointPattern {
        SEARCH_COMBINED, SEARCH_VIDEO, SEARCH_CHANNEL, SEARCH_PERSON,
        FEEDS_SHOWCASE, FEEDS_CATEGORY, FEEDS_PROMOGROUP, FEEDS_POPULAR,
        VIDEO_DETAIL, CHANNEL_DETAIL, PLAYLIST_DETAIL, TV_DETAIL, UNKNOWN
    }

    // ==================== PAID CONTENT DETECTION (enhanced) ====================

    private val PAID_PARTNERS = setOf(
        "PREMIER", "START", "IVI", "KION", "MORE.TV", "OKKO", "WINK", "AMEDIATEKA",
        "PREMIER.RUTUBE", "START.RUTUBE", "IVI.RUTUBE", "KION.RUTUBE"
    )

    private val PAID_SUBSCRIPTION_CODES = setOf(
        "PREMIER_RUTUBE_YAPPY", "PREMIER_RUTUBE_START", "PREMIER_RUTUBE_GAZPROM_BONUS",
        "premier-rutube-gazprom-bonus-lite", "START_RUTUBE", "IVI_RUTUBE", "KION_RUTUBE",
        "PREMIER", "START", "IVI", "KION"
    )

    private val PAID_KEYWORDS = listOf(
        "оформить подписку", "только по подписке", "доступно в подписке",
        "трейлер сериала", "подписка", "премиум", "premium", "subscription only"
    )

    data class PaidCheck(
        val isPaid: Boolean,
        val reason: String,
        val confidence: Double,
        val partner: String? = null,
        val requiresSubscription: Boolean = false
    )

    // ==================== HEURISTIC VALUE ANALYZER ====================

    /**
     * Анализирует ЗНАЧЕНИЕ поля и определяет, что это за каноническое поле.
     * Работает независимо от имени поля!
     */
    object HeuristicValueAnalyzer {

        // Паттерны для определения типа по значению
        private val THUMBNAIL_PATTERNS = listOf(
            Pattern.compile("https?://.*\.(jpg|jpeg|png|webp|gif)(\?.*)?$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("https?://.*thumb.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("https?://.*pic\.rtbcdn.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("https?://.*poster.*", Pattern.CASE_INSENSITIVE)
        )

        private val VIDEO_URL_PATTERNS = listOf(
            Pattern.compile("https?://.*rutube\.ru/video/[a-f0-9]+.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("https?://.*rutube\.ru/shorts/[a-f0-9]+.*", Pattern.CASE_INSENSITIVE)
        )

        private val DURATION_PATTERN = Pattern.compile("^\d+(:\d+)+$")
        private val ISO_DATE_PATTERN = Pattern.compile("^\d{4}-\d{2}-\d{2}.*")
        private val RUTUBE_DATE_PATTERN = Pattern.compile("^\d{4}-\d{2}-\d{2}T\d{2}:")

        fun analyzeValue(key: String, value: Any?, parentContext: JSONObject? = null): List<FieldGuess> {
            val guesses = mutableListOf<FieldGuess>()
            if (value == null) return guesses

            val strValue = value.toString()
            val keyLower = key.lowercase()

            // 1. Анализ строковых значений
            if (value is String && strValue.isNotBlank()) {
                // Thumbnail / Image
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

                // Video URL / ID
                if (VIDEO_URL_PATTERNS.any { it.matcher(strValue).matches() }) {
                    guesses.add(FieldGuess("video_url", 0.9, "rutube_video_pattern"))
                }

                // Duration string (e.g. "12:34")
                if (DURATION_PATTERN.matcher(strValue).matches()) {
                    val confidence = if (keyLower.contains("duration") || keyLower.contains("length")) 0.95 else 0.7
                    guesses.add(FieldGuess("duration", confidence, "duration_format"))
                }

                // Date
                if (RUTUBE_DATE_PATTERN.matcher(strValue).matches() || ISO_DATE_PATTERN.matcher(strValue).matches()) {
                    val confidence = if (keyLower.contains("date") || keyLower.contains("created") ||
                                       keyLower.contains("published") || keyLower.contains("time")) 0.95 else 0.7
                    guesses.add(FieldGuess("created", confidence, "date_format"))
                }

                // Title (короткая строка без спецсимволов, часто в начале объекта)
                if (strValue.length in 3..200 && !strValue.startsWith("http") &&
                    !strValue.matches(Regex("^\d+$"))) {
                    val confidence = if (keyLower.contains("title") || keyLower.contains("name") ||
                                       keyLower.contains("heading")) 0.9 else 0.4
                    guesses.add(FieldGuess("title", confidence, "text_heuristic"))
                }

                // Description (длинная строка)
                if (strValue.length > 50) {
                    val confidence = if (keyLower.contains("desc") || keyLower.contains("about") ||
                                       keyLower.contains("content")) 0.9 else 0.45
                    guesses.add(FieldGuess("description", confidence, "long_text"))
                }
            }

            // 2. Анализ числовых значений
            if (value is Number) {
                val num = value.toLong()
                val dbl = value.toDouble()

                // Views / Counters (большие числа > 100)
                if (num > 100) {
                    val confidence = if (keyLower.contains("view") || keyLower.contains("hit") ||
                                       keyLower.contains("watch") || keyLower.contains("count")) 0.9 else 0.5
                    guesses.add(FieldGuess("views", confidence, "large_number"))
                }

                // Subscribers (средние числа, часто рядом с именем автора)
                if (num > 10 && parentContext?.has("avatar") == true) {
                    val confidence = if (keyLower.contains("sub") || keyLower.contains("follow")) 0.95 else 0.55
                    guesses.add(FieldGuess("subscribers", confidence, "number_with_avatar"))
                }

                // Duration in seconds (обычно < 86400)
                if (dbl > 0 && dbl < 86400 && (keyLower.contains("duration") || keyLower.contains("length"))) {
                    guesses.add(FieldGuess("duration_seconds", 0.95, "duration_numeric"))
                }

                // Seasons count (обычно 1-50)
                if (num in 1..50 && (keyLower.contains("season") || keyLower.contains("seas"))) {
                    guesses.add(FieldGuess("seasons_count", 0.95, "seasons_numeric"))
                }

                // Year (1900-2030)
                if (num in 1900..2030 && (keyLower.contains("year") || keyLower.contains("release"))) {
                    guesses.add(FieldGuess("year", 0.95, "year_numeric"))
                }

                // Rating (0.0 - 10.0)
                if (dbl in 0.0..10.0 && (keyLower.contains("rating") || keyLower.contains("score") || keyLower.contains("rate"))) {
                    guesses.add(FieldGuess("rating", 0.9, "rating_numeric"))
                }
            }

            // 3. Анализ по имени ключа (fallback, но с высоким весом если имя точное)
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

    // ==================== SCHEMA SIGNATURE BUILDER ====================

    /**
     * Строит "сигнатуру" JSON-объекта — набор канонических полей с confidence.
     * Это позволяет понять, что за сущность перед нами, даже если имена полей неизвестны.
     */
    object SchemaAnalyzer {

        fun buildSignature(obj: JSONObject, depth: Int = 0): EntitySignature {
            if (depth > 3) return EntitySignature(emptyMap(), emptyList())

            val fieldMap = mutableMapOf<String, MutableList<FieldConfidence>>()
            val nestedArrays = mutableListOf<String>()

            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.opt(key)

                // Анализируем значение эвристически
                val guesses = HeuristicValueAnalyzer.analyzeValue(key, value, obj)
                for (guess in guesses) {
                    fieldMap.getOrPut(guess.canonicalField) { mutableListOf() }
                        .add(FieldConfidence(guess.confidence, key, guess.reason))
                }

                // Ищем вложенные массивы (потенциальные списки карточек)
                if (value is JSONArray && value.length() > 0) {
                    // Проверим, является ли это массивом объектов
                    if (value.length() > 0 && value.optJSONObject(0) != null) {
                        nestedArrays.add(key)
                    }
                }
            }

            // Выбираем лучший confidence для каждого канонического поля
            val bestFields = fieldMap.mapValues { (_, confidences) ->
                confidences.maxByOrNull { it.confidence } ?: FieldConfidence(0.0, "", "")
            }

            return EntitySignature(bestFields, nestedArrays)
        }

        fun detectEntityType(signature: EntitySignature, url: String? = null): EntityType {
            val fields = signature.fields
            val has = { key: String -> (fields[key]?.confidence ?: 0.0) > FIELD_CONFIDENCE_THRESHOLD }

            // Определяем по URL
            val urlLower = url?.lowercase() ?: ""
            when {
                urlLower.contains("/search/") -> return EntityType.VIDEO_LIST
                urlLower.contains("/feeds/") || urlLower.contains("/promogroup/") -> return EntityType.FEED_CATALOG
                urlLower.contains("/video/") && !urlLower.contains("/api/") -> return EntityType.VIDEO_ITEM
                urlLower.contains("/channel/") || urlLower.contains("/person/") -> return EntityType.CHANNEL
                urlLower.contains("/tv/") || urlLower.contains("/serial/") -> return EntityType.TV_SERIES
                urlLower.contains("/playlist/") -> return EntityType.PLAYLIST
            }

            // Определяем по сигнатуре полей
            return when {
                has("duration") && has("thumbnail") && has("title") && !has("seasons") && !has("subscribers") -> EntityType.VIDEO_ITEM
                has("seasons") && has("title") -> EntityType.TV_SERIES
                has("subscribers") && has("avatar") && !has("duration") -> EntityType.CHANNEL
                has("videos") && !has("duration") && !has("subscribers") -> EntityType.PLAYLIST
                signature.nestedArrays.isNotEmpty() && !has("duration") -> EntityType.CONTAINER
                has("title") && has("thumbnail") && !has("duration") && !has("subscribers") -> EntityType.PROMO_GROUP
                else -> EntityType.UNKNOWN
            }
        }
    }

    data class EntitySignature(
        val fields: Map<String, FieldConfidence>,
        val nestedArrays: List<String>
    )

    data class FieldConfidence(
        val confidence: Double,
        val sourceKey: String,
        val reason: String
    )

    // ==================== ADAPTIVE EXTRACTOR V2 ====================

    object AdaptiveExtractorV2 {

        /**
         * Извлекает значение по каноническому имени, используя:
         * 1. Кэш обучения для этого эндпоинта
         * 2. Эвристический анализ всех полей объекта
         * 3. Точные алиасы как fallback
         */
        fun extract(obj: JSONObject, canonicalField: String, endpointHint: String? = null): ExtractedValue? {
            // Пробуем кэш обучения
            if (endpointHint != null) {
                val cached = fieldLearningCache[endpointHint]?.get(canonicalField)
                if (cached != null) {
                    val raw = obj.opt(cached.sourcePath)
                    if (raw != null && raw.toString().isNotBlank() && raw.toString() != "null") {
                        return ExtractedValue(raw, cached.confidence, "learned_cache")
                    }
                }
            }

            // Эвристический анализ всех полей
            val signature = SchemaAnalyzer.buildSignature(obj)
            val bestField = signature.fields[canonicalField]
            if (bestField != null && bestField.confidence > FIELD_CONFIDENCE_THRESHOLD) {
                val raw = obj.opt(bestField.sourceKey)
                if (raw != null && raw.toString().isNotBlank() && raw.toString() != "null") {
                    // Сохраняем в кэш обучения
                    if (endpointHint != null) {
                        val endpointCache = fieldLearningCache.getOrPut(endpointHint) { mutableMapOf() }
                        endpointCache[canonicalField] = FieldMapping(canonicalField, bestField.confidence, bestField.sourceKey)
                        // Ограничиваем размер кэша
                        if (endpointCache.size > LEARNING_CACHE_SIZE) {
                            endpointCache.entries.iterator().next().also { endpointCache.remove(it.key) }
                        }
                    }
                    return ExtractedValue(raw, bestField.confidence, bestField.reason)
                }
            }

            // Fallback на стандартные алиасы V1
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
                else -> listOf(canonicalField)
            }

            for (alias in aliases) {
                // Прямой ключ
                if (obj.has(alias)) {
                    val value = obj.opt(alias)
                    if (value != null && value.toString().isNotBlank() && value.toString() != "null") {
                        return ExtractedValue(value, 0.6, "alias_fallback")
                    }
                }
                // Вложенный путь через точку
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
                    value.optString("value", null)
                        ?: value.optString("name", null)
                        ?: value.optString("title", null)
                        ?: value.optString("code", null)
                        ?: value.optString("id", null)
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

    // ==================== RECURSIVE CARD FINDER ====================

    /**
     * Рекурсивно ищет массивы объектов-карточек на любой глубине.
     * Использует эвристики: предпочитает массивы объектов с полями title/thumbnail/id.
     */
    object RecursiveCardFinder {

        fun findCardArrays(json: JSONObject, depth: Int = 0): List<JSONArray> {
            if (depth > MAX_RECURSION_DEPTH) return emptyList()
            val results = mutableListOf<JSONArray>()
            val visited = mutableSetOf<String>()

            findRecursive(json, depth, results, visited)

            // Сортируем по "качеству" массива (чем больше объектов с title/thumbnail, тем лучше)
            return results.sortedByDescending { scoreArray(it) }
        }

        private fun findRecursive(obj: Any?, depth: Int, results: MutableList<JSONArray>, visited: MutableSet<String>) {
            if (depth > MAX_RECURSION_DEPTH) return
            if (obj == null) return

            when (obj) {
                is JSONObject -> {
                    val identity = obj.toString().take(200)
                    if (identity in visited) return
                    visited.add(identity)

                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = obj.opt(key)

                        if (value is JSONArray && value.length() > 0) {
                            // Проверяем, похож ли массив на список карточек
                            if (looksLikeCardArray(value)) {
                                results.add(value)
                            } else {
                                // Иначе рекурсивно ищем внутри
                                for (i in 0 until value.length()) {
                                    findRecursive(value.opt(i), depth + 1, results, visited)
                                }
                            }
                        } else if (value is JSONObject) {
                            findRecursive(value, depth + 1, results, visited)
                        }
                    }
                }
                is JSONArray -> {
                    for (i in 0 until obj.length()) {
                        findRecursive(obj.opt(i), depth + 1, results, visited)
                    }
                }
            }
        }

        private fun looksLikeCardArray(arr: JSONArray): Boolean {
            if (arr.length() == 0) return false
            // Проверяем первые 3 элемента
            val sampleSize = minOf(arr.length(), 3)
            var score = 0
            for (i in 0 until sampleSize) {
                val obj = arr.optJSONObject(i) ?: continue
                val sig = SchemaAnalyzer.buildSignature(obj)
                // Если есть title + (thumbnail или id) — это карточки
                val hasTitle = (sig.fields["title"]?.confidence ?: 0.0) > 0.5
                val hasThumb = (sig.fields["thumbnail"]?.confidence ?: 0.0) > 0.5
                val hasId = (sig.fields["id"]?.confidence ?: 0.0) > 0.5
                if (hasTitle && (hasThumb || hasId)) score++
            }
            return score >= sampleSize / 2
        }

        private fun scoreArray(arr: JSONArray): Int {
            if (arr.length() == 0) return 0
            var score = 0
            val sampleSize = minOf(arr.length(), 5)
            for (i in 0 until sampleSize) {
                val obj = arr.optJSONObject(i) ?: continue
                val sig = SchemaAnalyzer.buildSignature(obj)
                if ((sig.fields["title"]?.confidence ?: 0.0) > 0.5) score += 3
                if ((sig.fields["thumbnail"]?.confidence ?: 0.0) > 0.5) score += 2
                if ((sig.fields["id"]?.confidence ?: 0.0) > 0.5) score += 2
                if ((sig.fields["duration"]?.confidence ?: 0.0) > 0.5) score += 1
            }
            return score
        }
    }

    // ==================== PAID CONTENT DETECTOR V2 ====================

    object PaidContentDetectorV2 {

        fun check(json: JSONObject, endpointHint: String? = null): PaidCheck {
            // 1. Коды подписки (высший приоритет)
            val subCodes = AdaptiveExtractorV2.getArray(json, "common_subscription_product_codes", endpointHint)
            if (subCodes != null && subCodes.length() > 0) {
                val codes = (0 until subCodes.length()).map { subCodes.optString(it) }
                val paidCode = codes.find { code ->
                    PAID_SUBSCRIPTION_CODES.any { code.contains(it, ignoreCase = true) }
                }
                if (paidCode != null) {
                    val partner = detectPartnerFromCodes(codes)
                    return PaidCheck(true, "Subscription: $paidCode", 0.99, partner, true)
                }
            }

            // 2. Партнёры в авторе/названии фида
            val authorName = AdaptiveExtractorV2.getString(json, "author", endpointHint)
            val feedName = AdaptiveExtractorV2.getString(json, "feed_name", endpointHint)
            val title = AdaptiveExtractorV2.getString(json, "title", endpointHint).lowercase()
            val desc = AdaptiveExtractorV2.getString(json, "description", endpointHint).lowercase()

            for (partner in PAID_PARTNERS) {
                if (authorName.contains(partner, ignoreCase = true) ||
                    feedName.contains(partner, ignoreCase = true)) {
                    return PaidCheck(true, "Partner: $partner", 0.95, partner, true)
                }
            }

            // 3. Прямой флаг
            if (json.optBoolean("is_paid", false)) {
                return PaidCheck(true, "is_paid flag", 0.92)
            }

            // 4. UMA контент
            if (json.optString("origin_type") == "uma") {
                return PaidCheck(true, "UMA origin", 0.6)
            }

            // 5. Лицензионный + официальный
            if (json.optBoolean("is_licensed", false) && json.optBoolean("is_official", false)) {
                return PaidCheck(true, "Licensed official", 0.65)
            }

            // 6. Ключевые слова
            if (PAID_KEYWORDS.any { title.contains(it) || desc.contains(it) }) {
                return PaidCheck(true, "Paid keywords", 0.85)
            }

            // 7. Короткий трейлер сериала
            val duration = AdaptiveExtractorV2.getDouble(json, "duration", endpointHint, 0.0)
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

    // ==================== NORMALIZED CARDS (same structure, enhanced) ====================

    sealed class NormalizedCard {
        data class VideoCard(
            val id: String,
            val title: String,
            val thumbnail: String?,
            val previewGif: String?,
            val duration: String,
            val channelName: String,
            val channelId: String?,
            val channelAvatar: String?,
            val views: String,
            val rawViews: Long = 0,
            val published: String,
            val publishedTimestamp: Long = 0,
            val rating: String?,
            val isPaid: Boolean,
            val paidReason: String? = null,
            val requiresSubscription: Boolean = false,
            val partner: String? = null,
            val description: String,
            val tags: List<String> = emptyList(),
            val actionUrl: String? = null,
            val confidence: Double = 1.0 // уверенность парсера в этой карте
        ) : NormalizedCard()

        data class TvSeriesCard(
            val id: String,
            val title: String,
            val originalTitle: String? = null,
            val poster: String?,
            val year: String?,
            val rating: Double?,
            val seasonsCount: Int,
            val description: String?,
            val isPaid: Boolean,
            val paidReason: String? = null,
            val requiresSubscription: Boolean = false,
            val partner: String? = null,
            val actionUrl: String? = null,
            val confidence: Double = 1.0
        ) : NormalizedCard()

        data class ChannelCard(
            val id: String,
            val name: String,
            val avatar: String?,
            val description: String?,
            val subscribers: String,
            val rawSubscribers: Long = 0,
            val videosCount: Int,
            val actionUrl: String? = null,
            val confidence: Double = 1.0
        ) : NormalizedCard()

        data class PromoCard(
            val id: String,
            val title: String,
            val thumbnail: String?,
            val description: String?,
            val actionUrl: String?,
            val confidence: Double = 1.0
        ) : NormalizedCard()

        data class PlaylistCard(
            val id: String,
            val title: String,
            val thumbnail: String?,
            val videosCount: Int,
            val actionUrl: String? = null,
            val confidence: Double = 1.0
        ) : NormalizedCard()

        data class UnknownCard(
            val id: String,
            val title: String,
            val thumbnail: String?,
            val rawType: String?,
            val actionUrl: String? = null,
            val extractedFields: Map<String, String> = emptyMap(), // ВСЕ извлечённые поля для отладки
            val confidence: Double = 0.5
        ) : NormalizedCard()
    }

    // ==================== UNIVERSAL CARD NORMALIZER ====================

    object UniversalNormalizer {

        fun normalizeOrNull(json: JSONObject, endpointHint: String? = null): NormalizedCard? {
            if (PaidContentDetectorV2.shouldExclude(json, endpointHint)) return null

            // Определяем, есть ли вложенный объект
            val data = if (json.has("object") && json.optJSONObject("object") != null)
                json.optJSONObject("object")!! else json

            // Строим сигнатуру и определяем тип
            val signature = SchemaAnalyzer.buildSignature(data)
            val detectedType = SchemaAnalyzer.detectEntityType(signature, endpointHint)

            // Также проверяем content_type.model если есть
            val model = extractModel(json, data)

            return when {
                detectedType == EntityType.VIDEO_ITEM || model in listOf("video", "live", "shorts") ->
                    normalizeVideo(data, signature, endpointHint)
                detectedType == EntityType.TV_SERIES || model in listOf("tv", "show", "serial", "tvshow", "movie") ->
                    normalizeTvShow(data, signature, endpointHint)
                detectedType == EntityType.CHANNEL || model in listOf("userchannel", "person", "channel", "author", "user") ->
                    normalizeChannel(data, signature, endpointHint)
                detectedType == EntityType.PLAYLIST || model == "playlist" ->
                    normalizePlaylist(data, signature, endpointHint)
                detectedType == EntityType.PROMO_GROUP || json.has("button") || json.has("target") || model == "promo" ->
                    normalizePromo(json, endpointHint)
                else -> {
                    // Fallback: пытаемся создать UnknownCard с максимумом данных
                    normalizeUnknown(data, signature, endpointHint)
                }
            }
        }

        private fun extractModel(outer: JSONObject, inner: JSONObject): String {
            val sources = listOf(
                outer.optJSONObject("content_type")?.optString("model"),
                inner.optJSONObject("content_type")?.optString("model"),
                outer.optJSONObject("type")?.optString("name"),
                inner.optJSONObject("type")?.optString("name"),
                outer.optJSONObject("type")?.optString("code"),
                inner.optJSONObject("type")?.optString("code"),
                outer.optString("type", null),
                inner.optString("type", null),
                outer.optString("model", null),
                inner.optString("model", null)
            )
            return sources.filterNotNull().firstOrNull { it.isNotBlank() } ?: ""
        }

        private fun normalizeVideo(data: JSONObject, sig: EntitySignature, endpointHint: String?): NormalizedCard.VideoCard {
            val id = AdaptiveExtractorV2.getString(data, "id", endpointHint)
                .takeIf { it.isNotBlank() }
                ?: AdaptiveExtractorV2.getString(data, "code", endpointHint)
                ?: makeId("video", data, endpointHint)

            val author = AdaptiveExtractorV2.getObject(data, "author", endpointHint)
            val authorName = AdaptiveExtractorV2.getString(author ?: data, "title", endpointHint)
                .takeIf { it.isNotBlank() }
                ?: AdaptiveExtractorV2.getString(data, "feed_name", endpointHint)
                ?: "Rutube"

            val viewsCount = AdaptiveExtractorV2.getLong(data, "views", endpointHint)
            val durationSeconds = AdaptiveExtractorV2.getDouble(data, "duration", endpointHint, -1.0)
            val paidCheck = PaidContentDetectorV2.check(data, endpointHint)

            val isLive = data.optJSONObject("type")?.optInt("id") == 12
                    || data.optString("type") == "live"
                    || data.optBoolean("is_live", false)

            val ageVal = data.optJSONObject("pg_rating")?.opt("age")?.toString() ?: ""
            val rawRating = AdaptiveExtractorV2.getString(data, "rating", endpointHint).takeIf { it.isNotBlank() }
            val ratingWithAge = if (ageVal.isNotBlank() && ageVal != "null") {
                if (rawRating != null) "$rawRating ($ageVal+)" else "$ageVal+"
            } else rawRating

            val actionUrl = normalizeUrl(
                AdaptiveExtractorV2.getString(data, "absolute_url", endpointHint)
                    .takeIf { it.isNotBlank() }
                    ?: AdaptiveExtractorV2.getString(data, "url", endpointHint)
            )

            return NormalizedCard.VideoCard(
                id = id,
                title = AdaptiveExtractorV2.getString(data, "title", endpointHint, "Untitled"),
                thumbnail = AdaptiveExtractorV2.getString(data, "thumbnail", endpointHint).takeIf { it.isNotBlank() },
                previewGif = AdaptiveExtractorV2.getString(data, "preview_url", endpointHint).takeIf { it.isNotBlank() },
                duration = if (isLive) "ЭФИР" else if (durationSeconds > 0) formatDuration(durationSeconds) else "00:00",
                channelName = authorName,
                channelId = AdaptiveExtractorV2.getString(author ?: data, "id", endpointHint).takeIf { it.isNotBlank() },
                channelAvatar = AdaptiveExtractorV2.getString(author ?: data, "thumbnail", endpointHint).takeIf { it.isNotBlank() },
                views = formatCount(viewsCount),
                rawViews = viewsCount,
                published = formatDate(AdaptiveExtractorV2.getString(data, "created", endpointHint)),
                publishedTimestamp = parseTimestamp(AdaptiveExtractorV2.getString(data, "created", endpointHint)),
                rating = ratingWithAge,
                isPaid = paidCheck.isPaid,
                paidReason = paidCheck.reason.takeIf { paidCheck.isPaid },
                requiresSubscription = paidCheck.requiresSubscription,
                partner = paidCheck.partner,
                description = AdaptiveExtractorV2.getString(data, "description", endpointHint),
                tags = AdaptiveExtractorV2.getArray(data, "tags", endpointHint)?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
                } ?: emptyList(),
                actionUrl = actionUrl,
                confidence = sig.fields.values.map { it.confidence }.average().coerceIn(0.0, 1.0)
            )
        }

        private fun normalizeTvShow(data: JSONObject, sig: EntitySignature, endpointHint: String?): NormalizedCard.TvSeriesCard {
            val actionUrl = normalizeUrl(
                AdaptiveExtractorV2.getString(data, "absolute_url", endpointHint)
                    .takeIf { it.isNotBlank() }
                    ?: AdaptiveExtractorV2.getString(data, "url", endpointHint)
            )
            val id = AdaptiveExtractorV2.getString(data, "id", endpointHint)
                .takeIf { it.isNotBlank() }
                ?: makeId("tv", data, endpointHint)
            val paidCheck = PaidContentDetectorV2.check(data, endpointHint)

            return NormalizedCard.TvSeriesCard(
                id = id,
                title = AdaptiveExtractorV2.getString(data, "title", endpointHint, "Untitled"),
                originalTitle = AdaptiveExtractorV2.getString(data, "original_title", endpointHint).takeIf { it.isNotBlank() },
                poster = AdaptiveExtractorV2.getString(data, "thumbnail", endpointHint).takeIf { it.isNotBlank() },
                year = AdaptiveExtractorV2.getString(data, "year", endpointHint).takeIf { it.isNotBlank() },
                rating = AdaptiveExtractorV2.getDouble(data, "rating", endpointHint).takeIf { it > 0 },
                seasonsCount = AdaptiveExtractorV2.getInt(data, "seasons", endpointHint, 1),
                description = AdaptiveExtractorV2.getString(data, "description", endpointHint).takeIf { it.isNotBlank() },
                isPaid = paidCheck.isPaid,
                paidReason = paidCheck.reason.takeIf { paidCheck.isPaid },
                requiresSubscription = paidCheck.requiresSubscription,
                partner = paidCheck.partner,
                actionUrl = actionUrl,
                confidence = sig.fields.values.map { it.confidence }.average().coerceIn(0.0, 1.0)
            )
        }

        private fun normalizeChannel(data: JSONObject, sig: EntitySignature, endpointHint: String?): NormalizedCard.ChannelCard {
            val url = AdaptiveExtractorV2.getString(data, "url", endpointHint)
            var id = AdaptiveExtractorV2.getString(data, "id", endpointHint)
            if (id.isBlank()) {
                val match = Regex("/(?:person|channel)/(\d+)").find(url)
                id = match?.groupValues?.get(1) ?: makeId("ch", data, endpointHint)
            }
            val subsCount = AdaptiveExtractorV2.getLong(data, "subscribers", endpointHint)

            return NormalizedCard.ChannelCard(
                id = id,
                name = AdaptiveExtractorV2.getString(data, "title", endpointHint, "Untitled"),
                avatar = AdaptiveExtractorV2.getString(data, "thumbnail", endpointHint).takeIf { it.isNotBlank() }
                    ?: AdaptiveExtractorV2.getString(data, "avatar", endpointHint).takeIf { it.isNotBlank() },
                description = AdaptiveExtractorV2.getString(data, "description", endpointHint).takeIf { it.isNotBlank() },
                subscribers = formatCount(subsCount),
                rawSubscribers = subsCount,
                videosCount = AdaptiveExtractorV2.getInt(data, "videos", endpointHint, 0),
                actionUrl = normalizeUrl(url),
                confidence = sig.fields.values.map { it.confidence }.average().coerceIn(0.0, 1.0)
            )
        }

        private fun normalizePlaylist(data: JSONObject, sig: EntitySignature, endpointHint: String?): NormalizedCard.PlaylistCard {
            val url = AdaptiveExtractorV2.getString(data, "url", endpointHint)
            val id = AdaptiveExtractorV2.getString(data, "id", endpointHint)
                .takeIf { it.isNotBlank() }
                ?: AdaptiveExtractorV2.getString(data, "playlist_id", endpointHint)
                ?: makeId("playlist", data, endpointHint)

            val vCount = AdaptiveExtractorV2.getInt(data, "videos", endpointHint, 0)
                .takeIf { it > 0 }
                ?: data.optInt("videos_count", data.optInt("video_count", 0))

            return NormalizedCard.PlaylistCard(
                id = id,
                title = AdaptiveExtractorV2.getString(data, "title", endpointHint, "Untitled"),
                thumbnail = AdaptiveExtractorV2.getString(data, "thumbnail", endpointHint).takeIf { it.isNotBlank() }
                    ?: AdaptiveExtractorV2.getString(data, "picture", endpointHint).takeIf { it.isNotBlank() }
                    ?: AdaptiveExtractorV2.getString(data, "image", endpointHint).takeIf { it.isNotBlank() },
                videosCount = vCount,
                actionUrl = normalizeUrl(url.takeIf { it.isNotBlank() } ?: "https://rutube.ru/api/playlist/custom/$id/videos/"),
                confidence = sig.fields.values.map { it.confidence }.average().coerceIn(0.0, 1.0)
            )
        }

        fun normalizePromo(data: JSONObject, endpointHint: String? = null): NormalizedCard.PromoCard? {
            val button = data.optJSONObject("button")
            val actionUrl = normalizeUrl(
                button?.optString("button_url")?.takeIf { it.isNotBlank() }
                    ?: AdaptiveExtractorV2.getString(data, "target", endpointHint)
                    ?: AdaptiveExtractorV2.getString(data, "url", endpointHint)
            )
            val title = AdaptiveExtractorV2.getString(data, "title", endpointHint, "Untitled")
            val description = AdaptiveExtractorV2.getString(data, "description", endpointHint)

            val checkText = (title + " " + description + " " + actionUrl).lowercase()
            if (isBlockedText(checkText)) return null

            val id = AdaptiveExtractorV2.getString(data, "id", endpointHint)
                .takeIf { it.isNotBlank() }
                ?: makeId("promo", data, endpointHint)

            return NormalizedCard.PromoCard(
                id = id,
                title = title,
                thumbnail = AdaptiveExtractorV2.getString(data, "thumbnail", endpointHint).takeIf { it.isNotBlank() }
                    ?: AdaptiveExtractorV2.getString(data, "picture", endpointHint).takeIf { it.isNotBlank() },
                description = description.takeIf { it.isNotBlank() },
                actionUrl = actionUrl
            )
        }

        private fun normalizeUnknown(data: JSONObject, sig: EntitySignature, endpointHint: String?): NormalizedCard.UnknownCard {
            val url = AdaptiveExtractorV2.getString(data, "url", endpointHint)
            val id = AdaptiveExtractorV2.getString(data, "id", endpointHint)
                .takeIf { it.isNotBlank() }
                ?: makeId("unknown", data, endpointHint)

            // Извлекаем ВСЕ поля для отладки и отображения
            val extractedFields = mutableMapOf<String, String>()
            for (key in data.keys()) {
                val value = data.opt(key)
                if (value != null && value.toString().isNotBlank() && value.toString().length < 500) {
                    extractedFields[key] = value.toString().take(100)
                }
            }

            return NormalizedCard.UnknownCard(
                id = id,
                title = AdaptiveExtractorV2.getString(data, "title", endpointHint, "Unknown"),
                thumbnail = AdaptiveExtractorV2.getString(data, "thumbnail", endpointHint).takeIf { it.isNotBlank() },
                rawType = data.optJSONObject("type")?.optString("name") ?: data.optString("type", "unknown"),
                actionUrl = normalizeUrl(url),
                extractedFields = extractedFields,
                confidence = sig.fields.values.map { it.confidence }.average().coerceIn(0.0, 1.0)
            )
        }

        private fun makeId(prefix: String, data: JSONObject, endpointHint: String?): String {
            val title = AdaptiveExtractorV2.getString(data, "title", endpointHint)
            val url = AdaptiveExtractorV2.getString(data, "url", endpointHint)
            val base = url.takeIf { it.isNotBlank() } ?: title
            val hash = base.hashCode().toString().replace("-", "n")
            return "${prefix}_$hash"
        }
    }

    // ==================== RESPONSE ANALYZER V2 ====================

    object ResponseAnalyzerV2 {

        fun parse(jsonObj: JSONObject, url: String? = null): ParsedResponse {
            val endpointHint = classifyEndpoint(url)
            val isPromoGroup = endpointHint.contains("promogroup") || url?.contains("/promogroup/") == true

            // 1. Проверяем стандартные паттерны
            if (jsonObj.has("tabs")) {
                return parseCatalogFeed(jsonObj, endpointHint, isPromoGroup)
            }

            if (jsonObj.has("results")) {
                return parsePaginatedResponse(jsonObj, endpointHint, isPromoGroup)
            }

            // 2. Проверяем, является ли корень одиночной сущностью
            val rootSig = SchemaAnalyzer.buildSignature(jsonObj)
            val rootType = SchemaAnalyzer.detectEntityType(rootSig, url)

            if (rootType in listOf(EntityType.VIDEO_ITEM, EntityType.TV_SERIES, EntityType.CHANNEL, EntityType.PLAYLIST)) {
                val card = UniversalNormalizer.normalizeOrNull(jsonObj, endpointHint)
                return ParsedResponse(
                    type = rootType,
                    items = card?.let { listOf(it) } ?: emptyList()
                )
            }

            // 3. Рекурсивный поиск карточек на любой глубине
            val cardArrays = RecursiveCardFinder.findCardArrays(jsonObj)
            if (cardArrays.isNotEmpty()) {
                val items = mutableListOf<NormalizedCard>()
                var filteredCount = 0
                for (arr in cardArrays) {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val normalized = if (isPromoGroup) {
                            UniversalNormalizer.normalizePromo(item, endpointHint)
                        } else {
                            UniversalNormalizer.normalizeOrNull(item, endpointHint)
                        }
                        if (normalized != null) items.add(normalized) else filteredCount++
                    }
                }
                val first = items.firstOrNull()
                val overallType = when (first) {
                    is NormalizedCard.PromoCard -> EntityType.PROMO_GROUP
                    is NormalizedCard.TvSeriesCard -> EntityType.TV_SERIES
                    is NormalizedCard.ChannelCard -> EntityType.CHANNEL
                    is NormalizedCard.PlaylistCard -> EntityType.PLAYLIST
                    else -> EntityType.VIDEO_LIST
                }
                return ParsedResponse(
                    type = overallType,
                    items = items,
                    metadata = ResponseMetadata(filteredPaidCount = filteredCount, totalOriginalCount = items.size + filteredCount)
                )
            }

            return ParsedResponse(type = EntityType.UNKNOWN, error = "Unrecognized structure", items = emptyList())
        }

        private fun parseCatalogFeed(jsonObj: JSONObject, endpointHint: String, isPromoGroup: Boolean): ParsedResponse {
            val tabsArray = jsonObj.optJSONArray("tabs") ?: JSONArray()
            val tabList = mutableListOf<TabInfo>()
            var totalResources = 0

            for (i in 0 until tabsArray.length()) {
                val tabObj = tabsArray.optJSONObject(i) ?: continue
                val resourcesArray = tabObj.optJSONArray("resources") ?: JSONArray()
                val resourceList = mutableListOf<ResourceInfo>()

                for (j in 0 until resourcesArray.length()) {
                    val resObj = resourcesArray.optJSONObject(j) ?: continue
                    val contentType = resObj.optJSONObject("content_type")
                    val model = contentType?.optString("model") ?: "unknown"
                    val urlVal = resObj.optString("url", "").lowercase()

                    var detectedType = when (model) {
                        "tag", "playlist" -> EntityType.VIDEO_LIST
                        "tv" -> EntityType.TV_SERIES
                        "cardgroup", "subscriptiontvseries", "promogroup" -> EntityType.CONTAINER
                        "userchannel", "person", "channel", "author" -> EntityType.CHANNEL
                        else -> EntityType.UNKNOWN
                    }

                    if (detectedType == EntityType.UNKNOWN && urlVal.isNotBlank()) {
                        detectedType = when {
                            urlVal.contains("/person/") || urlVal.contains("/channel/") || urlVal.contains("/userchannel/") -> EntityType.CHANNEL
                            urlVal.contains("/tv/") || urlVal.contains("/show/") || urlVal.contains("/serial/") || urlVal.contains("/subscriptiontvseries/") -> EntityType.TV_SERIES
                            urlVal.contains("/playlist/") -> EntityType.PLAYLIST
                            else -> EntityType.UNKNOWN
                        }
                    }

                    val nameVal = resObj.optString("name", "Раздел")
                    val normUrl = normalizeUrl(resObj.optString("url"))
                    if (!isBlockedText((nameVal + " " + normUrl).lowercase())) {
                        resourceList.add(ResourceInfo(nameVal, normUrl, detectedType, resObj.optJSONObject("extra_params")))
                        totalResources++
                    }
                }

                val tabName = tabObj.optString("name", "Вкладка")
                if (!isBlockedText(tabName.lowercase())) {
                    tabList.add(TabInfo(tabObj.optInt("id", 0), tabName, resourceList))
                }
            }

            // Извлечение связанного контента
            val relatedTv = extractRelated<NormalizedCard.TvSeriesCard>(jsonObj, "related_tv", endpointHint)
            val relatedPersons = extractRelated<NormalizedCard.ChannelCard>(jsonObj, "related_person", endpointHint)

            // Собираем items из results + табы
            val items = mutableListOf<NormalizedCard>()
            val resultsArray = jsonObj.optJSONArray("results")
            if (resultsArray != null) {
                for (i in 0 until resultsArray.length()) {
                    val item = resultsArray.optJSONObject(i) ?: continue
                    val nestedCards = item.optJSONArray("cards") ?: item.optJSONArray("items") ?: item.optJSONArray("results")
                    if (nestedCards != null) {
                        for (j in 0 until nestedCards.length()) {
                            val nested = nestedCards.optJSONObject(j) ?: continue
                            val norm = if (isPromoGroup) UniversalNormalizer.normalizePromo(nested, endpointHint) else UniversalNormalizer.normalizeOrNull(nested, endpointHint)
                            norm?.let { items.add(it) }
                        }
                    } else {
                        val norm = if (isPromoGroup) UniversalNormalizer.normalizePromo(item, endpointHint) else UniversalNormalizer.normalizeOrNull(item, endpointHint)
                        norm?.let { items.add(it) }
                    }
                }
            }

            // Добавляем табы как UnknownCard для навигации
            for (tab in tabList) {
                for (res in tab.resources) {
                    items.add(NormalizedCard.UnknownCard(
                        id = "res_${tab.id}_${res.name.hashCode()}",
                        title = res.name,
                        thumbnail = null,
                        rawType = tab.name,
                        actionUrl = res.url
                    ))
                }
            }

            return ParsedResponse(
                type = EntityType.FEED_CATALOG,
                title = AdaptiveExtractorV2.getString(jsonObj, "title", endpointHint, "Каталог"),
                description = AdaptiveExtractorV2.getString(jsonObj, "description", endpointHint).takeIf { it.isNotBlank() },
                tabs = tabList,
                items = items,
                pagination = PaginationExtractor.extract(jsonObj),
                relatedTv = relatedTv,
                relatedPersons = relatedPersons,
                metadata = ResponseMetadata(totalResources = totalResources)
            )
        }

        private fun parsePaginatedResponse(jsonObj: JSONObject, endpointHint: String, isPromoGroup: Boolean): ParsedResponse {
            val resultsArray = jsonObj.optJSONArray("results") ?: JSONArray()
            val pagination = PaginationExtractor.extract(jsonObj)
            if (resultsArray.length() == 0) return ParsedResponse(type = EntityType.EMPTY, pagination = pagination)

            val items = mutableListOf<NormalizedCard>()
            var filteredCount = 0

            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.optJSONObject(i) ?: continue
                // Разворачиваем widget-структуры
                val nestedCards = item.optJSONArray("cards")
                    ?: item.optJSONArray("items")
                    ?: item.optJSONArray("results")
                    ?: item.optJSONArray("videos")

                if (nestedCards != null && nestedCards.length() > 0) {
                    for (j in 0 until nestedCards.length()) {
                        val nested = nestedCards.optJSONObject(j) ?: continue
                        val norm = if (isPromoGroup) UniversalNormalizer.normalizePromo(nested, endpointHint) else UniversalNormalizer.normalizeOrNull(nested, endpointHint)
                        if (norm != null) items.add(norm) else filteredCount++
                    }
                } else {
                    val norm = if (isPromoGroup) UniversalNormalizer.normalizePromo(item, endpointHint) else UniversalNormalizer.normalizeOrNull(item, endpointHint)
                    if (norm != null) items.add(norm) else filteredCount++
                }
            }

            val first = items.firstOrNull()
            val overallType = when (first) {
                is NormalizedCard.PromoCard -> EntityType.PROMO_GROUP
                is NormalizedCard.TvSeriesCard -> EntityType.TV_SERIES
                is NormalizedCard.ChannelCard -> EntityType.CHANNEL
                is NormalizedCard.PlaylistCard -> EntityType.PLAYLIST
                else -> EntityType.VIDEO_LIST
            }

            return ParsedResponse(
                type = overallType,
                items = items,
                pagination = pagination,
                metadata = ResponseMetadata(filteredPaidCount = filteredCount, totalOriginalCount = resultsArray.length())
            )
        }

        private inline fun <reified T : NormalizedCard> extractRelated(jsonObj: JSONObject, key: String, endpointHint: String): List<T> {
            val result = mutableListOf<T>()
            val arr = jsonObj.optJSONArray(key)
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let {
                        UniversalNormalizer.normalizeOrNull(it, endpointHint)?.let { card ->
                            if (card is T) result.add(card)
                        }
                    }
                }
            } else {
                jsonObj.optJSONObject(key)?.let {
                    UniversalNormalizer.normalizeOrNull(it, endpointHint)?.let { card ->
                        if (card is T) result.add(card)
                    }
                }
            }
            return result
        }

        private fun classifyEndpoint(url: String?): String {
            if (url == null) return "unknown"
            return when {
                url.contains("/search/combined") -> "search_combined"
                url.contains("/search/video") -> "search_video"
                url.contains("/search/channel") -> "search_channel"
                url.contains("/search/person") -> "search_person"
                url.contains("/feeds/promogroup") -> "feeds_promogroup"
                url.contains("/feeds/") -> "feeds_category"
                url.contains("/video/popular") -> "video_popular"
                url.contains("/api/video/") && url.contains(Regex("/[a-f0-9]{32}")) -> "video_detail"
                else -> "unknown"
            }
        }
    }

    // ==================== PAGINATION EXTRACTOR (unchanged logic) ====================

    object PaginationExtractor {
        fun extract(json: JSONObject): PaginationInfo {
            if (json.has("has_next") || json.has("next")) {
                return PaginationInfo(
                    hasNext = json.optBoolean("has_next", json.has("next")),
                    nextUrl = normalizeUrl(json.optString("next", null)),
                    page = json.optInt("page", 1),
                    perPage = json.optInt("per_page", 20),
                    total = if (json.has("total")) json.getInt("total") else null
                )
            }
            val pagination = json.optJSONObject("pagination")
            if (pagination != null) {
                return PaginationInfo(
                    hasNext = pagination.optBoolean("has_next", false),
                    nextUrl = normalizeUrl(pagination.optString("next", null)),
                    page = pagination.optInt("page", 1),
                    perPage = pagination.optInt("per_page", 20),
                    total = if (pagination.has("total")) pagination.getInt("total") else null
                )
            }
            return PaginationInfo()
        }
    }

    // ==================== DATA CLASSES ====================

    data class ParsedResponse(
        val type: EntityType,
        val items: List<NormalizedCard> = emptyList(),
        val title: String? = null,
        val description: String? = null,
        val tabs: List<TabInfo> = emptyList(),
        val relatedTv: List<NormalizedCard.TvSeriesCard> = emptyList(),
        val relatedPersons: List<NormalizedCard.ChannelCard> = emptyList(),
        val pagination: PaginationInfo = PaginationInfo(),
        val metadata: ResponseMetadata = ResponseMetadata(),
        val error: String? = null
    )

    data class TabInfo(val id: Int, val name: String, val resources: List<ResourceInfo>)
    data class ResourceInfo(val name: String, val url: String?, val type: EntityType, val extraParams: JSONObject?)
    data class PaginationInfo(val hasNext: Boolean = false, val nextUrl: String? = null, val page: Int = 1, val perPage: Int = 20, val total: Int? = null)
    data class ResponseMetadata(val filteredPaidCount: Int = 0, val totalOriginalCount: Int = 0, val totalResources: Int = 0)

    // ==================== HELPERS ====================

    private fun isBlockedText(text: String): Boolean {
        return text.contains("premier") || text.contains("start") || text.contains("viju") ||
               text.contains("премьер") || text.contains("вижу")
    }

    private fun formatDuration(seconds: Double): String {
        val total = seconds.toInt()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    private fun formatCount(count: Long): String {
        return when {
            count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1000.0)
            count > 0 -> count.toString()
            else -> "0"
        }
    }

    private fun formatDate(dateString: String?): String {
        if (dateString.isNullOrBlank()) return "Недавно"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date = parser.parse(dateString) ?: return "Недавно"
            SimpleDateFormat("d MMM yyyy", Locale("ru")).format(date)
        } catch (e: Exception) { "Недавно" }
    }

    private fun parseTimestamp(dateString: String?): Long {
        if (dateString.isNullOrBlank()) return 0L
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            parser.parse(dateString)?.time ?: 0L
        } catch (e: Exception) { 0L }
    }

    private fun normalizeUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> "https://rutube.ru$trimmed"
            trimmed.contains("rutube.ru") -> "https://" + trimmed.removePrefix("http://").removePrefix("https://").removePrefix("//")
            else -> "https://rutube.ru/$trimmed"
        }
    }
}
