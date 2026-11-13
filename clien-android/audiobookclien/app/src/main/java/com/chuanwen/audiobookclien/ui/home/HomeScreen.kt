package com.chuanwen.audiobookclien.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chuanwen.audiobookclien.App
import com.chuanwen.audiobookclien.data.AlbumNfo
import com.chuanwen.audiobookclien.data.AlbumSimple
import com.chuanwen.audiobookclien.data.AudioRepository
import com.chuanwen.audiobookclien.data.LocalStore
import com.chuanwen.audiobookclien.ui.components.AlbumCover
import com.chuanwen.audiobookclien.ui.components.LoadingOrError
import com.chuanwen.audiobookclien.ui.components.formatTime
import com.chuanwen.audiobookclien.ui.theme.CardWhite
import com.chuanwen.audiobookclien.ui.theme.DividerSoft
import com.chuanwen.audiobookclien.ui.theme.TextPrimary
import com.chuanwen.audiobookclien.ui.theme.TextSecondary
import com.chuanwen.audiobookclien.ui.theme.WarmBackground
import com.chuanwen.audiobookclien.ui.theme.XimalayaOrange
import com.chuanwen.audiobookclien.ui.theme.XimalayaOrangeDeep
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 首页状态 */
data class HomeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val albums: List<AlbumSimple> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),
    val selectedCategory: String = AlbumCategories.ALL,
    val lastPlayback: LocalStore.LastPlayback? = null,
    val lastAlbumNfo: AlbumNfo? = null
)

class HomeViewModel : ViewModel() {

    private val repository = AudioRepository(App.context)
    private val _ui = MutableStateFlow(HomeUiState())
    val ui = _ui.asStateFlow()

    init {
        refresh()
        loadLastPlayback()
    }

    fun refresh() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val albums = repository.fetchAlbums()
                _ui.update { it.copy(albums = albums, loading = false, error = null) }
                // 并行拉取每个专辑的集数，用于角标展示
                albums.map { album ->
                    async {
                        val count = runCatching { repository.fetchAlbum(album.folder_name).audios.size }.getOrDefault(0)
                        album.folder_name to count
                    }
                }.awaitAll().forEach { (folder, count) ->
                    _ui.update { it.copy(counts = it.counts + (folder to count)) }
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun selectCategory(category: String) {
        _ui.update { it.copy(selectedCategory = category) }
    }

    /** 读取上次播放记忆，并尝试拉取专辑信息用于展示 */
    fun loadLastPlayback() {
        val lp = LocalStore.lastPlayback() ?: run {
            _ui.update { it.copy(lastPlayback = null, lastAlbumNfo = null) }
            return
        }
        _ui.update { it.copy(lastPlayback = lp) }
        viewModelScope.launch {
            val nfo = runCatching { repository.fetchAlbum(lp.folder).nfo }.getOrNull()
            _ui.update { it.copy(lastAlbumNfo = nfo) }
        }
    }

    /** 继续上次播放：拉取专辑并从记忆位置播放 */
    fun resumeLast(onResumed: () -> Unit) {
        val lp = _ui.value.lastPlayback ?: return
        viewModelScope.launch {
            try {
                val album = repository.fetchAlbum(lp.folder)
                if (album.audios.isEmpty()) return@launch
                val safeIndex = lp.index.coerceIn(0, album.audios.size - 1)
                repository.playAlbum(album.folder_name, album.nfo, album.audios, safeIndex, lp.positionMs)
                onResumed()
            } catch (e: Exception) {
                // 恢复失败时保留卡片，下一次点击可重试
            }
        }
    }

    fun dismissLast() {
        LocalStore.clearLastPlayback()
        loadLastPlayback()
    }
}

/** 按专辑名称关键词归类，模拟大厂的分类频道 */
object AlbumCategories {
    const val ALL = "全部"
    const val BEDTIME = "睡前故事"
    const val SONGS = "儿歌"
    const val DETECTIVE = "侦探探案"
    const val SCIENCE = "科普百科"
    const val CLASSIC = "国学启蒙"
    const val STORY = "精彩故事"

    val all = listOf(ALL, BEDTIME, SONGS, DETECTIVE, SCIENCE, CLASSIC, STORY)

    fun categoryOf(album: AlbumSimple): String {
        val t = album.nfo.title + album.folder_name
        return when {
            t.contains("睡前") || t.contains("哄睡") || t.contains("晚安") -> BEDTIME
            t.contains("儿歌") || t.contains("英文") -> SONGS
            t.contains("警长") || t.contains("探案") || t.contains("侦探") || t.contains("魔术") -> DETECTIVE
            t.contains("为什么") || t.contains("科学") || t.contains("百科") || t.contains("挑战") -> SCIENCE
            t.contains("三字经") || t.contains("国学") || t.contains("唐诗") || t.contains("成语") -> CLASSIC
            else -> STORY
        }
    }
}

@Composable
fun HomeScreen(
    onOpenAlbum: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val viewModel: HomeViewModel = viewModel()
    val ui by viewModel.ui.collectAsState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(WarmBackground),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            HomeHeader(onOpenSearch = onOpenSearch, onOpenSettings = onOpenSettings)
        }

