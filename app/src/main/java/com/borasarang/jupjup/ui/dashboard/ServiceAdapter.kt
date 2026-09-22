package com.borasarang.jupjup.ui.dashboard

import android.content.Context
import com.borasarang.common.util.NetUtils
import com.borasarang.jupjup.ui.nav.Service
import com.borasarang.macjupjup.MacJupJupRuntime
import com.borasarang.macjupjup.server.HttpServerService as MacHttpServerService
import com.borasarang.macjupjup.util.DebugLogger as MacDebugLogger
import com.borasarang.macjupjup.util.TimeUtils as MacTimeUtils
import com.borasarang.planjupjup.PlanJupJupRuntime
import com.borasarang.planjupjup.server.HttpServerService as PlanHttpServerService
import com.borasarang.planjupjup.util.DebugLogger as PlanDebugLogger
import com.borasarang.planjupjup.util.TimeUtils as PlanTimeUtils
import com.borasarang.promptjournaljupjup.PromptJournalRuntime
import com.borasarang.promptjournaljupjup.server.HttpServerService as PjHttpServerService
import com.borasarang.promptjournaljupjup.util.DebugLogger as PjDebugLogger
import com.borasarang.communityjupjup.CommunityJupJupRuntime
import com.borasarang.communityjupjup.server.HttpServerService as CmHttpServerService
import com.borasarang.communityjupjup.util.DebugLogger as CmDebugLogger
import com.borasarang.communityjupjup.util.TimeUtils as CmTimeUtils

/**
 * 서비스 어댑터 (R3). DashboardViewModel이 양쪽 Runtime을 직접 import하던 결합을 흡수한다.
 * 서비스 추가 시 이 인터페이스 구현 1개 + ServiceRegistry 등록이면 된다.
 * 각 메서드는 자신의 서비스 로거·에러코드로 로그를 소유한다.
 */
interface ServiceAdapter {
    val service: Service

    /** 대시보드 카드 상태 1회 조회 (IO 스레드에서 호출됨) */
    suspend fun loadState(ip: String?): DashboardServiceUi

    /** 지금 수집. 일시정지 상태면 스킵 로그만 남긴다 */
    suspend fun triggerCrawlIfEnabled(enabled: Boolean)

    /** 수집 중지/재개 + 주기 스케줄 재예약/취소 */
    suspend fun setCrawlEnabled(enabled: Boolean)

    /** 서버 시작/중지 */
    fun setServerRunning(context: Context, running: Boolean)
}

object MacServiceAdapter : ServiceAdapter {
    override val service = Service.MAC

    override suspend fun loadState(ip: String?): DashboardServiceUi {
        val app = MacJupJupRuntime
        val settings = app.preferences.getSettings()
        val macToken = try { app.preferences.getAdminToken() } catch (_: Exception) { "" }
        val stats = app.appRepository.overview()
        val net = try {
            app.appRepository.netTotals(30)
        } catch (_: Exception) {
            0L to 0L
        }
        return DashboardServiceUi(
            isServerRunning = NetUtils.isPortOpen(settings.port),
            address = "http://$ip:${settings.port}",
            statValue1 = stats.totalApps,
            statValue2 = stats.activeSources,
            adminToken = macToken,
            lastCollectedLabel = MacTimeUtils.formatRelative(stats.lastCollectedAt),
            crawlEnabled = settings.crawlEnabled,
            netLabel = "네트워크 30일 ${com.borasarang.common.util.NetMeter.formatBytes(net.first + net.second)}",
        )
    }

    override suspend fun triggerCrawlIfEnabled(enabled: Boolean) {
        if (!enabled) {
            MacDebugLogger.w("수동수집", "수집 일시정지 상태 — 대시보드 수동 수집 스킵(mac)")
            return
        }
        MacDebugLogger.i("수동수집", "대시보드 수동 수집 클릭(mac)")
        try {
            MacJupJupRuntime.crawlScheduler.triggerImmediate(null)
        } catch (e: Exception) {
            MacDebugLogger.e("수동수집", "E-AND-CRAWL-0201", "대시보드 수동 수집 예약 실패(mac): ${e.message}", e)
        }
    }

    override suspend fun setCrawlEnabled(enabled: Boolean) {
        val app = MacJupJupRuntime
        try {
            app.preferences.setCrawlEnabled(enabled)
            if (enabled) {
                app.crawlScheduler.scheduleAll()
                MacDebugLogger.i("수동수집", "대시보드 수집 재개 — 주기 스케줄 재예약(mac)")
            } else {
                app.crawlScheduler.cancelAll()
                MacDebugLogger.i("수동수집", "대시보드 수집 일시정지 — 실행/예약 수집 취소(mac)")
            }
        } catch (e: Exception) {
            MacDebugLogger.e("수동수집", "E-AND-CRAWL-0221", "대시보드 수집 중지/재개 저장 실패(mac): ${e.message}", e)
        }
    }

