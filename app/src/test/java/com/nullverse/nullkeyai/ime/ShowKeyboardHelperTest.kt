package com.nullverse.nullkeyai.ime

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import androidx.test.core.app.ApplicationProvider
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.ime.ShowKeyboardBridge.Mode
import com.nullverse.nullkeyai.ime.ShowKeyboardBridge.Outcome
import com.nullverse.nullkeyai.ui.ShowKeyboardActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShowKeyboardHelperTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        ShowKeyboardBridge.current()?.let { ShowKeyboardBridge.detach(it) }
    }

    @Test
    fun decisionTable() {
        assertEquals(Outcome.ENABLE_IN_SETTINGS, ShowKeyboardBridge.decide(Mode.SHOW, enabled = false, selected = false, imeRunning = false))
        assertEquals(Outcome.ENABLE_IN_SETTINGS, ShowKeyboardBridge.decide(Mode.SWITCH, enabled = false, selected = false, imeRunning = false))
        assertEquals(Outcome.PICKER, ShowKeyboardBridge.decide(Mode.SHOW, enabled = true, selected = false, imeRunning = false))
        assertEquals(Outcome.PICKER, ShowKeyboardBridge.decide(Mode.SHOW, enabled = true, selected = true, imeRunning = false))
        assertEquals(Outcome.SHOW_SELF, ShowKeyboardBridge.decide(Mode.SHOW, enabled = true, selected = true, imeRunning = true))
        assertEquals(Outcome.PICKER, ShowKeyboardBridge.decide(Mode.SWITCH, enabled = true, selected = true, imeRunning = true))
    }

    @Test
    fun onlyRealEditorsCount() {
        assertFalse(ShowKeyboardBridge.isTextEditor(null))
        assertFalse(ShowKeyboardBridge.isTextEditor(EditorInfo().apply { inputType = InputType.TYPE_NULL }))
        assertTrue(ShowKeyboardBridge.isTextEditor(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }))
    }

    @Test
    fun requestShowSelfOnlyFromApi28() {
        assertFalse(ShowKeyboardBridge.usesRequestShowSelf(27))
        assertTrue(ShowKeyboardBridge.usesRequestShowSelf(28))
        assertTrue(ShowKeyboardBridge.usesRequestShowSelf(36))
    }

    @Test
    fun runningImeRegistersWithTheBridge() {
        val controller = Robolectric.buildService(NullKeyImeService::class.java).create()
        assertSame(controller.get(), ShowKeyboardBridge.current())
        controller.destroy()
        assertNull(ShowKeyboardBridge.current())
    }

    @Test
    fun noFocusedFieldGivesClearToast() {
        val service = Robolectric.buildService(NullKeyImeService::class.java).create().get()
        service.showWhenEditorReady()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(NullKeyImeService.HELPER_SHOW_TIMEOUT_MS + 50))
        assertEquals(context.getString(R.string.show_keyboard_no_field), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun helperWithNullKeyDisabledOpensKeyboardSettings() {
        val controller = Robolectric.buildActivity(
            ShowKeyboardActivity::class.java,
            Intent(context, ShowKeyboardActivity::class.java).setAction(ShowKeyboardActivity.ACTION_SHOW),
        ).setup()
        val started = shadowOf(controller.get()).nextStartedActivity
        assertEquals(Settings.ACTION_INPUT_METHOD_SETTINGS, started.action)
        assertTrue(controller.get().isFinishing)
        assertEquals(context.getString(R.string.show_keyboard_enable_first), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun helperWithNullKeyEnabledButNotCurrentWaitsForFocusToOpenPicker() {
        enableNullKey()
        val controller = Robolectric.buildActivity(
            ShowKeyboardActivity::class.java,
            Intent(context, ShowKeyboardActivity::class.java).setAction(ShowKeyboardActivity.ACTION_SHOW),
        ).setup()
        assertFalse("waits for window focus before opening the picker", controller.get().isFinishing)
        assertNull(shadowOf(controller.get()).nextStartedActivity)
    }

    @Test
    fun helperWithNullKeyCurrentAsksTheImeToShow() {
        enableNullKey()
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
            ComponentName(context, NullKeyImeService::class.java).flattenToString(),
        )
        var asked = 0
        val target = ShowKeyboardBridge.Target { asked++ }
        ShowKeyboardBridge.attach(target)
        val controller = Robolectric.buildActivity(
            ShowKeyboardActivity::class.java,
            Intent(context, ShowKeyboardActivity::class.java).setAction(ShowKeyboardActivity.ACTION_SHOW),
        ).setup()
        assertEquals(1, asked)
        assertTrue(controller.get().isFinishing)
        ShowKeyboardBridge.detach(target)
    }

    @Test
    fun notificationHasShowAndSwitchKeyboardActions() {
        val service = Robolectric.buildService(com.nullverse.nullkeyai.clipboard.ClipboardMonitorService::class.java)
            .create().startCommand(0, 1).get()
        val notification: Notification = shadowOf(service).lastForegroundNotification
        val titles = notification.actions.map { it.title.toString() }
        assertEquals(
            listOf(
                context.getString(R.string.show_keyboard_action),
                context.getString(R.string.switch_keyboard_action),
                context.getString(R.string.open_vault),
            ),
            titles,
        )
        val show = shadowOf(notification.actions[0].actionIntent)
        assertTrue(show.isActivity)
        assertEquals(ShowKeyboardActivity.ACTION_SHOW, show.savedIntent.action)
        assertEquals(ShowKeyboardActivity::class.java.name, show.savedIntent.component?.className)
        assertEquals(ShowKeyboardActivity.ACTION_SWITCH, shadowOf(notification.actions[1].actionIntent).savedIntent.action)
    }

    private fun enableNullKey() {
        // Hidden test constructor: (packageName, className, label, settingsActivity).
        val imi = org.robolectric.util.ReflectionHelpers.callConstructor(
            InputMethodInfo::class.java,
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(String::class.java, context.packageName),
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(String::class.java, NullKeyImeService::class.java.name),
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(CharSequence::class.java, "NullKey"),
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(String::class.java, null),
        )
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        shadowOf(imm).setEnabledInputMethodInfoList(listOf(imi))
    }
}
