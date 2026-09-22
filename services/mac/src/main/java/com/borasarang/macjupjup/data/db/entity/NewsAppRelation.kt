package com.borasarang.macjupjup.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 뉴스-앱 연동 (R32 PLAN_v17).
 * `앱·업데이트` 뉴스 제목에 앱 이름이 있으면 관련 앱 카드 노출용.
 */
@Entity(
    tableName = "news_app_relation",
    primaryKeys = ["newsId", "appId"],
    indices = [Index("appId")],
)
data class NewsAppRelation(
    val newsId: String,
    val appId: String,
)
