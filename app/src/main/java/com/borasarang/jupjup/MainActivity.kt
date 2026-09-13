package com.borasarang.jupjup

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.borasarang.common.util.NetUtils
import com.borasarang.jupjup.databinding.ActivityMainBinding
import com.borasarang.jupjup.ui.dashboard.DashboardFragment
import com.borasarang.jupjup.ui.nav.Service
import com.borasarang.jupjup.ui.nav.ServiceRegistry
import com.borasarang.jupjup.ui.nav.ServiceTab
import com.borasarang.macjupjup.MacJupJupRuntime
import com.borasarang.macjupjup.util.Constants as MacConstants
import com.borasarang.macjupjup.util.DebugLogger as MacDebugLogger
import com.borasarang.planjupjup.PlanJupJupRuntime
import com.borasarang.planjupjup.util.Constants as PlanConstants
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 줍줍 시리즈 통합 홈 (서비스-우선 내비게이션).
 *
 * - 상단 세그먼트: 서비스 선택 (맥줍줍 / 요금줍줍) — 항상 표시
 * - 하단 탭: 대시보드(통합) / 홈 / 수집 소스 / 알림 / 설정
 * - 세그먼트 변경 시 대시보드로 강제 이동 (컨텍스트 모호성 제거)
 *
 * R3: 서비스·탭 enum과 Fragment 팩토리는 ui.nav 소유. 여기는 상태머신만 둔다.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var currentService = Service.MAC
    private var currentTab = ServiceTab.DASHBOARD

    /** 프로그래밍 선택 ↔ 리스너 재귀 호출 차단 가드 */
    private var suppressNavCallbacks = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        MacDebugLogger.i("내비", "통합 홈 진입")

        savedInstanceState?.let {
            currentService = it.getService("service") ?: Service.MAC
            currentTab = it.getServiceTab("tab") ?: ServiceTab.DASHBOARD
        }

        binding.serviceSegment.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (suppressNavCallbacks || !isChecked) return@addOnButtonCheckedListener
            val next = if (checkedId == R.id.seg_plan) Service.PLAN else Service.MAC
            if (next == currentService) return@addOnButtonCheckedListener
            currentService = next
            currentTab = ServiceTab.DASHBOARD
            MacDebugLogger.i("내비", "서비스 전환 → ${serviceTitle()} (대시보드로 이동)")
            refresh()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            if (suppressNavCallbacks) return@setOnItemSelectedListener true
            currentTab = when (item.itemId) {
                R.id.nav_tab_home -> ServiceTab.HOME
                R.id.nav_tab_source -> ServiceTab.SOURCE
                R.id.nav_tab_notif -> ServiceTab.NOTIF
                R.id.nav_tab_settings -> ServiceTab.SETTINGS
                else -> ServiceTab.DASHBOARD
            }
            applyTab()
            true
        }

        binding.toolbar.setOnMenuItemClickListener { item: MenuItem ->
            if (item.itemId == R.id.action_about) {
                showAbout()
                true
            } else false
        }

        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable("service", currentService)
        outState.putSerializable("tab", currentTab)
    }

    private fun applyTab() {
        showFragment()
        binding.toolbar.title = serviceTitle()
    }

    private fun refresh() {
        applyTab()
        suppressNavCallbacks = true
        try {
            val segId = if (currentService == Service.PLAN) R.id.seg_plan else R.id.seg_mac
            if (binding.serviceSegment.checkedButtonId != segId) {
                binding.serviceSegment.check(segId)
            }
            val tabId = tabItemId(currentTab)
            if (binding.bottomNav.selectedItemId != tabId) {
                binding.bottomNav.selectedItemId = tabId
            }
        } finally {
            suppressNavCallbacks = false
        }
    }

    private fun serviceTitle(): String = getString(
        if (currentService == Service.MAC) R.string.nav_mac else R.string.nav_plan,
    )

    private fun tabItemId(tab: ServiceTab): Int = when (tab) {
        ServiceTab.DASHBOARD -> R.id.nav_tab_dashboard
        ServiceTab.HOME -> R.id.nav_tab_home
        ServiceTab.SOURCE -> R.id.nav_tab_source
        ServiceTab.NOTIF -> R.id.nav_tab_notif
        ServiceTab.SETTINGS -> R.id.nav_tab_settings
    }

    private fun showFragment() {
        val tag = ServiceRegistry.tag(currentService, currentTab)
        // R3: FragmentManager 복원본 우선 — detach 캐시 영구 보유(누수) 제거
        val frag: Fragment = supportFragmentManager.findFragmentByTag(tag)
            ?: ServiceRegistry.fragment(currentService, currentTab)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, frag, tag)
            .commit()
        // 탭 복귀·서비스 전환 시 대시보드 카드 최신화 + 활성 강조
        if (currentTab == ServiceTab.DASHBOARD && frag is DashboardFragment) {
            frag.refreshData(currentService)
        }
    }

    /** 앱 정보: 아이콘·버전·서비스별 주소·Git 링크 */
    private fun showAbout() {
        MacDebugLogger.i("내비", "앱 정보 열기")
        lifecycleScope.launch {
            val (macPort, planPort, ip) = try {
                withContext(Dispatchers.IO) {
                    // R5: getLocalIp(바인더+NIC 열거) Main 호출 금지 → IO 합류
                    Triple(
                        MacJupJupRuntime.preferences.getSettings().port,
                        PlanJupJupRuntime.preferences.getSettings().port,
                        NetUtils.getLocalIp(this@MainActivity),
                    )
                }
            } catch (e: Exception) {
                MacDebugLogger.e("내비", "E-AND-DB-0402", "앱 정보 포트 조회 실패: ${e.message}", e)
                Triple(MacConstants.DEFAULT_PORT, PlanConstants.DEFAULT_PORT, null)
            }
            val view = layoutInflater.inflate(R.layout.dialog_about, null)
            view.findViewById<TextView>(R.id.about_version).text =
                getString(R.string.about_version, BuildConfig.VERSION_NAME)
            view.findViewById<TextView>(R.id.about_mac_row).text =
                getString(R.string.about_service_row, getString(R.string.nav_mac), macPort)
            view.findViewById<TextView>(R.id.about_mac_address).text =
                ip?.let { "http://$it:$macPort" } ?: getString(R.string.about_address_unknown)
            view.findViewById<TextView>(R.id.about_plan_row).text =
                getString(R.string.about_service_row, getString(R.string.nav_plan), planPort)
            view.findViewById<TextView>(R.id.about_plan_address).text =
                ip?.let { "http://$it:$planPort" } ?: getString(R.string.about_address_unknown)
            view.findViewById<TextView>(R.id.about_repo_link).setOnClickListener {
                openUrl(getString(R.string.about_repo_url))
            }
            MaterialAlertDialogBuilder(this@MainActivity)
                .setView(view)
                .setPositiveButton(R.string.about_close, null)
                .show()
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            MacDebugLogger.e("내비", "E-AND-UI-0701", "브라우저 열기 실패: ${e.message}", e)
            Toast.makeText(this, R.string.about_browser_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun Bundle.getService(key: String): Service? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSerializable(key, Service::class.java)
        } else {
            @Suppress("DEPRECATION")
            getSerializable(key) as? Service
        }

    private fun Bundle.getServiceTab(key: String): ServiceTab? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSerializable(key, ServiceTab::class.java)
        } else {
            @Suppress("DEPRECATION")
            getSerializable(key) as? ServiceTab
        }
}
