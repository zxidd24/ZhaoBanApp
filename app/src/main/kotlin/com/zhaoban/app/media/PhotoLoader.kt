package com.zhaoban.app.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.zhaoban.app.data.Photo
import com.zhaoban.app.data.SystemAlbum

/**
 * 从 MediaStore 读取照片列表。
 *
 * - 必须在 IO 线程调用；
 * - 每次 onResume 重新查询（部分访问集合会变，见开发手册 §1.3）；
 * - 支持 JPEG / PNG / WEBP，HEIC / HEIF 在 Android 9（API 28）以上可被系统解码，
 *   故也加入白名单以覆盖 iPhone 同步的照片；其余格式（动图、视频、Raw 等）保持过滤。
 */
object PhotoLoader {

    const val MAX_PHOTOS = 1000

    private val ALWAYS_SUPPORTED_MIME = listOf("image/jpeg", "image/png", "image/webp")

    /** Android 9 起系统自带 HEIF 解码器；低版本跳过避免花屏。 */
    private val HEIF_MIME = listOf("image/heif", "image/heic")

    private fun supportedMime(): Array<String> {
        val list = ArrayList(ALWAYS_SUPPORTED_MIME)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) list.addAll(HEIF_MIME)
        return list.toTypedArray()
    }

    /** 图片可存放的顶层根目录；只扫这些，避免把下载/系统内杂项算成相册。 */
    private val SCAN_ROOTS = arrayOf("DCIM/", "Pictures/")

    /**
     * 扫描系统已有相册文件夹（按 RELATIVE_PATH 聚合）。
     *
     * 仅在 Android 10（API 29）+ 可用（该列 API 29 才引入）。API 26-28 无 RELATIVE_PATH，
     * 直接返回空（MVP 目标机以 29+ 为主，见开发手册 minSdk 覆盖取舍）。
     * 必须在 IO 线程调用。
     */
    fun loadAlbums(context: Context): List<SystemAlbum> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val resolver = context.contentResolver
        val collection = collectionUri()
        val mime = supportedMime()

        val projection = arrayOf(
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media._ID,
        )

        val mimeSel = mime.joinToString(",") { "'$it'" }
        // 只取顶层 DCIM 与 Pictures 目录下的照片，且必须位于某子目录或根
        val selection = "${MediaStore.Images.Media.MIME_TYPE} IN ($mimeSel) AND " +
            "(${MediaStore.Images.Media.RELATIVE_PATH} IS NOT NULL)"
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val byPath = LinkedHashMap<String, MutableList<Long>>()
        try {
            resolver.query(collection, projection, selection, null, sortOrder)
                ?.use { c ->
                    val pathCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    while (c.moveToNext()) {
                        val rp = c.getString(pathCol) ?: continue
                        if (!SCAN_ROOTS.any { rp.startsWith(it) }) continue
                        // 归一化去掉末尾斜杠
                        val key = rp.trimEnd('/')
                        byPath.getOrPut(key) { ArrayList() }.add(c.getLong(idCol))
                    }
                }
        } catch (e: Exception) {
            return emptyList()
        }
        return byPath.map { (rp, ids) ->
            SystemAlbum(
                id = ids.first(),
                name = rp.substringAfterLast('/').ifBlank { rp },
                relativePath = rp,
                photoCount = ids.size,
                coverPhotoId = ids.first(), // DATE_ADDED DESC 已排序，首张为最新
            )
        }.sortedBy { it.relativePath }
    }

    fun collectionUri(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    fun load(context: Context, limit: Int = MAX_PHOTOS): List<Photo> {
        val resolver = context.contentResolver
        val collection = collectionUri()
        val mime = supportedMime()

        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.SIZE)
            add(MediaStore.Images.Media.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                add(MediaStore.Images.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        val placeholders = (0 until mime.size).joinToString(",") { "?" }
        val selection = "${MediaStore.Images.Media.MIME_TYPE} IN ($placeholders)"
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val result = ArrayList<Photo>(limit)
        try {
            resolver.query(collection, projection, selection, mime, sortOrder)
                ?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                    val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    val pathCol = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)

                    while (cursor.moveToNext() && result.size < limit) {
                        val id = cursor.getLong(idCol)
                        result.add(
                            Photo(
                                id = id,
                                uri = ContentUris.withAppendedId(collection, id),
                                displayName = cursor.getString(nameCol),
                                dateAdded = cursor.getLong(dateCol),
                                bucketName = bucketCol.takeIf { it >= 0 }?.let { cursor.getString(it) },
                                relativePath = pathCol.takeIf { it >= 0 }?.let { cursor.getString(it) },
                                size = cursor.getLong(sizeCol),
                                mimeType = cursor.getString(mimeCol),
                            )
                        )
                    }
                }
        } catch (e: SecurityException) {
            return result
        } catch (e: Exception) {
            return result
        }
        return result
    }
}