package com.borasarang.macjupjup.util

/** 앱 전역 상수 */
object Constants {
    const val DEFAULT_PORT = 3010
    const val DEFAULT_RETENTION_DAYS = 30
    const val DEFAULT_AUTO_START = true
    const val DEFAULT_WATCHDOG_INTERVAL_SEC = 60

    const val MIN_PORT = 1024
    const val MAX_PORT = 65535
    const val MIN_WATCHDOG_SEC = 15
    const val MAX_WATCHDOG_SEC = 3600
    const val MIN_RETENTION_DAYS = 1
    const val MAX_RETENTION_DAYS = 365

    const val API_MAX_PAGE_SIZE = 100
    const val API_DEFAULT_PAGE_SIZE = 50

    /** 본문 절단 정책: 요약 500자 / 전문 2000자 (크롤러 공통) */
    const val MAX_SUMMARY_LEN = 500
    const val MAX_BODY_LEN = 2000

    const val CRAWL_REQUEST_DELAY_MS = 1000L
    // 느린 서버 1건이 워커를 최대 ~60s 점유하던 것을 단축 (수집 예의 delay 1s는 유지)
    const val CRAWL_TIMEOUT_SEC = 20L
    const val USER_AGENT = "MacJupJup/0.1 (Linux; Android) Mac-App-Trend-Portal; contact leeborasarang@gmail.com"

    const val NOTIFICATION_ID_SERVER = 1001
    const val NOTIFICATION_ID_CRAWL_BASE = 1100
    const val CHANNEL_ID_SERVER = "jupjup_mac_server"

    // 소스 ID (InitialDataSeeder와 일치, PH·HN·MMB 제거됨)
    const val SOURCE_GITHUB_SEARCH = "github_search"
    const val SOURCE_GITHUB_RELEASES = "github_releases"
    const val SOURCE_CHART_RSS = "chart_rss"
    const val SOURCE_ITUNES_LOOKUP = "itunes_lookup"
    const val SOURCE_NAME_MATCH = "name_match"
    const val SOURCE_MAS_DISCOVERY = "mas_discovery"

    // 소스 타입
    const val TYPE_GITHUB_SEARCH = "GITHUB_SEARCH"
    const val TYPE_GITHUB_RELEASES = "GITHUB_RELEASES"
    const val TYPE_CHART_RSS = "CHART_RSS"
    const val TYPE_ITUNES_LOOKUP = "ITUNES_LOOKUP"
    const val TYPE_NAME_MATCH = "NAME_MATCH"
    const val TYPE_MAS_DISCOVERY = "MAS_DISCOVERY"

    // 수집 상태
    const val STATUS_NEVER_RUN = "NEVER_RUN"
    const val STATUS_SUCCESS = "SUCCESS"
    const val STATUS_FAILED = "FAILED"
    const val STATUS_RUNNING = "RUNNING"
    const val STATUS_DISABLED = "DISABLED"

    // 라이선스
    const val LICENSE_OSS = "OSS"
    const val LICENSE_FREE = "FREE"
    const val LICENSE_PAID = "PAID"

    const val PLATFORM_MACOS = "macOS"
    const val STORE_COUNTRY = "us"
}
