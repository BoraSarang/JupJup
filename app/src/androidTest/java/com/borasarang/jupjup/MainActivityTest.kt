package com.borasarang.jupjup

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 스모크: 대시보드 + 세그먼트 서비스 전환 + 하단 5탭 (S22 connected) */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    // 알림 권한 다이얼로그가 RESUME을 막지 않도록 사전 부여
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.POST_NOTIFICATIONS,
    )

    @Test
    fun dashboard_showsBothServiceCards() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.dashboard_mac_card)).check(matches(isDisplayed()))
            onView(withId(R.id.dashboard_plan_card)).check(matches(isDisplayed()))
            onView(withId(R.id.dashboard_mac_btn_crawl)).check(matches(withText("지금 수집하기")))
        }
    }

    @Test
    fun segment_switchesActiveService() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // 기본 MAC 활성
            onView(withId(R.id.dashboard_mac_active_badge)).check(matches(isDisplayed()))
            // PLAN으로 전환 → 대시보드로 이동 + plan 카드 강조
            onView(withId(R.id.seg_plan)).perform(click())
            onView(withId(R.id.dashboard_plan_active_badge)).check(matches(isDisplayed()))
            onView(withId(R.id.dashboard_mac_active_badge)).check(matches(not(isDisplayed())))
            // 홈 탭 → plan 홈 노출
            onView(withId(R.id.nav_tab_home)).perform(click())
            onView(withId(com.borasarang.planjupjup.R.id.plan_server_status_text))
                .check(matches(isDisplayed()))
            // MAC으로 복귀 → mac 홈 노출
            onView(withId(R.id.seg_mac)).perform(click())
            onView(withId(R.id.nav_tab_home)).perform(click())
            onView(withId(com.borasarang.macjupjup.R.id.mac_server_status_text))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun bottomNav_switchesToSourceAndBackToDashboard() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.nav_tab_source)).perform(click())
            onView(withId(com.borasarang.macjupjup.R.id.mac_source_recycler)).check(matches(isDisplayed()))
            onView(withId(R.id.nav_tab_dashboard)).perform(click())
            onView(withId(R.id.dashboard_mac_card)).check(matches(isDisplayed()))
        }
    }
}
