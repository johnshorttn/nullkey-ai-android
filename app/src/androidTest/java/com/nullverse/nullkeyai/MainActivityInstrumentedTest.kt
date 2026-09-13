package com.nullverse.nullkeyai

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nullverse.nullkeyai.ui.MainActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device smoke test: proves the app actually launches and renders on a real
 * (emulated) Android device. This is the test the emulator CI job runs, which
 * exercises hardware-accelerated KVM on the CI runner.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {

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
    fun showsAppTitle() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("NullKey AI")).check(matches(isDisplayed()))
        }
    }
}
