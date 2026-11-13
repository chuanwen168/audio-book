package com.chuanwen.audiobookclien.ui.detail

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chuanwen.audiobookclien.App
import com.chuanwen.audiobookclien.data.AlbumFull
import com.chuanwen.audiobookclien.data.AudioRepository
import com.chuanwen.audiobookclien.data.CacheManager
import com.chuanwen.audiobookclien.data.LocalStore
import com.chuanwen.audiobookclien.playback.PlaybackUi
import com.chuanwen.audiobookclien.ui.components.AlbumCover
import com.chuanwen.audiobookclien.ui.components.LoadingOrError
import com.chuanwen.audiobookclien.ui.theme.CardWhite
import com.chuanwen.audiobookclien.ui.theme.TextPrimary
import com.chuanwen.audiobookclien.ui.theme.TextSecondary
import com.chuanwen.audiobookclien.ui.theme.WarmBackground
import com.chuanwen.audiobookclien.ui.theme.XimalayaOrange
import com.chuanwen.audiobookclien.ui.theme.XimalayaOrangeDeep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 专辑详情状态 */
data class DetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val album: AlbumFull? = null
)

class DetailViewModel(private val folder: String) : ViewModel() {

    private val repository = AudioRepository(App.context)
    private val _ui = MutableStateFlow(DetailUiState())
    val ui = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        _ui.value = DetailUiState(loading = true, error = null, album = null)
        viewModelScope.launch {
            try {
                val album = repository.fetchAlbum(folder)
                _ui.value = DetailUiState(loading = false, album = album)
            } catch (e: Exception) {
                _ui.value = DetailUiState(loading = false, error = e.message ?: "加载失败")
            }
        }
    }

    /** 从指定分集开始播放整个专辑 */
    fun playFrom(index: Int) {
        val album = _ui.value.album ?: return
        repository.playAlbum(album.folder_name, album.nfo, album.audios, index)
    }

    fun cleanName(fileName: String): String = repository.cleanName(fileName)
}

@Composable
fun AlbumDetailScreen(folder: String, onBack: () -> Unit) {
    val viewModel: DetailViewModel = viewModel(
        key = "detail_$folder",
        factory = viewModelFactory {
            initializer { DetailViewModel(folder) }
        }
    )
    val ui by viewModel.ui.collectAsState()

    // 当前正在播放的分集
    val holder by PlaybackUi.holder.collectAsState()
    val currentMediaId = holder?.player?.currentMediaItem?.mediaId
    val isPlaying = holder?.player?.isPlaying == true

    Column(modifier = Modifier.fillMaxSize().background(WarmBackground)) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = TextPrimary)
            }
            Text(
                "专辑详情",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            var favorited by remember { mutableStateOf(LocalStore.isFavorite(folder)) }
            IconButton(onClick = {
                val title = ui.album?.nfo?.title ?: folder
                favorited = LocalStore.toggleFavorite(folder, title)
            }) {
                Icon(
                    if (favorited) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = if (favorited) "取消收藏" else "收藏",
                    tint = if (favorited) XimalayaOrange else TextSecondary
                )
            }
        }

        when {
            ui.loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                LoadingOrError(loading = true, error = null, onRetry = {})
            }

            ui.error != null -> Box(Modifier.fillMaxWidth().weight(1f)) {
                LoadingOrError(loading = false, error = ui.error, onRetry = viewModel::load)
            }

            ui.album != null -> {
                val album = ui.album!!
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { DetailHeader(album) }
                    item {
                        Button(
                            onClick = { viewModel.playFrom(0) },
                            colors = ButtonDefaults.buttonColors(containerColor = XimalayaOrange),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("播放全部（${album.audios.size} 集）", fontWeight = FontWeight.Bold)
                        }
                    }
                    item { CacheProgressLine(folder = folder) }
                    item {
                        Text(
                            "分集列表（${album.audios.size}）",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    itemsIndexed(album.audios, key = { _, audio -> audio.file_name }) { index, audio ->
                        TrackRow(
                            name = viewModel.cleanName(audio.file_name),
                            isCurrent = currentMediaId == audio.file_name,
                            isPlaying = isPlaying && currentMediaId == audio.file_name,
                            onClick = { viewModel.playFrom(index) }
                        )
                    }
                }
            }
        }
    }
}

/** 专辑头部：封面 + 标题 + 文件夹标签 + 简介 */
@Composable
private fun DetailHeader(album: AlbumFull) {
    Row {
        AlbumCover(
            url = album.nfo.cover,
            title = album.nfo.title,
            modifier = Modifier.size(110.dp),
            radius = 14.dp
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                album.nfo.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF5EDE6)
            ) {
                Text(
                    album.folder_name,
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (album.nfo.desc.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    album.nfo.desc,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 本书缓存进度提示（播放本书时自动缓存整本书） */
@Composable
private fun CacheProgressLine(folder: String) {
    val progress by CacheManager.progress.collectAsState()
    val p = progress
    if (p == null || p.folder != folder) return
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
        Text(
            if (p.finished) "本书已缓存 ${p.total} 集，可离线收听"
            else "正在自动缓存本书：${p.done}/${p.total}",
            color = XimalayaOrangeDeep,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** 单集行：当前播放高亮 + 动态均衡器图标 */
@Composable
private fun TrackRow(
    name: String,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) Color(0xFFFFEDE6) else CardWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCurrent) {
            Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = XimalayaOrange,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = "▶",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(22.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            name,
            color = if (isCurrent) XimalayaOrangeDeep else TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isPlaying) {
            Text(
                "播放中",
                color = XimalayaOrange,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 8.dp)
            )
        } else {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Color(0xFFDDD3CA),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
