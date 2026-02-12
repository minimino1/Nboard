package com.nboard.ime.clipboard

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class ClipboardItem(
    val text: String,
    val pinned: Boolean,
    val updatedAtMs: Long
)

class ClipboardHistoryStore(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var cachedItems: List<ClipboardItem>? = null

    fun addItem(rawText: String) {
        val text = rawText.trim()
        if (text.isBlank()) {
            return
        }

        val now = System.currentTimeMillis()
        val current = getItems().toMutableList()
        val existingIndex = current.indexOfFirst { it.text == text }
        if (existingIndex >= 0) {
            val existing = current.removeAt(existingIndex)
            current.add(0, existing.copy(updatedAtMs = now))
        } else {
            current.add(
                0,
                ClipboardItem(
                    text = text,
                    pinned = false,
                    updatedAtMs = now
                )
            )
        }

        saveItems(sortItems(current).take(MAX_ITEMS))
    }

    fun setPinned(text: String, pinned: Boolean) {
        val current = getItems().toMutableList()
        val index = current.indexOfFirst { it.text == text }
        if (index < 0) {
            return
        }

        val entry = current.removeAt(index)
        current.add(
            0,
            entry.copy(
                pinned = pinned,
                updatedAtMs = System.currentTimeMillis()
            )
        )
        saveItems(sortItems(current))
    }

    fun removeItem(text: String) {
        val filtered = getItems().filterNot { it.text == text }
        saveItems(filtered)
    }

    fun getItems(): List<ClipboardItem> {
        cachedItems?.let { return it }
        val encoded = preferences.getString(KEY_ITEMS, null)
        if (encoded.isNullOrBlank()) {
            cachedItems = emptyList()
            return emptyList()
        }
        val parsedItems = try {
            val parsed = JSONArray(encoded)
            buildList(parsed.length()) {
                for (i in 0 until parsed.length()) {
                    val item = parsed.opt(i)
                    when (item) {
                        is String -> {
                            val text = item.trim()
                            if (text.isNotBlank()) {
                                add(
                                    ClipboardItem(
                                        text = text,
                                        pinned = false,
                                        updatedAtMs = 0L
                                    )
                                )
                            }
                        }
                        is JSONObject -> {
                            val text = item.optString("text").trim()
                            if (text.isNotBlank()) {
                                add(
                                    ClipboardItem(
                                        text = text,
                                        pinned = item.optBoolean("pinned", false),
                                        updatedAtMs = item.optLong("updatedAtMs", 0L)
                                    )
                                )
                            }
                        }
                    }
                }
            }.let(::sortItems)
        } catch (_: Exception) {
            emptyList()
        }
        cachedItems = parsedItems
        return parsedItems
    }

    private fun saveItems(items: List<ClipboardItem>) {
        val normalized = sortItems(items).take(MAX_ITEMS)
        val encoded = JSONArray().apply {
            normalized.forEach { item ->
                put(
                    JSONObject().apply {
                        put("text", item.text)
                        put("pinned", item.pinned)
                        put("updatedAtMs", item.updatedAtMs)
                    }
                )
            }
        }
        cachedItems = normalized
        preferences.edit().putString(KEY_ITEMS, encoded.toString()).apply()
    }

    private fun sortItems(items: List<ClipboardItem>): List<ClipboardItem> {
        return items
            .distinctBy { it.text }
            .sortedWith(
                compareByDescending<ClipboardItem> { it.pinned }
                    .thenByDescending { it.updatedAtMs }
            )
    }

    companion object {
        private const val PREFS_NAME = "nboard_clipboard"
        private const val KEY_ITEMS = "items"
        private const val MAX_ITEMS = 20
    }
}
