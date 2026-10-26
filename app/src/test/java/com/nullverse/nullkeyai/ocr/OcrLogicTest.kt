package com.nullverse.nullkeyai.ocr

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.google.android.datatransport.cct.CCTDestination
import com.google.mlkit.common.MlKit
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OcrLogicTest {
    @Test
    fun normalizerCollapsesSpaceAndCapsLength() {
        assertEquals("", OcrTextNormalizer.normalize("  \n\t"))
        assertEquals("Hello\n\nthere", OcrTextNormalizer.normalize(" Hello \r\n\r\n\r\n there  "))
        val long = "a".repeat(OcrTextNormalizer.MAX_CHARS + 50)
        assertEquals(OcrTextNormalizer.MAX_CHARS, OcrTextNormalizer.normalize(long).length)
    }

    @Test
    fun eligibilityBlocksNonImagesMissingFilesAndLockedClips() {
        assertEquals(OcrBlock.NOT_IMAGE, OcrEligibility.blockReason("TEXT", true, false, true))
        assertEquals(OcrBlock.MISSING_ASSET, OcrEligibility.blockReason("IMAGE", false, false, true))
        assertEquals(OcrBlock.PROTECTED_LOCKED, OcrEligibility.blockReason("IMAGE", true, true, false))
        assertEquals(null, OcrEligibility.blockReason("IMAGE", true, true, true))
        assertEquals(null, OcrEligibility.blockReason("IMAGE", true, false, false))
        assertTrue(OcrEligibility.persistExtractedText(protected = false))
        assertTrue(!OcrEligibility.persistExtractedText(protected = true))
    }

    @Test
    fun extractorNormalizesRecognizerOutput() = kotlinx.coroutines.runBlocking {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val ocr = VaultImageOcr { RecognizerRaw.Text("  Gate\tA4 \r\n") }
        val result = ocr.extract(bitmap)
        assertEquals(OcrExtractResult.Text("Gate A4"), result)
        assertEquals(OcrExtractResult.NoText, VaultImageOcr { RecognizerRaw.Text("   ") }.extract(bitmap))
        assertEquals(OcrExtractResult.Unavailable, VaultImageOcr { RecognizerRaw.Unavailable }.extract(bitmap))
        assertEquals(OcrExtractResult.Failed, VaultImageOcr { RecognizerRaw.Failed }.extract(bitmap))
    }

    @Test
    fun recognizerClientConstructsWithoutClearcutClasses() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            MlKit.initialize(context)
        } catch (_: IllegalStateException) {
            // A previous test in this process already initialized ML Kit.
        }
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        client.close()
    }

    @Test
    fun clearcutLinkageStubDoesNotAdvertiseAnUploader() {
        val destination = CCTDestination.INSTANCE
        assertEquals("cct", destination.name)
        assertNotNull(destination.extras)
        assertEquals(0, destination.extras!!.size)
        val encodings = destination.supportedEncodings.map { it.name }.toSet()
        assertEquals(setOf("proto", "json"), encodings)
    }

    @Test
    fun exifRotationMapsToDegrees() {
        assertEquals(90, OcrBitmaps.rotationDegrees(ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(180, OcrBitmaps.rotationDegrees(ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(270, OcrBitmaps.rotationDegrees(ExifInterface.ORIENTATION_ROTATE_270))
        assertEquals(0, OcrBitmaps.rotationDegrees(ExifInterface.ORIENTATION_NORMAL))
        assertEquals(0, OcrBitmaps.rotationDegrees(0))
    }
}
