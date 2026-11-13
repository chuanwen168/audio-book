package com.chuanwen.audiobookclien.data

import com.chuanwen.audiobookclien.App
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * 专辑元数据本地缓存：
 * - 缓存专辑列表（/api/v1/albums）与专辑详情（分集列表、封面地址、简介）
 * - 离线时首页/搜索/专辑页/最近播放都能正常打开，播放队列也能凭本地快照重建
 */
object AlbumCache {

    private val gson = Gson()

    private val dir: File
        get() = File(App.context.filesDir, "album_meta").apply { mkdirs() }

    private fun sha1(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun albumFile(folder: String): File = File(dir, sha1(folder) + ".json")

    private val listFile: File get() = File(dir, "albums.json")

    suspend fun saveAlbum(album: AlbumFull) = withContext(Dispatchers.IO) {
        runCatching { albumFile(album.folder_name).writeText(gson.toJson(album)) }
        Unit
    }

    suspend fun loadAlbum(folder: String): AlbumFull? = withContext(Dispatchers.IO) {
        runCatching {
            val f = albumFile(folder)
            if (!f.exists()) null else gson.fromJson(f.readText(), AlbumFull::class.java)
        }.getOrNull()
    }

    suspend fun saveAlbumList(list: List<AlbumSimple>) = withContext(Dispatchers.IO) {
        runCatching { listFile.writeText(gson.toJson(list)) }
        Unit
    }

    suspend fun loadAlbumList(): List<AlbumSimple>? = withContext(Dispatchers.IO) {
        runCatching {
            val f = listFile
            if (!f.exists()) null
            else gson.fromJson<List<AlbumSimple>>(
                f.readText(),
                object : TypeToken<List<AlbumSimple>>() {}.type
            )
        }.getOrNull()
    }
}
