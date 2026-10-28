package com.nullverse.nullkeyai.ui

import android.content.Context
import android.widget.Button
import android.widget.CheckBox
import androidx.test.core.app.ApplicationProvider
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.ime.engine.KeyboardEnginePreferences
import com.nullverse.nullkeyai.ime.engine.LayoutOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * First launch with no saved settings: every keyboard setting button shows
 * its current value straight away, without touching the theme picker first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityFirstLaunchTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPrefs() {
        context.getSharedPreferences(KeyboardEnginePreferences.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun launch(): MainActivity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

    @Test
    fun layoutButtonsShowCurrentModeOnFirstLaunch() {
        val activity = launch()
        assertEquals(
            "Portrait layout: 4-row (standard)",
            activity.findViewById<Button>(R.id.btn_layout_portrait).text.toString(),
        )
        assertEquals(
            "Landscape layout: 5-row (number row)",
            activity.findViewById<Button>(R.id.btn_layout_landscape).text.toString(),
        )
    }

    @Test
    fun layoutButtonOpensPickerAndSavesOnFirstLaunch() {
        val activity = launch()
        activity.findViewById<Button>(R.id.btn_layout_portrait).performClick()
        val dialog = ShadowDialog.getLatestDialog() as? android.app.AlertDialog
        assertNotNull("layout picker should open without a theme change first", dialog)
        dialog!!.listView.performItemClick(dialog.listView, 2, 2L)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(
            "Portrait layout: Full (PC keys: F1–F12, Esc, Ctrl, arrows)",
            activity.findViewById<Button>(R.id.btn_layout_portrait).text.toString(),
        )
        assertEquals(
            com.nullverse.nullkeyai.ime.engine.KeyboardLayoutMode.FULL,
            KeyboardEnginePreferences.layoutMode(context, LayoutOrientation.PORTRAIT),
        )
    }

    @Test
    fun terminalCheckboxesMatchAndSavePrefsOnFirstLaunch() {
        val activity = launch()
        val auto = activity.findViewById<CheckBox>(R.id.terminal_mode_auto)
        assertTrue("terminal mode defaults on", auto.isChecked)
        auto.performClick()
        assertFalse(KeyboardEnginePreferences.terminalModeAuto(context))

        val row = activity.findViewById<CheckBox>(R.id.terminal_row_enabled)
        assertFalse(row.isChecked)
        row.performClick()
        assertTrue(KeyboardEnginePreferences.terminalRowEnabled(context))
    }
}
