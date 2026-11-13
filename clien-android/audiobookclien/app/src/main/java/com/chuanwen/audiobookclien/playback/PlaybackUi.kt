package com.chuanwen.audiobookclien.playback

import androidx.media3.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局播放连接容器：MainActivity 连接后台服务成功后，
 * 把 MediaController 包装成 PlayerStateHolder 放入此处，供各界面观察。
 */
object PlaybackUi {

    private val _holder = MutableStateFlow<PlayerStateHolder?>(null)
    val holder: StateFlow<PlayerStateHolder?> = _holder.asStateFlow()

    fun bind(controller: MediaController?) {
        _holder.value = controller?.let { PlayerStateHolder(it) }
    }
}
