package com.nullverse.nullkeyai.ui

import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.db.DefaultTags
import com.nullverse.nullkeyai.db.Tag

/** Renders attached (removable) tags and suggested starter tags on clip detail. */
object TagChipUi {
    fun bind(
        attachedGroup: ChipGroup,
        emptyView: TextView,
        suggestedGroup: ChipGroup,
        attached: List<Tag>,
        onRemove: (Tag) -> Unit,
        onAddSuggested: (String) -> Unit,
        suggestedNames: List<String> = DefaultTags.NAMES
    ) {
        attachedGroup.removeAllViews()
        suggestedGroup.removeAllViews()
        if (attached.isEmpty()) {
            emptyView.visibility = View.VISIBLE
        } else {
            emptyView.visibility = View.GONE
            attached.forEach { tag ->
                attachedGroup.addView(removableChip(attachedGroup, tag, onRemove))
            }
        }
        val attachedNames = attached.map { it.name.lowercase() }.toSet()
        suggestedNames.filter { it.lowercase() !in attachedNames }.forEach { name ->
            suggestedGroup.addView(suggestedChip(suggestedGroup, name, onAddSuggested))
        }
        suggestedGroup.visibility =
            if (suggestedGroup.childCount == 0) View.GONE else View.VISIBLE
    }

    private fun removableChip(parent: ChipGroup, tag: Tag, onRemove: (Tag) -> Unit): Chip {
        return styledChip(parent, tag.name).apply {
            isCloseIconVisible = true
            contentDescription = parent.context.getString(R.string.remove_tag, tag.name)
            setOnCloseIconClickListener { onRemove(tag) }
            setOnClickListener { onRemove(tag) }
        }
    }

    private fun suggestedChip(parent: ChipGroup, name: String, onAdd: (String) -> Unit): Chip {
        return styledChip(parent, name).apply {
            isCloseIconVisible = false
            contentDescription = parent.context.getString(R.string.add_suggested_tag, name)
            setOnClickListener { onAdd(name) }
        }
    }

    private fun styledChip(parent: ChipGroup, label: String): Chip {
        val context = parent.context
        return Chip(context).apply {
            text = label
            isClickable = true
            isCheckable = false
            chipBackgroundColor = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.kb_key)
            )
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            closeIconTint = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.violet)
            )
        }
    }
}
