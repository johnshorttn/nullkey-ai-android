package com.nullverse.nullkeyai

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.nullverse.nullkeyai.clipboard.ClipboardMonitorService
import com.nullverse.nullkeyai.ime.NullKeyImeService
import com.nullverse.nullkeyai.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-side regression for the targetSdk 36 bump. Runs on the API-34 CI
 * emulator; [android.content.pm.ApplicationInfo.targetSdkVersion] is still 36.
 */
@RunWith(AndroidJUnit4::class)
class TargetSdkInstrumentedTest {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            GrantPermissionRule.grant()
        }

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun packageTargetsApi36() {
        assertEquals(36, context.applicationInfo.targetSdkVersion)
    }

    @Test
    fun imeAndClipboardMonitorKeepRequiredDeclarations() {
        val pm = context.packageManager
        val ime = pm.getServiceInfo(ComponentName(context, NullKeyImeService::class.java), 0)
        assertTrue(ime.exported)
        assertEquals(android.Manifest.permission.BIND_INPUT_METHOD, ime.permission)

        val monitor = pm.getServiceInfo(
            ComponentName(context, ClipboardMonitorService::class.java),
            PackageManager.GET_META_DATA
        )
        assertFalse(monitor.exported)
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            monitor.foregroundServiceType
        )
    }

    @Test
    fun doesNotHoldInternetPermission() {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = info.requestedPermissions?.toList().orEmpty()
        assertFalse(requested.contains(android.Manifest.permission.INTERNET))
        assertTrue(requested.contains(android.Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE))
        assertTrue(requested.contains(android.Manifest.permission.POST_NOTIFICATIONS))
    }

    @Test
    fun startMonitorPromotesSpecialUseForegroundService() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.btn_start_monitor)).perform(scrollTo()).perform(click())
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val running = am.getRunningServices(50)
            assertTrue(
                running.any { it.service.className == ClipboardMonitorService::class.java.name }
            )
            ClipboardMonitorService.stop(context)
        }
    }
}
