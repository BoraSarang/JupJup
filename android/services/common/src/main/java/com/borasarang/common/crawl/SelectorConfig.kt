package com.borasarang.common.crawl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 보드 목록 파싱용 CSS 셀렉터 묶음 (community + mac `main` 병합) */
data class SelectorConfig(
    val listRow: String,
    val title: String,
    val author: String = "",
    val time: String = "",
    val likes: String = "",
    val views: String = "",
    val comments: String = "",
    val snippet: String = "",
    /** 이 셀렉터에 매칭되는 행은 제외 (예: 공지 `.notice`) */
    val excludeRow: String = "",
    /** 상세 페이지 본문 셀렉터 (콤마 폴백). 비면 범용 휴리스틱 */
    val detailContent: String = "",
    /** 썸네일 img 셀렉터 (비면 없음) */
    val thumbnail: String = "",
    /** 카테고리 main: apple / mac / ai (mac 전용, 비면 없음) */
    val main: String = "",
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** 코드 기본값: 범용 휴리스틱 (어드민 테스트 API로 조정 가능) */
        fun defaults() = SelectorConfig(
            listRow = "tr, li.post, div.list_item",
            title = "a",
            author = ".nickname, .writer, .author",
            time = "time, .list_time, .time",
        )

        fun parse(raw: String?): SelectorConfig {
            if (raw.isNullOrBlank()) return defaults()
            return try {
                val o = json.parseToJsonElement(raw).jsonObject
                fun s(key: String) = o[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: ""
                val d = defaults()
                SelectorConfig(
                    listRow = s("listRow").ifBlank { d.listRow },
                    title = s("title").ifBlank { d.title },
                    author = s("author").ifBlank { d.author },
                    time = s("time").ifBlank { d.time },
                    likes = s("likes"),
                    views = s("views"),
                    comments = s("comments"),
                    snippet = s("snippet"),
                    excludeRow = s("excludeRow"),
                    detailContent = s("detailContent"),
                    thumbnail = s("thumbnail"),
                    main = s("main"),
                )
            } catch (_: Exception) {
                defaults()
            }
        }
    }
}
