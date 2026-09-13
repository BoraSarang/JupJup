package com.borasarang.jupjup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.borasarang.jupjup.databinding.ActivityMainBinding
import com.borasarang.jupjup.ui.dashboard.DashboardFragment
import com.borasarang.macjupjup.MacJupJupRuntime
import com.borasarang.macjupjup.ui.home.HomeFragment as MacHomeFragment
import com.borasarang.macjupjup.ui.notif.NotificationFragment as MacNotificationFragment
import com.borasarang.macjupjup.ui.settings.SettingsFragment as MacSettingsFragment
import com.borasarang.macjupjup.ui.source.SourceManageFragment as MacSourceManageFragment
import com.borasarang.macjupjup.util.DebugLogger as MacDebugLogger
import com.borasarang.macjupjup.util.NetUtils as MacNetUtils
import com.borasarang.planjupjup.PlanJupJupRuntime
import com.borasarang.planjupjup.ui.home.HomeFragment as PlanHomeFragment
import com.borasarang.planjupjup.ui.notif.NotificationFragment as PlanNotificationFragment
import com.borasarang.planjupjup.ui.settings.SettingsFragment as PlanSettingsFragment
import com.borasarang.planjupjup.ui.source.SourceManageFragment as PlanSourceManageFragment
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
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private enum class Service { MAC, PLAN }
    private enum class Tab { DASHBOARD, HOME, SOURCE, NOTIF, SETTINGS }

    private var currentService = Service.MAC
    private var currentTab = Tab.DASHBOARD
    private val fragCache = mutableMapOf<String, Fragment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        MacDebugLogger.i("내비", "통합 홈 진입")

        savedInstanceState?.let {
            @Suppress("DEPRECATION")
            currentService = it.getSerializable("service") as? Service ?: Service.MAC
            @Suppress("DEPRECATION")
            currentTab = it.getSerializable("tab") as? Tab ?: Tab.DASHBOARD
        }

        binding.serviceSegment.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val next = if (checkedId == R.id.seg_plan) Service.PLAN else Service.MAC
            if (next == currentService) return@addOnButtonCheckedListener
            currentService = next
            currentTab = Tab.DASHBOARD
            MacDebugLogger.i("내비", "서비스 전환 → ${serviceTitle()} (대시보드로 이동)")
            refresh()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            currentTab = when (item.itemId) {
                R.id.nav_tab_home -> Tab.HOME
                R.id.nav_tab_source -> Tab.SOURCE
                R.id.nav_tab_notif -> Tab.NOTIF
                R.id.nav_tab_settings -> Tab.SETTINGS
                else -> Tab.DASHBOARD
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
        val segId = if (currentService == Service.PLAN) R.id.seg_plan else R.id.seg_mac
        if (binding.serviceSegment.checkedButtonId != segId) {
            binding.serviceSegment.check(segId)
        }
        val tabId = tabItemId(currentTab)
        if (binding.bottomNav.selectedItemId != tabId) {
            binding.bottomNav.selectedItemId = tabId
        }
    }

    private fun serviceTitle(): String = getString(
        if (currentService == Service.MAC) R.string.nav_mac else R.string.nav_plan,
    )

    private fun tabItemId(tab: Tab): Int = when (tab) {
        Tab.DASHBOARD -> R.id.nav_tab_dashboard
        Tab.HOME -> R.id.nav_tab_home
        Tab.SOURCE -> R.id.nav_tab_source
        Tab.NOTIF -> R.id.nav_tab_notif
        Tab.SETTINGS -> R.id.nav_tab_settings
    }

    private fun showFragment() {
        // 대시보드는 서비스 무관 → tag 고정
        val tag =
            if (currentTab == Tab.DASHBOARD) "DASHBOARD"
            else "${currentService.name}_${currentTab.name}"
        val frag = fragCache.getOrPut(tag) { createFragment() }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, frag, tag)
            .commit()
        // 탭 복귀·서비스 전환 시 대시보드 카드 최신화 + 활성 강조
        if (currentTab == Tab.DASHBOARD && frag is DashboardFragment) {
            frag.refreshData(currentService.name)
        }
    }

    private fun createFragment(): Fragment = when (currentTab) {
        Tab.DASHBOARD -> DashboardFragment()
        Tab.HOME -> when (currentService) {
            Service.MAC -> MacHomeFragment()
            Service.PLAN -> PlanHomeFragment()
        }
        Tab.SOURCE -> when (currentService) {
            Service.MAC -> MacSourceManageFragment()
            Service.PLAN -> PlanSourceManageFragment()
        }
        Tab.NOTIF -> when (currentService) {
            Service.MAC -> MacNotificationFragment()
            Service.PLAN -> PlanNotificationFragment()
        }
        Tab.SETTINGS -> when (currentService) {
            Service.MAC -> MacSettingsFragment()
            Service.PLAN -> PlanSettingsFragment()
        }
    }

    /** 앱 정보: 아이콘·버전·서비스별 주소·Git 링크 */
    private fun showAbout() {
        MacDebugLogger.i("내비", "앱 정보 열기")
        lifecycleScope.launch {
            val (macPort, planPort) = try {
                withContext(Dispatchers.IO) {
                    MacJupJupRuntime.preferences.getSettings().port to
                        PlanJupJupRuntime.preferences.getSettings().port
                }
            } catch (e: Exception) {
                MacDebugLogger.e("내비", "E-AND-DB-0402", "앱 정보 포트 조회 실패: ${e.message}", e)
                3000 to 3001
            }
            val ip = MacNetUtils.getLocalIp(this@MainActivity)
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
}
