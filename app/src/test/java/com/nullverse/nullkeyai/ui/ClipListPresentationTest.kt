package com.nullverse.nullkeyai.ui

import com.nullverse.nullkeyai.db.Clip
import com.nullverse.nullkeyai.db.ClipCaptureMethod
import com.nullverse.nullkeyai.db.ClipContentType
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipListPresentationTest {

    private val copy = ClipListCopy(
        protectedClip = "Protected clip",
        imageFallback = "Image",
        fileFallback = "File",
        typeText = "Text",
        typeImage = "Image",
        typeFile = "File",
        typeUri = "Link",
        typeRich = "Rich text",
        badgePin = "Pinned",
        badgeProtected = "Protected",
        badgeNote = "Note",
        captureClipboard = "Clipboard",
        captureIme = "Keyboard",
        captureShare = "Share",
        captureManual = "Manual",
        captureOcr = "Scan",
        captureImport = "Import",
        captureRemote = "Remote",
        captureUnknown = "Unknown source",
    )

    @Test
    fun previewUsesLocalizedPlaceholdersForProtectedImageAndFile() {
        assertEquals(
            "Protected clip",
            ClipListPresentation.preview(Clip(content = "secret", protected = true), copy)
        )
        assertEquals(
            "Image",
            ClipListPresentation.preview(
                Clip(content = "content://x", contentType = ClipContentType.IMAGE.name),
                copy,
            )
        )
        assertEquals(
            "Vacation",
            ClipListPresentation.preview(
                Clip(content = "content://x", contentType = ClipContentType.IMAGE.name, notes = "Vacation"),
                copy,
            )
        )
        assertEquals(
            "application/pdf",
            ClipListPresentation.preview(
                Clip(
                    content = "content://file",
                    contentType = ClipContentType.FILE.name,
                    mimeType = "application/pdf",
                ),
                copy,
            )
        )
        assertEquals("hello", ClipListPresentation.preview(Clip(content = "hello"), copy))
        assertEquals(
            "Gate A4",
            ClipListPresentation.preview(
                Clip(
                    content = "content://x",
                    contentType = ClipContentType.IMAGE.name,
                    ocrText = "Gate A4\nseat 12",
                ),
                copy,
            )
        )
    }

    @Test
    fun metaMarksScannedText() {
        val clip = Clip(
            content = "content://x",
            contentType = ClipContentType.IMAGE.name,
            ocrText = "Gate A4",
        )
        assertEquals("Image • Scanned text", ClipListPresentation.meta(clip, copy))
    }

    @Test
    fun metaJoinsLocalizedTypeAndBadges() {
        val clip = Clip(
            content = "hello",
            pinned = true,
            protected = true,
            notes = "n",
            sourceAppLabel = "Messages",
        )
        assertEquals("Text • Pinned • Protected • Note • Messages", ClipListPresentation.meta(clip, copy))
    }

    @Test
    fun detailMetaLocalizesCaptureMethod() {
        val clip = Clip(
            content = "hello",
            mimeType = "text/plain",
            sourcePackage = "com.example",
            captureMethod = ClipCaptureMethod.MANUAL.name,
        )
        assertEquals("Text • text/plain • com.example • Manual", ClipListPresentation.detailMeta(clip, copy))
    }
}
