package com.borasarang.planjupjup.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 알림 로그. 앱 푸시/포털 알림 센터용 */
@Entity(
    tableName = "notification_logs",
    indices = [
        Index("type"),
        Index("createdAt"),
        Index("isRead"),
    ]
)
data class NotificationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** CRAWL_COMPLETE / CRAWL_FAILED / NEW_PLANS_FOUND / NEW_PLAN_DETAIL / CRAWL_SUMMARY / CRAWL_FAILED_STREAK */
    val type: String,
    /** 요약 메시지 (푸시/앱에서 표시) */
    val summary: String,
    /** 상세 JSON (포털에서 파싱해서 표시) */
    val detailJson: String,
    val createdAt: Long,
    val isRead: Boolean = false,
) {
    /** 리스트용 간단 JSON */
    fun toJson(): String = Json.encodeToString(
        buildJsonObject {
            put("id", id)
            put("type", type)
            put("summary", summary)
            put("createdAt", createdAt)
            put("isRead", isRead)
        }
    )
}

/** 알림 타입 상수 */
object NotificationType {
    const val CRAWL_COMPLETE = "CRAWL_COMPLETE"
    const val CRAWL_FAILED = "CRAWL_FAILED"
    const val NEW_PLANS_FOUND = "NEW_PLANS_FOUND"
    const val NEW_PLAN_DETAIL = "NEW_PLAN_DETAIL"
    const val CRAWL_SUMMARY = "CRAWL_SUMMARY"
    const val CRAWL_FAILED_STREAK = "CRAWL_FAILED_STREAK"
}