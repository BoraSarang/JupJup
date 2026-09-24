package com.borasarang.planjupjup.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 알뜰폰 통신사 브랜드 (brand.do 크롤링 결과). 향후 공식 크롤러 점진 추가용 */
@Entity(
    tableName = "carrier_brands",
    indices = [Index("networkType"), Index("isActive")],
)
data class CarrierBrand(
    @PrimaryKey val id: String,
    val name: String,
    /** "SKT" / "KT" / "LGU+" */
    val networkType: String,
    val logoUrl: String?,
    val homepageUrl: String?,
    val planListUrl: String?,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val collectedAt: Long,
)
