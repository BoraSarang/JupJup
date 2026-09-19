package com.borasarang.promptfactoryjupjup.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.borasarang.common.prefs.SettingsStores
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.modelStore: DataStore<Preferences>
    get() = SettingsStores.get(this, "pf_models")

/**
 * 공급자별 모델 투입(활성) 상태 저장소.
 * - absent(null): 저장값 없음 → 기본값(전체 투입) 유지.
 * - empty set: 모두 해제 상태 그대로 복원 (absent와 구분).
 */
class ModelEnabledStore(private val context: Context) {

    private fun key(provider: String) = stringSetPreferencesKey("enabled_models_$provider")

    suspend fun getEnabled(provider: String): Set<String>? {
        return context.modelStore.data.map { prefs ->
            if (prefs.contains(key(provider))) {
                prefs[key(provider)] ?: emptySet()
            } else {
                null
            }
        }.first()
    }

    suspend fun saveEnabled(provider: String, enabledIds: Set<String>) {
        context.modelStore.edit { prefs ->
            prefs[key(provider)] = enabledIds
        }
    }

    companion object {
        @Volatile
        private var instance: ModelEnabledStore? = null

        fun getInstance(context: Context): ModelEnabledStore {
            return instance ?: synchronized(this) {
                instance ?: ModelEnabledStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
