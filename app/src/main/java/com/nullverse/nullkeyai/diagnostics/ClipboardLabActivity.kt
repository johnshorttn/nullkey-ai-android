package com.nullverse.nullkeyai.diagnostics

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nullverse.nullkeyai.R

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
        if (consented) { runForegroundProbe(); return }
        AlertDialog.Builder(this)
            .setTitle(R.string.clipboard_lab_consent_title)
            .setMessage(R.string.clipboard_lab_consent_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.clipboard_lab_run) { _, _ -> consented = true; runForegroundProbe() }
            .show()
    }

    private fun runForegroundProbe() {
        runButton.isEnabled = false
        output.text = getString(R.string.clipboard_lab_running)
        ClipboardCapabilityProbe(this).runForegroundProbe { r ->
            runOnUiThread {
                val latency = r.latencyMs?.toString()?.plus(" ms") ?: "n/a"
                output.text = "NullKey Clipboard Capability Report\n" +
                    "Android API: " + r.sdkInt + "\nDevice: " + r.manufacturer + " " + r.model + "\n\n" +
                    "Foreground generated-token test\n" +
                    "Change event: " + (if (r.changeEventReceived) "YES" else "NO") + "\n" +
                    "Content readable: " + (if (r.contentReadable) "YES" else "NO") + "\n" +
                    "Latency: " + latency + "\n" +
                    "Original clipboard restored: " + (if (r.restoredOriginalClip) "YES" else "NO/EMPTY") + "\n\n" +
                    r.note + "\n\nThis first slice tests only a generated value while NullKey is foreground."
                runButton.isEnabled = true
            }
        }
    }
}
