package com.borasarang.jupjup

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
import com.google.android.material.tabs.TabLayout

/**
 * 줍줍 시리즈 통합 홈.
 *
 * - 하단 네비게이션: 서비스 선택 (맥줍줍 / 요금줍줍, 추후 추가 서비스 확장 가능)
 * - 상단 탭: 기능 (홈 / 수집 소스 / 알림 / 설정)
 *
 * 서비스가 바뀌면 현재 기능 탭을 그 서비스의 프래그먼트로 교체하고, 기능 탭이 바뀌면
 * 현재 서비스의 프래그먼트로 교체한다. 프래그먼트는 캐시해 탭 오가는 손실을 줄인다.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private enum class Service { MAC, PLAN }
    private enum class Tab { HOME, SOURCE, NOTIF, SETTINGS }

    private var currentService = Service.MAC
    private var currentTab = Tab.HOME
    private val fragCache = mutableMapOf<String, Fragment>()
    private var ignoreTabEvent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        savedInstanceState?.let {
            @Suppress("DEPRECATION")
            currentService = it.getSerializable("service") as? Service ?: Service.MAC
            @Suppress("DEPRECATION")
            currentTab = it.getSerializable("tab") as? Tab ?: Tab.HOME
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            currentService = if (item.itemId == R.id.nav_plan) Service.PLAN else Service.MAC
            showFragment()
            true
        }

        binding.tabs.apply {
            addTab(newTab().setText(R.string.tab_home))
            addTab(newTab().setText(R.string.tab_source))
            addTab(newTab().setText(R.string.tab_notif))
            addTab(newTab().setText(R.string.tab_settings))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    if (ignoreTabEvent) return
                    tab?.let { currentTab = Tab.values()[it.position] }
                    showFragment()
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
                override fun onTabReselected(tab: TabLayout.Tab?) = Unit
            })
        }

        showFragment()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable("service", currentService)
        outState.putSerializable("tab", currentTab)
    }

    private fun showFragment() {
        val tag = "${currentService.name}_${currentTab.name}"
        val frag = fragCache.getOrPut(tag) { createFragment() }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, frag, tag)
            .commit()
        ignoreTabEvent = true
        binding.tabs.getTabAt(currentTab.ordinal)?.let { binding.tabs.selectTab(it) }
        ignoreTabEvent = false
    }

    private fun createFragment(): Fragment = when (currentService) {
        Service.MAC -> when (currentTab) {
            Tab.HOME -> MacHomeFragment()
            Tab.SOURCE -> MacSourceManageFragment()
            Tab.NOTIF -> MacNotificationFragment()
            Tab.SETTINGS -> MacSettingsFragment()
        }
        Service.PLAN -> when (currentTab) {
            Tab.HOME -> PlanHomeFragment()
            Tab.SOURCE -> PlanSourceManageFragment()
            Tab.NOTIF -> PlanNotificationFragment()
            Tab.SETTINGS -> PlanSettingsFragment()
        }
    }
}