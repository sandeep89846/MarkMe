package com.markme.app.util

import org.json.JSONArray
import org.json.JSONObject

/**
 * Deterministic JSON canonicalizer.
 * This logic MUST be byte-for-byte identical to the server's implementation.
 *
 * Rules:
 * 1. Sort object keys alphabetically.
 * 2. Do not sort array elements.
 * 3. Serialize to a compact string (no whitespace).
 */
object Canonicalizer {

    fun canonicalize(data: Map<String, Any>): String {
        return canonicalizeMap(data)
    }

    private fun canonicalizeValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> JSONObject.quote(value) // Use JSONObject just for string quoting
            is Boolean, is Number -> value.toString()
            is Map<*, *> -> canonicalizeMap(value as Map<String, Any>)
            is List<*> -> canonicalizeList(value as List<Any>)
            // Fallback for other types
            else -> JSONObject.quote(value.toString())
        }
    }

    private fun canonicalizeMap(map: Map<String, Any>): String {
        // Sort keys alphabetically, just like the server
        val sortedKeys = map.keys.sorted()

        // Manually build the string to guarantee order
        return sortedKeys.joinToString(
            separator = ",",
            prefix = "{",
            postfix = "}"
        ) { key ->
            // "key":"value"
            "${JSONObject.quote(key)}:${canonicalizeValue(map[key])}"
        }
    }

    private fun canonicalizeList(list: List<Any>): String {
        // Arrays are not sorted, just processed in order
        return list.joinToString(
            separator = ",",
            prefix = "[",
            postfix = "]"
        ) { value ->
            canonicalizeValue(value)
        }
    }
}