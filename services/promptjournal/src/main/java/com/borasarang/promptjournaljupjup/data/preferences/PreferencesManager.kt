package com.borasarang.promptjournaljupjup.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.borasarang.common.prefs.SettingsStores
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences>
    get() = SettingsStores.get(this, "pj_settings")

data class PjSettings(
    val port: Int = 3002,
    val autoStart: Boolean = false
)

class PreferencesManager(private val context: Context) {

    private object Keys {
        val PORT = intPreferencesKey("server_port")
        val AUTO_START = booleanPreferencesKey("auto_start")
    }

    suspend fun getSettings(): PjSettings {
        return context.settingsStore.data.map { prefs ->
            PjSettings(
                port = prefs[Keys.PORT] ?: 3002,
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