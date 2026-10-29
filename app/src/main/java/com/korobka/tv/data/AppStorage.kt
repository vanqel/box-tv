package com.korobka.tv.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AppStorage(context: Context) {
    private val prefs = context.getSharedPreferences("korobka_tv_store", Context.MODE_PRIVATE)

    fun getHistory(): List<PlaybackHistoryItem> {
        val jsonStr = prefs.getString("history", null) ?: return emptyList()
        val list = mutableListOf<PlaybackHistoryItem>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    PlaybackHistoryItem(
                        itemId = obj.getString("item_id"),
                        title = obj.getString("title"),
                        poster = obj.optString("poster", "").ifEmpty { null },
                        season = if (obj.has("season")) obj.getInt("season") else null,
                        episode = if (obj.has("episode")) obj.getInt("episode") else null,
                        translation = obj.optString("translation", "").ifEmpty { null },
                        positionMs = obj.optLong("position_ms", 0),
                        durationMs = obj.optLong("duration_ms", 0),
                        updatedAt = obj.optLong("updated_at", 0)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveHistoryItem(item: PlaybackHistoryItem) {
        val current = getHistory().toMutableList()
        current.removeAll { it.itemId == item.itemId }
        current.add(0, item)
        val trimmed = current.take(40)

        val arr = JSONArray()
        for (it in trimmed) {
            val obj = JSONObject().apply {
                put("item_id", it.itemId)
                put("title", it.title)
                put("poster", it.poster ?: "")
                if (it.season != null) put("season", it.season)
                if (it.episode != null) put("episode", it.episode)
                put("translation", it.translation ?: "")
                put("position_ms", it.positionMs)
                put("duration_ms", it.durationMs)
                put("updated_at", it.updatedAt)
            }
            arr.put(obj)
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    fun getFavorites(): List<MediaItem> {
        val jsonStr = prefs.getString("favorites", null) ?: return emptyList()
        val list = mutableListOf<MediaItem>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    MediaItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        poster = obj.optString("poster", "").ifEmpty { null },
                        isSeries = obj.optBoolean("is_series", false),
                        ratingKp = obj.optDouble("rating_kp", 8.0).toFloat()
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun toggleFavorite(item: MediaItem): Boolean {
        val favs = getFavorites().toMutableList()
        val exists = favs.any { it.id == item.id }
        if (exists) {
            favs.removeAll { it.id == item.id }
        } else {
            favs.add(0, item)
        }

        val arr = JSONArray()
        for (it in favs) {
            val obj = JSONObject().apply {
                put("id", it.id)
                put("title", it.title)
                put("poster", it.poster ?: "")
                put("is_series", it.isSeries)
                put("rating_kp", it.ratingKp ?: 8.0f)
            }
            arr.put(obj)
        }
        prefs.edit().putString("favorites", arr.toString()).apply()
        return !exists
    }

    fun isFavorite(itemId: String): Boolean {
        return getFavorites().any { it.id == itemId }
    }
}
