package com.borasarang.planjupjup

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.borasarang.planjupjup.ui.main.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 스모크: 3개 탭 전환 + 홈 핵심 요소 노출 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    // 알림 권한 다이얼로그가 RESUME을 막지 않도록 사전 부여
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.POST_NOTIFICATIONS,
    )

    @Test
    fun home_showsServerStatusAndCrawlButton() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.server_status_text)).check(matches(isDisplayed()))
            onView(withId(R.id.btn_manual_crawl)).check(matches(isDisplayed()))
            onView(withId(R.id.btn_manual_crawl)).check(matches(withText("지금 수집하기")))
            onView(withId(R.id.btn_open_portal)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun bottomNav_switchesToSourceAndSettings() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.nav_source)).perform(click())
            onView(withId(R.id.source_recycler)).check(matches(isDisplayed()))
            onView(withId(R.id.nav_settings)).perform(click())
            onView(withId(R.id.battery_status)).check(matches(isDisplayed()))
            onView(withId(R.id.nav_home)).perform(click())
            onView(withId(R.id.btn_manual_crawl)).check(matches(isDisplayed()))
        }
    }
}
