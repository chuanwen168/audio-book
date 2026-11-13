package com.chuanwen.audiobookclien.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.chuanwen.audiobookclien.App
import com.chuanwen.audiobookclien.playback.Playback
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * 数据仓库：负责调服务端接口、处理服务器地址变化、把分集列表灌入播放器。
 * 网络不可用时自动回退到本地缓存的专辑列表 / 专辑详情，保证离线可浏览、可续播。
 */
class AudioRepository(private val context: Context) {

    @Volatile
    private var api: ApiService? = null

    @Volatile
    private var cachedBaseUrl: String? = null

    /** 获取（或按当前服务器地址重建）Retrofit 服务实例 */
    private fun service(): ApiService {
        val base = ServerConfig.getBaseUrl(context)
        api?.let { if (cachedBaseUrl == base) return it }
        synchronized(this) {
            api?.let { if (cachedBaseUrl == base) return it }
            cachedBaseUrl = base
            val client = OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl(base)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(ApiService::class.java).also { api = it }
        }
    }

    /** 把服务端返回的相对路径拼成完整地址 */
    private fun abs(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return ServerConfig.getBaseUrl(context).trimEnd('/') + path
    }

    /** 拉取专辑列表：网络失败时回退本地缓存（支持离线首页/搜索） */
    suspend fun fetchAlbums(): List<AlbumSimple> {
        return try {
            val result = service().albums()
            if (result.code != 200) throw IllegalStateException(result.msg ?: "服务端返回错误")
            val data = result.data ?: emptyList()
            if (data.isNotEmpty()) AlbumCache.saveAlbumList(data)
            data
        } catch (e: Exception) {
            AlbumCache.loadAlbumList()?.takeIf { it.isNotEmpty() } ?: throw e
        }
    }

    /** 拉取专辑详情：网络失败时回退本地缓存（支持离线进专辑页、离线续播） */
    suspend fun fetchAlbum(folder: String): AlbumFull {
        return try {
            val result = service().album(folder)
            if (result.code != 200) throw IllegalStateException(result.msg ?: "专辑不存在")
            val data = result.data ?: throw IllegalStateException("数据为空")
            AlbumCache.saveAlbum(data)
            // 顺带把封面落盘，之后离线也能看到专辑图
            CacheManager.prefetchCover(abs(data.nfo.cover))
            data
        } catch (e: Exception) {
            AlbumCache.loadAlbum(folder) ?: throw e
        }
    }

    /** 测试服务器连通性（必须走实时网络，不使用离线缓存） */
    suspend fun testConnection(): String {
        val result = service().albums()
        if (result.code != 200) throw IllegalStateException(result.msg ?: "服务端返回错误")
        return "连接成功，共 ${result.data?.size ?: 0} 个专辑"
    }

    /**
     * 把某个专辑的全部分集作为播放队列，从 startIndex 开始播放。
     * 已缓存的分集优先播放本地文件；同时把整本书加入自动缓存队列。
     */
    fun playAlbum(folder: String, nfo: AlbumNfo, audios: List<AudioItem>, startIndex: Int, startPositionMs: Long = 0L) {
        if (audios.isEmpty()) return
        val base = ServerConfig.getBaseUrl(context).trimEnd('/')
        Playback.currentFolder = folder
        val urls = audios.map { base + it.file_url }
        val coverUrl = abs(nfo.cover)
        // 本地保存专辑快照：下次离线时仍能重建播放队列、打开专辑页
        App.appScope.launch { AlbumCache.saveAlbum(AlbumFull(folder, nfo, audios)) }
        // 正在听整本书：已缓存的音频/封面标记为已使用，避免 7 天清理误删
        CacheManager.touchAlbum(urls)
        CacheManager.touchCover(coverUrl)
        val items = audios.map { audio ->
            val url = base + audio.file_url
            val uri = CacheManager.cachedUri(url) ?: Uri.parse(url)
            MediaItem.Builder()
                .setMediaId(audio.file_name)
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(cleanName(audio.file_name))
                        .setAlbumTitle(nfo.title)
                        .setArtworkUri(Uri.parse(coverUrl))
                        .build()
                )
                .build()
        }
        val safeIndex = startIndex.coerceIn(0, items.size - 1)
        Playback.player.setMediaItems(items, safeIndex, startPositionMs.coerceAtLeast(0L))
        Playback.player.prepare()
        Playback.player.play()
        // 播放当前书：自动缓存整本书（后台逐个下载未缓存分集与封面）
        CacheManager.cacheAlbum(folder, urls, coverUrl)
    }

    /** 去掉文件名中的 .mp3 后缀，用于展示 */
    fun cleanName(fileName: String): String =
        fileName
            .removeSuffix(".mp3")
            .removeSuffix(".MP3")
            .removeSuffix(".Mp3")
}
