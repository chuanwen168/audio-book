package com.chuanwen.audiobookclien.data

import android.content.Context
import android.net.Uri
import com.chuanwen.audiobookclien.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 音频 / 封面缓存管理：
 * - 音频文件保存在应用私有目录 filesDir/audio_cache（无需存储权限）
 * - 封面保存在 filesDir/cover_cache，离线时专辑图仍然可见
 * - 缓存清单记录每个文件的最近使用时间，自动清理 7 天未使用的缓存
 * - 播放时优先使用本地缓存，并自动下载当前播放的整本书
 */
object CacheManager {

    private const val PREFS = "cache_registry"
    private const val KEY_REGISTRY = "files"
    private const val CLEAN_DAYS = 7L
    private const val CLEAN_MS = CLEAN_DAYS * 24 * 60 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 正在缓存的专辑，避免连续点集时多个协程并发写同一个文件 */
    private val cachingFolders: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    val cacheDir: File by lazy { File(App.context.filesDir, "audio_cache").apply { mkdirs() } }

    val coverDir: File by lazy { File(App.context.filesDir, "cover_cache").apply { mkdirs() } }

    /** 整本书缓存进度；folder 为空表示当前空闲 */
    data class CacheProgress(val folder: String, val done: Int, val total: Int) {
        val finished: Boolean get() = total > 0 && done >= total
    }

    private val _progress = MutableStateFlow<CacheProgress?>(null)
    val progress: StateFlow<CacheProgress?> = _progress.asStateFlow()

