package com.zhaoban.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条「待同步」的系统相册动作。
 *
 * 用户在整理页的每一次手势（下滑=收藏 / 上滑=待删除 / 点卡片=移入某分类 / 新建=移入自定义目录）
 * 只落在这里，不直接改系统文件；等用户按「确认」后统一批量同步到 MediaStore。
 *
 * 用 [targetRelativePath] 表达目标目录（如 `Pictures/旅行`、`DCIM/Camera`）；
 * 收藏用特殊值 [FAVORITE_FLAG] 占位（对应 MediaStore 的 IS_FAVORITE），
 * 删除用特殊值 [DELETE_FLAG] 占位（对应 createTrashRequest）。
 */
@Entity(tableName = "pending_action")
data class PendingActionEntity(
    @PrimaryKey val photoId: Long,
    val action: String,             // MOVE / FAVORITE / DELETE
    val targetRelativePath: String, // MOVE 时为目标目录；其余为占位
    val markedAt: Long,
) {
    companion object {
        const val ACTION_MOVE = "MOVE"
        const val ACTION_FAVORITE = "FAVORITE"
        const val ACTION_DELETE = "DELETE"

        const val FAVORITE_FLAG = "__favorite__"
        const val DELETE_FLAG = "__delete__"
    }
}

/**
 * 记录一张「本轮已做过决定」的照片（收藏/移动/删除/保持不变都算已看过）。
 * 用于整理队列去重与进程重启后续上进度：unhandled = 照片 - reviewed。
 */
@Entity(tableName = "reviewed")
data class ReviewedEntity(
    @PrimaryKey val photoId: Long,
    val reviewedAt: Long,
)
