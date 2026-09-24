package com.nullverse.nullkeyai.diagnostics

import androidx.test.core.app.ApplicationProvider
import com.nullverse.nullkeyai.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClipboardLabReportTest {

    @Test
    fun formatUsesLocalizedSectionHeadersAndYesNo() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val report = ClipboardLabReport.format(
            context.resources,
            ClipboardProbeResult(
                sdkInt = 34,
                targetSdk = 34,
                manufacturer = "Null",
                model = "Key",
                changeEventReceived = true,
                contentReadable = false,
                latencyMs = 12,
                restoredOriginalClip = true,
                note = context.getString(R.string.clipboard_lab_note_ok),
            ),
            imeEnabled = true,
            imeSelected = false,
        )
        assertTrue(report.contains(context.getString(R.string.clipboard_lab_report_title)))
        assertTrue(report.contains(context.getString(R.string.clipboard_lab_report_ime_section)))
        assertTrue(report.contains("YES"))
        assertTrue(report.contains("NO"))
        assertTrue(report.contains("12 ms"))
        assertTrue(report.contains(context.getString(R.string.clipboard_lab_report_footer)))
    }
}
