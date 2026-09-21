package com.nullverse.nullkeyai.diagnostics

import android.content.res.Resources
import com.nullverse.nullkeyai.R

object ClipboardLabReport {
    fun format(
        resources: Resources,
        result: ClipboardProbeResult,
        imeEnabled: Boolean,
        imeSelected: Boolean,
    ): String {
        val latency = result.latencyMs
            ?.let { resources.getString(R.string.clipboard_lab_latency_ms, it) }
            ?: resources.getString(R.string.clipboard_lab_latency_na)
        return buildString {
            appendLine(resources.getString(R.string.clipboard_lab_report_title))
            appendLine(resources.getString(R.string.clipboard_lab_report_api, result.sdkInt))
            appendLine(resources.getString(R.string.clipboard_lab_report_target_sdk, result.targetSdk))
            appendLine(
                resources.getString(
                    R.string.clipboard_lab_report_device,
                    result.manufacturer,
                    result.model,
                )
            )
            appendLine()
            appendLine(resources.getString(R.string.clipboard_lab_report_ime_section))
            appendLine(
                resources.getString(
                    R.string.clipboard_lab_report_ime_enabled,
                    yesNo(resources, imeEnabled),
                )
            )
            appendLine(
                resources.getString(
                    R.string.clipboard_lab_report_ime_selected,
                    yesNo(resources, imeSelected),
                )
            )
            appendLine()
            appendLine(resources.getString(R.string.clipboard_lab_report_probe_section))
            appendLine(
                resources.getString(
                    R.string.clipboard_lab_report_change_event,
                    yesNo(resources, result.changeEventReceived),
                )
            )
            appendLine(
                resources.getString(
                    R.string.clipboard_lab_report_readable,
                    yesNo(resources, result.contentReadable),
                )
            )
            appendLine(resources.getString(R.string.clipboard_lab_report_latency, latency))
            appendLine(
                resources.getString(
                    R.string.clipboard_lab_report_restored,
                    yesNo(resources, result.restoredOriginalClip),
                )
            )
            appendLine()
            appendLine(result.note)
            appendLine()
            append(resources.getString(R.string.clipboard_lab_report_footer))
        }
    }

    fun yesNo(resources: Resources, value: Boolean): String =
        resources.getString(if (value) R.string.clipboard_lab_yes else R.string.clipboard_lab_no)
}
