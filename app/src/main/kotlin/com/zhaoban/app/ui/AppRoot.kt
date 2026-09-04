package com.zhaoban.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhaoban.app.media.evaluateMediaAccess
import com.zhaoban.app.media.requiredImagePermissions
import com.zhaoban.app.ui.screens.HomeScreen
import com.zhaoban.app.ui.screens.ReviewScreen
import com.zhaoban.app.vm.MainViewModel

private sealed interface Screen {
    data object Home : Screen
    data class Review(val unhandledOnly: Boolean, val startIndex: Int) : Screen
}

@Composable
fun AppRoot(vm: MainViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var permTick by remember { mutableIntStateOf(0) }
    val access = remember(permTick, context) { evaluateMediaAccess(context) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permTick++ }

    var autoRequested by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoRequested) {
            autoRequested = true
            if (!access.hasAny) permLauncher.launch(requiredImagePermissions())
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permTick++
                if (evaluateMediaAccess(context).hasAny) vm.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(access.hasAny) { if (access.hasAny) vm.refresh() }
    LaunchedEffect(access.partial) {
        if (access.partial) snackbarHostState.showSnackbar("当前是部分访问，只显示你选择的照片")
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbarHostState.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        if (access.hasAny) {
            AppNav(vm)
        } else {
            PermissionScreen(
                onRequest = { permLauncher.launch(requiredImagePermissions()) },
                onOpenSettings = { openAppSettings(context) },
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }
}

@Composable
private fun AppNav(vm: MainViewModel) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    val photos by vm.photos.collectAsStateWithLifecycle()
    val unhandled by vm.unhandled.collectAsStateWithLifecycle()

    fun openReview(unhandledOnly: Boolean, startIndex: Int) {
        stack.add(Screen.Review(unhandledOnly, startIndex))
    }

    when (val screen = stack.last()) {
        Screen.Home -> HomeScreen(
            vm = vm,
            onOpenReview = { unhandledOnly, startIndex -> openReview(unhandledOnly, startIndex) },
        )
        is Screen.Review -> {
            val list = if (screen.unhandledOnly) unhandled else photos
            ReviewScreen(
                vm = vm,
                photos = list,
                startIndex = screen.startIndex,
                onExit = { stack.removeAt(stack.lastIndex) },
                onFinished = {},
            )
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("需要读取照片的权限", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "照办只在本地整理你的照片，不会上传任何内容。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRequest) { Text("授权读取照片") }
            TextButton(onClick = onOpenSettings) { Text("去系统设置") }
        }
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
