package com.zhaoban.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhaoban.app.ui.components.PhotoThumb
import com.zhaoban.app.vm.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenReview: (unhandledOnly: Boolean, startIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val photos by vm.photos.collectAsStateWithLifecycle()
    val unhandled by vm.unhandled.collectAsStateWithLifecycle()
    val pendingCount by vm.pendingCount.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("照办 · 未整理 ${unhandled.size} / 共 ${photos.size} 张") },
                actions = {
                    if (pendingCount > 0) {
                        TextButton(onClick = { onOpenReview(true, 0) }) {
                            Text("待同步 ($pendingCount)")
                        }
                    }
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenReview(true, 0) },
                icon = { Icon(Icons.Default.Check, contentDescription = null) },
                text = { Text(if (unhandled.isEmpty()) "已全部整理" else "开始整理 ${unhandled.size} 张") },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                contentPadding = PaddingValues(bottom = 92.dp, start = 3.dp, end = 3.dp, top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(unhandled, key = { _, p -> p.id }) { index, photo ->
                    PhotoThumb(
                        photo = photo,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { onOpenReview(true, index) },
                    )
                }
            }

            if (unhandled.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (photos.isEmpty()) (if (isLoading) "正在读取照片…" else "没有读取到照片") else "全部整理完毕 🎉",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "主页只显示未整理的照片；已归类照片请在系统相册查看",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
