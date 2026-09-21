package com.nullverse.nullkeyai.diagnostics

import android.app.AlertDialog
import android.content.ComponentName
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.ime.NullKeyImeService

class ClipboardLabActivity : AppCompatActivity() {
    private lateinit var output: TextView
    private lateinit var runButton: Button
    private var consented = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clipboard_lab)
        output = findViewById(R.id.lab_output)
        runButton = findViewById(R.id.btn_run_clipboard_lab)
        runButton.setOnClickListener { requestConsentAndRun() }
    }

    private fun requestConsentAndRun() {
        if (consented) {
            runForegroundProbe()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.clipboard_lab_consent_title)
            .setMessage(R.string.clipboard_lab_consent_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.clipboard_lab_run) { _, _ ->
                consented = true
                runForegroundProbe()
            }
            .show()
    }

    private fun runForegroundProbe() {
        runButton.isEnabled = false
        output.text = getString(R.string.clipboard_lab_running)
        ClipboardCapabilityProbe(this).runForegroundProbe { r ->
            runOnUiThread {
                val latency = r.latencyMs?.toString()?.plus(" ms") ?: "n/a"
                val enabled = isNullKeyImeEnabled()
                val selected = isNullKeyDefaultIme()
                output.text = buildString {
                    appendLine("NullKey Clipboard Capability Report")
                    appendLine("Android API: ${r.sdkInt}")
                    appendLine("Target SDK: ${r.targetSdk}")
                    appendLine("Device: ${r.manufacturer} ${r.model}")
                    appendLine()
                    appendLine("IME state")
                    appendLine("NullKey enabled: ${yesNo(enabled)}")
                    appendLine("NullKey default IME: ${yesNo(selected)}")
                    appendLine()
                    appendLine("Foreground generated-token test")
                    appendLine("Change event: ${yesNo(r.changeEventReceived)}")
                    appendLine("Content readable: ${yesNo(r.contentReadable)}")
                    appendLine("Latency: $latency")
                    appendLine("Original clipboard restored: ${yesNo(r.restoredOriginalClip)}")
                    appendLine()
                    appendLine(r.note)
                    appendLine()
                    append("No existing clipboard text is displayed, logged, or persisted by this test.")
                }
                runButton.isEnabled = true
            }
        }
    }

    private fun isNullKeyImeEnabled(): Boolean {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val component = ComponentName(this, NullKeyImeService::class.java)
        return imm.enabledInputMethodList.any {
            ComponentName(it.packageName, it.serviceName) == component
        }
    }

    private fun isNullKeyDefaultIme(): Boolean {
        val selected = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: return false
        val component = ComponentName(this, NullKeyImeService::class.java)
        return selected == component.flattenToString() ||
            selected == component.flattenToShortString()
    }

    private fun yesNo(value: Boolean) = if (value) "YES" else "NO"
}
