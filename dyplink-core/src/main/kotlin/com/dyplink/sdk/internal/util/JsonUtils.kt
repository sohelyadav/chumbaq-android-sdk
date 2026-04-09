package com.dyplink.sdk.internal.util

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight conversion helpers between [Map]/[List] and the
 * `org.json` types that ship with Android.
 */
internal object JsonUtils {

    // ── Map  →  JSONObject ──────────────────────────────────────────

    /**
     * Converts a [Map] to a [JSONObject], recursively handling nested
     * maps, lists, numbers, strings, booleans, and nulls.
     */
    @JvmStatic
    fun Map<String, Any?>.toJsonObject(): JSONObject {
        val json = JSONObject()
        for ((key, value) in this) {
            json.put(key, wrapValue(value))
        }
        return json
    }

    // ── JSONObject  →  Map ──────────────────────────────────────────

    /**
     * Converts a [JSONObject] to a plain [Map], recursively unwrapping
     * nested objects and arrays.
     */
    @JvmStatic
    fun JSONObject.toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = this.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = unwrapValue(this.get(key))
        }
        return map
    }

    // ── JSONObject  →  String? ──────────────────────────────────────

    /**
     * Returns the JSON string representation, or `null` when the
     * object has no keys.
     */
    @JvmStatic
    fun JSONObject.toStringOrNull(): String? {
        return if (this.length() == 0) null else this.toString()
    }

    // ── Internal helpers ────────────────────────────────────────────

    private fun wrapValue(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                (value as Map<String, Any?>).toJsonObject()
            }
            is List<*> -> {
                val array = JSONArray()
                for (item in value) {
                    array.put(wrapValue(item))
                }
                array
            }
            is Number, is String, is Boolean -> value
            else -> value.toString()
        }
    }

    private fun unwrapValue(value: Any?): Any? {
        return when (value) {
            JSONObject.NULL, null -> null
            is JSONObject -> value.toMap()
            is JSONArray -> {
                val list = mutableListOf<Any?>()
                for (i in 0 until value.length()) {
                    list.add(unwrapValue(value.get(i)))
                }
                list
            }
            else -> value
        }
    }
}
