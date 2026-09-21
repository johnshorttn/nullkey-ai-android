package com.nullverse.nullkeyai.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.max

/**
 * Decodes Vault image previews at a size that fits the on-screen ImageView
 * instead of allocating a full-resolution bitmap (which OOMs on large assets).
 */
object SampledBitmapDecoder {
    fun sampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        val targetWidth = max(1, reqWidth)
        val targetHeight = max(1, reqHeight)
        var inSampleSize = 1
        if (srcWidth > targetWidth || srcHeight > targetHeight) {
            val halfWidth = srcWidth / 2
            val halfHeight = srcHeight / 2
            while (halfWidth / inSampleSize >= targetWidth &&
                halfHeight / inSampleSize >= targetHeight
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /** Power-of-two sample size so the longer edge is at most about [maxEdge]. */
    fun sampleSizeForMaxEdge(srcWidth: Int, srcHeight: Int, maxEdge: Int): Int {
        val edge = max(srcWidth, srcHeight)
        val target = max(1, maxEdge)
        var sample = 1
        while (sample < 32 && edge / (sample * 2) >= target) {
            sample *= 2
        }
        return sample
    }

    fun previewBounds(resources: Resources, fallbackHeightDp: Int = 280): Pair<Int, Int> {
        val metrics = resources.displayMetrics
        val width = max(1, metrics.widthPixels)
        val height = max(1, (fallbackHeightDp * metrics.density).toInt())
        return width to height
    }

    fun decodeFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, opts)
    }

    fun decodeByteArray(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    fun decodeFileMaxEdge(path: String, maxEdge: Int): Bitmap? =
        decodeSampled(path = path, bytes = null, maxEdge = maxEdge)

    fun decodeByteArrayMaxEdge(bytes: ByteArray, maxEdge: Int): Bitmap? =
        decodeSampled(path = null, bytes = bytes, maxEdge = maxEdge)

    private fun decodeSampled(path: String?, bytes: ByteArray?, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (path != null) BitmapFactory.decodeFile(path, bounds) else {
            bytes ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeForMaxEdge(bounds.outWidth, bounds.outHeight, maxEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return if (path != null) {
            BitmapFactory.decodeFile(path, opts)
        } else {
            bytes ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }
    }
}
