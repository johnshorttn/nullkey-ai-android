package com.nullverse.nullkeyai.ui

import android.content.Context
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.db.Clip
import com.nullverse.nullkeyai.db.ClipCaptureMethod
import com.nullverse.nullkeyai.db.ClipContentType

/**
 * Localized preview and meta lines for vault rows and clip detail. Kept free of
 * Android view types so presentation can be unit-tested.
 */
data class ClipListCopy(
    val protectedClip: String,
    val imageFallback: String,
    val fileFallback: String,
    val typeText: String,
    val typeImage: String,
    val typeFile: String,
    val typeUri: String,
    val typeRich: String,
    val badgePin: String,
    val badgeProtected: String,
    val badgeNote: String,
    val captureClipboard: String,
    val captureIme: String,
    val captureShare: String,
    val captureManual: String,
    val captureOcr: String,
    val captureImport: String,
    val captureRemote: String,
    val captureUnknown: String,
    val badgeOcr: String = "Scanned text",
    val separator: String = " • ",
) {
    companion object {
        fun from(context: Context): ClipListCopy = ClipListCopy(
            protectedClip = context.getString(R.string.protected_clip),
            imageFallback = context.getString(R.string.content_type_image),
            fileFallback = context.getString(R.string.content_type_file),
            typeText = context.getString(R.string.content_type_text),
            typeImage = context.getString(R.string.content_type_image),
            typeFile = context.getString(R.string.content_type_file),
            typeUri = context.getString(R.string.content_type_uri),
            typeRich = context.getString(R.string.content_type_rich),
            badgePin = context.getString(R.string.clip_badge_pin),
            badgeProtected = context.getString(R.string.clip_badge_protected),
            badgeNote = context.getString(R.string.clip_badge_note),
            captureClipboard = context.getString(R.string.capture_method_clipboard),
            captureIme = context.getString(R.string.capture_method_ime),
            captureShare = context.getString(R.string.capture_method_share),
            captureManual = context.getString(R.string.capture_method_manual),
            captureOcr = context.getString(R.string.capture_method_ocr),
            captureImport = context.getString(R.string.capture_method_import),
            captureRemote = context.getString(R.string.capture_method_remote),
            captureUnknown = context.getString(R.string.capture_method_unknown),
            badgeOcr = context.getString(R.string.clip_badge_ocr),
        )
    }

    fun typeLabel(contentType: String): String = when (contentType) {
        ClipContentType.IMAGE.name -> typeImage
        ClipContentType.FILE.name -> typeFile
        ClipContentType.URI.name -> typeUri
        ClipContentType.RICH.name -> typeRich
        else -> typeText
    }

    fun captureLabel(captureMethod: String): String = when (captureMethod) {
        ClipCaptureMethod.CLIPBOARD.name -> captureClipboard
        ClipCaptureMethod.IME.name -> captureIme
        ClipCaptureMethod.SHARE.name -> captureShare
        ClipCaptureMethod.MANUAL.name -> captureManual
        ClipCaptureMethod.OCR.name -> captureOcr
        ClipCaptureMethod.IMPORT.name -> captureImport
        ClipCaptureMethod.REMOTE.name -> captureRemote
        else -> captureUnknown
    }
}

object ClipListPresentation {
    fun preview(clip: Clip, copy: ClipListCopy): String = when {
        clip.protected -> copy.protectedClip
        clip.contentType == ClipContentType.IMAGE.name ->
            clip.notes.ifBlank {
                clip.ocrText?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.take(80)
                    ?: copy.imageFallback
            }
        clip.contentType == ClipContentType.FILE.name ->
            clip.notes.ifBlank { clip.mimeType ?: copy.fileFallback }
        else -> clip.content
    }

    fun meta(clip: Clip, copy: ClipListCopy): String {
        val parts = mutableListOf(copy.typeLabel(clip.contentType))
        if (clip.pinned) parts += copy.badgePin
        if (clip.protected) parts += copy.badgeProtected
        if (clip.notes.isNotBlank() && clip.contentType == ClipContentType.TEXT.name) {
            parts += copy.badgeNote
        }
        if (!clip.protected && !clip.ocrText.isNullOrBlank()) {
            parts += copy.badgeOcr
        }
        clip.sourceAppLabel?.takeIf { it.isNotBlank() }?.let { parts += it }
            ?: clip.sourcePackage?.takeIf { it.isNotBlank() }?.let { parts += it }
        return parts.joinToString(copy.separator)
    }

    fun detailMeta(clip: Clip, copy: ClipListCopy): String {
        val parts = mutableListOf(copy.typeLabel(clip.contentType))
        clip.mimeType?.takeIf { it.isNotBlank() }?.let { parts += it }
        clip.sourceAppLabel?.takeIf { it.isNotBlank() }?.let { parts += it }
            ?: clip.sourcePackage?.takeIf { it.isNotBlank() }?.let { parts += it }
        parts += copy.captureLabel(clip.captureMethod)
        return parts.joinToString(copy.separator)
    }
}
