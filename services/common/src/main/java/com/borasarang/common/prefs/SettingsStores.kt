package com.borasarang.common.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 서비스별 DataStore 팩토리 (R2).
 * `preferencesDataStore` 위임은 파일명을 컴파일 상수로만 받으므로,
 * 서비스 구분자 주입이 필요해 팩토리로 일원화한다.
 * 파일명(`mac_settings`·`plan_settings`)은 기존과 동일 — 데이터 승계됨.
 */
object SettingsStores {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cache = ConcurrentHashMap<String, DataStore<Preferences>>()

    fun get(context: Context, name: String): DataStore<Preferences> {
        return cache.getOrPut(name) {
            PreferenceDataStoreFactory.create(scope = scope) {
                context.applicationContext.filesDir.resolve("datastore/$name.preferences_pb")
            }
        }
    }
}
