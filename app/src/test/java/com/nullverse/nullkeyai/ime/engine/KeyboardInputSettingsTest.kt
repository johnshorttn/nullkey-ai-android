package com.nullverse.nullkeyai.ime.engine

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.nullverse.nullkeyai.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyboardInputSettingsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(KeyboardEnginePreferences.PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun themeParseFallsBackToDarkVault() {
        assertEquals(KeyboardThemeId.DARK_VAULT, KeyboardThemeId.parse(null))
        assertEquals(KeyboardThemeId.DARK_VAULT, KeyboardThemeId.parse("not-a-theme"))
        assertEquals(KeyboardThemeId.LIGHT, KeyboardThemeId.parse("light"))
        assertEquals(KeyboardThemeId.SYSTEM, KeyboardThemeId.parse("SYSTEM"))
    }

    @Test
    fun systemThemeFollowsNightMode() {
        assertEquals(KeyboardThemeId.DARK_VAULT, KeyboardThemeId.SYSTEM.resolve(nightMode = true))
        assertEquals(KeyboardThemeId.LIGHT, KeyboardThemeId.SYSTEM.resolve(nightMode = false))
        assertEquals(KeyboardThemeId.LIGHT, KeyboardThemeId.LIGHT.resolve(nightMode = true))
    }

    @Test
    fun heightProgressRoundTripsDefaultAndBounds() {
        assertEquals(4, KeyboardInputSettings.heightScaleToProgress(1.0f))
        assertEquals(1.0f, KeyboardInputSettings.progressToHeightScale(4), 0.0001f)
        assertEquals(0.80f, KeyboardInputSettings.clampHeightScale(0.1f), 0.0001f)
        assertEquals(1.40f, KeyboardInputSettings.clampHeightScale(9f), 0.0001f)
        assertEquals(1.05f, KeyboardInputSettings.clampHeightScale(1.03f), 0.0001f)
        assertEquals(100, KeyboardInputSettings.heightPercent(1.0f))
        assertEquals(80, KeyboardInputSettings.heightPercent(0.80f))
    }

    @Test
    fun longPressProgressRoundTripsDefaultAndBounds() {
        assertEquals(4, KeyboardInputSettings.longPressMsToProgress(400))
        assertEquals(400, KeyboardInputSettings.progressToLongPressMs(4))
        assertEquals(200, KeyboardInputSettings.clampLongPressMs(0))
        assertEquals(800, KeyboardInputSettings.clampLongPressMs(5000))
        assertEquals(450, KeyboardInputSettings.clampLongPressMs(440))
    }

    @Test
    fun imePrefsDefaultToCurrentKeyboardBehavior() {
        assertTrue(KeyboardEnginePreferences.useCustomEngine(context))
        assertTrue(KeyboardEnginePreferences.swipeTypingEnabled(context))
        assertEquals(KeyboardThemeId.DARK_VAULT, KeyboardEnginePreferences.themeId(context))
        assertEquals(1.0f, KeyboardEnginePreferences.heightScale(context), 0.0001f)
        assertTrue(KeyboardEnginePreferences.hapticsEnabled(context))
        assertFalse(KeyboardEnginePreferences.soundEnabled(context))
        assertEquals(400, KeyboardEnginePreferences.longPressMs(context))
    }

    @Test
    fun imePrefsPersistThemeHeightHapticsSoundAndLongPress() {
        KeyboardEnginePreferences.setThemeId(context, KeyboardThemeId.LIGHT)
        KeyboardEnginePreferences.setHeightScale(context, 1.25f)
        KeyboardEnginePreferences.setHapticsEnabled(context, false)
        KeyboardEnginePreferences.setSoundEnabled(context, true)
        KeyboardEnginePreferences.setLongPressMs(context, 250)

        assertEquals(KeyboardThemeId.LIGHT, KeyboardEnginePreferences.themeId(context))
        assertEquals(1.25f, KeyboardEnginePreferences.heightScale(context), 0.0001f)
        assertFalse(KeyboardEnginePreferences.hapticsEnabled(context))
        assertTrue(KeyboardEnginePreferences.soundEnabled(context))
        assertEquals(250, KeyboardEnginePreferences.longPressMs(context))
    }

    @Test
    fun lightThemeUsesLightPalette() {
        val dark = KeyboardTheme.from(context, LayoutOrientation.PORTRAIT, KeyboardThemeId.DARK_VAULT)
        val light = KeyboardTheme.from(context, LayoutOrientation.PORTRAIT, KeyboardThemeId.LIGHT)
        assertEquals(ContextCompat.getColor(context, R.color.kb_background), dark.backgroundColor)
        assertEquals(ContextCompat.getColor(context, R.color.kb_light_background), light.backgroundColor)
        assertEquals(ContextCompat.getColor(context, R.color.kb_light_label), light.labelColor)
        assertTrue(dark.backgroundColor != light.backgroundColor)
    }

    @Test
    fun keyFeedbackRespectsTogglesAndSilentStream() {
        assertTrue(KeyFeedback.shouldHaptic(true))
        assertFalse(KeyFeedback.shouldHaptic(false))
        assertTrue(KeyFeedback.shouldPlaySound(true, 7))
        assertFalse(KeyFeedback.shouldPlaySound(true, 0))
        assertFalse(KeyFeedback.shouldPlaySound(false, 7))
    }
}
