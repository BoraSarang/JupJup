package com.borasarang.communityjupjup.util

/** 커뮤니티 뉴스 크롤러 전역 상수 */
object Constants {
    const val DEFAULT_PORT = 3040
    const val DEFAULT_RETENTION_DAYS = 3
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

    /** 본문 저장 금지 — 요약 500자만 (법적 정책, V2 §10) */
    const val MAX_SUMMARY_LEN = 500

    /** TTL: 핫딜 3일·중고 7일·그 외 retentionDays (V2 §5 용량 정책) */
    const val RETENTION_HOTDEAL_DAYS = 3
    const val RETENTION_USED_DAYS = 7

    const val CRAWL_REQUEST_DELAY_MS = 1000L
    const val CRAWL_TIMEOUT_SEC = 30L
    // 실측: 클리앙이 (Linux; Android)/Mozilla 포함 UA에 302 봇월 → 짧은 식별 UA 사용 (200 확인)
    const val USER_AGENT = "CommunityJupJup/1.0 (contact leeborasarang@gmail.com)"

    const val NOTIFICATION_ID_SERVER = 4001
    const val NOTIFICATION_ID_CRAWL_BASE = 4100
    const val CHANNEL_ID_SERVER = "jupjup_community_server"

    // MVP 소스 ID (InitialDataSeeder와 일치)
    const val SOURCE_CLIEN_PARK = "clien_park"
    const val SOURCE_CLIEN_JIRUM = "clien_jirum"
    const val SOURCE_FM_POTEN = "fmkorea_poten"
    const val SOURCE_FM_HUMOR = "fmkorea_humor"
    const val SOURCE_RULI_BEST = "ruliweb_best"
    const val SOURCE_PPOMPPU = "ppomppu_ppomppu"
    const val SOURCE_DC_BEST = "dc_dcbest"
    const val SOURCE_BOBA_BEST = "boba_best"
    const val SOURCE_THEQOO_HOT = "theqoo_hot"
    const val SOURCE_OHU_BEST = "ohu_best"

    // 소스 타입 (selector_config 기반 범용 보드 크롤러)
    const val TYPE_BOARD = "BOARD"

    // 수집 상태
    const val STATUS_NEVER_RUN = "NEVER_RUN"
    const val STATUS_SUCCESS = "SUCCESS"
    const val STATUS_FAILED = "FAILED"
    const val STATUS_RUNNING = "RUNNING"
    const val STATUS_DISABLED = "DISABLED"
}
