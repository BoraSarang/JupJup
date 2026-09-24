package com.borasarang.macjupjup.crawler

import com.borasarang.macjupjup.data.db.entity.AppSourceMapping

/** 크롤러 간 매핑 생성 중복 제거 */
object AppSourceMappingHelper {
    fun mapping(appId: String, sourceName: String, sourceUrl: String?, now: Long) =
        AppSourceMapping(
            appId = appId,
            sourceName = sourceName,
            sourceUrl = sourceUrl,
            fetchedAt = now,
        )
}
