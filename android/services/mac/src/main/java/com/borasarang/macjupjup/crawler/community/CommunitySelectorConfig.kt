package com.borasarang.macjupjup.crawler.community

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 커뮤니티 보드 목록 파싱용 CSS 셀렉터 (community 서비스 SelectorConfig 이식) */
data class CommunitySelectorConfig(
    val listRow: String,
    val title: String,
    val author: String = "",
    val time: String = "",
    val comments: String = "",
    val views: String = "",
    val snippet: String = "",
    val excludeRow: String = "",
    val detailContent: String = "",
    val thumbnail: String = "",
    /** 카테고리 main: apple / mac / ai */
    val main: String = "",
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun defaults() = CommunitySelectorConfig(
            listRow = "tr, li.post, div.list_item",
            title = "a",
            author = ".nickname, .writer, .author",
            time = "time, .list_time, .time",
        )

        fun parse(raw: String?): CommunitySelectorConfig {
            if (raw.isNullOrBlank()) return defaults()
            return try {
                val o = json.parseToJsonElement(raw).jsonObject
                fun s(key: String) = o[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: ""
                val d = defaults()
                CommunitySelectorConfig(
                    listRow = s("listRow").ifBlank { d.listRow },
                    title = s("title").ifBlank { d.title },
                    author = s("author").ifBlank { d.author },
                    time = s("time").ifBlank { d.time },
                    comments = s("comments"),
                    views = s("views"),
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
