package com.borasarang.communityjupjup.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.borasarang.common.prefs.SettingsStores
import com.borasarang.communityjupjup.data.repository.SettingsData
import com.borasarang.communityjupjup.util.Constants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** R2: SettingsStores 팩토리로 일원화 */
private val Context.settingsStore: DataStore<Preferences>
    get() = SettingsStores.get(this, "community_settings")

/** DataStore 설정 저장소. 포트·보관기간·자동시작·Watchdog 주기·알림 토글 */
class PreferencesManager(private val context: Context) {

    private object Keys {
        val PORT = intPreferencesKey("server_port")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val WATCHDOG_SEC = intPreferencesKey("watchdog_interval_sec")
        val NOTIF_CRAWL_COMPLETE = booleanPreferencesKey("notif_crawl_complete")
        val NOTIF_NEW_POST = booleanPreferencesKey("notif_new_post")
        val NOTIF_FAILURE = booleanPreferencesKey("notif_failure")
        val CRAWL_ENABLED = booleanPreferencesKey("crawl_enabled")
        val SEED_STATUS = stringPreferencesKey("seed_status")
        val SEED_STARTED_AT = longPreferencesKey("seed_started_at")
        val WORK_V4_DONE = booleanPreferencesKey("work_v4_done")
        val ADMIN_TOKEN = stringPreferencesKey("admin_token")
        val ADMIN_ID = stringPreferencesKey("admin_id")
        val ADMIN_PW = stringPreferencesKey("admin_pw")
    }

    suspend fun getSettings(): SettingsData {
        return context.settingsStore.data.map { prefs ->
            SettingsData(
                port = prefs[Keys.PORT] ?: Constants.DEFAULT_PORT,
                retentionDays = prefs[Keys.RETENTION_DAYS] ?: Constants.DEFAULT_RETENTION_DAYS,
                autoStart = prefs[Keys.AUTO_START] ?: Constants.DEFAULT_AUTO_START,
                watchdogIntervalSec = (prefs[Keys.WATCHDOG_SEC] ?: Constants.DEFAULT_WATCHDOG_INTERVAL_SEC)
                    .coerceIn(Constants.MIN_WATCHDOG_SEC, Constants.MAX_WATCHDOG_SEC),
                notifCrawlComplete = prefs[Keys.NOTIF_CRAWL_COMPLETE] ?: true,
                notifNewPost = prefs[Keys.NOTIF_NEW_POST] ?: true,
                notifFailure = prefs[Keys.NOTIF_FAILURE] ?: true,
                crawlEnabled = prefs[Keys.CRAWL_ENABLED] ?: true,
            )
        }.first()
    }

    suspend fun saveSettings(settings: SettingsData) {
        context.settingsStore.edit { prefs ->
            prefs[Keys.PORT] = settings.port.coerceIn(Constants.MIN_PORT, Constants.MAX_PORT)
            prefs[Keys.RETENTION_DAYS] = settings.retentionDays
                .coerceIn(Constants.MIN_RETENTION_DAYS, Constants.MAX_RETENTION_DAYS)
            prefs[Keys.AUTO_START] = settings.autoStart
            prefs[Keys.WATCHDOG_SEC] = settings.watchdogIntervalSec
                .coerceIn(Constants.MIN_WATCHDOG_SEC, Constants.MAX_WATCHDOG_SEC)
            prefs[Keys.NOTIF_CRAWL_COMPLETE] = settings.notifCrawlComplete
            prefs[Keys.NOTIF_NEW_POST] = settings.notifNewPost
            prefs[Keys.NOTIF_FAILURE] = settings.notifFailure
        }
    }

    /** 수집 일시정지 플래그 단독 저장 (설정 화면 전체 저장과 독립) */
    suspend fun setCrawlEnabled(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[Keys.CRAWL_ENABLED] = enabled
        }
    }


    /** 통합 관리자 ID/PW 조회 (R48, 4서비스 동일값 등록) */
    suspend fun getAdminCredential(): Pair<String, String> {
        return context.settingsStore.data.map { prefs ->
            (prefs[Keys.ADMIN_ID] ?: "") to (prefs[Keys.ADMIN_PW] ?: "")
        }.first()
    }

    /** 통합 관리자 ID/PW 저장 (R48, 빈 값이면 삭제) */
    suspend fun setAdminCredential(id: String, pw: String) {
        context.settingsStore.edit { prefs ->
            val i = id.trim()
            val p = pw.trim()
            if (i.isEmpty()) prefs.remove(Keys.ADMIN_ID) else prefs[Keys.ADMIN_ID] = i
            if (p.isEmpty()) prefs.remove(Keys.ADMIN_PW) else prefs[Keys.ADMIN_PW] = p
        }
    }

    /** 관리웹 토큰 — 최초 1회 발급·영속 (R44). 값은 루프백 페어링 외 노출 금지 */
    suspend fun getAdminToken(): String {
        val existing = context.settingsStore.data.map { it[Keys.ADMIN_TOKEN] }.first()
        if (!existing.isNullOrBlank()) return existing
        val fresh = java.util.UUID.randomUUID().toString().replace("-", "")
        context.settingsStore.edit { it[Keys.ADMIN_TOKEN] = fresh }
        return fresh
    }

    /** 수동 시드 상태 조회 (HttpServerService 호환 유지) */
    suspend fun getSeedState(): Pair<String, Long> {
        return context.settingsStore.data.map { prefs ->
            (prefs[Keys.SEED_STATUS] ?: "idle") to (prefs[Keys.SEED_STARTED_AT] ?: 0L)
        }.first()
    }

    /** 수동 시드 상태 저장 */
    suspend fun saveSeedState(status: String, startedAt: Long) {
        context.settingsStore.edit { prefs ->
            prefs[Keys.SEED_STATUS] = status
            prefs[Keys.SEED_STARTED_AT] = startedAt
        }
    }

    /** 보드 단위 스케줄 1회 마이그레이션 완료 여부 (R27, 구 소스 작업명 잔재 정리용) */
    suspend fun isWorkV4Done(): Boolean {
        return context.settingsStore.data.map { prefs ->
            prefs[Keys.WORK_V4_DONE] ?: false
        }.first()
    }

    suspend fun setWorkV4Done() {
        context.settingsStore.edit { prefs ->
            prefs[Keys.WORK_V4_DONE] = true
        }
    }

    companion object {
        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
