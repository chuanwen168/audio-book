package com.chuanwen.audiobookclien.data

import android.content.Context

/**
 * 服务器地址配置（SharedPreferences 持久化）。
 * 默认 http://10.0.2.2:8086/ 便于 Android 模拟器直连本机服务端。
 */
object ServerConfig {

    private const val PREFS_NAME = "server_config"
    private const val KEY_BASE_URL = "base_url"

    const val DEFAULT_BASE_URL = "http://10.0.2.2:8086/"

    fun getBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_BASE_URL, null) ?: DEFAULT_BASE_URL
        return normalize(saved)
    }

    fun setBaseUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, normalize(url))
            .apply()
    }

    /** 规范化：补充协议头与结尾斜杠 */
    fun normalize(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return DEFAULT_BASE_URL
        if (!s.contains("://")) s = "http://$s"
        if (!s.endsWith("/")) s += "/"
        return s
    }

    /** 把服务端返回的相对路径（如 /media/xxx/cover.jpg）拼成完整 URL */
    fun absolute(context: Context, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return getBaseUrl(context).trimEnd('/') + path
    }
}
