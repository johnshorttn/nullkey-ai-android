package com.nullverse.nullkeyai.ui

import android.text.Editable
import android.text.TextWatcher

/** Minimal [TextWatcher] that only cares about the final text. */
class SimpleWatcher(private val onChanged: () -> Unit) : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: Editable?) = onChanged()
}
