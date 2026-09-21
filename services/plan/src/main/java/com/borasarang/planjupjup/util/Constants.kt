package com.borasarang.planjupjup.util

/** 앱 전역 상수 */
object Constants {
    const val DEFAULT_PORT = 3020
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
    const val PORTAL_PRELOAD_SIZE = 500

    const val CRAWL_REQUEST_DELAY_MS = 1000L
    // 느린 서버 1건이 워커를 최대 ~60s 점유하던 것을 단축 (수집 예의 delay 1s는 유지)
    const val CRAWL_TIMEOUT_SEC = 20L
    const val USER_AGENT = "PlanJupJup/1.0 (Linux; Android) MVNO-Plan-Portal; contact leeborasarang@gmail.com"

    const val NOTIFICATION_ID_SERVER = 2001
    const val NOTIFICATION_ID_CRAWL_BASE = 2100
    const val CHANNEL_ID_SERVER = "jupjup_plan_server"

    // 소스 ID (InitialDataSeeder와 일치)
    const val SOURCE_MVNOHUB = "mvnohub"
    const val SOURCE_MOYO = "moyo"
    const val SOURCE_KTMMOBILE = "ktmmobile"
    const val SOURCE_LIIVM = "liivm"
    const val SOURCE_BRAND_LIST = "brand_list"

    // 소스 타입
    const val TYPE_COMPARE_SITE = "COMPARE_SITE"
    const val TYPE_CARRIER_SITE = "CARRIER_SITE"
    const val TYPE_BRAND_LIST = "BRAND_LIST"

    // 수집 상태
    const val STATUS_NEVER_RUN = "NEVER_RUN"
    const val STATUS_SUCCESS = "SUCCESS"
    const val STATUS_FAILED = "FAILED"
    const val STATUS_RUNNING = "RUNNING"
    const val STATUS_DISABLED = "DISABLED"

    // 큐레이션 태그
    const val TAG_VALUE_YOUTH = "가성비청년"
    const val TAG_HEAVY = "해비유저"
    const val TAG_SENIOR = "효도폰"
    const val TAG_VIDEO = "영상시청"
    const val TAG_NEW = "신규출시"
}
