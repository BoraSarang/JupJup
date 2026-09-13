package com.borasarang.jupjup

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.borasarang.jupjup.databinding.ActivityMainBinding
import com.borasarang.jupjup.ui.insight.InsightFragment
import com.borasarang.macjupjup.ui.home.HomeFragment as MacHomeFragment
import com.borasarang.macjupjup.ui.notif.NotificationFragment as MacNotificationFragment
import com.borasarang.macjupjup.ui.settings.SettingsFragment as MacSettingsFragment
import com.borasarang.macjupjup.ui.source.SourceManageFragment as MacSourceManageFragment
import com.borasarang.planjupjup.ui.home.HomeFragment as PlanHomeFragment
import com.borasarang.planjupjup.ui.notif.NotificationFragment as PlanNotificationFragment
import com.borasarang.planjupjup.ui.settings.SettingsFragment as PlanSettingsFragment
import com.borasarang.planjupjup.ui.source.SourceManageFragment as PlanSourceManageFragment

/**
 * 줍줍 시리즈 통합 홈.
 *
 * - 시작 화면 = 인사이트 (맥줍줍·요금줍줍 두 서비스를 카드 한 화면에서 병렬 조회)
 * - 상단 앱바(햄버거): 드로어 — 줍줍 시리즈 / 맥줍줍 / 요금줍줍
 * - 하단 탭: 인사이트 / 수집 소스 / 알림 / 설정
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private enum class Service { MAC, PLAN }
    private enum class Tab { INSIGHT, SOURCE, NOTIF, SETTINGS }

    private var currentService = Service.MAC
    private var currentTab = Tab.INSIGHT
    private val fragCache = mutableMapOf<String, Fragment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        savedInstanceState?.let {
            @Suppress("DEPRECATION")
            currentService = it.getSerializable("service") as? Service ?: Service.MAC
            @Suppress("DEPRECATION")
            currentTab = it.getSerializable("tab") as? Tab ?: Tab.INSIGHT
        }

        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            currentTab = when (item.itemId) {
                R.id.nav_tab_source -> Tab.SOURCE
                R.id.nav_tab_notif -> Tab.NOTIF
                R.id.nav_tab_settings -> Tab.SETTINGS
                else -> Tab.INSIGHT
            }
            applyTab()
            true
        }

        binding.drawerNav.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_series -> currentTab = Tab.INSIGHT
                R.id.nav_mac -> currentService = Service.MAC
                R.id.nav_plan -> currentService = Service.PLAN
            }
            binding.drawerLayout.closeDrawers()
            refresh()
            true
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
        binding.toolbar.title = getString(tabTitleRes())
        binding.drawerNav.setCheckedItem(drawerItemId())
    }

    private fun refresh() {
        applyTab()
        val tabId = tabItemId(currentTab)
        if (binding.bottomNav.selectedItemId != tabId) {
            binding.bottomNav.selectedItemId = tabId
        }
    }

    /** 앱바 타이틀 — 인사이트는 시리즈명, 그 외엔 선택된 서비스명 */
    private fun tabTitleRes(): Int = when (currentTab) {
        Tab.INSIGHT -> R.string.nav_series
        else -> serviceTitleRes(currentService)
    }

    private fun serviceTitleRes(service: Service): Int = when (service) {
        Service.MAC -> R.string.nav_mac
        Service.PLAN -> R.string.nav_plan
    }

    /** 드로어 체크 표시 — 인사이트면 '줍줍 시리즈', 그 외엔 서비스 항목 */
    private fun drawerItemId(): Int = when (currentTab) {
        Tab.INSIGHT -> R.id.nav_series
        else -> when (currentService) {
            Service.MAC -> R.id.nav_mac
            Service.PLAN -> R.id.nav_plan
        }
    }

    private fun tabItemId(tab: Tab): Int = when (tab) {
        Tab.INSIGHT -> R.id.nav_tab_insight
        Tab.SOURCE -> R.id.nav_tab_source
        Tab.NOTIF -> R.id.nav_tab_notif
        Tab.SETTINGS -> R.id.nav_tab_settings
    }

    private fun showFragment() {
        // 인사이트는 서비스 무관 → tag 고정 (드로어 서비스 전환 시에도 화면 유지)
        val tag =
            if (currentTab == Tab.INSIGHT) "INSIGHT"
            else "${currentService.name}_${currentTab.name}"
        val frag = fragCache.getOrPut(tag) { createFragment() }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, frag, tag)
            .commit()
        // 서비스 전환/탭 복귀 시 인사이트 카드 최신화
        if (currentTab == Tab.INSIGHT && frag is InsightFragment) {
            frag.refreshData()
        }
    }

    private fun createFragment(): Fragment = when (currentTab) {
        Tab.INSIGHT -> InsightFragment()
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
}
