package com.borasarang.planjupjup.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.borasarang.planjupjup.data.repository.SettingsData
import com.borasarang.planjupjup.util.Constants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")

/** DataStore 설정 저장소. 포트·보관기간·자동시작·Watchdog 주기 */
class PreferencesManager(private val context: Context) {

    private object Keys {
        val PORT = intPreferencesKey("server_port")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val WATCHDOG_SEC = intPreferencesKey("watchdog_interval_sec")
        val NOTIF_CRAWL_COMPLETE = booleanPreferencesKey("notif_crawl_complete")
        val NOTIF_NEW_PLAN = booleanPreferencesKey("notif_new_plan")
        val NOTIF_FAILURE = booleanPreferencesKey("notif_failure")
    }

    suspend fun getSettings(): SettingsData {
        return context.settingsStore.data.map { prefs ->
            SettingsData(
                port = prefs[Keys.PORT] ?: Constants.DEFAULT_PORT,
                retentionDays = prefs[Keys.RETENTION_DAYS] ?: Constants.DEFAULT_RETENTION_DAYS,
                autoStart = prefs[Keys.AUTO_START] ?: Constants.DEFAULT_AUTO_START,
                watchdogIntervalSec = prefs[Keys.WATCHDOG_SEC] ?: Constants.DEFAULT_WATCHDOG_INTERVAL_SEC,
                notifCrawlComplete = prefs[Keys.NOTIF_CRAWL_COMPLETE] ?: true,
                notifNewPlan = prefs[Keys.NOTIF_NEW_PLAN] ?: true,
                notifFailure = prefs[Keys.NOTIF_FAILURE] ?: true,
            )
        }.first()
    }

    suspend fun saveSettings(settings: SettingsData) {
        context.settingsStore.edit { prefs ->
            prefs[Keys.PORT] = settings.port.coerceIn(Constants.MIN_PORT, Constants.MAX_PORT)
            prefs[Keys.RETENTION_DAYS] = settings.retentionDays
            prefs[Keys.AUTO_START] = settings.autoStart
            prefs[Keys.WATCHDOG_SEC] = settings.watchdogIntervalSec
                .coerceIn(Constants.MIN_WATCHDOG_SEC, Constants.MAX_WATCHDOG_SEC)
            prefs[Keys.NOTIF_CRAWL_COMPLETE] = settings.notifCrawlComplete
            prefs[Keys.NOTIF_NEW_PLAN] = settings.notifNewPlan
            prefs[Keys.NOTIF_FAILURE] = settings.notifFailure
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
