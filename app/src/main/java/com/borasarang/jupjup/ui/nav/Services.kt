package com.borasarang.jupjup.ui.nav

import androidx.fragment.app.Fragment
import com.borasarang.jupjup.ui.dashboard.DashboardFragment
import com.borasarang.macjupjup.ui.home.HomeFragment as MacHomeFragment
import com.borasarang.macjupjup.ui.notif.NotificationFragment as MacNotificationFragment
import com.borasarang.macjupjup.ui.settings.SettingsFragment as MacSettingsFragment
import com.borasarang.macjupjup.ui.source.SourceManageFragment as MacSourceManageFragment
import com.borasarang.planjupjup.ui.home.HomeFragment as PlanHomeFragment
import com.borasarang.planjupjup.ui.notif.NotificationFragment as PlanNotificationFragment
import com.borasarang.planjupjup.ui.settings.SettingsFragment as PlanSettingsFragment
import com.borasarang.planjupjup.ui.source.SourceManageFragment as PlanSourceManageFragment
import com.borasarang.promptfactoryjupjup.ui.home.HomeFragment as PfHomeFragment
import com.borasarang.promptfactoryjupjup.ui.notif.NotificationFragment as PfNotificationFragment
import com.borasarang.promptfactoryjupjup.ui.settings.SettingsFragment as PfSettingsFragment
import com.borasarang.promptfactoryjupjup.ui.provider.ProviderManageFragment as PfProviderManageFragment

/** 줍줍 시리즈 서비스. 문자열("MAC"/"PLAN") 대신 이 enum을 전달한다 (R3) */
enum class Service { MAC, PLAN, PROMPTFACTORY }

/** 하단 기능 탭 */
enum class ServiceTab { DASHBOARD, HOME, SOURCE, NOTIF, SETTINGS }

/**
 * 서비스 등록소 (R3: AGENTS.local.md에는 있었으나 구현이 없던 것 실체화).
 * 서비스 추가 시 어댑터 1개 + 아래 when 1곳만 추가하면 된다.
 */
object ServiceRegistry {

    val adapters: Map<Service, com.borasarang.jupjup.ui.dashboard.ServiceAdapter> =
        mapOf(
            Service.MAC to com.borasarang.jupjup.ui.dashboard.MacServiceAdapter,
            Service.PLAN to com.borasarang.jupjup.ui.dashboard.PlanServiceAdapter,
            Service.PROMPTFACTORY to com.borasarang.jupjup.ui.dashboard.PfServiceAdapter,
        )

    fun fragment(service: Service, tab: ServiceTab): Fragment = when (tab) {
        ServiceTab.DASHBOARD -> DashboardFragment()
        ServiceTab.HOME -> when (service) {
            Service.MAC -> MacHomeFragment()
            Service.PLAN -> PlanHomeFragment()
            Service.PROMPTFACTORY -> PfHomeFragment()
        }
        ServiceTab.SOURCE -> when (service) {
            Service.MAC -> MacSourceManageFragment()
            Service.PLAN -> PlanSourceManageFragment()
            Service.PROMPTFACTORY -> PfProviderManageFragment()
        }
        ServiceTab.NOTIF -> when (service) {
            Service.MAC -> MacNotificationFragment()
            Service.PLAN -> PlanNotificationFragment()
            Service.PROMPTFACTORY -> PfNotificationFragment()
        }
        ServiceTab.SETTINGS -> when (service) {
            Service.MAC -> MacSettingsFragment()
            Service.PLAN -> PlanSettingsFragment()
            Service.PROMPTFACTORY -> PfSettingsFragment()
        }
    }

    /** FragmentManager 태그. 대시보드는 서비스 무관이라 고정 */
    fun tag(service: Service, tab: ServiceTab): String =
        if (tab == ServiceTab.DASHBOARD) "DASHBOARD" else "${service.name}_${tab.name}"
}
