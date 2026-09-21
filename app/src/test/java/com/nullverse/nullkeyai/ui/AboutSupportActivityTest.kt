package com.nullverse.nullkeyai.ui

import android.content.Intent
import android.net.Uri
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import com.nullverse.nullkeyai.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AboutSupportActivityTest {

    @Test
    fun linksOpenHttpsDestinationsInAnExternalBrowser() {
        val activity = Robolectric.buildActivity(AboutSupportActivity::class.java).setup().get()
        assertTrue(activity.findViewById<android.widget.TextView>(R.id.about_version).text.contains("1.2"))

        val expected = listOf(
            R.id.btn_privacy to AboutSupportActivity.PRIVACY_URL,
            R.id.btn_support to AboutSupportActivity.SUPPORT_URL,
            R.id.btn_bug to AboutSupportActivity.BUG_URL,
            R.id.btn_feature to AboutSupportActivity.FEATURE_URL,
            R.id.btn_security to AboutSupportActivity.SECURITY_URL,
            R.id.btn_beta to AboutSupportActivity.BETA_URL,
            R.id.btn_sponsor to AboutSupportActivity.SPONSOR_URL
        )
        expected.forEach { (id, url) ->
            assertTrue(url.startsWith("https://"))
            activity.findViewById<Button>(id).performClick()
            val intent = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals(Uri.parse(url), intent.data)
        }
    }

    @Test
    fun privacyUrlMatchesThePublishedPolicy() {
        val url = AboutSupportActivity.PRIVACY_URL
        assertEquals("https://johnshorttn.github.io/nullkey-ai-android/privacy.html", url)
        val policy = File(repoRoot(), "docs/privacy.md").readText()
        assertTrue(policy.contains(url))
        assertTrue(policy.contains("does **not** include the `INTERNET` permission"))
        assertTrue(policy.contains("Clipboard"))
        assertTrue(policy.contains("Vault"))
        assertFalse(policy.contains("allowBackup=\"true\""))
        assertFalse(policy.contains("allowBackup is currently enabled"))

        val requested = ApplicationProvider.getApplicationContext<android.content.Context>()
            .packageManager
            .getPackageInfo(
                "com.nullverse.nullkeyai",
                android.content.pm.PackageManager.GET_PERMISSIONS
            )
            .requestedPermissions
            ?.toList()
            .orEmpty()
        assertFalse(requested.contains(android.Manifest.permission.INTERNET))
    }

    private fun repoRoot(): File {
        val cwd = File(".").canonicalFile
        return listOf(cwd, cwd.parentFile, cwd.parentFile?.parentFile)
            .filterNotNull()
            .first { File(it, "docs/privacy.md").isFile && File(it, "settings.gradle.kts").isFile }
    }
}
