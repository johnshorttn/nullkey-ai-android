package com.nullverse.nullkeyai

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PlayReleaseScaffoldTest {

    private fun repoRoot(): File {
        val cwd = File(".").canonicalFile
        val candidates = listOf(cwd, cwd.parentFile, cwd.parentFile?.parentFile).filterNotNull()
        return candidates.firstOrNull { File(it, "keystore.properties.example").isFile }
            ?: error("Could not locate repo root from $cwd")
    }

    @Test
    fun gitignoreAndExampleKeepSecretsOutOfTheRepo() {
        val root = repoRoot()
        val gitignore = File(root, ".gitignore").readText()
        assertTrue(gitignore.contains("keystore.properties"))
        assertTrue(gitignore.contains("*.jks"))
        assertTrue(gitignore.contains("*.aab"))
        assertTrue(gitignore.contains("*.keystore"))

        val example = File(root, "keystore.properties.example").readText()
        assertTrue(example.contains("CHANGE_ME"))
        assertTrue(example.contains("KEYSTORE_FILE"))
        assertTrue(example.contains("bundleRelease"))
        assertTrue(example.lineSequence().any { it == "storePassword=CHANGE_ME" })
        assertTrue(example.lineSequence().any { it == "keyPassword=CHANGE_ME" })
    }

    @Test
    fun packageIdentityMatchesPlayListingNotes() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("com.nullverse.nullkeyai", context.packageName)
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        assertEquals("com.nullverse.nullkeyai", info.packageName)
    }

    @Test
    fun manifestStaysOfflineAndDeclaresImePlusClipboardMonitor() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
        val info = context.packageManager.getPackageInfo(context.packageName, flags)
        val permissions = info.requestedPermissions?.toList().orEmpty()

        assertFalse(
            "INTERNET would change Play Data Safety; keep the IME offline-default.",
            permissions.contains(Manifest.permission.INTERNET)
        )
        assertTrue(permissions.contains(Manifest.permission.FOREGROUND_SERVICE))
        assertTrue(permissions.contains(Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE))
        assertTrue(permissions.contains(Manifest.permission.POST_NOTIFICATIONS))

        val services = info.services?.toList().orEmpty()
        val ime = services.single { it.name.endsWith("NullKeyImeService") }
        assertTrue(ime.exported)
        assertEquals(Manifest.permission.BIND_INPUT_METHOD, ime.permission)

        val monitor = services.single { it.name.endsWith("ClipboardMonitorService") }
        assertFalse(monitor.exported)
    }
}
