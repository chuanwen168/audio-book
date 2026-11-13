package com.chuanwen.audiobookclien.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chuanwen.audiobookclien.playback.PlaybackUi
import com.chuanwen.audiobookclien.playback.PlayerUiState
import com.chuanwen.audiobookclien.ui.components.AlbumCover
import com.chuanwen.audiobookclien.ui.detail.AlbumDetailScreen
import com.chuanwen.audiobookclien.ui.fav.FavoriteScreen
import com.chuanwen.audiobookclien.ui.home.HomeScreen
import com.chuanwen.audiobookclien.ui.player.PlayerScreen
import com.chuanwen.audiobookclien.ui.search.SearchScreen
import com.chuanwen.audiobookclien.ui.settings.SettingsScreen
import com.chuanwen.audiobookclien.ui.theme.CardWhite
import com.chuanwen.audiobookclien.ui.theme.DividerSoft
import com.chuanwen.audiobookclien.ui.theme.TextPrimary
import com.chuanwen.audiobookclien.ui.theme.TextSecondary
import com.chuanwen.audiobookclien.ui.theme.XimalayaOrange
import kotlinx.coroutines.flow.MutableStateFlow

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val FAV = "fav"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val ALBUM = "album/{folder}"
    fun album(folder: String) = "album/${Uri.encode(folder)}"
}

/** 应用根：底部导航 + 迷你播放条 + 路由 */
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    val showBottomBar = route == Routes.HOME || route == Routes.SEARCH || route == Routes.FAV || route == Routes.SETTINGS
    val showMiniPlayer = route != Routes.PLAYER

    Scaffold(
        containerColor = Color(0xFFFFF7F0),
        // 内容统一避开系统安全区（状态栏 / 虚拟按键导航栏 / 刘海）
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            // 底部栏由自身统一吸收“底部安全区”inset（虚拟按键 / 手势条高度），
            // 并把 NavigationBar 自带的 inset 关掉，避免重复 padding。
            // 关键点：底部栏不能只在“有内容”时才存在、也不能依赖默认行为——
            // 播放页/专辑详情页底部栏为空时，Scaffold 会把“高度为 0 的底部栏”视为已处理 insets，
            // 从而不给内容留出虚拟按键高度，底部控件会画到系统按键下面导致误触。
            // 这里用 Column 无条件垫上底部安全区，任何页面都不会与虚拟按键重叠。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .background(CardWhite)
            ) {
                if (showMiniPlayer) {
                    MiniPlayer(
                        onClick = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } }
                    )
                }
                if (showBottomBar) {
                    BottomBar(
                        route = route,
                        onSelect = { destination ->
                            navController.navigate(destination) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenAlbum = { folder -> navController.navigate(Routes.album(folder)) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenPlayer = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } }
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onOpenAlbum = { folder -> navController.navigate(Routes.album(folder)) }
                )
            }
            composable(Routes.FAV) {
                FavoriteScreen(
                    onOpenAlbum = { folder -> navController.navigate(Routes.album(folder)) },
                    onOpenPlayer = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } }
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.PLAYER) { PlayerScreen(onBack = { navController.popBackStack() }) }
            composable(
                route = Routes.ALBUM,
                arguments = listOf(navArgument("folder") { type = NavType.StringType })
            ) { entry ->
                val folder = entry.arguments?.getString("folder")?.let { Uri.decode(it) } ?: ""
                AlbumDetailScreen(folder = folder, onBack = { navController.popBackStack() })
            }
        }
    }
}

/** 底部导航（首页 / 搜索 / 收藏 / 我的） */
@Composable
private fun BottomBar(route: String?, onSelect: (String) -> Unit) {
    NavigationBar(
        containerColor = CardWhite,
        // 底部安全区（虚拟按键高度）已由外层 Column 统一吸收，这里关闭自带 inset 防止双重 padding
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        NavigationBarItem(
            selected = route == Routes.HOME,
            onClick = { onSelect(Routes.HOME) },
            icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
            label = { Text("首页") }
        )
        NavigationBarItem(
            selected = route == Routes.SEARCH,
            onClick = { onSelect(Routes.SEARCH) },
            icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            label = { Text("搜索") }
        )
        NavigationBarItem(
            selected = route == Routes.FAV,
            onClick = { onSelect(Routes.FAV) },
            icon = { Icon(Icons.Rounded.Star, contentDescription = null) },
            label = { Text("收藏") }
        )
        NavigationBarItem(
            selected = route == Routes.SETTINGS,
            onClick = { onSelect(Routes.SETTINGS) },
            icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
            label = { Text("我的") }
        )
    }
}

/** 迷你播放条：封面 + 标题 + 播放/暂停，点击进入全屏播放页 */
@Composable
private fun MiniPlayer(onClick: () -> Unit) {
    val holder by PlaybackUi.holder.collectAsState()
    val state by remember(holder) { holder?.state ?: MutableStateFlow(PlayerUiState()) }.collectAsState()
    if (!state.hasSession) return

    val controller = holder?.player

    Surface(
        onClick = onClick,
        color = CardWhite,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumCover(
                    url = state.artworkUrl,
                    title = state.title,
                    modifier = Modifier.size(40.dp),
                    radius = 8.dp
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        state.album,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = {
                    if (state.isPlaying) controller?.pause() else controller?.play()
                }) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        tint = XimalayaOrange
                    )
                }
            }
            // 播放进度细条
            val progress = if (state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f, 1f) else 0f
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(DividerSoft)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(2.dp)
                        .background(XimalayaOrange)
                )
            }
        }
    }
}
