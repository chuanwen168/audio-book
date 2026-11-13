package com.chuanwen.audiobookclien

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.chuanwen.audiobookclien.playback.IdleExitMonitor
import com.chuanwen.audiobookclien.playback.PlaybackUi
import com.chuanwen.audiobookclien.ui.AppRoot
import com.chuanwen.audiobookclien.ui.theme.AudiobookTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        connectToPlayerService()
        // 无操作 60 分钟自动停止播放并退出 App（后台“后门”，与定时关闭互不干扰）
        IdleExitMonitor.onExit = { finishAndRemoveTask() }
        IdleExitMonitor.touch()
        IdleExitMonitor.start()
        setContent {
            AudiobookTheme {
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        IdleExitMonitor.touch()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        IdleExitMonitor.touch()
    }

    override fun onDestroy() {
        IdleExitMonitor.onExit = null
        super.onDestroy()
    }

    /** 任何触屏操作都算“有人在用”，重置 60 分钟无操作计时 */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        IdleExitMonitor.touch()
        return super.dispatchTouchEvent(ev)
    }

    /**
     * 异步连接后台播放服务（Media3 MediaSessionService），
     * 连接成功后把 MediaController 交给全局 PlaybackUi 供 UI 观察与操控。
     */
    private fun connectToPlayerService() {
        val sessionToken = SessionToken(this, ComponentName(this, PlayerService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({
            runOnUiThread {
                PlaybackUi.bind(runCatching { controllerFuture.get() }.getOrNull())
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }
}
