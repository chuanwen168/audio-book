package com.chuanwen.audiobookclien

import android.app.Application
import android.content.Context
import com.chuanwen.audiobookclien.data.CacheManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用入口：持有全局 Context，供播放器单例等使用。
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 启动时自动清理 7 天未使用的缓存
        appScope.launch { CacheManager.cleanExpired() }
    }

    companion object {
        lateinit var instance: App
            private set

        /** 全局后台协程作用域（IO），用于缓存下载等长任务 */
        val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val context: Context
            get() = instance.applicationContext
    }
}