        if (ui.albums.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Banner(
                    albums = ui.albums,
                    counts = ui.counts,
                    onOpenAlbum = onOpenAlbum
                )
            }
        }

        // 上次播放记忆：继续收听入口
        if (ui.lastPlayback != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ResumeCard(
                    playback = ui.lastPlayback!!,
                    nfo = ui.lastAlbumNfo,
                    onResume = { viewModel.resumeLast(onOpenPlayer) },
                    onDismiss = viewModel::dismissLast
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            CategoryChips(
                selected = ui.selectedCategory,
                onSelect = viewModel::selectCategory
            )
        }

        if (ui.loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    LoadingOrError(loading = true, error = null, onRetry = {})
                }
            }
        }

        if (ui.error != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().height(180.dp)) {
                    LoadingOrError(loading = false, error = ui.error, onRetry = viewModel::refresh)
                }
            }
        }

        val filtered = ui.albums.filter {
            ui.selectedCategory == AlbumCategories.ALL ||
                AlbumCategories.categoryOf(it) == ui.selectedCategory
        }
        items(filtered, key = { it.folder_name }) { album ->
            AlbumCard(
                album = album,
                count = ui.counts[album.folder_name],
                onClick = { onOpenAlbum(album.folder_name) }
            )
        }

        if (filtered.isEmpty() && !ui.loading && ui.error == null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Text("该分类暂无专辑", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** 顶部渐变横幅：品牌 + 搜索入口 + 设置 */
@Composable
private fun HomeHeader(onOpenSearch: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(XimalayaOrangeDeep, XimalayaOrange)))
            .padding(top = 20.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Headphones,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    "宝宝听书",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "宝宝巴士 · 睡前故事 · 儿歌 · 百科",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "设置", tint = Color.White)
            }
        }
        Spacer(Modifier.height(14.dp))
        Surface(
            onClick = onOpenSearch,
            shape = RoundedCornerShape(22.dp),
            color = Color.White.copy(alpha = 0.95f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(44.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("搜索专辑 / 故事", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** 精选轮播 Banner（自动播放） */
@Composable
private fun Banner(
    albums: List<AlbumSimple>,
    counts: Map<String, Int>,
    onOpenAlbum: (String) -> Unit
) {
    val pages = albums.take(5)
    if (pages.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { pages.size })

    LaunchedEffect(pagerState, pages.size) {
        while (true) {
            delay(4000)
            val next = (pagerState.currentPage + 1) % pages.size
            pagerState.animateScrollToPage(next)
        }
    }

    Box(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 0.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val album = pages[page]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpenAlbum(album.folder_name) }
            ) {
                AlbumCover(
                    url = album.nfo.cover,
                    title = album.nfo.title,
                    modifier = Modifier.fillMaxSize(),
                    radius = 16.dp
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                ) {
                    Text(
                        album.nfo.title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "共 ${counts[album.folder_name] ?: "?"} 集 · ${AlbumCategories.categoryOf(album)}",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        // 指示点
        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            pages.indices.forEach { i ->
                Box(
                    modifier = Modifier
                        .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (i == pagerState.currentPage) Color.White else Color.White.copy(alpha = 0.5f))
                )
            }
        }
    }
}

/** 分类筛选条 */
@Composable
private fun CategoryChips(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AlbumCategories.all.forEach { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(category) },
                border = null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = CardWhite,
                    labelColor = TextSecondary,
                    selectedContainerColor = XimalayaOrange,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

/** 继续收听卡片：上次播放记忆入口 */
@Composable
private fun ResumeCard(
    playback: LocalStore.LastPlayback,
    nfo: AlbumNfo?,
    onResume: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        onClick = onResume,
        shape = RoundedCornerShape(14.dp),
        color = CardWhite,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumCover(
                url = nfo?.cover ?: "/media/${playback.folder}/cover.jpg",
                title = nfo?.title ?: playback.folder,
                modifier = Modifier.size(52.dp),
                radius = 10.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "继续收听",
                    color = XimalayaOrangeDeep,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    nfo?.title ?: playback.folder,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "第 ${playback.index + 1} 集 · 上次听到 ${formatTime(playback.positionMs)}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "移除",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** 专辑卡片（封面 + 标题 + 简介 + 集数角标） */
@Composable
private fun AlbumCard(
    album: AlbumSimple,
    count: Int?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            AlbumCover(
                url = album.nfo.cover,
                title = album.nfo.title,
                modifier = Modifier.fillMaxSize(),
                radius = 14.dp
            )
            if (count != null && count > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("$count 集", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            album.nfo.title,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (album.nfo.desc.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                album.nfo.desc,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