    override fun setServerRunning(context: Context, running: Boolean) {
        if (running) {
            MacDebugLogger.i("서버", "대시보드 서버 중지(mac)")
            MacHttpServerService.stop(context)
        } else {
            MacDebugLogger.i("서버", "대시보드 서버 시작(mac)")
            MacHttpServerService.start(context)
        }
    }
}

object PlanServiceAdapter : ServiceAdapter {
    override val service = Service.PLAN

    override suspend fun loadState(ip: String?): DashboardServiceUi {
        val app = PlanJupJupRuntime
        val settings = app.preferences.getSettings()
        val planToken = try { app.preferences.getAdminToken() } catch (_: Exception) { "" }
        val stats = app.planRepository.getStats()
        return DashboardServiceUi(
            isServerRunning = NetUtils.isPortOpen(settings.port),
            address = "http://$ip:${settings.port}",
            statValue1 = stats.totalPlans,
            statValue2 = stats.activeSources,
            adminToken = planToken,
            lastCollectedLabel = PlanTimeUtils.formatRelative(stats.lastCollectedAt),
            crawlEnabled = settings.crawlEnabled,
            netLabel = "네트워크 24시간 ${com.borasarang.common.util.NetMeter.formatBytes(stats.netRx24h + stats.netTx24h)}",
        )
    }

    override suspend fun triggerCrawlIfEnabled(enabled: Boolean) {
        if (!enabled) {
            PlanDebugLogger.w("수동수집", "수집 일시정지 상태 — 대시보드 수동 수집 스킵(plan)")
            return
        }
        PlanDebugLogger.i("수동수집", "대시보드 수동 수집 클릭(plan)")
        try {
            PlanJupJupRuntime.crawlScheduler.triggerImmediate(null)
        } catch (e: Exception) {
            PlanDebugLogger.e("수동수집", "E-AND-CRAWL-0211", "대시보드 수동 수집 예약 실패(plan): ${e.message}", e)
        }
    }

    override suspend fun setCrawlEnabled(enabled: Boolean) {
        val app = PlanJupJupRuntime
        try {
            app.preferences.setCrawlEnabled(enabled)
            if (enabled) {
                app.crawlScheduler.scheduleAll()
                PlanDebugLogger.i("수동수집", "대시보드 수집 재개 — 주기 스케줄 재예약(plan)")
            } else {
                app.crawlScheduler.cancelAll()
                PlanDebugLogger.i("수동수집", "대시보드 수집 일시정지 — 실행/예약 수집 취소(plan)")
            }
        } catch (e: Exception) {
            PlanDebugLogger.e("수동수집", "E-AND-CRAWL-0221", "대시보드 수집 중지/재개 저장 실패(plan): ${e.message}", e)
        }
    }

    override fun setServerRunning(context: Context, running: Boolean) {
        if (running) {
            PlanDebugLogger.i("서버", "대시보드 서버 중지(plan)")
            PlanHttpServerService.stop(context)
        } else {
            PlanDebugLogger.i("서버", "대시보드 서버 시작(plan)")
            PlanHttpServerService.start(context)
        }
    }
}

object PjServiceAdapter : ServiceAdapter {
    override val service = Service.PROMPTJOURNAL

    override suspend fun loadState(ip: String?): DashboardServiceUi {
        val app = PromptJournalRuntime
        if (!app.isInitialized) {
            return DashboardServiceUi(
                isServerRunning = false,
                address = "초기화 중…",
                statValue1 = 0,
                statValue2 = 0,
                lastCollectedLabel = "대기 중",
                crawlEnabled = false,
            )
        }
        val settings = app.preferences.getSettings()
        val count = app.promptExecutionRepository.count()
        // getAll()은 프롬프트 본문(대형 마크다운)까지 전건 로딩한다 → 활성 목록만 조회
        val enabled = app.promptRepository.getEnabled()
        val activeCount = enabled.size
        val aiNet = com.borasarang.common.util.NetMeter.snapshotFor("ai")
        val pjToken = try { app.preferences.getAdminToken() } catch (_: Exception) { "" }
        return DashboardServiceUi(
            isServerRunning = NetUtils.isPortOpen(settings.port),
            address = "http://$ip:${settings.port}",
            statValue1 = count,
            statValue2 = activeCount,
            lastCollectedLabel = if (activeCount > 0) "프롬프트 ${activeCount}개 활성" else "활성 프롬프트 없음",
            crawlEnabled = activeCount > 0,
            netLabel = "AI·검색 ${com.borasarang.common.util.NetMeter.formatBytes(aiNet.totalBytes)}",
            adminToken = pjToken,
        )
    }

