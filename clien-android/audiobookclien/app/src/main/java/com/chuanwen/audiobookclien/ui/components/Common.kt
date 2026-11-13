package com.chuanwen.audiobookclien.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.chuanwen.audiobookclien.data.CacheManager
import com.chuanwen.audiobookclien.data.ServerConfig
import com.chuanwen.audiobookclien.ui.theme.KidBlue
import com.chuanwen.audiobookclien.ui.theme.KidGreen
import com.chuanwen.audiobookclien.ui.theme.KidYellow
import com.chuanwen.audiobookclien.ui.theme.TextSecondary
import com.chuanwen.audiobookclien.ui.theme.XimalayaOrange
import com.chuanwen.audiobookclien.ui.theme.XimalayaOrangeDeep
import kotlin.math.abs

/** 封面色板：占位时按标题取色，活泼不重复 */
private val CoverPalette = listOf(
    XimalayaOrange,
    XimalayaOrangeDeep,
    KidBlue,
    KidGreen,
    KidYellow,
    Color(0xFF9B7BFF),
    Color(0xFFFF7BAC),
    Color(0xFF2FB5C9)
)

/**
 * 专辑封面：优先读本地缓存的封面文件（离线可用），没有本地文件时走网络；
 * 加载中/失败时显示标题首字的彩色占位。
 */
@Composable
fun AlbumCover(
    url: String,
    title: String,
    modifier: Modifier = Modifier,
    radius: Dp = 12.dp
) {
    val context = LocalContext.current
    val placeholderColor = CoverPalette[abs(title.hashCode()) % CoverPalette.size]
    val absolute = ServerConfig.absolute(context, url)
    val localCover = CacheManager.coverFileFor(absolute).takeIf { it.exists() && it.length() > 0 }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(localCover ?: absolute)
            .crossfade(true)
            .build(),
        contentDescription = title,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(radius)),
        loading = { CoverPlaceholder(title, placeholderColor) },
        error = { CoverPlaceholder(title, placeholderColor) }
    )
}

@Composable
private fun CoverPlaceholder(title: String, color: Color) {
    Box(
        modifier = Modifier.fillMaxSize().background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.firstOrNull()?.toString() ?: "听",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** 加载中 / 出错 / 重试 通用组件 */
@Composable
fun LoadingOrError(
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            loading -> CircularProgressIndicator(color = XimalayaOrange)
            error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "加载失败：$error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                androidx.compose.material3.TextButton(onClick = onRetry) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("重试", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

/** 毫秒 → mm:ss / h:mm:ss */
fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}
