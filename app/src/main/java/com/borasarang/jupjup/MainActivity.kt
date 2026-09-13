package com.borasarang.jupjup

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.borasarang.jupjup.databinding.ActivityMainBinding
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
 * - 상단 앱바(햄버거): 서비스 전환 드로어 (맥줍줍 / 요금줍줍)
 * - 하단 탭: 서버 상태 / 수집 소스 / 알림 / 설정
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private enum class Service { MAC, PLAN }
    private enum class Tab { SERVER, SOURCE, NOTIF, SETTINGS }

    private var currentService = Service.MAC
    private var currentTab = Tab.SERVER
    private val fragCache = mutableMapOf<String, Fragment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        savedInstanceState?.let {
            @Suppress("DEPRECATION")
            currentService = it.getSerializable("service") as? Service ?: Service.MAC
            @Suppress("DEPRECATION")
            currentTab = it.getSerializable("tab") as? Tab ?: Tab.SERVER
        }

        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            currentTab = when (item.itemId) {
                R.id.nav_tab_source -> Tab.SOURCE
                R.id.nav_tab_notif -> Tab.NOTIF
                R.id.nav_tab_settings -> Tab.SETTINGS
                else -> Tab.SERVER
            }
            refresh()
            true
        }

        binding.drawerNav.setNavigationItemSelectedListener { item ->
            currentService = if (item.itemId == R.id.nav_plan) Service.PLAN else Service.MAC
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

    private fun refresh() {
        showFragment()
        binding.toolbar.title = getString(serviceTitleRes())
        binding.drawerNav.setCheckedItem(serviceItemId(currentService))
        binding.bottomNav.selectedItemId = tabItemId(currentTab)
    }

    private fun serviceTitleRes(): Int = when (currentService) {
        Service.MAC -> R.string.nav_mac
        Service.PLAN -> R.string.nav_plan
    }

    private fun serviceItemId(service: Service): Int = when (service) {
        Service.MAC -> R.id.nav_mac
        Service.PLAN -> R.id.nav_plan
    }

    private fun tabItemId(tab: Tab): Int = when (tab) {
        Tab.SERVER -> R.id.nav_tab_server
        Tab.SOURCE -> R.id.nav_tab_source
        Tab.NOTIF -> R.id.nav_tab_notif
        Tab.SETTINGS -> R.id.nav_tab_settings
    }

    private fun showFragment() {
        val tag = "${currentService.name}_${currentTab.name}"
        val frag = fragCache.getOrPut(tag) { createFragment() }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, frag, tag)
            .commit()
    }

    private fun createFragment(): Fragment = when (currentService) {
        Service.MAC -> when (currentTab) {
            Tab.SERVER -> MacHomeFragment()
            Tab.SOURCE -> MacSourceManageFragment()
            Tab.NOTIF -> MacNotificationFragment()
            Tab.SETTINGS -> MacSettingsFragment()
        }
        Service.PLAN -> when (currentTab) {
            Tab.SERVER -> PlanHomeFragment()
            Tab.SOURCE -> PlanSourceManageFragment()
            Tab.NOTIF -> PlanNotificationFragment()
            Tab.SETTINGS -> PlanSettingsFragment()
        }
    }
}