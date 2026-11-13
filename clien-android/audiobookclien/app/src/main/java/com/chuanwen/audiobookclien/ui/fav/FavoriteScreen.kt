package com.chuanwen.audiobookclien.ui.fav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.chuanwen.audiobookclien.App
import com.chuanwen.audiobookclien.data.AudioRepository
import com.chuanwen.audiobookclien.data.LocalStore
import com.chuanwen.audiobookclien.ui.components.AlbumCover
import com.chuanwen.audiobookclien.ui.components.formatTime
import com.chuanwen.audiobookclien.ui.theme.CardWhite
import com.chuanwen.audiobookclien.ui.theme.TextPrimary
import com.chuanwen.audiobookclien.ui.theme.TextSecondary
import com.chuanwen.audiobookclien.ui.theme.WarmBackground
import com.chuanwen.audiobookclien.ui.theme.XimalayaOrange
import com.chuanwen.audiobookclien.ui.theme.XimalayaOrangeDeep
import kotlinx.coroutines.launch

/** 收藏页：我的收藏 + 最近收听（本地数据） */
@Composable
fun FavoriteScreen(
    onOpenAlbum: (String) -> Unit,
    onOpenPlayer: () -> Unit
) {
    val repository = remember { AudioRepository(App.context) }
    val scope = rememberCoroutineScope()

    var favorites by remember { mutableStateOf(LocalStore.favorites()) }
    var history by remember { mutableStateOf(LocalStore.history()) }
    var resuming by remember { mutableStateOf(false) }
    var resumeError by remember { mutableStateOf<String?>(null) }

    // 每次回到该页时刷新本地数据（从专辑详情返回后收藏状态即时生效）
    LifecycleResumeEffect(Unit) {
        favorites = LocalStore.favorites()
        history = LocalStore.history()
        onPauseOrDispose { }
    }

    fun resume(h: LocalStore.HistoryItem) {
        if (resuming) return
        resuming = true
        resumeError = null
        scope.launch {
            runCatching {
                val album = repository.fetchAlbum(h.folder)
                if (album.audios.isEmpty()) error("专辑暂无音频")
                val safeIndex = h.index.coerceIn(0, album.audios.size - 1)
                repository.playAlbum(album.folder_name, album.nfo, album.audios, safeIndex, h.positionMs)
            }.onSuccess { onOpenPlayer() }.onFailure {
                resumeError = it.message ?: "恢复播放失败"
            }
            resuming = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(WarmBackground)) {
        Text(
            "收藏",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (favorites.isEmpty() && history.isEmpty() && !resuming) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.StarBorder,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "还没有收藏和收听记录\n在专辑详情页点星标收藏，播放过的会自动记录",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (favorites.isNotEmpty()) {
                item {
                    Text(
                        "我的收藏（${favorites.size}）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                items(favorites, key = { "fav_${it.folder}" }) { fav ->
                    FavoriteRow(
                        folder = fav.folder,
                        title = fav.title,
                        onClick = { onOpenAlbum(fav.folder) },
                        onRemove = {
                            LocalStore.toggleFavorite(fav.folder, fav.title)
                            favorites = LocalStore.favorites()
                        }
                    )
                }
                item { Spacer(Modifier.height(6.dp)) }
            }

            if (history.isNotEmpty()) {
                item {
                    Text(
                        "最近收听",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                items(history, key = { "his_${it.folder}_${it.fileName}" }) { h ->
                    HistoryRow(
                        item = h,
                        onClick = { resume(h) },
                        disabled = resuming
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            onClick = {
                                LocalStore.clearHistory()
                                history = LocalStore.history()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF5EDE6)
                        ) {
                            Text(
                                "清空历史",
                                color = TextSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            if (resuming) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = XimalayaOrange)
                        Spacer(Modifier.width(8.dp))
                        Text("正在恢复播放…", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            resumeError?.let { err ->
                item {
                    Text(
                        "恢复失败：$err",
                        color = Color(0xFFE5484D),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/** 收藏专辑行 */
@Composable
private fun FavoriteRow(
    folder: String,
    title: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumCover(
            url = "/media/$folder/cover.jpg",
            title = title,
            modifier = Modifier.size(54.dp),
            radius = 10.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "已收藏",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Rounded.Star,
                contentDescription = "取消收藏",
                tint = XimalayaOrange
            )
        }
    }
}

/** 最近收听行：点击恢复播放 */
@Composable
private fun HistoryRow(
    item: LocalStore.HistoryItem,
    onClick: () -> Unit,
    disabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .clickable(enabled = !disabled, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.History,
            contentDescription = null,
            tint = XimalayaOrange,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.trackTitle,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${item.albumTitle} · 上次听到 ${formatTime(item.positionMs)} · ${relativeTime(item.playedAt)}",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "继续",
            color = XimalayaOrangeDeep,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** 相对时间：刚刚 / N 分钟前 / N 小时前 / N 天前 */
private fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> "刚刚"
        diff < hour -> "${diff / minute} 分钟前"
        diff < day -> "${diff / hour} 小时前"
        else -> "${diff / day} 天前"
    }
}
