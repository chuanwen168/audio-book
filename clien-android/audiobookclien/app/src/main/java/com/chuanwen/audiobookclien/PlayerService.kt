package com.chuanwen.audiobookclien

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.chuanwen.audiobookclien.playback.Playback

/**
 * 后台播放服务：托管全局 ExoPlayer，提供 MediaSession，
 * 使通知栏控制、锁屏控制、后台/熄屏继续播放均可用。
 */
class PlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSession.Builder(this, Playback.player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** 任务被划掉时：仍在播放则保持后台播放，否则停服 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = Playback.player
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
