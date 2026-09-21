package com.nullverse.nullkeyai.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.common.MlKit
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Latin-script text recognition using the model bundled in the APK
 * (`libmlkit_google_ocr_pipeline.so` via `text-recognition` 16.0.1).
 * The unbundled Play Services download path is not used. ML Kit's optional
 * Clearcut uploader is excluded from the build, and INTERNET is stripped
 * from the merged manifest, so recognition cannot fall back to the network.
 *
 * The auto-init provider is removed. [MlKit.initialize] runs on first use so
 * unit tests and ordinary app start do not load the native pipeline.
 */
class MlKitOnDeviceTextRecognizer(
    context: Context,
) : OnDeviceTextRecognizer {
    private val appContext = context.applicationContext

    override suspend fun recognize(bitmap: Bitmap): RecognizerRaw {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return RecognizerRaw.Failed
        }
        return try {
            ensureInitialized()
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val text = suspendCancellableCoroutine { cont ->
                    recognizer.process(image)
                        .addOnSuccessListener { result ->
                            if (cont.isActive) cont.resume(result.text.orEmpty())
                        }
                        .addOnFailureListener { error ->
                            if (cont.isActive) cont.resumeWithException(error)
                        }
                }
                RecognizerRaw.Text(text)
            } finally {
                recognizer.close()
            }
        } catch (error: Throwable) {
            when {
                isUnavailable(error) -> RecognizerRaw.Unavailable
                else -> RecognizerRaw.Failed
            }
        }
    }

    private fun ensureInitialized() {
        try {
            MlKit.initialize(appContext)
        } catch (already: IllegalStateException) {
            // A previous OCR call already initialized ML Kit in this process.
        }
    }

    private fun isUnavailable(error: Throwable): Boolean {
        if (error is UnsatisfiedLinkError || error is LinkageError || error is ClassNotFoundException) {
            return true
        }
        if (error is MlKitException) {
            return error.errorCode == MlKitException.UNAVAILABLE ||
                error.errorCode == MlKitException.NOT_FOUND
        }
        return false
    }
}
