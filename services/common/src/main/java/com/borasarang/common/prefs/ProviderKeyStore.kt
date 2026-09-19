package com.borasarang.common.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.borasarang.common.ai.AiProvider
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 공급자별 API 키 저장소. 웹(공급자 관리)에서 저장/조회한다. (R21 공통 모듈 이관)
 * storeName은 서비스별 DataStore 파일명으로 인스턴스가 분리된다.
 * 키는 평문 DataStore 보관 — 앱 로컬 전용이며 네트워크로는 로컬 서버로만 노출된다.
 */
class ProviderKeyStore private constructor(
    private val context: Context,
    private val storeName: String,
) {
    private val store: DataStore<Preferences>
        get() = SettingsStores.get(context, storeName)

    private fun key(provider: String) = stringPreferencesKey("api_key_$provider")

    suspend fun getKey(provider: String): String {
        return store.data.map { prefs -> prefs[key(provider)] ?: "" }.first()
    }

    suspend fun getAllKeys(): Map<String, String> {
        return store.data.map { prefs ->
            AiProvider.entries.associate { it ->
                it.name to (prefs[key(it.name)] ?: "")
            }.filterValues { it.isNotBlank() }
        }.first()
    }

    suspend fun setKey(provider: String, value: String) {
        val trimmed = value.trim()
        store.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(key(provider))
            } else {
                prefs[key(provider)] = trimmed
            }
        }
    }

    companion object {
        private val instances = ConcurrentHashMap<String, ProviderKeyStore>()

        fun getInstance(context: Context, storeName: String): ProviderKeyStore {
            return instances.getOrPut(storeName) {
                ProviderKeyStore(context.applicationContext, storeName)
            }
        }
    }
}