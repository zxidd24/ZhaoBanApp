package com.zhaoban.app.media

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore

/**
 * 把 MediaStore 的写 / 删 / 收藏 PendingIntent 批量构造出来。
 * Android 11（API 30）起改其他应用创建的照片没有静默权限，必须走用户确认的系统弹窗（开发手册 §1.4 / §5）。
 */
object MediaStoreActions {

    /** API 30+ 才支持 createWriteRequest / createTrashRequest / createFavoriteRequest。 */
    val supportsSystemOps: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    private fun urisOf(collection: android.net.Uri, photoIds: List<Long>): List<android.net.Uri> =
        photoIds.map { ContentUris.withAppendedId(collection, it) }

    /** 修改 RELATIVE_PATH 的授权弹窗（多个可一次）。 */
    fun buildWriteRequest(context: Context, photoIds: List<Long>): IntentSender? {
        if (!supportsSystemOps || photoIds.isEmpty()) return null
        val uris = urisOf(PhotoLoader.collectionUri(), photoIds)
        return try {
            MediaStore.createWriteRequest(context.contentResolver, uris).intentSender
        } catch (e: Exception) {
            null
        }
    }

    /** 加入系统「最近删除」（trashed=true）的一次性授权弹窗。 */
    fun buildTrashRequest(context: Context, photoIds: List<Long>): IntentSender? {
        if (!supportsSystemOps || photoIds.isEmpty()) return null
        val uris = urisOf(PhotoLoader.collectionUri(), photoIds)
        return try {
            MediaStore.createTrashRequest(context.contentResolver, uris, true).intentSender
        } catch (e: Exception) {
            null
        }
    }

    /** 加入系统「收藏」的一次性授权弹窗。 */
    fun buildFavoriteRequest(context: Context, photoIds: List<Long>, favorite: Boolean): IntentSender? {
        if (!supportsSystemOps || photoIds.isEmpty()) return null
        val uris = urisOf(PhotoLoader.collectionUri(), photoIds)
        return try {
            MediaStore.createFavoriteRequest(context.contentResolver, uris, favorite).intentSender
        } catch (e: Exception) {
            null
        }
    }
}
