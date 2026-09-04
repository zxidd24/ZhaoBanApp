package com.zhaoban.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import com.zhaoban.app.ui.AppRoot
import com.zhaoban.app.ui.theme.ZhaoBanTheme
import com.zhaoban.app.vm.MainViewModel

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 16（target 36）强制边到边，需在 setContentView 前启用
        enableEdgeToEdge()
        setContent {
            ZhaoBanTheme {
                ZhaoBanApp(vm)
            }
        }
    }
}

@Composable
private fun ZhaoBanApp(vm: MainViewModel) {
    AppRoot(vm)
}
