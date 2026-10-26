package com.nullverse.nullkeyai.ime

import com.nullverse.nullkeyai.db.Clip
import com.nullverse.nullkeyai.db.ClipContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipPastePolicyTest {

    @Test
    fun commitsPlainText() {
        val decision = ClipPastePolicy.decide(Clip(content = "hello vault"))
        assertEquals(ClipPastePolicy.Decision.Commit("hello vault"), decision)
    }

    @Test
    fun blocksProtectedClipsSoCiphertextIsNotPasted() {
        val decision = ClipPastePolicy.decide(Clip(content = "nkenc:secret", protected = true))
        assertEquals(ClipPastePolicy.Decision.BlockProtected, decision)
    }

    @Test
    fun imageClipPastesExtractedText() {
        val decision = ClipPastePolicy.decide(
            Clip(
                content = "content://media/1",
                contentType = ClipContentType.IMAGE.name,
                ocrText = "gate A4",
            )
        )
        assertEquals(ClipPastePolicy.Decision.Commit("gate A4"), decision)
    }

    @Test
    fun skipsBlankContent() {
        assertTrue(ClipPastePolicy.decide(Clip(content = "   ")) is ClipPastePolicy.Decision.SkipEmpty)
    }
}
