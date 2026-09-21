package com.nullverse.nullkeyai.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.SystemClock
import java.util.UUID

data class ClipboardProbeResult(
    val sdkInt: Int,
    val targetSdk: Int,
    val manufacturer: String,
    val model: String,
    val changeEventReceived: Boolean,
    val contentReadable: Boolean,
    val latencyMs: Long?,
    val restoredOriginalClip: Boolean,
    val note: String
)

class ClipboardCapabilityProbe(private val context: Context) {
    private val clipboard =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun runForegroundProbe(onComplete: (ClipboardProbeResult) -> Unit) {
        val token = "NullKey-Test-" + UUID.randomUUID()
        val started = SystemClock.elapsedRealtime()

        // Held in memory only long enough to restore it. It is never logged or persisted.
        val original = runCatching { clipboard.primaryClip }.getOrNull()
        var completed = false

        lateinit var listener: ClipboardManager.OnPrimaryClipChangedListener
        listener = ClipboardManager.OnPrimaryClipChangedListener {
            if (completed) return@OnPrimaryClipChangedListener

            val current = runCatching { clipboard.primaryClip }.getOrNull()
            val text = current
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()

            if (text == token) {
                completed = true
                clipboard.removePrimaryClipChangedListener(listener)
                onComplete(
                    result(
                        event = true,
                        readable = true,
                        latency = SystemClock.elapsedRealtime() - started,
                        restored = restore(original),
                        note = context.getString(com.nullverse.nullkeyai.R.string.clipboard_lab_note_ok)
                    )
                )
            }
        }

        clipboard.addPrimaryClipChangedListener(listener)
        clipboard.setPrimaryClip(ClipData.newPlainText("NullKey diagnostic", token))

        android.os.Handler(context.mainLooper).postDelayed({
            if (!completed) {
                completed = true
                clipboard.removePrimaryClipChangedListener(listener)
                val current = runCatching { clipboard.primaryClip }.getOrNull()
                val text = current
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.coerceToText(context)
                    ?.toString()

                onComplete(
                    result(
                        event = false,
                        readable = text == token,
                        latency = null,
                        restored = restore(original),
                        note = context.getString(com.nullverse.nullkeyai.R.string.clipboard_lab_note_timeout)
                    )
                )
            }
        }, 1500L)
    }

    private fun result(
        event: Boolean,
        readable: Boolean,
        latency: Long?,
        restored: Boolean,
        note: String
    ) = ClipboardProbeResult(
        sdkInt = Build.VERSION.SDK_INT,
        targetSdk = context.applicationInfo.targetSdkVersion,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        changeEventReceived = event,
        contentReadable = readable,
        latencyMs = latency,
        restoredOriginalClip = restored,
        note = note
    )

    private fun restore(original: ClipData?): Boolean {
        if (original == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return runCatching {
                    clipboard.clearPrimaryClip()
                    true
                }.getOrDefault(false)
            }
            return false
        }

        return runCatching {
            clipboard.setPrimaryClip(original)
            true
        }.getOrDefault(false)
    }
}
