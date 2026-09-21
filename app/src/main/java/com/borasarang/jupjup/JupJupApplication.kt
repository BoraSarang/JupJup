package com.borasarang.jupjup

import android.app.Application
import com.borasarang.communityjupjup.CommunityJupJupRuntime
import com.borasarang.macjupjup.MacJupJupRuntime
import com.borasarang.planjupjup.PlanJupJupRuntime
import com.borasarang.promptjournaljupjup.PromptJournalRuntime

/**
 * 줍줍 시리즈 통합 앱.
 *
 * 서비스별 Application은 Android 1-앱/1-Application 제약 때문에 Runtime object로 전환했다.
 * 여기서는 서비스 런타임을 모두 초기화해 각자의 DB·서버·워커가 동일 프로세스에서 동작하게 한다.
 */
class JupJupApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MacJupJupRuntime.initialize(this)
        PlanJupJupRuntime.initialize(this)
        PromptJournalRuntime.initialize(this)
        CommunityJupJupRuntime.initialize(this)
    }
}