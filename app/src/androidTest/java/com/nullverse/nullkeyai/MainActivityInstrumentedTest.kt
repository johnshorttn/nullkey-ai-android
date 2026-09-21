package com.nullverse.nullkeyai

import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.action.ViewActions.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.nullverse.nullkeyai.ui.MainActivity
import com.nullverse.nullkeyai.ime.engine.KeyboardEnginePreferences
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device smoke test: proves the app actually launches and renders on a real
 * (emulated) Android device. This is the test the emulator CI job runs, which
 * exercises hardware-accelerated KVM on the CI runner.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {

    // Pre-grant POST_NOTIFICATIONS so MainActivity's runtime-permission dialog
    // never appears and pause the activity out from under Espresso (API 33+).
    @get:Rule
    val permissionRule: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            GrantPermissionRule.grant()
        }

    @Test
    fun launches_andShowsSetupControls() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.btn_enable)).check(matches(isDisplayed()))
            onView(withId(R.id.btn_switch)).check(matches(isDisplayed()))
            onView(withId(R.id.search)).check(matches(isDisplayed()))
            onView(withId(R.id.files_only)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun swipeTypingTogglePersistsPreference() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        KeyboardEnginePreferences.setSwipeTypingEnabled(context, true)
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.swipe_typing_enabled)).check(matches(isChecked()))
            onView(withId(R.id.swipe_typing_enabled)).perform(click())
            onView(withId(R.id.swipe_typing_enabled)).check(matches(isNotChecked()))
            org.junit.Assert.assertFalse(KeyboardEnginePreferences.swipeTypingEnabled(context))
        }
        KeyboardEnginePreferences.setSwipeTypingEnabled(context, true)
    }

    @Test
    fun swipeActionButtonsShowLocalizedDefaults() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.btn_swipe_left_action))
                .check(matches(withText("Left swipe: Delete")))
            onView(withId(R.id.btn_swipe_right_action))
                .check(matches(withText("Right swipe: Pin")))
        }
    }

    @Test
    fun showsAppTitle() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("NullKey AI")).check(matches(isDisplayed()))
        }
    }
}
