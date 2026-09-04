package com.zhaoban.app.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 照片访问权限状态。
 *
 * 权限状态**不做持久化**：用户随时可在系统设置里改，所以每次判定都实时查询
 * （见开发手册 §1.3）。
 */
data class MediaAccess(
    /** 全量访问：能看到所有照片 */
    val full: Boolean,
    /** 部分访问：Android 14+ 用户只勾选了部分照片 */
    val partial: Boolean,
) {
    val hasAny: Boolean get() = full || partial
}

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

fun evaluateMediaAccess(context: Context): MediaAccess {
    // Android 13 起 READ_EXTERNAL_STORAGE 不再授予媒体访问权，必须按类型判定
    val full = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        granted(context, Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val partial = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

    return MediaAccess(full = full, partial = partial)
}

/** 按系统版本分流申请，一次性请求全部，避免多次系统弹窗。 */
fun requiredImagePermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else ->
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}