    override suspend fun triggerCrawlIfEnabled(enabled: Boolean) {
        if (!enabled) {
            PjDebugLogger.w("실행", "활성 프롬프트 없음 — 즉시 실행 스킵")
            return
        }
        PjDebugLogger.i("실행", "대시보드 즉시 실행 클릭")
        try {
            val first = PromptJournalRuntime.promptRepository.getEnabled().firstOrNull()
            if (first == null) {
                PjDebugLogger.w("실행", "활성 프롬프트 없음")
            } else {
                PromptJournalRuntime.scheduler.triggerImmediate(first.id)
            }
        } catch (e: Exception) {
            PjDebugLogger.e("실행", "E-AND-REPORT-0804", "즉시 실행 예약 실패: ${e.message}", e)
        }
    }

    override suspend fun setCrawlEnabled(enabled: Boolean) {
        val app = PromptJournalRuntime
        try {
            if (enabled) {
                app.scheduler.rescheduleAll()
                PjDebugLogger.i("실행", "실행 재개 — 스케줄 재예약")
            } else {
                app.scheduler.cancelAll()
                PjDebugLogger.i("실행", "실행 일시정지 — 스케줄 취소")
            }
        } catch (e: Exception) {
            PjDebugLogger.e("실행", "E-AND-REPORT-0803", "실행 중지/재개 저장 실패: ${e.message}", e)
        }
    }

    override fun setServerRunning(context: Context, running: Boolean) {
        if (running) {
            PjDebugLogger.i("서버", "대시보드 서버 중지")
            PjHttpServerService.stop(context)
        } else {
            PjDebugLogger.i("서버", "대시보드 서버 시작")
            PjHttpServerService.start(context)
        }
    }
}

object CmServiceAdapter : ServiceAdapter {
    override val service = Service.COMMUNITY

    override suspend fun loadState(ip: String?): DashboardServiceUi {
        val app = CommunityJupJupRuntime
        val settings = app.preferences.getSettings()
        val cmToken = try { app.preferences.getAdminToken() } catch (_: Exception) { "" }
        val stats = app.communityRepository.stats()
        val net = try {
            app.communityRepository.netTotals(30)
        } catch (_: Exception) {
            0L to 0L
        }
        return DashboardServiceUi(
            isServerRunning = NetUtils.isPortOpen(settings.port),
            address = "http://$ip:${settings.port}",
            statValue1 = stats.totalPosts,
            statValue2 = stats.activeSources,
            adminToken = cmToken,
            lastCollectedLabel = CmTimeUtils.formatRelative(stats.lastCollectedAt),
            crawlEnabled = settings.crawlEnabled,
            netLabel = "네트워크 30일 ${com.borasarang.common.util.NetMeter.formatBytes(net.first + net.second)}",
        )
    }

    override suspend fun triggerCrawlIfEnabled(enabled: Boolean) {
        if (!enabled) {
            CmDebugLogger.w("수동수집", "수집 일시정지 상태 — 대시보드 수동 수집 스킵(community)")
            return
        }
        CmDebugLogger.i("수동수집", "대시보드 수동 수집 클릭(community)")
        try {
            CommunityJupJupRuntime.crawlScheduler.triggerImmediate(null)
        } catch (e: Exception) {
            CmDebugLogger.e("수동수집", "E-AND-CRAWL-0201", "대시보드 수동 수집 예약 실패(community): ${e.message}", e)
        }
    }

    override suspend fun setCrawlEnabled(enabled: Boolean) {
        val app = CommunityJupJupRuntime
        try {
            app.preferences.setCrawlEnabled(enabled)
            if (enabled) {
                app.crawlScheduler.scheduleAll()
                CmDebugLogger.i("수동수집", "대시보드 수집 재개 — 주기 스케줄 재예약(community)")
            } else {
                app.crawlScheduler.cancelAll()
                CmDebugLogger.i("수동수집", "대시보드 수집 일시정지 — 실행/예약 수집 취소(community)")
            }
        } catch (e: Exception) {
            CmDebugLogger.e("수동수집", "E-AND-CRAWL-0221", "대시보드 수집 중지/재개 저장 실패(community): ${e.message}", e)
        }
    }

    override fun setServerRunning(context: Context, running: Boolean) {
        if (running) {
            CmDebugLogger.i("서버", "대시보드 서버 중지(community)")
            CmHttpServerService.stop(context)
        } else {
            CmDebugLogger.i("서버", "대시보드 서버 시작(community)")
            CmHttpServerService.start(context)
        }
    }
}
