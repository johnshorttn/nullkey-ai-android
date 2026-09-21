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
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.Visibility
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
            onView(withId(R.id.search)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.files_only)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.incognito_enabled)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.developer_options_enabled)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.btn_clipboard_lab)).check(matches(withEffectiveVisibility(Visibility.GONE)))
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
    fun incognitoAndDeveloperOptionsPersistAndRevealClipboardLab() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        KeyboardEnginePreferences.setIncognitoEnabled(context, false)
        KeyboardEnginePreferences.setDeveloperOptionsEnabled(context, false)
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.incognito_enabled)).perform(scrollTo()).check(matches(isNotChecked()))
            onView(withId(R.id.incognito_enabled)).perform(click())
            onView(withId(R.id.incognito_enabled)).check(matches(isChecked()))
            org.junit.Assert.assertTrue(KeyboardEnginePreferences.incognitoEnabled(context))
            org.junit.Assert.assertFalse(KeyboardEnginePreferences.shouldLearn(context))
            onView(withId(R.id.btn_clipboard_lab)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.developer_options_enabled)).perform(scrollTo()).perform(click())
            onView(withId(R.id.developer_options_enabled)).check(matches(isChecked()))
            onView(withId(R.id.btn_clipboard_lab)).perform(scrollTo()).check(matches(isDisplayed()))
            org.junit.Assert.assertTrue(KeyboardEnginePreferences.developerOptionsEnabled(context))
        }
        KeyboardEnginePreferences.setIncognitoEnabled(context, false)
        KeyboardEnginePreferences.setDeveloperOptionsEnabled(context, false)
    }

    @Test
    fun showsAppTitle() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("NullKey AI")).check(matches(isDisplayed()))
        }
    }
}
