package com.borasarang.communityjupjup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.borasarang.communityjupjup.data.db.entity.NotificationLog

@Dao
interface NotificationLogDao {
    @Insert
    suspend fun insert(log: NotificationLog): Long

    @Query("SELECT * FROM notification_logs ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun list(limit: Int, offset: Int): List<NotificationLog>

    @Query(
        """SELECT * FROM notification_logs
        WHERE (:type IS NULL OR type = :type)
          AND (:isRead IS NULL OR isRead = :isRead)
        ORDER BY createdAt DESC LIMIT :limit OFFSET :offset"""
    )
    suspend fun listFiltered(type: String?, isRead: Boolean?, limit: Int, offset: Int): List<NotificationLog>

    @Query(
        """SELECT COUNT(*) FROM notification_logs
        WHERE (:type IS NULL OR type = :type)
          AND (:isRead IS NULL OR isRead = :isRead)"""
    )
    suspend fun countFiltered(type: String?, isRead: Boolean?): Int

    @Query("SELECT * FROM notification_logs WHERE id = :id")
    suspend fun getById(id: Long): NotificationLog?

    @Query("SELECT COUNT(*) FROM notification_logs WHERE isRead = 0")
    suspend fun unreadCount(): Int

    @Query("UPDATE notification_logs SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long): Int

    @Query("UPDATE notification_logs SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllRead(): Int

    @Query("DELETE FROM notification_logs WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM notification_logs WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long): Int
}
