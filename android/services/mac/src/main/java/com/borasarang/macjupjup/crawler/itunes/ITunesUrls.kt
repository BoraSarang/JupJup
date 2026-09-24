package com.borasarang.macjupjup.crawler.itunes

import java.net.URLEncoder

/** iTunes API URL 조립 단일 진실 (R1-12). country=US 고정(PLAN 확정) */
internal const val ITUNES_COUNTRY = "us"
internal const val ITUNES_ENTITY = "macSoftware"

internal fun itunesLookupUrl(ids: String): String =
    "https://itunes.apple.com/lookup?id=$ids&country=$ITUNES_COUNTRY&entity=$ITUNES_ENTITY"

internal fun itunesSearchUrl(term: String, limit: Int = 5): String =
    "https://itunes.apple.com/search" +
        "?term=${URLEncoder.encode(term, "UTF-8")}" +
        "&country=$ITUNES_COUNTRY&entity=$ITUNES_ENTITY&limit=$limit"
