package com.nullverse.nullkeyai.ocr

import android.graphics.Bitmap

/**
 * On-device image text. The recognizer implementation may be unavailable on a
 * device; callers must show that honestly and must not fall back to a network
 * service. This app does not request INTERNET.
 */
object OcrTextNormalizer {
    const val MAX_CHARS = 20_000
    const val MAX_EDGE_PX = 1600

    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n')
            .lines()
            .map { it.replace(Regex("[ \\t]+"), " ").trim() }
        val joined = lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
        return if (joined.length <= MAX_CHARS) joined else joined.take(MAX_CHARS).trimEnd()
    }
}

enum class OcrBlock {
    NOT_IMAGE,
    MISSING_ASSET,
    PROTECTED_LOCKED,
}

object OcrEligibility {
    fun blockReason(
        contentType: String,
        hasReadableAsset: Boolean,
        protected: Boolean,
        unlocked: Boolean,
    ): OcrBlock? {
        if (contentType != "IMAGE") return OcrBlock.NOT_IMAGE
        if (!hasReadableAsset) return OcrBlock.MISSING_ASSET
        if (protected && !unlocked) return OcrBlock.PROTECTED_LOCKED
        return null
    }

    /** Protected rows keep OCR text in memory only so it is not stored in plaintext. */
    fun persistExtractedText(protected: Boolean): Boolean = !protected
}

sealed class RecognizerRaw {
    data class Text(val raw: String) : RecognizerRaw()
    data object Unavailable : RecognizerRaw()
    data object Failed : RecognizerRaw()
}

fun interface OnDeviceTextRecognizer {
    suspend fun recognize(bitmap: Bitmap): RecognizerRaw
}

sealed class OcrExtractResult {
    data class Text(val text: String) : OcrExtractResult()
    data object NoText : OcrExtractResult()
    data object Unavailable : OcrExtractResult()
    data object Failed : OcrExtractResult()
}

class VaultImageOcr(private val recognizer: OnDeviceTextRecognizer) {
    suspend fun extract(bitmap: Bitmap): OcrExtractResult {
        return when (val raw = recognizer.recognize(bitmap)) {
            is RecognizerRaw.Text -> {
                val text = OcrTextNormalizer.normalize(raw.raw)
                if (text.isEmpty()) OcrExtractResult.NoText else OcrExtractResult.Text(text)
            }
            RecognizerRaw.Unavailable -> OcrExtractResult.Unavailable
            RecognizerRaw.Failed -> OcrExtractResult.Failed
        }
    }
}
