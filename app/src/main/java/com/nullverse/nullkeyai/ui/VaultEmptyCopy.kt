package com.nullverse.nullkeyai.ui

import androidx.annotation.StringRes
import com.nullverse.nullkeyai.R

/** Chooses empty-state copy for the vault list and IME clip panel. */
object VaultEmptyCopy {
    @StringRes
    fun messageRes(query: String, filesOnly: Boolean): Int {
        val searching = query.isNotBlank()
        return when {
            searching && filesOnly -> R.string.no_file_search_results
            searching -> R.string.no_search_results
            filesOnly -> R.string.no_file_clips
            else -> R.string.no_clips
        }
    }
}
