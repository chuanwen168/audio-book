package com.chuanwen.audiobookclien.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chuanwen.audiobookclien.data.AudioRepository
import com.chuanwen.audiobookclien.data.CacheManager
import com.chuanwen.audiobookclien.data.ServerConfig
import com.chuanwen.audiobookclien.ui.theme.CardWhite
import com.chuanwen.audiobookclien.ui.theme.KidGreen
import com.chuanwen.audiobookclien.ui.theme.TextPrimary
import com.chuanwen.audiobookclien.ui.theme.TextSecondary
import com.chuanwen.audiobookclien.ui.theme.WarmBackground
import com.chuanwen.audiobookclien.ui.theme.XimalayaOrange
import kotlinx.coroutines.launch

/** 设置页：服务器地址配置 + 连接测试 + 关于 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var url by rememberSaveable { mutableStateOf(ServerConfig.getBaseUrl(context)) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val repository = remember { AudioRepository(context) }

    Column(
        modifier = Modifier.fillMaxSize().background(WarmBackground).padding(16.dp)
    ) {
        Text(
            "我的",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("服务器设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("服务器地址") },
                    placeholder = { Text("http://192.168.1.100:8086/") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            ServerConfig.setBaseUrl(context, url)
                            result = "已保存，重新加载首页生效"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = XimalayaOrange)
                    ) {
                        Text("保存")
                    }
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(
                        onClick = {
                            testing = true
                            result = null
                            scope.launch {
                                result = runCatching { repository.testConnection() }
                                    .getOrElse { "连接失败：${it.message}" }
                                testing = false
                            }
                        },
                        enabled = !testing
                    ) {
                        if (testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = XimalayaOrange
                            )
                        } else {
                            Text("测试连接")
                        }
                    }
                }
                result?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        message,
                        color = if (message.startsWith("连接成功")) KidGreen else Color(0xFFE5484D),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "提示：手机与服务器需在同一局域网。\n电脑上查看本机 IP：命令行执行 ipconfig；\n模拟器访问本机服务端用 http://10.0.2.2:8086/",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("缓存管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                var cacheSize by remember { mutableStateOf(CacheManager.totalSize()) }
                var cacheCount by remember { mutableStateOf(CacheManager.cachedCount()) }
                LaunchedEffect(Unit) {
                    CacheManager.cleanExpired()
                    cacheSize = CacheManager.totalSize()
                    cacheCount = CacheManager.cachedCount()
                }
                Text(
                    "已缓存 $cacheCount 个音频，占用 ${formatSize(cacheSize)}",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        CacheManager.clearAll()
                        cacheSize = CacheManager.totalSize()
                        cacheCount = CacheManager.cachedCount()
                    }
                ) {
                    Text("清空缓存")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "播放过的书会自动缓存；7 天未使用的缓存会在下次打开 App 时自动清理。",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("关于", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "宝宝听书 v1.1.0 —— 基于自建 audiobook-server 的安卓客户端。\n支持专辑浏览、分类检索、后台播放、倍速、定时关闭、本地收藏、播放记忆、整书缓存。",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "服务端接口：GET /api/v1/albums · GET /api/v1/album/:folder",
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 字节数 → 友好展示（B / KB / MB / GB） */
private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
