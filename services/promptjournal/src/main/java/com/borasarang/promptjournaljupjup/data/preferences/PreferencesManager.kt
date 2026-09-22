package com.borasarang.promptjournaljupjup.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.borasarang.common.prefs.SettingsStores
import com.borasarang.promptjournaljupjup.Constants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences>
    get() = SettingsStores.get(this, "pj_settings")

data class PjSettings(
    val port: Int = Constants.DEFAULT_PORT,
    val autoStart: Boolean = false
)

class PreferencesManager(private val context: Context) {

    private object Keys {
        val PORT = intPreferencesKey("server_port")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val EXA_API_KEY = stringPreferencesKey("exa_api_key")
        val ADMIN_TOKEN = stringPreferencesKey("admin_token")
    }

    suspend fun getSettings(): PjSettings {
        return context.settingsStore.data.map { prefs ->
            PjSettings(
                port = prefs[Keys.PORT] ?: Constants.DEFAULT_PORT,
                autoStart = prefs[Keys.AUTO_START] ?: false
            )
        }.first()
    }

    suspend fun saveSettings(settings: PjSettings) {
        context.settingsStore.edit { prefs ->
            prefs[Keys.PORT] = settings.port.coerceIn(1024, 65535)
            prefs[Keys.AUTO_START] = settings.autoStart
        }
    }

    // R21: Exa 검색엔진 API 키 — 평문 DataStore, 로컬 서버로만 노출
    suspend fun getExaApiKey(): String {
        return context.settingsStore.data.map { prefs -> prefs[Keys.EXA_API_KEY] ?: "" }.first()
    }

    suspend fun saveExaApiKey(value: String) {
        val trimmed = value.trim()
        context.settingsStore.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(Keys.EXA_API_KEY)
            } else {
                prefs[Keys.EXA_API_KEY] = trimmed
            }
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