package com.nullverse.nullkeyai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.nullverse.nullkeyai.ui.SampledBitmapDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SampledBitmapDecoderTest {

    @Test
    fun sampleSize_isOneWhenAlreadySmall() {
        assertEquals(1, SampledBitmapDecoder.sampleSize(100, 80, 200, 200))
    }

    @Test
    fun sampleSize_downscalesLargeImagesByPowersOfTwo() {
        assertEquals(8, SampledBitmapDecoder.sampleSize(2000, 1000, 200, 100))
        assertEquals(8, SampledBitmapDecoder.sampleSize(4000, 4000, 400, 400))
    }

    @Test
    fun sampleSizeForMaxEdge_shrinksTheLongSide() {
        assertEquals(1, SampledBitmapDecoder.sampleSizeForMaxEdge(100, 80, 1600))
        assertEquals(2, SampledBitmapDecoder.sampleSizeForMaxEdge(4000, 3000, 1600))
        assertEquals(4, SampledBitmapDecoder.sampleSizeForMaxEdge(8000, 500, 1600))
    }

    @Test
    fun decodeFile_returnsSmallerBitmapThanSource() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val file = File(context.cacheDir, "sampled-preview.png")
        file.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val decoded = SampledBitmapDecoder.decodeFile(file.absolutePath, 80, 60)
        assertNotNull(decoded)
        assertTrue(decoded!!.width < 640)
        assertTrue(decoded.height < 480)

        val full = BitmapFactory.decodeFile(file.absolutePath)
        assertEquals(640, full.width)
    }
}
