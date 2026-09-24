package com.borasarang.common.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 공급자별 모델 투입(활성) 상태 저장소. (R21 공통 모듈 이관)
 * storeName은 서비스별 DataStore 파일명으로 인스턴스가 분리된다.
 * - absent(null): 저장값 없음 → 기본값(시드 기본만) 유지.
 * - empty set: 모두 해제 상태 그대로 복원 (absent와 구분).
 */
class ModelEnabledStore private constructor(
    private val context: Context,
    private val storeName: String,
) {
    private val store: DataStore<Preferences>
        get() = SettingsStores.get(context, storeName)

    private fun key(provider: String) = stringSetPreferencesKey("enabled_models_$provider")

    suspend fun getEnabled(provider: String): Set<String>? {
        return store.data.map { prefs ->
            if (prefs.contains(key(provider))) {
                prefs[key(provider)] ?: emptySet()
            } else {
                null
            }
        }.first()
    }

    suspend fun saveEnabled(provider: String, enabledIds: Set<String>) {
        store.edit { prefs ->
            prefs[key(provider)] = enabledIds
        }
    }

    companion object {
        private val instances = ConcurrentHashMap<String, ModelEnabledStore>()

        fun getInstance(context: Context, storeName: String): ModelEnabledStore {
            return instances.getOrPut(storeName) {
                ModelEnabledStore(context.applicationContext, storeName)
            }
        }
    }
}