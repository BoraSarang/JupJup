package com.borasarang.planjupjup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.borasarang.planjupjup.data.db.entity.NotificationLog
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationLogDao {
    @Insert
    suspend fun insert(log: NotificationLog): Long

    @Query("SELECT * FROM notification_logs WHERE id = :id")
    suspend fun getById(id: Long): NotificationLog?

    @Query("SELECT * FROM notification_logs WHERE (:type IS NULL OR type = :type) AND (:isRead IS NULL OR isRead = :isRead) ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaged(type: String?, isRead: Boolean?, limit: Int, offset: Int): List<NotificationLog>

    @Query("SELECT COUNT(*) FROM notification_logs WHERE (:type IS NULL OR type = :type) AND (:isRead IS NULL OR isRead = :isRead)")
    suspend fun count(type: String?, isRead: Boolean?): Int

    @Query("SELECT COUNT(*) FROM notification_logs WHERE isRead = 0")
    suspend fun countUnread(): Int

    @Query("UPDATE notification_logs SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long): Int

    @Query("UPDATE notification_logs SET isRead = 1")
    suspend fun markAllAsRead(): Int

    @Query("DELETE FROM notification_logs WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM notification_logs WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM notification_logs")
    suspend fun deleteAll(): Int
}