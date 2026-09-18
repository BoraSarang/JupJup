package com.borasarang.promptfactoryjupjup.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.borasarang.common.prefs.SettingsStores
import com.borasarang.promptfactoryjupjup.ai.AiProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.keyStore: DataStore<Preferences>
    get() = SettingsStores.get(this, "pf_api_keys")

/**
 * 공급자별 API 키 저장소. 웹(공급자 관리)에서 저장/조회한다.
 * 키는 평문 DataStore 보관 — 앱 로컬 전용이며 네트워크로는 로컬 서버로만 노출된다.
 */
class ProviderKeyStore(private val context: Context) {

    private fun key(provider: String) = stringPreferencesKey("api_key_$provider")

    suspend fun getKey(provider: String): String {
        return context.keyStore.data.map { prefs -> prefs[key(provider)] ?: "" }.first()
    }

    suspend fun getAllKeys(): Map<String, String> {
        return context.keyStore.data.map { prefs ->
            AiProvider.entries.associate { it ->
                it.name to (prefs[key(it.name)] ?: "")
            }.filterValues { it.isNotBlank() }
        }.first()
    }

    suspend fun setKey(provider: String, value: String) {
        val trimmed = value.trim()
        context.keyStore.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(key(provider))
            } else {
                prefs[key(provider)] = trimmed
            }
        }
    }

    companion object {
        @Volatile
        private var instance: ProviderKeyStore? = null

        fun getInstance(context: Context): ProviderKeyStore {
            return instance ?: synchronized(this) {
                instance ?: ProviderKeyStore(context.applicationContext).also { instance = it }
            }
        }
    }
}