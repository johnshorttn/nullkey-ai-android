package com.nullverse.nullkeyai

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import com.nullverse.nullkeyai.clipboard.ClipboardMonitorService
import com.nullverse.nullkeyai.ime.NullKeyImeService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * targetSdk 36 contract for NullKey's real surfaces: IME, clipboard FGS, and
 * privacy/offline defaults. Robolectric runs on API 34; targetSdk still reports 36.
 */
@RunWith(RobolectricTestRunner::class)
class TargetSdkContractTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageManager: PackageManager = context.packageManager

    @Test
    fun targetsAndroid16() {
        assertEquals(36, context.applicationInfo.targetSdkVersion)
    }

    @Test
    fun doesNotRequestInternet() {
        val requested = requestedPermissions()
        assertFalse(requested.contains(Manifest.permission.INTERNET))
        assertFalse(requested.contains(Manifest.permission.ACCESS_NETWORK_STATE))
    }

    @Test
    fun declaresImeAndClipboardMonitorPermissions() {
        val requested = requestedPermissions()
        assertTrue(requested.contains(Manifest.permission.FOREGROUND_SERVICE))
        assertTrue(requested.contains(Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE))
        assertTrue(requested.contains(Manifest.permission.POST_NOTIFICATIONS))
    }

    @Test
    fun imeIsExportedOnlyBehindBindInputMethod() {
        val info = packageManager.getServiceInfo(
            ComponentName(context, NullKeyImeService::class.java),
            0
        )
        assertTrue(info.exported)
        assertEquals(Manifest.permission.BIND_INPUT_METHOD, info.permission)
    }

    @Test
    fun clipboardMonitorIsPrivateSpecialUseForegroundService() {
        val info = packageManager.getServiceInfo(
            ComponentName(context, ClipboardMonitorService::class.java),
            PackageManager.GET_META_DATA
        )
        assertFalse(info.exported)
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            info.foregroundServiceType
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            ClipboardMonitorService.foregroundServiceType(sdkInt = 34)
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            ClipboardMonitorService.foregroundServiceType(sdkInt = 36)
        )
        assertEquals(0, ClipboardMonitorService.foregroundServiceType(sdkInt = 33))
    }

    @Test
    fun vaultIsExcludedFromAndroidBackup() {
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun backupRuleResourcesExcludeVaultDomains() {
        val backupDomains = excludeDomains(R.xml.backup_rules)
        assertTrue(backupDomains.contains("database"))
        assertTrue(backupDomains.contains("sharedpref"))
        assertTrue(excludePaths(R.xml.backup_rules).contains("vault/"))

        val extractionDomains = excludeDomains(R.xml.data_extraction_rules)
        assertTrue(extractionDomains.contains("database"))
        assertTrue(extractionDomains.contains("sharedpref"))
        assertTrue(excludePaths(R.xml.data_extraction_rules).contains("vault/"))
    }

    private fun excludeDomains(xmlRes: Int): Set<String> {
        val parser = context.resources.getXml(xmlRes)
        val domains = mutableSetOf<String>()
        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (event == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "exclude") {
                parser.getAttributeValue(null, "domain")?.let { domains += it }
            }
            event = parser.next()
        }
        return domains
    }

    private fun excludePaths(xmlRes: Int): Set<String> {
        val parser = context.resources.getXml(xmlRes)
        val paths = mutableSetOf<String>()
        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (event == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "exclude") {
                parser.getAttributeValue(null, "path")?.let { paths += it }
            }
            event = parser.next()
        }
        return paths
    }

    private fun requestedPermissions(): Set<String> {
        val info = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        return info.requestedPermissions?.toSet().orEmpty()
    }
}
