package com.zhaoban.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zhaoban.app.data.Photo
import com.zhaoban.app.data.SystemAlbum
import com.zhaoban.app.vm.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val SWIPE_THRESHOLD_DP = 120.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReviewScreen(
    vm: MainViewModel,
    photos: List<Photo>,
    startIndex: Int,
    onExit: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val systemAlbums by vm.systemAlbums.collectAsStateWithLifecycle()
    val pendingCount by vm.pendingCount.collectAsStateWithLifecycle()
    val isSyncing by vm.isSyncing.collectAsStateWithLifecycle()
    // 订阅动作队列，确保 beginSync 读到最新值（WhileSubscribed 需有订阅者才激活）
    val pendingActionsList by vm.pendingActions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val density = LocalDensity.current
    val thresholdPx = with(density) { SWIPE_THRESHOLD_DP.toPx() }

    var dragY by remember { mutableFloatStateOf(0f) }
    val animatedDrag by animateFloatAsState(
        targetValue = dragY,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "swipe",
    )
    var finished by remember { mutableStateOf(false) }
    var syncStage by remember { mutableIntStateOf(0) }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0)),
        pageCount = { photos.size },
    )
    val currentPhoto = photos.getOrNull(pagerState.currentPage)

    var pendingLaunch by remember { mutableStateOf<android.content.IntentSender?>(null) }

    val syncLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val ok = result.resultCode == Activity.RESULT_OK
        when (syncStage) {
            1 -> {
                val next = vm.onWriteDialogResult(context, ok)
                if (next != null) {
                    syncStage = 2
                    pendingLaunch = next.sender
                } else syncStage = 0
            }
            2 -> {
                vm.onDeleteDialogResult(ok)
                syncStage = 0
            }
            else -> syncStage = 0
        }
    }

    // 供回调内继续拉起下一步系统弹窗
    LaunchedEffect(pendingLaunch) {
        pendingLaunch?.let { s ->
            pendingLaunch = null
            syncLauncher.launch(IntentSenderRequest.Builder(s).build())
        }
    }

    /** 处理完当前照片后：非最后一张则推进 pager；否则视为整批看完。 */
    fun next() {
        val last = photos.lastIndex
        if (pagerState.currentPage >= last) finished = true
        else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
    }

    fun actMove(album: SystemAlbum) {
        currentPhoto?.let { p ->
            vm.moveTo(p.id, album.relativePath)
            next()
        }
    }
    fun actCreate(name: String) {
        currentPhoto?.let { p ->
            vm.createAlbumAndMove(p.id, name)
            next()
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        SwipeHint(dragY = animatedDrag)

        // 图片层：左右翻页由 HorizontalPager 处理
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, animatedDrag.roundToInt()) }
                .alpha(1f - 0.35f * (abs(animatedDrag) / thresholdPx).coerceIn(0f, 1f)),
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                photos.getOrNull(page)?.let { PhotoPage(it) }
            }
        }

        // 竖向手势：上滑=删除，下滑=收藏
        val up: () -> Unit = { currentPhoto?.let { p -> vm.markDelete(p.id); next() } }
        val down: () -> Unit = { currentPhoto?.let { p -> vm.favorite(p.id); next() } }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(currentPhoto?.id, finished) {
                    if (finished) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { dragY = 0f },
                        onDragEnd = {
                            when {
                                dragY <= -thresholdPx -> up()
                                dragY >= thresholdPx -> down()
                            }
                            dragY = 0f
                        },
                        onDragCancel = { dragY = 0f },
                        onVerticalDrag = { _, amount -> dragY += amount },
                    )
                }
        )

        // 顶部栏
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExit) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
            Text(
                text = "${pagerState.currentPage + 1} / ${photos.size}",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.undoLast() }, enabled = pendingCount > 0) {
                Icon(Icons.Default.Undo, contentDescription = "撤销", tint = Color.White)
            }
            BadgedBox(badge = { if (pendingCount > 0) Badge { Text(pendingCount.toString()) } }) {
                Button(
                    onClick = {
                        vm.beginSync(context)?.let { step ->
                            syncStage = 1
                            syncLauncher.launch(IntentSenderRequest.Builder(step.sender).build())
                        }
                    },
                    enabled = pendingCount > 0 && !isSyncing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00695C),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (pendingCount > 0) "确认同步 ($pendingCount)" else "确认同步")
                }
            }
        }

        // 底部：信息 + 系统相册归类栏
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xCC000000))
                .navigationBarsPadding(),
        ) {
            currentPhoto?.let { PhotoInfo(it) }
            SystemAlbumDock(albums = systemAlbums, photo = currentPhoto, onPick = ::actMove, onCreateForCurrent = ::actCreate)
        }

        // 到末尾 / 空时：不遮挡顶部确认按钮，只给底部一行提示
        if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有可整理的照片，请先到系统相册拍一些", color = Color.White)
            }
        } else if (finished) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 0.dp)
                    .fillMaxWidth(),
                color = Color(0xAA111111),
            ) {
                Column(
                    Modifier
                        .padding(12.dp)
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("已到这一批末尾", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (pendingCount > 0)
                            "有 $pendingCount 处改动尚未同步，请点上方「确认同步」写入系统相册"
                        else "没有待同步改动，可返回首页或继续翻看",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoPage(photo: Photo) {
    val context = LocalContext.current
    val request = remember(photo.id, context) {
        ImageRequest.Builder(context).data(photo.uri).crossfade(true).build()
    }
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = request,
            contentDescription = photo.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SwipeHint(dragY: Float) {
    val goingUp = dragY < 0
    val p = (abs(dragY) / (SWIPE_THRESHOLD_DP.value * LocalDensity.current.density)).coerceIn(0f, 1f)
    if (p < 0.02f) return
    val tint = if (goingUp) Color(0xFFFF3B30) else Color(0xFFFFB300)
    Box(
        Modifier
            .fillMaxSize()
            .background(tint.copy(alpha = 0.25f + 0.35f * p)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (goingUp) Icons.Default.Delete else Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(72.dp),
            )
            Text(
                text = if (goingUp) "松开，标记删除" else "松开，加入收藏",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun PhotoInfo(photo: Photo) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            text = photo.displayName.orEmpty(),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(formatDate(photo.dateAdded), formatSize(photo.size), photo.relativePath)
                .joinToString(" · "),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SystemAlbumDock(
    albums: List<SystemAlbum>,
    photo: Photo?,
    onPick: (SystemAlbum) -> Unit,
    onCreateForCurrent: (String) -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = if (photo != null) "当前照片归到：点击系统相册卡片移动" else "没有可整理的照片",
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            albums.forEach { album ->
                SystemAlbumCard(name = album.name, count = album.photoCount) { onPick(album) }
            }
            Surface(
                modifier = Modifier
                    .size(width = 96.dp, height = 64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showCreate = true },
                color = Color(0xFF333333),
            ) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                    Text("新建", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    if (showCreate) {
        CreateAlbumDialog(
            onDismiss = { showCreate = false },
            onConfirm = { name -> onCreateForCurrent(name); showCreate = false },
        )
    }
}

@Composable
private fun SystemAlbumCard(name: String, count: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(width = 88.dp, height = 64.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = Color(0xFF444444),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(name, color = Color.White, style = MaterialTheme.typography.labelMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("$count 张", color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CreateAlbumDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val submit = { if (text.isNotBlank()) onConfirm(text.trim()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建相册") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("相册名称") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
            )
        },
        confirmButton = { TextButton(onClick = { submit() }) { Text("创建") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun formatDate(seconds: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(seconds * 1000))

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "—"
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    else -> String.format(Locale.US, "%d KB", bytes / 1024)
}
