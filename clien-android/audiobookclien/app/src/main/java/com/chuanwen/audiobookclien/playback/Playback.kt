package com.chuanwen.audiobookclien.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.chuanwen.audiobookclien.App
import com.chuanwen.audiobookclien.data.LocalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 全局唯一的播放器实例（应用进程级单例），由 MediaSessionService 托管，
 * 支持后台播放、来电暂停（音频焦点）、插拔耳机自动暂停。
 */
object Playback {

    /** 当前播放的专辑文件夹（由 AudioRepository.playAlbum 写入，供进度记忆使用） */
    @Volatile
    var currentFolder: String? = null

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(App.context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply { playWhenReady = true }
    }
}

/** 播放器 UI 状态快照 */
data class PlayerUiState(
    val connected: Boolean = false,
    val hasSession: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val title: String = "",
    val album: String = "",
    val folder: String = "",
    val artworkUrl: String = "",
    val duration: Long = 0L,
    val position: Long = 0L,
    val speed: Float = 1f,
    val count: Int = 0,
    val currentIndex: Int = 0
)

/**
 * 监听 MediaController（即 Player）状态变化，聚合为 Compose 可观察的 StateFlow。
 * 播放期间每 500ms 刷新一次进度。
 */
class PlayerStateHolder(private val controller: Player) : Player.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(PlayerUiState())
    val state = _state.asStateFlow()

    val player: Player = controller

    /** 上次保存进度的时间点，用于播放中节流（每 5 秒保存一次） */
    private var lastSavedPos = -1L

    init {
        controller.addListener(this)
        refresh()
        scope.launch {
            while (isActive) {
                if (controller.isPlaying) {
                    refreshPosition()
                    maybeSaveProgress()
                }
                delay(500)
            }
        }
    }

    private fun refreshPosition() {
        _state.update { it.copy(position = controller.currentPosition) }
    }

    /** 节流保存上次播放记忆 + 最近收听历史（本地） */
    private fun maybeSaveProgress() {
        val folder = Playback.currentFolder ?: return
        val mediaItem = controller.currentMediaItem ?: return
        val mediaId = mediaItem.mediaId
        if (mediaId.isBlank()) return
        if (controller.duration <= 0) return
        val pos = controller.currentPosition
        // 播放中每 5 秒保存一次；暂停 / 切换时立即保存
        if (controller.isPlaying && pos - lastSavedPos < 5000) return
        lastSavedPos = pos
        val meta = mediaItem.mediaMetadata
        val trackTitle = meta.title?.toString() ?: mediaId
        val albumTitle = meta.albumTitle?.toString() ?: folder
        LocalStore.saveLastPlayback(folder, mediaId, trackTitle, albumTitle, controller.currentMediaItemIndex, pos)
        LocalStore.addHistory(folder, mediaId, trackTitle, albumTitle, controller.currentMediaItemIndex, pos)
    }

    private fun refresh() {
        val metadata = controller.currentMediaItem?.mediaMetadata
        _state.value = PlayerUiState(
            connected = true,
            hasSession = controller.mediaItemCount > 0,
            isPlaying = controller.isPlaying,
            isBuffering = controller.playbackState == Player.STATE_BUFFERING,
            title = metadata?.title?.toString() ?: "",
            album = metadata?.albumTitle?.toString() ?: "",
            folder = Playback.currentFolder ?: "",
            artworkUrl = metadata?.artworkUri?.toString() ?: "",
            duration = if (controller.duration > 0) controller.duration else 0L,
            position = controller.currentPosition,
            speed = controller.playbackParameters.speed,
            count = controller.mediaItemCount,
            currentIndex = controller.currentMediaItemIndex
        )
    }

    override fun onEvents(player: Player, events: Player.Events) {
        refresh()
        maybeSaveProgress()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        refresh()
        maybeSaveProgress()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        refresh()
        maybeSaveProgress()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        refresh()
        if (playbackState == Player.STATE_ENDED) maybeSaveProgress()
    }

    override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) = refresh()

    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = refresh()
}
