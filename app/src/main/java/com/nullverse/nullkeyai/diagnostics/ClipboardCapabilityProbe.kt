package com.nullverse.nullkeyai.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import java.util.UUID

data class ClipboardProbeResult(
    val sdkInt: Int, val manufacturer: String, val model: String,
    val changeEventReceived: Boolean, val contentReadable: Boolean,
    val latencyMs: Long?, val restoredOriginalClip: Boolean, val note: String
)

class ClipboardCapabilityProbe(private val context: Context) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun runForegroundProbe(onComplete: (ClipboardProbeResult) -> Unit) {
        val token = "NullKey-Test-" + UUID.randomUUID()
        val started = SystemClock.elapsedRealtime()
        val original = runCatching { clipboard.primaryClip }.getOrNull()
        var completed = false
        lateinit var listener: ClipboardManager.OnPrimaryClipChangedListener
        listener = ClipboardManager.OnPrimaryClipChangedListener {
            if (!completed) {
                val current = runCatching { clipboard.primaryClip }.getOrNull()
                val text = current?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                if (text == token) {
                    completed = true
                    clipboard.removePrimaryClipChangedListener(listener)
                    onComplete(result(true, true, SystemClock.elapsedRealtime() - started, restore(original), "Foreground generated-token probe"))
                }
            }
        }
        clipboard.addPrimaryClipChangedListener(listener)
        clipboard.setPrimaryClip(ClipData.newPlainText("NullKey diagnostic", token))
        android.os.Handler(context.mainLooper).postDelayed({
            if (!completed) {
                completed = true
                clipboard.removePrimaryClipChangedListener(listener)
                val current = runCatching { clipboard.primaryClip }.getOrNull()
                val text = current?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                onComplete(result(false, text == token, null, restore(original), "No matching change callback within 1500 ms"))
            }
        }, 1500L)
    }

    private fun result(event: Boolean, readable: Boolean, latency: Long?, restored: Boolean, note: String) =
        ClipboardProbeResult(Build.VERSION.SDK_INT, Build.MANUFACTURER, Build.MODEL, event, readable, latency, restored, note)

    private fun restore(original: ClipData?): Boolean {
        if (original == null) return false
        return runCatching { clipboard.setPrimaryClip(original); true }.getOrDefault(false)
    }
}
