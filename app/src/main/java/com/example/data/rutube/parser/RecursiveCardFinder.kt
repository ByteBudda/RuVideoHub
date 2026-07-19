package com.example.data.rutube.parser

import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object RecursiveCardFinder {

    fun findCardArrays(json: JSONObject, depth: Int = 0): List<JSONArray> {
        if (depth > MAX_RECURSION_DEPTH) return emptyList()
        val results = mutableListOf<JSONArray>()
        val visited = mutableSetOf<Int>()

        findRecursive(json, depth, results, visited)
        return results.sortedByDescending { scoreArray(it) }
    }

    private fun findRecursive(obj: Any?, depth: Int, results: MutableList<JSONArray>, visited: MutableSet<Int>) {
        if (depth > MAX_RECURSION_DEPTH) return
        if (obj == null) return

        when (obj) {
            is JSONObject -> {
                val identity = System.identityHashCode(obj)
                if (identity in visited) return
                visited.add(identity)

                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = obj.opt(key)

                    if (value is JSONArray && value.length() > 0) {
                        if (looksLikeCardArray(value)) {
                            results.add(value)
                        } else {
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
                val identity = System.identityHashCode(obj)
                if (identity in visited) return
                visited.add(identity)
                
                for (i in 0 until obj.length()) {
                    findRecursive(obj.opt(i), depth + 1, results, visited)
                }
            }
        }
    }

    private fun looksLikeCardArray(arr: JSONArray): Boolean {
        if (arr.length() == 0) return false
        val sampleSize = minOf(arr.length(), 3)
        var score = 0
        for (i in 0 until sampleSize) {
            val obj = arr.optJSONObject(i) ?: continue
            val sig = SchemaAnalyzer.buildSignature(obj)
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
