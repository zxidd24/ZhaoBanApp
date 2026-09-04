package com.zhaoban.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private const val SEED = 0xFF00695C

private val LightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(SEED),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = androidx.compose.ui.graphics.Color(0xFF4F5B62),
    error = androidx.compose.ui.graphics.Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF4DB6AC),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF00352F),
    secondary = androidx.compose.ui.graphics.Color(0xFFB0BEC5),
    error = androidx.compose.ui.graphics.Color(0xFFF2B8B5),
)

@Composable
fun ZhaoBanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
