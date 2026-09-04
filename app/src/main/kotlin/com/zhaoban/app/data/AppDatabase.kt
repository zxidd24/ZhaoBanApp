package com.zhaoban.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ZhaoBanDao {

    // region 待同步动作
    @Query("SELECT * FROM pending_action ORDER BY markedAt ASC")
    fun observePendingActions(): Flow<List<PendingActionEntity>>

    @Query("SELECT * FROM pending_action")
    suspend fun getPendingActions(): List<PendingActionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingAction(action: PendingActionEntity)

    @Query("DELETE FROM pending_action WHERE photoId = :photoId")
    suspend fun removePendingAction(photoId: Long)

    @Query("DELETE FROM pending_action WHERE action = :action")
    suspend fun removePendingByAction(action: String)

    @Query("DELETE FROM pending_action")
    suspend fun clearPendingActions()

    @Query("SELECT COUNT(*) FROM pending_action")
    fun observePendingCount(): Flow<Int>
    // endregion

    // region 已查看
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReviewed(entity: ReviewedEntity)

    @Query("SELECT * FROM reviewed ORDER BY reviewedAt DESC LIMIT 500")
    fun observeReviewed(): Flow<List<ReviewedEntity>>

    @Query("DELETE FROM reviewed WHERE photoId = :photoId")
    suspend fun removeReviewed(photoId: Long)

    @Query("DELETE FROM reviewed")
    suspend fun clearReviewed()
    // endregion
}

@Database(
    entities = [PendingActionEntity::class, ReviewedEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dao(): ZhaoBanDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "zhaoban.db",
                )
                    // v1 内建 albums / photo_album / pending_delete 已废弃，直接重建更稳妥
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
