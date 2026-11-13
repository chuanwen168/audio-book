package com.chuanwen.audiobookclien.playback

import com.chuanwen.audiobookclien.App
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * 定时关闭（全局，跨页面有效）：
 * - 选择 10/20/30/40/60 分钟后开始倒计时
 * - 显示的剩余分钟数每分钟刷新一次（设 20 分钟，1 分钟后显示 19 分钟）
 * - 到点停止播放并退出 App
 * 与无操作 60 分钟自动退出相互独立，互不干扰。
 */
object SleepTimer {

    private val _remainingMinutes = MutableStateFlow<Int?>(null)

    /** 剩余分钟数；null 表示未设置定时 */
    val remainingMinutes: StateFlow<Int?> = _remainingMinutes.asStateFlow()

    private var endAt: Long? = null

    private var initialMinutes: Int? = null

    private var job: Job? = null

    /** 当前设置的总时长（用于对话框回显勾选） */
    val activeMinutes: Int? get() = initialMinutes

    val active: Boolean get() = endAt != null

    fun start(minutes: Int) {
        cancel()
        val end = System.currentTimeMillis() + minutes * 60_000L
        endAt = end
        initialMinutes = minutes
        _remainingMinutes.value = minutes
        job = App.appScope.launch {
            while (isActive) {
                val left = end - System.currentTimeMillis()
                if (left <= 0) {
                    _remainingMinutes.value = null
                    endAt = null
                    initialMinutes = null
                    IdleExitMonitor.stopAndExit()
                    break
                }
                val minutesLeft = ceil(left / 60_000.0).toInt()
                if (_remainingMinutes.value != minutesLeft) {
                    _remainingMinutes.value = minutesLeft
                }
                delay(1000L)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        endAt = null
        initialMinutes = null
        _remainingMinutes.value = null
    }
}
