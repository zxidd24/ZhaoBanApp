package com.zhaoban.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zhaoban.app.data.Photo

/**
 * 统一缩略图：不显式指定 size，由 Coil 按控件尺寸自动采样，
 * 避免手动 decode 原图导致 OOM（开发手册 §7）。
 */
@Composable
fun PhotoThumb(
    photo: Photo,
    modifier: Modifier = Modifier,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    val context = LocalContext.current
    val request = remember(photo.id, context) {
        ImageRequest.Builder(context)
            .data(photo.uri)
            .crossfade(true)
            .build()
    }

    // URI 失效时 Coil 只画空白，不让整页报错（开发手册 §1.3）
    Box(modifier.clip(RoundedCornerShape(4.dp))) {
        AsyncImage(
            model = request,
            contentDescription = photo.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        overlay?.invoke(this)
    }
}
