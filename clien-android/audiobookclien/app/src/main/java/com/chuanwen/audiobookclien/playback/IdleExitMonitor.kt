package com.chuanwen.audiobookclien.playback

import android.content.Intent
import com.chuanwen.audiobookclien.App
import com.chuanwen.audiobookclien.PlayerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 无操作自动退出（后台“后门”）：连续 60 分钟没有任何用户操作时，
 * 停止播放并退出 App。与播放页的定时关闭是两个独立计时器，互不干扰，
 * 用于家长没有设置定时、小孩一直听的情况。
 */
object IdleExitMonitor {

    /** 无操作超时：60 分钟 */
    const val IDLE_TIMEOUT_MS = 60L * 60 * 1000

    @Volatile
    private var lastInteraction = System.currentTimeMillis()

    @Volatile
    private var started = false

    @Volatile
    private var exiting = false

    /** 退出时通知界面层关闭 Activity（MainActivity 注册，销毁时清空） */
    @Volatile
    var onExit: (() -> Unit)? = null

    /** 记录一次用户操作（触屏、回到前台等），重置 60 分钟计时 */
    fun touch() {
        lastInteraction = System.currentTimeMillis()
    }

    /** 启动后台监测（进程级，只会启动一次一个循环） */
    fun start() {
        if (started) return
        started = true
        App.appScope.launch {
            while (isActive) {
                delay(30_000L)
                if (System.currentTimeMillis() - lastInteraction >= IDLE_TIMEOUT_MS) {
                    stopAndExit()
                    break
                }
            }
            started = false
        }
    }

    /**
     * 停止播放并退出 App：
     * 定时关闭到点、无操作超时（后门）共用这一套退出逻辑。
     */
    suspend fun stopAndExit() = withContext(Dispatchers.Main) {
        if (exiting) return@withContext
        exiting = true
        runCatching { Playback.player.pause() }
        runCatching { Playback.player.stop() }
        runCatching { App.context.stopService(Intent(App.context, PlayerService::class.java)) }
        runCatching { onExit?.invoke() }
        lastInteraction = System.currentTimeMillis()
        exiting = false
    }
}
