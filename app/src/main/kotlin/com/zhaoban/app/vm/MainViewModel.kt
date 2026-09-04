package com.zhaoban.app.vm

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhaoban.app.data.AppDatabase
import com.zhaoban.app.data.PendingActionEntity
import com.zhaoban.app.data.Photo
import com.zhaoban.app.data.ReviewedEntity
import com.zhaoban.app.data.SystemAlbum
import com.zhaoban.app.media.MediaStoreActions
import com.zhaoban.app.media.PhotoLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 照办核心 VM。
 *
 * 模型：整理页每一下只往 Room [PendingActionEntity] 写一条「待同步动作」，不改系统文件；
 * 用户按「确认」时按动作类型分最多两步调系统授权批量落库：
 *   - 写步（移动 RELATIVE_PATH + 收藏 IS_FAVORITE）→ createWriteRequest
 *   - 删步（移入系统「最近删除」）→ createTrashRequest
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()
    private val dao = AppDatabase.get(app).dao()

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos.asStateFlow()

    private val _systemAlbums = MutableStateFlow<List<SystemAlbum>>(emptyList())
    val systemAlbums: StateFlow<List<SystemAlbum>> = _systemAlbums.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: MutableSharedFlow<String> get() = _messages

    val pendingActions: StateFlow<List<PendingActionEntity>> =
        dao.observePendingActions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingCount: StateFlow<Int> =
        dao.observePendingCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val reviewedIds: StateFlow<Set<Long>> =
        dao.observeReviewed()
            .map { rows -> rows.mapTo(HashSet()) { it.photoId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** 系统默认「收件箱」目录：位于这里的照片视为未整理。 */
    private val INBOX_FOLDERS = setOf("DCIM", "DCIM/Camera", "DCIM/Screenshots", "Pictures", "Pictures/Screenshots")

    private fun normalizePath(p: String?): String? = p?.trimEnd('/')

    /** 待整理队列：全部照片去掉本轮已决定过的，且只保留还在默认收件箱目录里的。 */
    val unhandled: StateFlow<List<Photo>> =
        combine(_photos, reviewedIds) { photos, reviewed ->
            photos.filter { photo ->
                photo.id !in reviewed && isUnhandledLocation(photo.relativePath)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 已被 Slidebox 等移进自定义文件夹的照片不算未整理。 */
    private fun isUnhandledLocation(relativePath: String?): Boolean {
        val rp = normalizePath(relativePath) ?: return true
        if (rp in INBOX_FOLDERS) return true
        // 位于自定义文件夹（如 Pictures/旅行）→ 已整理
        return false
    }

    init { refresh() }

    // region 照片 / 系统相册加载
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _photos.value = PhotoLoader.load(app)
            _systemAlbums.value = PhotoLoader.loadAlbums(app)
            _isLoading.value = false
        }
    }

    fun findPhoto(id: Long): Photo? = _photos.value.firstOrNull { it.id == id }
    // endregion

    // region 手势 → 待同步动作
    fun favorite(photoId: Long) =
        enqueue(photoId, PendingActionEntity.ACTION_FAVORITE, PendingActionEntity.FAVORITE_FLAG)
    fun markDelete(photoId: Long) =
        enqueue(photoId, PendingActionEntity.ACTION_DELETE, PendingActionEntity.DELETE_FLAG)
    fun moveTo(photoId: Long, albumRelativePath: String) =
        enqueue(photoId, PendingActionEntity.ACTION_MOVE, albumRelativePath)

    /** 新建相册并让当前照片待移入；返回新建相对路径供 UI 提示。 */
    fun createAlbumAndMove(photoId: Long, albumName: String): String {
        val relative = "Pictures/" + safeFolder(albumName)
        moveTo(photoId, relative)
        return relative
    }

    private fun enqueue(photoId: Long, action: String, target: String) {
        markReviewed(photoId)
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertPendingAction(PendingActionEntity(photoId, action, target, System.currentTimeMillis()))
        }
    }

    /** 只看不动（保持原样）：把照片标记为已看过，不进动作队列。 */
    fun keep(photoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertReviewed(ReviewedEntity(photoId, System.currentTimeMillis()))
        }
    }

    private fun markReviewed(photoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertReviewed(ReviewedEntity(photoId, System.currentTimeMillis()))
        }
    }

    /** 开启新一轮整理：清空已看过，让全部照片重新进入待整理队列。 */
    fun resetRound() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearReviewed()
            dao.clearPendingActions()
        }
    }

    fun removePending(photoId: Long) {
        viewModelScope.launch(Dispatchers.IO) { dao.removePendingAction(photoId) }
    }

    fun undoLast() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.getPendingActions().lastOrNull()?.let {
                dao.removePendingAction(it.photoId)
                // 同步清掉已看标记，让这张照片回到待整理队列
                dao.removeReviewed(it.photoId)
            }
        }
    }
    // endregion

    // region 确认 → 批量同步
    /** 同步步进：0=写步(move+favorite)，1=删步(delete)。 */
    data class SyncStep(val sender: IntentSender, val description: String)

    // begin 时冻结的快照，供后续 apply / 规划使用（避免与清库竞态）
    private var moveFavSnapshot: List<PendingActionEntity> = emptyList()
    private var deleteIdsSnapshot: List<Long> = emptyList()

    /** 用户点「确认」：冻结当前队列并返回首个系统授权步。 */
    fun beginSync(context: Context): SyncStep? {
        if (_isSyncing.value) return null
        val snapshot = pendingActions.value
        if (snapshot.isEmpty()) { toast("这一轮没有改动"); return null }

        moveFavSnapshot = snapshot.filter {
            it.action == PendingActionEntity.ACTION_MOVE ||
                it.action == PendingActionEntity.ACTION_FAVORITE
        }
        deleteIdsSnapshot = snapshot.filter { it.action == PendingActionEntity.ACTION_DELETE }
            .map { it.photoId }

        if (moveFavSnapshot.isEmpty() && deleteIdsSnapshot.isEmpty()) {
            _isSyncing.value = false
            return null
        }
        _isSyncing.value = true
        return if (moveFavSnapshot.isNotEmpty()) {
            MediaStoreActions.buildWriteRequest(context, moveFavSnapshot.map { it.photoId })?.let {
                SyncStep(it, "修改 ${moveFavSnapshot.size} 张照片（移动/收藏）")
            } ?: nextDeleteStepOrDone(context)
        } else {
            nextDeleteStepOrDone(context)
        }
    }

    /** 写步授权结果回调：授权后应用移动/收藏，然后进入删步。 */
    fun onWriteDialogResult(context: Context, ok: Boolean): SyncStep? {
        if (ok) {
            applyMovesAndFavorites()   // 快照驱动，不与清库竞态
        } else {
            toast("已取消同步（本轮改动保留，可再点确认重试）")
            _isSyncing.value = false
            return null
        }
        return nextDeleteStepOrDone(context)
    }

    /** 删步授权结果回调：无论是否授权，删除都结束并清空队列。 */
    fun onDeleteDialogResult(ok: Boolean): SyncStep? {
        if (ok) toast("已移入系统回收站")
        else toast("已取消删除，其余改动已应用")
        finishAndRefresh()
        return null
    }

    private fun nextDeleteStepOrDone(context: Context): SyncStep? {
        if (deleteIdsSnapshot.isNotEmpty()) {
            return MediaStoreActions.buildTrashRequest(context, deleteIdsSnapshot)?.let {
                SyncStep(it, "移入回收站 ${deleteIdsSnapshot.size} 张")
            } ?: finishAndRefresh().let { null }
        }
        finishAndRefresh()
        return null
    }

    private fun applyMovesAndFavorites() {
        viewModelScope.launch(Dispatchers.IO) {
            var okMoves = 0
            var okFav = 0
            for (a in moveFavSnapshot) {
                val uri = photoUri(a.photoId)
                val values = ContentValues().apply {
                    when (a.action) {
                        PendingActionEntity.ACTION_MOVE ->
                            put(MediaStore.Images.Media.RELATIVE_PATH, a.targetRelativePath)
                        PendingActionEntity.ACTION_FAVORITE ->
                            put(MediaStore.Images.Media.IS_FAVORITE, 1)
                    }
                    put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                }
                try {
                    if (app.contentResolver.update(uri, values, null, null) > 0) {
                        if (a.action == PendingActionEntity.ACTION_MOVE) okMoves++ else okFav++
                    }
                } catch (_: Exception) { }
            }
            _messages.tryEmit(if (okFav == 0) "已把 $okMoves 张移入相册" else "已移动 $okMoves 张、收藏 $okFav 张")
        }
    }

    private fun finishAndRefresh() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearPendingActions()
            refresh()
            _isSyncing.value = false
            moveFavSnapshot = emptyList()
            deleteIdsSnapshot = emptyList()
        }
    }

    private fun photoUri(photoId: Long): android.net.Uri =
        android.content.ContentUris.withAppendedId(PhotoLoader.collectionUri(), photoId)
    // endregion

    private fun safeFolder(name: String): String =
        name.trim().replace(Regex("[\\\\/:*?\"<>|]+"), "_").ifBlank { "未命名" }

    fun toast(message: String) { _messages.tryEmit(message) }
}
