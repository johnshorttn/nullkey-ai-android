package com.nullverse.nullkeyai

import android.view.View
import android.widget.TextView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.nullverse.nullkeyai.db.Tag
import com.nullverse.nullkeyai.ui.TagChipUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class VaultUiHardeningTest {

    @Test
    fun tagChips_renderRemovableAttachedAndSuggestedDefaults() {
        val context = RuntimeEnvironment.getApplication()
        val themed = android.view.ContextThemeWrapper(context, R.style.Theme_NullKey)
        val attachedGroup = ChipGroup(themed)
        val suggestedGroup = ChipGroup(themed)
        val empty = TextView(themed)
        var removed: Tag? = null
        var added: String? = null

        TagChipUi.bind(
            attachedGroup = attachedGroup,
            emptyView = empty,
            suggestedGroup = suggestedGroup,
            attached = listOf(Tag(id = 1, name = "Work")),
            onRemove = { removed = it },
            onAddSuggested = { added = it }
        )

        assertEquals(View.GONE, empty.visibility)
        assertEquals(1, attachedGroup.childCount)
        val attachedChip = attachedGroup.getChildAt(0) as Chip
        assertEquals("Work", attachedChip.text.toString())
        attachedChip.performClick()
        assertEquals("Work", removed?.name)

        val suggested = (0 until suggestedGroup.childCount)
            .map { (suggestedGroup.getChildAt(it) as Chip).text.toString() }
            .toSet()
        assertEquals(setOf("Personal", "Coding"), suggested)
        (suggestedGroup.getChildAt(0) as Chip).performClick()
        assertTrue(added == "Personal" || added == "Coding")
    }

    @Test
    fun vaultHomeLayoutIsScrollableSoInsetsDoNotHideSetupControls() {
        val context = android.view.ContextThemeWrapper(
            RuntimeEnvironment.getApplication(),
            R.style.Theme_NullKey
        )
        val root = android.view.LayoutInflater.from(context)
            .inflate(R.layout.activity_main, null)
        assertTrue(root is android.widget.ScrollView)
        assertEquals(R.id.main_scroll, root.id)
        assertTrue(root.findViewById<android.view.View>(R.id.search) != null)
        assertTrue(root.findViewById<android.view.View>(R.id.files_only) != null)
        assertTrue(root.findViewById<android.view.View>(R.id.ime_status) != null)
        assertTrue(root.findViewById<android.view.View>(R.id.tagline) != null)
        assertTrue(root.findViewById<android.view.View>(R.id.btn_start_monitor) != null)
        assertTrue(root.findViewById<android.view.View>(R.id.swipe_typing_enabled) != null)
        assertTrue(root.findViewById<android.view.View>(R.id.btn_swipe_left_action) != null)
        assertTrue(root.findViewById<android.view.View>(R.id.btn_swipe_right_action) != null)
        assertTrue(root.findViewById<View>(R.id.btn_keyboard_theme) != null)
        assertTrue(root.findViewById<View>(R.id.keyboard_height_seek) != null)
        assertTrue(root.findViewById<View>(R.id.haptics_enabled) != null)
        assertTrue(root.findViewById<View>(R.id.key_sound_enabled) != null)
        assertTrue(root.findViewById<View>(R.id.long_press_seek) != null)
        assertTrue(root.findViewById<View>(R.id.incognito_enabled) != null)
        assertTrue(root.findViewById<View>(R.id.developer_options_enabled) != null)
        assertTrue(root.findViewById<View>(R.id.btn_clipboard_lab) != null)
        assertEquals(View.GONE, root.findViewById<View>(R.id.btn_clipboard_lab).visibility)
    }

    @Test
    fun imeClipPanelExposesEmptyState() {
        val context = android.view.ContextThemeWrapper(
            RuntimeEnvironment.getApplication(),
            R.style.Theme_NullKey
        )
        val root = android.view.LayoutInflater.from(context)
            .inflate(R.layout.keyboard, null)
        assertTrue(root.findViewById<android.view.View>(R.id.clips_empty) != null)
        assertTrue(root.findViewById<android.view.View>(R.id.clips_list) != null)
        assertEquals(View.GONE, root.findViewById<View>(R.id.vault_panel).visibility)
        val tools = root.findViewById<android.widget.ImageButton>(R.id.keyboard_tools)
        assertEquals(View.VISIBLE, tools.visibility)
        assertEquals(context.getString(R.string.keyboard_tools_open), tools.contentDescription.toString())
    }
}
