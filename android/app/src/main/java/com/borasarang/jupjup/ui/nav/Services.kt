package com.borasarang.jupjup.ui.nav

import androidx.fragment.app.Fragment
import com.borasarang.jupjup.ui.dashboard.DashboardFragment
import com.borasarang.macjupjup.ui.home.HomeFragment as MacHomeFragment
import com.borasarang.macjupjup.ui.settings.SettingsFragment as MacSettingsFragment
import com.borasarang.planjupjup.ui.home.HomeFragment as PlanHomeFragment
import com.borasarang.planjupjup.ui.settings.SettingsFragment as PlanSettingsFragment
import com.borasarang.promptjournaljupjup.ui.home.HomeFragment as PjHomeFragment
import com.borasarang.promptjournaljupjup.ui.settings.SettingsFragment as PjSettingsFragment
import com.borasarang.communityjupjup.ui.home.HomeFragment as CmHomeFragment
import com.borasarang.communityjupjup.ui.settings.SettingsFragment as CmSettingsFragment

/** 줍줍 시리즈 서비스. 문자열("MAC"/"PLAN") 대신 이 enum을 전달한다 (R3) */
enum class Service { MAC, PLAN, PROMPTJOURNAL, COMMUNITY }

/** 하단 기능 탭 (R45: SOURCE·NOTIF는 웹 이관으로 삭제) */
enum class ServiceTab { DASHBOARD, HOME, SETTINGS }

/**
 * 서비스 등록소 (R3: AGENTS.local.md에는 있었으나 구현이 없던 것 실체화).
 * 서비스 추가 시 어댑터 1개 + 아래 when 1곳만 추가하면 된다.
 */
object ServiceRegistry {

    val adapters: Map<Service, com.borasarang.jupjup.ui.dashboard.ServiceAdapter> =
        mapOf(
            Service.MAC to com.borasarang.jupjup.ui.dashboard.MacServiceAdapter,
            Service.PLAN to com.borasarang.jupjup.ui.dashboard.PlanServiceAdapter,
            Service.PROMPTJOURNAL to com.borasarang.jupjup.ui.dashboard.PjServiceAdapter,
            Service.COMMUNITY to com.borasarang.jupjup.ui.dashboard.CmServiceAdapter,
        )

    fun fragment(service: Service, tab: ServiceTab): Fragment = when (tab) {
        ServiceTab.DASHBOARD -> DashboardFragment()
        ServiceTab.HOME -> when (service) {
            Service.MAC -> MacHomeFragment()
            Service.PLAN -> PlanHomeFragment()
            Service.PROMPTJOURNAL -> PjHomeFragment()
            Service.COMMUNITY -> CmHomeFragment()
        }
        ServiceTab.SETTINGS -> when (service) {
            Service.MAC -> MacSettingsFragment()
            Service.PLAN -> PlanSettingsFragment()
            Service.PROMPTJOURNAL -> PjSettingsFragment()
            Service.COMMUNITY -> CmSettingsFragment()
        }
    }

    /** FragmentManager 태그. 대시보드는 서비스 무관이라 고정 */
    fun tag(service: Service, tab: ServiceTab): String =
        if (tab == ServiceTab.DASHBOARD) "DASHBOARD" else "${service.name}_${tab.name}"
}
