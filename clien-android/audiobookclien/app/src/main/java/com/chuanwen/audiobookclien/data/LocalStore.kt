package com.chuanwen.audiobookclien.data

import android.content.Context
import com.chuanwen.audiobookclien.App
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地数据仓库（SharedPreferences + JSON 持久化，全部本地保存、不上传）：
 * 1. 收藏的专辑
 * 2. 最近收听历史
 * 3. 上次播放记忆（专辑 / 分集 / 进度）
 */
object LocalStore {

    private const val PREFS = "local_store"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_HISTORY = "history"
    private const val KEY_LAST_PLAYBACK = "last_playback"

    private val prefs: android.content.SharedPreferences
        get() = App.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------- 收藏 ----------------

    data class Favorite(val folder: String, val title: String, val addedAt: Long)

    fun favorites(): List<Favorite> {
        val raw = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = ArrayList<Favorite>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list += Favorite(
                    folder = o.optString("folder"),
                    title = o.optString("title"),
                    addedAt = o.optLong("addedAt")
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    fun isFavorite(folder: String): Boolean = favorites().any { it.folder == folder }

    /** 切换收藏状态；返回切换后是否为已收藏 */
    fun toggleFavorite(folder: String, title: String): Boolean {
        val current = favorites().toMutableList()
        val exists = current.any { it.folder == folder }
        if (exists) {
            current.removeAll { it.folder == folder }
        } else {
            current.add(0, Favorite(folder, title, System.currentTimeMillis()))
        }
        val arr = JSONArray()
        current.forEach { f ->
            arr.put(
                JSONObject()
                    .put("folder", f.folder)
                    .put("title", f.title)
                    .put("addedAt", f.addedAt)
            )
        }
        prefs.edit().putString(KEY_FAVORITES, arr.toString()).apply()
        return !exists
    }

    // ---------------- 最近收听历史 ----------------

    data class HistoryItem(
        val folder: String,
        val fileName: String,
        val trackTitle: String,
        val albumTitle: String,
        val index: Int,
        val positionMs: Long,
        val playedAt: Long
    )

    private const val HISTORY_MAX = 50

    fun history(): List<HistoryItem> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = ArrayList<HistoryItem>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list += HistoryItem(
                    folder = o.optString("folder"),
                    fileName = o.optString("fileName"),
                    trackTitle = o.optString("trackTitle"),
                    albumTitle = o.optString("albumTitle"),
                    index = o.optInt("index"),
                    positionMs = o.optLong("positionMs"),
                    playedAt = o.optLong("playedAt")
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    /** 同一分集再次收听时：移动到最前并更新进度与时间 */
    fun addHistory(folder: String, fileName: String, trackTitle: String, albumTitle: String, index: Int, positionMs: Long) {
        if (folder.isEmpty() || fileName.isEmpty()) return
        val current = history().toMutableList()
        current.removeAll { it.folder == folder && it.fileName == fileName }
        current.add(
            0,
            HistoryItem(folder, fileName, trackTitle, albumTitle, index, positionMs, System.currentTimeMillis())
        )
        if (current.size > HISTORY_MAX) {
            current.subList(HISTORY_MAX, current.size).clear()
        }
        val arr = JSONArray()
        current.forEach { h ->
            arr.put(
                JSONObject()
                    .put("folder", h.folder)
                    .put("fileName", h.fileName)
                    .put("trackTitle", h.trackTitle)
                    .put("albumTitle", h.albumTitle)
                    .put("index", h.index)
                    .put("positionMs", h.positionMs)
                    .put("playedAt", h.playedAt)
            )
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    // ---------------- 上次播放记忆 ----------------

    data class LastPlayback(
        val folder: String,
        val fileName: String,
        val trackTitle: String,
        val albumTitle: String,
        val index: Int,
        val positionMs: Long,
        val updatedAt: Long
    )

    fun lastPlayback(): LastPlayback? {
        val raw = prefs.getString(KEY_LAST_PLAYBACK, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            LastPlayback(
                folder = o.optString("folder"),
                fileName = o.optString("fileName"),
                trackTitle = o.optString("trackTitle"),
                albumTitle = o.optString("albumTitle"),
                index = o.optInt("index"),
                positionMs = o.optLong("positionMs"),
                updatedAt = o.optLong("updatedAt")
            )
        }.getOrNull()
    }

    fun saveLastPlayback(folder: String, fileName: String, trackTitle: String, albumTitle: String, index: Int, positionMs: Long) {
        if (folder.isEmpty() || fileName.isEmpty()) return
        val o = JSONObject()
            .put("folder", folder)
            .put("fileName", fileName)
            .put("trackTitle", trackTitle)
            .put("albumTitle", albumTitle)
            .put("index", index)
            .put("positionMs", positionMs)
            .put("updatedAt", System.currentTimeMillis())
        prefs.edit().putString(KEY_LAST_PLAYBACK, o.toString()).apply()
    }

    fun clearLastPlayback() {
        prefs.edit().remove(KEY_LAST_PLAYBACK).apply()
    }
}
