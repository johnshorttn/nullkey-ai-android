package com.nullverse.nullkeyai.ocr

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.nullverse.nullkeyai.ui.SampledBitmapDecoder
import java.io.ByteArrayInputStream

/**
 * Decodes a Vault image small enough for on-device OCR and applies EXIF
 * orientation so sideways photos are not read rotated.
 */
object OcrBitmaps {
    fun decodeFile(path: String, maxEdge: Int = OcrTextNormalizer.MAX_EDGE_PX): Bitmap? {
        val bitmap = SampledBitmapDecoder.decodeFileMaxEdge(path, maxEdge) ?: return null
        val degrees = runCatching {
            rotationDegrees(ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED))
        }.getOrDefault(0)
        return rotate(bitmap, degrees)
    }

    fun decodeBytes(bytes: ByteArray, maxEdge: Int = OcrTextNormalizer.MAX_EDGE_PX): Bitmap? {
        val bitmap = SampledBitmapDecoder.decodeByteArrayMaxEdge(bytes, maxEdge) ?: return null
        val degrees = runCatching {
            val orientation = ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED,
            )
            rotationDegrees(orientation)
        }.getOrDefault(0)
        return rotate(bitmap, degrees)
    }

    /** Maps EXIF orientation constants to clockwise degrees. Unknown stays upright. */
    fun rotationDegrees(orientation: Int): Int = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90, 6 -> 90
        ExifInterface.ORIENTATION_ROTATE_180, 3 -> 180
        ExifInterface.ORIENTATION_ROTATE_270, 8 -> 270
        else -> 0
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }
}