    private val registry: JSONObject
        get() {
            val raw = App.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_REGISTRY, null)
            return raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        }

    private fun saveRegistry(o: JSONObject) {
        App.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REGISTRY, o.toString())
            .apply()
    }

    private fun sha1(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun fileFor(url: String): File = File(cacheDir, sha1(url) + ".mp3")

    fun isCached(url: String): Boolean {
        val f = fileFor(url)
        return f.exists() && f.length() > 0
    }

    /** 已缓存时返回本地 Uri，否则返回 null（由调用方回退到网络地址） */
    fun cachedUri(url: String): Uri? = if (isCached(url)) Uri.fromFile(fileFor(url)) else null

    /** 标记缓存文件最近被使用（播放本书 / 下载完成时调用） */
    fun recordUsed(url: String) {
        val key = sha1(url)
        val o = registry
        o.put(key, System.currentTimeMillis())
        saveRegistry(o)
    }

    /** 播放某本书时，把本书已缓存的音频全部标记为已使用，避免 7 天清理误删正在听的书 */
    fun touchAlbum(urls: List<String>) {
        urls.forEach { if (isCached(it)) recordUsed(it) }
    }

    // ---------------- 封面 ----------------

    fun coverFileFor(url: String): File = File(coverDir, sha1(url) + ".jpg")

    fun isCoverCached(url: String): Boolean {
        val f = coverFileFor(url)
        return f.exists() && f.length() > 0
    }

    /** 刷新封面文件的最近使用时间，参与 7 天清理 */
    fun touchCover(url: String) {
        if (url.isBlank()) return
        val f = coverFileFor(url)
        if (f.exists()) f.setLastModified(System.currentTimeMillis())
    }

    /** 后台预取封面；已缓存则只刷新使用时间 */
    fun prefetchCover(url: String) {
        if (url.isBlank()) return
        if (isCoverCached(url)) {
            touchCover(url)
            return
        }
        scope.launch { if (!isCoverCached(url)) downloadCover(url) }
    }

    suspend fun downloadCover(url: String): Boolean = withContext(Dispatchers.IO) {
        var ok = false
        val dest = coverFileFor(url)
        val tmp = File(dest.parentFile, dest.name + ".part")
        try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body
                    if (body != null) {
                        tmp.parentFile?.mkdirs()
                        tmp.delete()
                        body.byteStream().use { input ->
                            tmp.outputStream().use { out -> input.copyTo(out) }
                        }
                        if (tmp.exists() && tmp.length() > 0) {
                            dest.delete()
                            ok = tmp.renameTo(dest)
                            if (!ok) {
                                tmp.copyTo(dest, overwrite = true)
                                ok = dest.exists() && dest.length() > 0
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            ok = false
        } finally {
            tmp.delete()
        }
        if (ok) dest.setLastModified(System.currentTimeMillis())
        ok
    }

    // ---------------- 统计与清理 ----------------

    fun cachedCount(): Int = registry.length()

    fun totalSize(): Long {
        val audio = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        val cover = coverDir.listFiles()?.sumOf { it.length() } ?: 0L
        return audio + cover
    }

    fun clearAll() {
        cacheDir.listFiles()?.forEach { it.delete() }
        coverDir.listFiles()?.forEach { it.delete() }
        saveRegistry(JSONObject())
        _progress.value = null
    }

    /** 自动清理 7 天未使用的缓存（应用启动时调用） */
    fun cleanExpired() {
        val now = System.currentTimeMillis()
        val o = registry
        val expired = ArrayList<String>()
        o.keys().forEach { key ->
            val used = o.optLong(key, 0L)
            if (now - used > CLEAN_MS) expired.add(key)
        }
        expired.forEach { key ->
            File(cacheDir, "$key.mp3").delete()
            o.remove(key)
        }
        // 清理清单外的孤儿文件（中断下载等遗留），同样按 7 天
        cacheDir.listFiles()?.forEach { f ->
            val key = f.name.removeSuffix(".mp3")
            if (!o.has(key) && now - f.lastModified() > CLEAN_MS) {
                f.delete()
            }
        }
        // 封面缓存：7 天未使用一并清理
        coverDir.listFiles()?.forEach { f ->
            if (now - f.lastModified() > CLEAN_MS) f.delete()
        }
        // 清理历史版本遗留的坏缓存（下载中断导致的空文件 / 截断文件），
        // 避免出现“显示已缓存但离线播不出来”的情况
        var corrupted = false
        cacheDir.listFiles()?.filter { it.name.endsWith(".mp3") }?.forEach { f ->
            if (!looksLikeAudio(f)) {
                f.delete()
                o.remove(f.name.removeSuffix(".mp3"))
                corrupted = true
            }
        }
        if (expired.isNotEmpty() || corrupted) saveRegistry(o)
    }

    /** 简单校验缓存文件是不是可播放的 mp3（ID3 标签或帧同步头），用于清理坏缓存 */
    private fun looksLikeAudio(f: File): Boolean {
        if (!f.exists() || f.length() < 1024) return false
        return runCatching {
            val head = ByteArray(4)
            FileInputStream(f).use { it.read(head) }
            val isId3 = head[0] == 0x49.toByte() && head[1] == 0x44.toByte() && head[2] == 0x33.toByte()
            if (isId3) return@runCatching true
            head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0
        }.getOrDefault(false)
    }

    // ---------------- 下载 ----------------

    /**
     * 下载单个音频到缓存：先写 .part 临时文件，校验长度后原子改名，
     * 避免中断/失败留下“非空但残缺”的 mp3 被当成已缓存。
     * 成功返回 true 并记录使用时间。
     */
    suspend fun download(url: String): Boolean = withContext(Dispatchers.IO) {
        var ok = false
        val dest = fileFor(url)
        val tmp = File(dest.parentFile, dest.name + ".part")
        try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body
                    if (body != null) {
                        val expected = body.contentLength()
                        tmp.parentFile?.mkdirs()
                        tmp.delete()
                        var written = 0L
                        body.byteStream().use { input ->
                            tmp.outputStream().use { out ->
                                val buf = ByteArray(8192)
                                while (true) {
                                    val n = input.read(buf)
                                    if (n <= 0) break
                                    out.write(buf, 0, n)
                                    written += n
                                }
                            }
                        }
                        val lengthOk = (expected <= 0 || written == expected) && written > 0
                        if (lengthOk) {
                            dest.delete()
                            ok = tmp.renameTo(dest)
                            if (!ok) {
                                tmp.copyTo(dest, overwrite = true)
                                ok = dest.exists() && dest.length() == written
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            ok = false
        } finally {
            if (!ok) tmp.delete()
        }
        if (ok) recordUsed(url)
        ok
    }

    /**
     * 缓存整本书：逐个下载未缓存的分集，进度通过 [progress] 对外发布。
     * 同一本书同时只允许一个缓存任务，避免并发写坏文件。
     */
    fun cacheAlbum(folder: String, urls: List<String>, coverUrl: String? = null) {
        if (urls.isEmpty()) return
        if (!cachingFolders.add(folder)) return
        scope.launch {
            try {
                coverUrl?.let { cover ->
                    if (!isCoverCached(cover)) downloadCover(cover)
                }
                // 开始新书前复位旧的进度提示
                val total = urls.size
                var done = urls.count { isCached(it) }
                _progress.value = CacheProgress(folder, done, total)
                urls.forEach { url ->
                    if (!isCached(url)) {
                        if (download(url)) done++
                        _progress.value = CacheProgress(folder, done, total)
                    }
                }
                // 完成后短暂保留“已缓存”提示再复位
                delay(3000)
                if (_progress.value?.folder == folder) _progress.value = null
            } finally {
                cachingFolders.remove(folder)
            }
        }
    }
}
