package com.nullverse.nullkeyai.ui

import com.nullverse.nullkeyai.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ImeSetupStatusTest {

    @Test
    fun selectedWinsOverEnabled() {
        assertEquals(R.string.ime_status_selected, ImeSetupStatus.messageRes(enabled = true, selected = true))
    }

    @Test
    fun enabledButNotSelectedPromptsSwitch() {
        assertEquals(R.string.ime_status_enabled, ImeSetupStatus.messageRes(enabled = true, selected = false))
    }

    @Test
    fun disabledPromptsEnable() {
        assertEquals(R.string.ime_status_disabled, ImeSetupStatus.messageRes(enabled = false, selected = false))
    }
}
