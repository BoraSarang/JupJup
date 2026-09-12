package com.borasarang.planjupjup.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 정규화된 요금제. carrierName+planName 정규화 해시가 id (중복 병합 기준) */
@Entity(
    tableName = "plans",
    indices = [
        Index(value = ["carrierName", "planName"], unique = true),
        Index("mvnoNetwork"),
        Index("networkType"),
        Index("price"),
        Index("collectedAt"),
        Index("isNew"),
        Index("tags"),
    ],
)
data class Plan(
    @PrimaryKey val id: String,
    val carrierName: String,
    /** 원 통신망: SKT / KT / LGU+ / UNKNOWN */
    val mvnoNetwork: String,
    val planName: String,
    /** 월 요금 (원) */
    val price: Int,
    val priceAfterDiscount: Int?,
    val discountMonths: Int?,
    /** "15GB", "무제한+5Mbps" 등 원문 유지 */
    val dataAmount: String,
    val voice: String,
    val sms: String,
    /** "5G" / "LTE" */
    val networkType: String,
    val eventBadge: String?,
    val collectedAt: Long,
    /** 최초 수집 시각. 갱신 시 유지 (마이그레이션 백필 포함) */
    val firstCollectedAt: Long,
    val isNew: Boolean,
    /** 대표(최초 수집) 소스 ID */
    val sourceId: String?,
    /** 큐레이션 태그 CSV: "가성비청년,신규출시" */
    val tags: String?,
)
