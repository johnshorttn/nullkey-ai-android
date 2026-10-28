package com.nullverse.nullkeyai.boot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.UserManager
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.ime.NullKeyImeService
import com.nullverse.nullkeyai.ime.engine.KeyboardEnginePreferences
import com.nullverse.nullkeyai.ime.engine.KeyboardLayoutMode
import com.nullverse.nullkeyai.ime.engine.KeyboardThemeId
import com.nullverse.nullkeyai.ime.engine.LayoutOrientation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Before the first unlock after a reboot the keyboard runs in basic mode:
 * look-and-feel from the device-protected mirror, and the Vault, learned
 * words, and credential-encrypted settings stay out of reach.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DirectBootBasicModeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val ce get() = context.getSharedPreferences(KeyboardEnginePreferences.PREFS, Context.MODE_PRIVATE)
    private val mirror get() = BootSettingsMirror.deviceProtectedPrefs(context)
    private val learned get() = context.getSharedPreferences("nullkey_suggester", Context.MODE_PRIVATE)

    @Before
    fun clean() {
        setUnlocked(true)
        ce.edit().clear().commit()
        mirror.edit().clear().commit()
        learned.edit().clear().commit()
        context.deleteDatabase("nullkey.db")
    }

    @After
    fun restore() = setUnlocked(true)

    @Test
    fun mirrorCopiesOnlyAllowlistedLookAndFeelKeys() {
        KeyboardEnginePreferences.setThemeId(context, KeyboardThemeId.entries.last())
        KeyboardEnginePreferences.setHeightScale(context, 1.1f)
        KeyboardEnginePreferences.setHapticsEnabled(context, false)
        KeyboardEnginePreferences.setLayoutMode(context, LayoutOrientation.PORTRAIT, KeyboardLayoutMode.FULL)
        KeyboardEnginePreferences.setIncognitoEnabled(context, true)
        KeyboardEnginePreferences.setDeveloperOptionsEnabled(context, true)
        // Something unrelated and private in the same CE file must never be mirrored.
        ce.edit().putString("some_secret", "s3cr3t").commit()
        KeyboardEnginePreferences.syncBootMirror(context)

        val copied = mirror.all
        assertTrue(BootSettingsMirror.ALLOWED_KEYS.containsAll(copied.keys))
        assertEquals(KeyboardThemeId.entries.last().name, copied[KeyboardEnginePreferences.KEY_THEME])
        assertEquals(false, copied[KeyboardEnginePreferences.KEY_HAPTICS])
        assertEquals(KeyboardLayoutMode.FULL.name, copied[KeyboardEnginePreferences.KEY_LAYOUT_MODE_PORTRAIT])
        assertFalse(copied.containsKey(KeyboardEnginePreferences.KEY_INCOGNITO))
        assertFalse(copied.containsKey(KeyboardEnginePreferences.KEY_DEVELOPER_OPTIONS))
        assertFalse(copied.containsKey("some_secret"))
    }

    @Test
    fun lockedReadsComeFromMirrorAndWritesTouchNothing() {
        KeyboardEnginePreferences.setLayoutMode(context, LayoutOrientation.PORTRAIT, KeyboardLayoutMode.FULL)
        KeyboardEnginePreferences.setHapticsEnabled(context, false)
        val ceBefore = ce.all.toMap()
        val mirrorBefore = mirror.all.toMap()

        setUnlocked(false)
        assertEquals(KeyboardLayoutMode.FULL, KeyboardEnginePreferences.layoutMode(context, LayoutOrientation.PORTRAIT))
        assertFalse(KeyboardEnginePreferences.hapticsEnabled(context))

        KeyboardEnginePreferences.setLayoutMode(context, LayoutOrientation.PORTRAIT, KeyboardLayoutMode.FOUR_ROW)
        KeyboardEnginePreferences.setHapticsEnabled(context, true)
        KeyboardEnginePreferences.setIncognitoEnabled(context, false)
        KeyboardEnginePreferences.syncBootMirror(context)

        assertEquals(ceBefore, ce.all.toMap())
        assertEquals(mirrorBefore, mirror.all.toMap())
        assertEquals(KeyboardLayoutMode.FULL, KeyboardEnginePreferences.layoutMode(context, LayoutOrientation.PORTRAIT))
    }

    @Test
    fun lockedModeNeverLearnsAndHidesDeveloperOptions() {
        ce.edit().putBoolean(KeyboardEnginePreferences.KEY_DEVELOPER_OPTIONS, true).commit()
        setUnlocked(false)
        assertTrue(KeyboardEnginePreferences.incognitoEnabled(context))
        assertFalse(KeyboardEnginePreferences.shouldLearn(context))
        assertFalse(KeyboardEnginePreferences.developerOptionsEnabled(context))
    }

    @Test
    fun lockedImeOpensNoVaultAndLearnsNothing() {
        setUnlocked(false)
        val controller = Robolectric.buildService(NullKeyImeService::class.java).create()
        val service = controller.get()
        assertFalse(service.fullMode)
        assertVaultLockedAndUntouched(service)

        learn(service, "secretword")
        assertTrue("nothing learned while locked", learned.all.isEmpty())
        assertFalse("no CE database opened while locked", context.getDatabasePath("nullkey.db").exists())
        controller.destroy()
    }

    @Test
    fun unlockBroadcastSwitchesToFullModeWithoutRestart() {
        setUnlocked(false)
        val service = Robolectric.buildService(NullKeyImeService::class.java).create().get()
        assertVaultLockedAndUntouched(service)

        setUnlocked(true)
        context.sendBroadcast(Intent(Intent.ACTION_USER_UNLOCKED))
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(service.fullMode)
        learn(service, "hello")
        assertTrue("learning works again after unlock", learned.all.isNotEmpty())
    }

    @Test
    fun unlockedImeStartsInFullMode() {
        val service = Robolectric.buildService(NullKeyImeService::class.java).create().get()
        assertTrue(service.fullMode)
    }

    @Test
    fun onlyTheKeyboardServiceIsDirectBootAware() {
        val flags = android.content.pm.PackageManager.GET_ACTIVITIES or
            android.content.pm.PackageManager.GET_SERVICES or
            android.content.pm.PackageManager.GET_RECEIVERS or
            android.content.pm.PackageManager.GET_PROVIDERS
        val info = context.packageManager.getPackageInfo(context.packageName, flags)
        val aware = listOfNotNull(info.activities, info.services, info.receivers, info.providers)
            .flatMap { it.toList() }
            .filter { it.directBootAware && it.packageName == context.packageName && it.name.startsWith("com.nullverse") }
            .map { it.name }
        assertEquals(listOf(NullKeyImeService::class.java.name), aware)
    }

    private fun assertVaultLockedAndUntouched(service: NullKeyImeService) {
        val root = service.onCreateInputView()
        Robolectric.buildActivity(Activity::class.java).setup().get().setContentView(root)
        service.onStartInputView(EditorInfo().apply { packageName = "com.example"; inputType = android.text.InputType.TYPE_CLASS_TEXT }, false)
        val empty = root.findViewById<TextView>(R.id.clips_empty)
        assertEquals(context.getString(R.string.ime_vault_locked_until_unlock), empty.text.toString())
        assertEquals(View.VISIBLE, empty.visibility)
        assertNull(repositoryOf(service))
    }

    private fun learn(service: NullKeyImeService, word: String) {
        NullKeyImeService::class.java.getDeclaredMethod("learnTyped", String::class.java)
            .apply { isAccessible = true }
            .invoke(service, word)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun repositoryOf(service: NullKeyImeService): Any? =
        NullKeyImeService::class.java.getDeclaredField("repository").apply { isAccessible = true }.get(service)

    private fun setUnlocked(unlocked: Boolean) {
        shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(unlocked)
    }
}
