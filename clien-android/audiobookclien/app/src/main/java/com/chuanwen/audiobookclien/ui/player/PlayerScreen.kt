package com.chuanwen.audiobookclien.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chuanwen.audiobookclien.playback.PlaybackUi
import com.chuanwen.audiobookclien.playback.PlayerUiState
import com.chuanwen.audiobookclien.playback.SleepTimer
import com.chuanwen.audiobookclien.ui.components.AlbumCover
import com.chuanwen.audiobookclien.ui.components.formatTime
import com.chuanwen.audiobookclien.ui.theme.CardWhite
import com.chuanwen.audiobookclien.ui.theme.TextPrimary
import com.chuanwen.audiobookclien.ui.theme.TextSecondary
import com.chuanwen.audiobookclien.ui.theme.WarmBackground
import com.chuanwen.audiobookclien.ui.theme.XimalayaOrange
import com.chuanwen.audiobookclien.ui.theme.XimalayaOrangeDeep
import kotlinx.coroutines.flow.MutableStateFlow

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

private fun nextSpeed(current: Float): Float {
    val idx = SPEEDS.indexOfFirst { it == current }
    val base = if (idx >= 0) idx else 1
    return SPEEDS[(base + 1) % SPEEDS.size]
}

/** 全屏播放页：封面、进度条、倍速、定时关闭、播放列表 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(onBack: () -> Unit) {
    val holder by PlaybackUi.holder.collectAsState()
    val state by remember(holder) { holder?.state ?: MutableStateFlow(PlayerUiState()) }.collectAsState()
    val controller = holder?.player

    var showPlaylist by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }

    // 定时关闭：全局倒计时状态，每分钟刷新剩余分钟数（设 20 分钟 → 1 分钟后显示 19 分钟）
    val remainingMinutes by SleepTimer.remainingMinutes.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFF1E8), WarmBackground)))
    ) {
        if (!state.hasSession) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "暂无播放内容\n请先在专辑页选择音频",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                Icon(Icons.Rounded.ExpandMore, contentDescription = "收起", tint = TextPrimary, modifier = Modifier.size(30.dp))
            }
            return@Box
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            // 顶部：收起 + 标题 + 占位
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ExpandMore, contentDescription = "收起", tint = TextPrimary, modifier = Modifier.size(30.dp))
                }
                Text(
                    "正在播放",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(48.dp))
            }

            // 封面
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                AlbumCover(
                    url = state.artworkUrl,
                    title = state.title,
                    modifier = Modifier
                        .size(250.dp)
                        .shadow(24.dp, RoundedCornerShape(28.dp), spotColor = XimalayaOrange.copy(alpha = 0.35f)),
                    radius = 28.dp
                )
            }

            // 标题区
            Text(
                state.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                state.album,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )

            // 进度条
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(formatTime(state.position), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Slider(
                    value = if (state.duration > 0) state.position.toFloat().coerceIn(0f, state.duration.toFloat()) else 0f,
                    onValueChange = { controller?.seekTo(it.toLong()) },
                    valueRange = 0f..(if (state.duration > 0) state.duration.toFloat() else 1f),
                    colors = SliderDefaults.colors(
                        thumbColor = XimalayaOrange,
                        activeTrackColor = XimalayaOrange,
                        inactiveTrackColor = Color(0xFFEADFD5)
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                )
                Text(formatTime(state.duration), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }

            // 主控制条
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { controller?.setPlaybackSpeed(nextSpeed(state.speed)) }) {
                    Text("${state.speed}x", color = TextSecondary, fontWeight = FontWeight.Medium)
                }
                IconButton(onClick = { controller?.seekToPreviousMediaItem() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一集", tint = TextPrimary, modifier = Modifier.size(34.dp))
                }
                Surface(
                    onClick = { if (state.isPlaying) controller?.pause() else controller?.play() },
                    shape = CircleShape,
                    color = XimalayaOrange,
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
                IconButton(onClick = { controller?.seekToNextMediaItem() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "下一集", tint = TextPrimary, modifier = Modifier.size(34.dp))
                }
                TextButton(onClick = { showTimerDialog = true }) {
                    Icon(
                        Icons.Rounded.Timer,
                        contentDescription = "定时关闭",
                        tint = if (remainingMinutes != null) XimalayaOrange else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 播放列表入口 + 定时倒计时
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showPlaylist = true }) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("播放列表（${state.count}）", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                val left = remainingMinutes
                if (left != null) {
                    Spacer(Modifier.width(12.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = XimalayaOrange.copy(alpha = 0.12f)) {
                        Text(
                            "$left 分钟后退出",
                            color = XimalayaOrangeDeep,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    // 播放列表底部弹层
    if (showPlaylist) {
        val items = if (controller == null) emptyList() else
            (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it) }
        ModalBottomSheet(onDismissRequest = { showPlaylist = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    "播放列表（${items.size}）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                items.forEachIndexed { index, item ->
                    val name = item.mediaMetadata.title?.toString() ?: item.mediaId
                    val isCurrent = index == state.currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                controller?.seekTo(index, 0L)
                                showPlaylist = false
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isCurrent) Icons.Rounded.GraphicEq else Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = if (isCurrent) XimalayaOrange else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            name,
                            color = if (isCurrent) XimalayaOrangeDeep else TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isCurrent) {
                            Text("播放中", color = XimalayaOrange, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    // 定时关闭对话框
    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            title = { Text("定时关闭播放") },
            text = {
                Column {
                    Text(
                        "到点后停止播放并退出 App；剩余时间每分钟刷新一次",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    listOf(10, 20, 30, 40, 60).forEach { minutes ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    SleepTimer.start(minutes)
                                    showTimerDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("$minutes 分钟", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            if (SleepTimer.activeMinutes == minutes) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = XimalayaOrange)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                SleepTimer.cancel()
                                showTimerDialog = false
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("取消定时", color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTimerDialog = false }) { Text("关闭") }
            }
        )
    }
}
