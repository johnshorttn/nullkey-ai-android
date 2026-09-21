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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.nullverse.nullkeyai.ui.MainActivity
import com.nullverse.nullkeyai.ime.engine.KeyboardEnginePreferences
import com.nullverse.nullkeyai.ime.engine.KeyboardThemeId
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
    fun keyboardInputSettingsPersistThemeHapticsAndSound() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        KeyboardEnginePreferences.setThemeId(context, KeyboardThemeId.DARK_VAULT)
        KeyboardEnginePreferences.setHapticsEnabled(context, true)
        KeyboardEnginePreferences.setSoundEnabled(context, false)
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.btn_keyboard_theme)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.haptics_enabled)).perform(scrollTo()).check(matches(isChecked()))
            onView(withId(R.id.haptics_enabled)).perform(click())
            onView(withId(R.id.haptics_enabled)).check(matches(isNotChecked()))
            org.junit.Assert.assertFalse(KeyboardEnginePreferences.hapticsEnabled(context))
            onView(withId(R.id.key_sound_enabled)).perform(scrollTo()).check(matches(isNotChecked()))
            onView(withId(R.id.key_sound_enabled)).perform(click())
            onView(withId(R.id.key_sound_enabled)).check(matches(isChecked()))
            org.junit.Assert.assertTrue(KeyboardEnginePreferences.soundEnabled(context))
            onView(withId(R.id.btn_keyboard_theme)).perform(scrollTo()).perform(click())
            onView(withText("Light")).perform(click())
            org.junit.Assert.assertEquals(
                KeyboardThemeId.LIGHT,
                KeyboardEnginePreferences.themeId(context),
            )
        }
        KeyboardEnginePreferences.setThemeId(context, KeyboardThemeId.DARK_VAULT)
        KeyboardEnginePreferences.setHapticsEnabled(context, true)
        KeyboardEnginePreferences.setSoundEnabled(context, false)
    }

    @Test
    fun showsAppTitle() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("NullKey AI")).check(matches(isDisplayed()))
        }
    }
}
