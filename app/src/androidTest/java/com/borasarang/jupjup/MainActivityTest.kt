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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 스모크: 인사이트 시작 화면 + 하단 탭 전환 (S22 connected) */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    // 알림 권한 다이얼로그가 RESUME을 막지 않도록 사전 부여
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.POST_NOTIFICATIONS,
    )

    @Test
    fun insight_showsBothServiceCards() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.insight_mac_card)).check(matches(isDisplayed()))
            onView(withId(R.id.insight_plan_card)).check(matches(isDisplayed()))
            onView(withId(R.id.insight_mac_btn_crawl)).check(matches(withText("지금 수집하기")))
        }
    }

    @Test
    fun bottomNav_switchesToSourceAndBackToInsight() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.nav_tab_source)).perform(click())
            onView(withId(com.borasarang.macjupjup.R.id.mac_source_recycler)).check(matches(isDisplayed()))
            onView(withId(R.id.nav_tab_insight)).perform(click())
            onView(withId(R.id.insight_mac_card)).check(matches(isDisplayed()))
        }
    }
}
