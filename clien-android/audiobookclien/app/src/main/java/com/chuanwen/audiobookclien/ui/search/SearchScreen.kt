package com.chuanwen.audiobookclien.ui.search

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chuanwen.audiobookclien.App
import com.chuanwen.audiobookclien.data.AlbumSimple
import com.chuanwen.audiobookclien.data.AudioRepository
import com.chuanwen.audiobookclien.ui.components.AlbumCover
import com.chuanwen.audiobookclien.ui.components.LoadingOrError
import com.chuanwen.audiobookclien.ui.theme.CardWhite
import com.chuanwen.audiobookclien.ui.theme.TextPrimary
import com.chuanwen.audiobookclien.ui.theme.TextSecondary
import com.chuanwen.audiobookclien.ui.theme.WarmBackground
import com.chuanwen.audiobookclien.ui.theme.XimalayaOrange
import kotlinx.coroutines.launch

/** 搜索页：本地过滤专辑标题 / 文件夹名 */
@Composable
fun SearchScreen(onOpenAlbum: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var albums by remember { mutableStateOf<List<AlbumSimple>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val repository = remember { AudioRepository(App.context) }

    fun load() {
        loading = true
        error = null
        scope.launch {
            try {
                albums = repository.fetchAlbums()
                loading = false
            } catch (e: Exception) {
                error = e.message ?: "加载失败"
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (albums == null) load()
    }

    Column(modifier = Modifier.fillMaxSize().background(WarmBackground)) {
        // 搜索输入框
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索专辑名称", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CardWhite,
                    unfocusedContainerColor = CardWhite,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        when {
            loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                LoadingOrError(loading = true, error = null, onRetry = {})
            }

            error != null -> Box(Modifier.fillMaxWidth().weight(1f)) {
                LoadingOrError(loading = false, error = error, onRetry = ::load)
            }

            albums != null -> {
                val all = albums!!
                val keyword = query.trim()
                val results = all.filter {
                    keyword.isEmpty() ||
                        it.nfo.title.contains(keyword, ignoreCase = true) ||
                        it.folder_name.contains(keyword, ignoreCase = true)
                }

                if (keyword.isEmpty() && all.isNotEmpty()) {
                    // 热门专辑快速入口
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text("热门专辑", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            lazyItems(all.take(10)) { album ->
                                AssistChip(
                                    onClick = { query = album.nfo.title },
                                    label = { Text(album.nfo.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                        }
                    }
                }

                if (results.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("没有找到相关专辑", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(results, key = { it.folder_name }) { album ->
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onOpenAlbum(album.folder_name) }
                            ) {
                                AlbumCover(
                                    url = album.nfo.cover,
                                    title = album.nfo.title,
                                    modifier = Modifier.fillMaxWidth().height(150.dp),
                                    radius = 14.dp
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    album.nfo.title,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
