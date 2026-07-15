package com.example.data.rutube.parser

import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object ResponseAnalyzer {

    fun parse(jsonObj: JSONObject, url: String? = null): ParsedResponse {
        val endpointHint = classifyEndpoint(url)
        val isPromoGroup = endpointHint.contains("promogroup") || url?.contains("/promogroup/") == true

        if (jsonObj.has("tabs")) {
            return parseCatalogFeed(jsonObj, endpointHint, isPromoGroup)
        }

        if (jsonObj.has("results") || jsonObj.has("videos") || jsonObj.has("items")) {
            return parsePaginatedResponse(jsonObj, endpointHint, isPromoGroup)
        }

        val rootSig = SchemaAnalyzer.buildSignature(jsonObj)
        val rootType = SchemaAnalyzer.detectEntityType(rootSig, url)

        if (rootType in listOf(EntityType.VIDEO_ITEM, EntityType.TV_SERIES, EntityType.CHANNEL, EntityType.PLAYLIST)) {
            val card = UniversalNormalizer.normalizeOrNull(jsonObj, endpointHint)
            return ParsedResponse(
                type = rootType,
                items = card?.let { listOf(it) } ?: emptyList()
            )
        }

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

        val relatedTv = extractRelated<NormalizedCard.TvSeriesCard>(jsonObj, "related_tv", endpointHint)
        val relatedPersons = extractRelated<NormalizedCard.ChannelCard>(jsonObj, "related_person", endpointHint).toMutableList()

        // Also search for other possible root-level fields that represent the author/channel of this feed
        val possibleRootKeys = listOf("author", "person", "channel", "user", "owner")
        for (key in possibleRootKeys) {
            val optObj = jsonObj.optJSONObject(key)
            if (optObj != null) {
                val normalized = UniversalNormalizer.normalizeOrNull(optObj, endpointHint)
                if (normalized is NormalizedCard.ChannelCard) {
                    if (relatedPersons.none { it.id == normalized.id }) {
                        relatedPersons.add(normalized)
                    }
                }
            }
        }

        val items = mutableListOf<NormalizedCard>()
        val resultsArray = jsonObj.optJSONArray("results")
        if (resultsArray != null) {
            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.optJSONObject(i) ?: continue
                val nestedCards = item.optJSONArray("cards") ?: item.optJSONArray("items") ?: item.optJSONArray("results")
                if (nestedCards != null) {
                    for (j in 0 until nestedCards.length()) {
                        val nested = nestedCards.optJSONObject(j) ?: continue
                        val norm = UniversalNormalizer.normalizeOrNull(nested, endpointHint)
                        norm?.let { items.add(it) }
                    }
                } else {
                    val norm = UniversalNormalizer.normalizeOrNull(item, endpointHint)
                    norm?.let { items.add(it) }
                }
            }
        }

        for (tab in tabList) {
            for (res in tab.resources) {
                items.add(NormalizedCard.UnknownCard(
                    id = "res_${tab.id}_${res.name.hashCode()}",
                    title = res.name,
                    thumbnail = null,
                    rawType = tab.name,
                    extractedFields = emptyMap(),
                    actionUrl = res.url,
                    confidence = 1.0
                ))
            }
        }

        return ParsedResponse(
            type = EntityType.FEED_CATALOG,
            title = AdaptiveExtractor.getString(jsonObj, "title", endpointHint, "Каталог"),
            description = AdaptiveExtractor.getString(jsonObj, "description", endpointHint).takeIf { it.isNotBlank() },
            tabs = tabList,
            items = items,
            pagination = PaginationExtractor.extract(jsonObj),
            relatedTv = relatedTv,
            relatedPersons = relatedPersons,
            metadata = ResponseMetadata(totalResources = totalResources)
        )
    }

    private fun parsePaginatedResponse(jsonObj: JSONObject, endpointHint: String, isPromoGroup: Boolean): ParsedResponse {
        val resultsArray = jsonObj.optJSONArray("results") 
            ?: jsonObj.optJSONArray("videos")
            ?: jsonObj.optJSONArray("items")
            ?: JSONArray()
        val pagination = PaginationExtractor.extract(jsonObj)
        if (resultsArray.length() == 0) return ParsedResponse(type = EntityType.EMPTY, pagination = pagination)

        val items = mutableListOf<NormalizedCard>()
        var filteredCount = 0

        for (i in 0 until resultsArray.length()) {
            val item = resultsArray.optJSONObject(i) ?: continue
            val nestedCards = item.optJSONArray("cards")
                ?: item.optJSONArray("items")
                ?: item.optJSONArray("results")
                ?: item.optJSONArray("videos")

            if (nestedCards != null && nestedCards.length() > 0) {
                for (j in 0 until nestedCards.length()) {
                    val nested = nestedCards.optJSONObject(j) ?: continue
                    val norm = UniversalNormalizer.normalizeOrNull(nested, endpointHint)
                    if (norm != null) items.add(norm) else filteredCount++
                }
            } else {
                val norm = UniversalNormalizer.normalizeOrNull(item, endpointHint)
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

        val relatedTv = extractRelated<NormalizedCard.TvSeriesCard>(jsonObj, "related_tv", endpointHint)
        val relatedPersons = extractRelated<NormalizedCard.ChannelCard>(jsonObj, "related_person", endpointHint).toMutableList()

        // Also search for other possible root-level fields that represent the author/channel of this feed
        val possibleRootKeys = listOf("author", "person", "channel", "user", "owner")
        for (key in possibleRootKeys) {
            val optObj = jsonObj.optJSONObject(key)
            if (optObj != null) {
                val normalized = UniversalNormalizer.normalizeOrNull(optObj, endpointHint)
                if (normalized is NormalizedCard.ChannelCard) {
                    if (relatedPersons.none { it.id == normalized.id }) {
                        relatedPersons.add(normalized)
                    }
                }
            }
        }

        return ParsedResponse(
            type = overallType,
            items = items,
            pagination = pagination,
            relatedTv = relatedTv,
            relatedPersons = relatedPersons,
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
