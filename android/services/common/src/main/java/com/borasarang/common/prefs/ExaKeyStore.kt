package com.borasarang.common.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Exa 검색엔진 API 키 저장소 — 평문 DataStore, 로컬 서버로만 노출.
 * storeName은 서비스별 DataStore 파일명으로 인스턴스가 분리된다.
 */
class ExaKeyStore private constructor(
    private val context: Context,
    private val storeName: String,
) {
    private val store: DataStore<Preferences>
        get() = SettingsStores.get(context, storeName)

    private val key = stringPreferencesKey("exa_api_key")

    suspend fun getKey(): String {
        return store.data.map { it[key] ?: "" }.first()
    }

    suspend fun setKey(value: String) {
        val trimmed = value.trim()
        store.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(key)
            } else {
                prefs[key] = trimmed
            }
        }
    }

    companion object {
        private val instances = ConcurrentHashMap<String, ExaKeyStore>()

        fun getInstance(context: Context, storeName: String): ExaKeyStore {
            return instances.getOrPut(storeName) {
                ExaKeyStore(context.applicationContext, storeName)
            }
        }
    }
}
