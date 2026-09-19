package com.borasarang.jupjup.ui.dashboard

import android.app.Application
import android.content.Context
import com.borasarang.jupjup.ui.nav.Service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** R3: 어댑터 주입으로 JVM 단위 테스트 가능해진 DashboardViewModel 검증 (Robolectric 불필요) */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private class FakeAdapter(
        override val service: Service,
        var state: DashboardServiceUi = DashboardServiceUi(),
        val script: ArrayDeque<DashboardServiceUi> = ArrayDeque(),
    ) : ServiceAdapter {
        val calls = mutableListOf<String>()
        var lastServerRunning: Boolean? = null
        var lastEnabledArg: Boolean? = null

        override suspend fun loadState(ip: String?): DashboardServiceUi {
            calls += "loadState"
            return if (script.isNotEmpty()) script.removeFirst() else state
        }

        override suspend fun triggerCrawlIfEnabled(enabled: Boolean) {
            calls += "trigger:$enabled"
        }

        override suspend fun setCrawlEnabled(enabled: Boolean) {
            calls += "setCrawl:$enabled"
            lastEnabledArg = enabled
            state = state.copy(crawlEnabled = enabled)
        }

        override fun setServerRunning(context: Context, running: Boolean) {
            calls += "server:$running"
            lastServerRunning = running
            state = state.copy(isServerRunning = !running)
        }
    }

    private lateinit var mac: FakeAdapter
    private lateinit var plan: FakeAdapter
    private lateinit var pj: FakeAdapter

    @Before
    fun setUp() {
        mac = FakeAdapter(Service.MAC)
        plan = FakeAdapter(Service.PLAN)
        pj = FakeAdapter(Service.PROMPTJOURNAL)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refresh_병합_양쪽상태반영() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        mac.state = DashboardServiceUi(isServerRunning = true, address = "http://1:3000", statValue1 = 10)
        plan.state = DashboardServiceUi(isServerRunning = true, address = "http://1:3001", statValue1 = 5)
        pj.state = DashboardServiceUi(isServerRunning = true, address = "http://1:3002", statValue1 = 3)

        val viewModel = DashboardViewModel(
            Application(),
            mapOf(Service.MAC to mac, Service.PLAN to plan, Service.PROMPTJOURNAL to pj),
            main,
        )
        viewModel.refresh()
        advanceUntilIdle()

        val ui = viewModel.uiState.value
        assertFalse(ui.isChecking)
        assertTrue(ui.mac.isServerRunning)
        assertTrue(ui.plan.isServerRunning)
        assertTrue(ui.pj.isServerRunning)
        assertEquals("http://1:3000", ui.mac.address)
        assertEquals(10, ui.mac.statValue1)
        assertEquals(5, ui.plan.statValue1)
        assertEquals(3, ui.pj.statValue1)
    }

    @Test
    fun refresh_중지표시_2초뒤1회재조회() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        // 첫 조회: mac 중지 → 재조회 1회 → 실행중
        mac.script.addAll(
            listOf(
                DashboardServiceUi(isServerRunning = false),
                DashboardServiceUi(isServerRunning = true),
            ),
        )
        plan.state = DashboardServiceUi(isServerRunning = true)
        pj.state = DashboardServiceUi(isServerRunning = true)

        val viewModel = DashboardViewModel(
            Application(),
            mapOf(Service.MAC to mac, Service.PLAN to plan, Service.PROMPTJOURNAL to pj),
            main,
        )
        viewModel.refresh()
        advanceUntilIdle()
        // 2초 정착 대기 + 재조회까지 가상시간 전진
        advanceTimeBy(2100)
        advanceUntilIdle()

        assertEquals(2, mac.calls.count { it == "loadState" })
        assertEquals(2, plan.calls.count { it == "loadState" })
        assertEquals(2, pj.calls.count { it == "loadState" })
        assertTrue(viewModel.uiState.value.mac.isServerRunning)
    }

    @Test
    fun refresh_계속중지면_재조회1회로종료() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        mac.state = DashboardServiceUi(isServerRunning = false)
        plan.state = DashboardServiceUi(isServerRunning = false)
        pj.state = DashboardServiceUi(isServerRunning = false)

        val viewModel = DashboardViewModel(
            Application(),
            mapOf(Service.MAC to mac, Service.PLAN to plan, Service.PROMPTJOURNAL to pj),
            main,
        )
        viewModel.refresh()
        advanceUntilIdle()
        advanceTimeBy(2100)
        advanceUntilIdle()

        // 최초 + 재조회 1회 = 2회, 그 이상 반복 없음 (재귀 없음)
        assertEquals(2, mac.calls.count { it == "loadState" })
        assertFalse(viewModel.uiState.value.mac.isServerRunning)
    }

    @Test
    fun toggleServer_현재상태반전위임() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        mac.state = DashboardServiceUi(isServerRunning = true)

        val viewModel = DashboardViewModel(
            Application(),
            mapOf(Service.MAC to mac, Service.PLAN to plan, Service.PROMPTJOURNAL to pj),
            main,
        )
        // 먼저 상태 병합 후 토글 (초기 crawlEnabled/isServerRunning 반영)
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.toggleServer(Service.MAC)
        advanceUntilIdle()

        assertEquals(true, mac.lastServerRunning)
        assertTrue(mac.calls.any { it == "server:true" })
    }

    @Test
    fun triggerCrawl_수집중전이후어댑터위임() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        mac.state = DashboardServiceUi(crawlEnabled = true)

        val viewModel = DashboardViewModel(
            Application(),
            mapOf(Service.MAC to mac, Service.PLAN to plan, Service.PROMPTJOURNAL to pj),
            main,
        )
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.triggerCrawl(Service.MAC)
        advanceUntilIdle()

        assertTrue(mac.calls.any { it == "trigger:true" })
        assertFalse(viewModel.uiState.value.mac.isCrawling)
    }

    @Test
    fun toggleCrawl_활성화반전저장() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        mac.state = DashboardServiceUi(crawlEnabled = true)

        val viewModel = DashboardViewModel(
            Application(),
            mapOf(Service.MAC to mac, Service.PLAN to plan, Service.PROMPTJOURNAL to pj),
            main,
        )
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.toggleCrawl(Service.MAC)
        advanceUntilIdle()

        assertEquals(false, mac.lastEnabledArg)
    }
}
