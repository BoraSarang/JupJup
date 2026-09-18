package com.borasarang.promptfactoryjupjup.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.borasarang.promptfactoryjupjup.data.preferences.PreferencesManager
import com.borasarang.promptfactoryjupjup.server.HttpServerService
import com.borasarang.promptfactoryjupjup.util.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        scope.launch {
            try {
                val settings = PreferencesManager.getInstance(context).getSettings()
                if (settings.autoStart) {
                    DebugLogger.i("부팅", "부팅 완료 — 서버 자동 시작")
                    HttpServerService.start(context)
                } else {
                    DebugLogger.i("부팅", "부팅 완료 — 자동 시작 꺼짐")
                }
            } catch (e: Exception) {
                DebugLogger.e("부팅", "E-AND-SRV-0102", "부팅 자동 시작 실패: ${e.message}", e)
            }
        }
    }
}
