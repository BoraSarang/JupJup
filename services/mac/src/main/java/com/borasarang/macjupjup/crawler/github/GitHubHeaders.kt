package com.borasarang.macjupjup.crawler.github

/** GitHub API 공통 헤더 (R1-2 통합) */
internal const val GITHUB_API_VERSION = "2022-11-28"
internal const val GITHUB_ACCEPT_JSON = "application/vnd.github+json"

internal fun githubHeaders(token: String): Map<String, String> = buildMap {
    put("Accept", GITHUB_ACCEPT_JSON)
    put("X-GitHub-Api-Version", GITHUB_API_VERSION)
    if (token.isNotBlank()) put("Authorization", "Bearer $token")
}
