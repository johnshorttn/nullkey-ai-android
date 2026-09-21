package com.nullverse.nullkeyai

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.nullverse.nullkeyai.db.Tag
import com.nullverse.nullkeyai.ime.ClipAdapter
import com.nullverse.nullkeyai.ui.TagChipUi
import com.nullverse.nullkeyai.db.Clip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class VaultUiHardeningTest {

    @Test
    fun clipAdapter_showsRestoreOnlyWhenRequested() {
        val parent = FrameLayout(ApplicationProvider.getApplicationContext())
        val clip = Clip(id = 7, content = "trashed", trashedAt = 1L)

        val hidden = ClipAdapter(showRestore = false) {}
        hidden.submit(listOf(clip))
        val hiddenHolder = hidden.createViewHolder(parent, 0)
        hidden.bindViewHolder(hiddenHolder, 0)
        assertEquals(View.GONE, hiddenHolder.itemView.findViewById<View>(R.id.clip_restore).visibility)

        val shown = ClipAdapter(showRestore = true) {}
        shown.submit(listOf(clip))
        val shownHolder = shown.createViewHolder(parent, 0)
        shown.bindViewHolder(shownHolder, 0)
        assertEquals(View.VISIBLE, shownHolder.itemView.findViewById<View>(R.id.clip_restore).visibility)
    }

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
}
