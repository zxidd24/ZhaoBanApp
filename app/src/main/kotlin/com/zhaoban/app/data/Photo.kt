package com.zhaoban.app.data

import android.net.Uri

/**
 * 一张照片的元数据。
 *
 * [uri] 一律视为临时资源：用户随时可能撤销访问权，读取时必须 try-catch（见开发手册 §1.3）。
 */
data class Photo(
    val id: Long,
    val uri: Uri,
    val displayName: String?,
    val dateAdded: Long,
    val bucketName: String?,
    val relativePath: String?,
    val size: Long,
    val mimeType: String?,
)

/**
 * 一个「系统相册分类」，即 MediaStore 中一张真实存在的相册文件夹。
 * [relativePath] 形如 `DCIM/Camera` 或 `Pictures/Screenshots`，可直接用作移动目标。
 */
data class SystemAlbum(
    val id: Long,
    val name: String,          // 展示名（文件夹末段），如 Camera / Screenshots
    val relativePath: String,  // 如 DCIM/Camera
    val photoCount: Int,
    val coverPhotoId: Long?,   // 该分类内最近一张照片，用于封面
)
