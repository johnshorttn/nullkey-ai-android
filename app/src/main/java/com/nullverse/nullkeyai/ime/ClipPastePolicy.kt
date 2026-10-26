package com.nullverse.nullkeyai.ime

import com.nullverse.nullkeyai.db.Clip
import com.nullverse.nullkeyai.db.ClipContentType

/**
 * Decides whether tapping a vault clip in the IME should commit text.
 * Protected rows stay locked; empty or asset-only rows do not dump ciphertext
 * or placeholder URIs into the editor.
 */
object ClipPastePolicy {
    sealed class Decision {
        data class Commit(val text: String) : Decision()
        data object BlockProtected : Decision()
        data object SkipEmpty : Decision()
    }

    fun decide(clip: Clip): Decision {
        if (clip.protected) return Decision.BlockProtected
        val ocr = clip.ocrText?.trim().orEmpty()
        if (ocr.isNotEmpty() && isAsset(clip)) return Decision.Commit(ocr)
        val text = clip.content.trim()
        if (text.isEmpty()) return Decision.SkipEmpty
        return Decision.Commit(clip.content)
    }

    private fun isAsset(clip: Clip): Boolean =
        clip.contentType == ClipContentType.IMAGE.name || clip.contentType == ClipContentType.FILE.name
}
