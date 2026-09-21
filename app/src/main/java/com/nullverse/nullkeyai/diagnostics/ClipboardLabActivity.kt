package com.nullverse.nullkeyai.diagnostics

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.ui.ImeSetupStatus
import com.nullverse.nullkeyai.ui.SystemBarInsets

class ClipboardLabActivity : AppCompatActivity() {
    private lateinit var output: TextView
    private lateinit var runButton: Button
    private var consented = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clipboard_lab)
        SystemBarInsets.applyToActivity(this)
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
                output.text = ClipboardLabReport.format(
                    resources,
                    r,
                    imeEnabled = ImeSetupStatus.isEnabled(this),
                    imeSelected = ImeSetupStatus.isSelected(this),
                )
                runButton.isEnabled = true
            }
        }
    }

}
