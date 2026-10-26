package com.nullverse.nullkeyai.ui

import android.os.Bundle
import android.app.AlertDialog
import android.content.Intent
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.content.FileProvider
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.clipboard.ClipCaptureRequest
import com.nullverse.nullkeyai.clipboard.ClipRepository
import com.nullverse.nullkeyai.clipboard.VaultAssetStore
import com.nullverse.nullkeyai.db.Clip
import com.nullverse.nullkeyai.db.ClipCaptureMethod
import com.nullverse.nullkeyai.db.ClipContentType
import com.nullverse.nullkeyai.db.ClipSourceConfidence
import com.nullverse.nullkeyai.db.NullKeyDatabase
import com.nullverse.nullkeyai.ocr.MlKitOnDeviceTextRecognizer
import com.nullverse.nullkeyai.ocr.OcrBitmaps
import com.nullverse.nullkeyai.ocr.OcrBlock
import com.nullverse.nullkeyai.ocr.OcrEligibility
import com.nullverse.nullkeyai.ocr.OcrExtractResult
import com.nullverse.nullkeyai.ocr.VaultImageOcr
import com.nullverse.nullkeyai.security.VaultCrypto
import com.nullverse.nullkeyai.sync.DeviceIdentity
import com.nullverse.nullkeyai.writing.BundledSpellingDictionary
import com.nullverse.nullkeyai.writing.WritingAssistant
import com.nullverse.nullkeyai.writing.WritingFixes
import com.nullverse.nullkeyai.writing.WritingReplacement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClipDetailActivity : AppCompatActivity() {
    private lateinit var repository: ClipRepository
    private var clipId: Long = 0L
    private lateinit var assetStore: VaultAssetStore
    private lateinit var vaultCrypto: VaultCrypto
    private var initiallyProtected = false
    private var protectedUnlocked = false
    private var loadedContentType: String = ClipContentType.TEXT.name
    private var editedContent: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clip_detail)
        SystemBarInsets.applyToActivity(this)
        val db = NullKeyDatabase.get(this)
        assetStore = VaultAssetStore(this)
        vaultCrypto = VaultCrypto()
        repository = ClipRepository(db.clipDao(), assetStore, db.tagDao(), vaultCrypto, DeviceIdentity.from(this))
        clipId = intent.getLongExtra(EXTRA_CLIP_ID, 0L)
        if (clipId == 0L) { finish(); return }

        lifecycleScope.launch {
            val clip = db.clipDao().byId(clipId) ?: run { finish(); return@launch }
            initiallyProtected = clip.protected
            protectedUnlocked = !clip.protected
            val contentView = findViewById<TextView>(R.id.detail_content)
            val imageView = findViewById<ImageView>(R.id.detail_image)
            val assetStatus = findViewById<TextView>(R.id.detail_asset_status)
            val asset = assetStore.resolve(clip.localAssetPath)
            val (previewWidth, previewHeight) = SampledBitmapDecoder.previewBounds(resources)
            if (!clip.protected && clip.contentType == "IMAGE" && asset != null) {
                val bitmap = runCatching {
                    SampledBitmapDecoder.decodeFile(asset.absolutePath, previewWidth, previewHeight)
                }.getOrNull()
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    imageView.visibility = View.VISIBLE
                    contentView.visibility = View.GONE
                }
            }
            if (clip.localAssetPath != null) {
                assetStatus.visibility = View.VISIBLE
                assetStatus.text = if (asset != null) getString(R.string.asset_saved_private)
                    else getString(R.string.asset_unavailable)
            }
            if (!clip.protected && asset != null) {
                findViewById<View>(R.id.detail_asset_actions).visibility = View.VISIBLE
                val uri = FileProvider.getUriForFile(this@ClipDetailActivity, "${packageName}.vault.files", asset)
                findViewById<Button>(R.id.detail_open_asset).setOnClickListener {
                    launchExternal(Intent(Intent.ACTION_VIEW).setDataAndType(uri, clip.mimeType ?: "*/*"))
                }
                findViewById<Button>(R.id.detail_share_asset).setOnClickListener {
                    launchExternal(Intent(Intent.ACTION_SEND).apply {
                        type = clip.mimeType ?: "application/octet-stream"
                        putExtra(Intent.EXTRA_STREAM, uri)
                    })
                }
            }
            contentView.text = if (clip.protected) getString(R.string.protected_clip) else clip.content
            val unlock = findViewById<Button>(R.id.detail_unlock)
            if (clip.protected) {
                unlock.visibility = View.VISIBLE
                unlock.setOnClickListener { authenticateAndReveal() }
            }
            findViewById<TextView>(R.id.detail_meta).text =
                ClipListPresentation.detailMeta(clip, ClipListCopy.from(this@ClipDetailActivity))
            findViewById<EditText>(R.id.detail_notes).apply {
                setText(if (clip.protected) "" else clip.notes)
                isEnabled = !clip.protected
            }
            findViewById<CheckBox>(R.id.detail_pinned).isChecked = clip.pinned
            findViewById<CheckBox>(R.id.detail_protected).apply {
                isChecked = clip.protected
                isEnabled = !clip.protected
            }
            loadedContentType = clip.contentType
            bindOcrAndWriting(clip, unlocked = !clip.protected)
            refreshTags()
        }

        findViewById<Button>(R.id.detail_save).setOnClickListener {
            lifecycleScope.launch {
                val wantsProtected = findViewById<CheckBox>(R.id.detail_protected).isChecked
                val pinned = findViewById<CheckBox>(R.id.detail_pinned).isChecked
                val notes = findViewById<EditText>(R.id.detail_notes).text.toString()
                val saved = runCatching {
                    if (initiallyProtected) {
                        repository.setPinned(clipId, pinned)
                        if (protectedUnlocked && !wantsProtected) {
                            // Decrypt the row first; only then persist the revealed notes as plaintext.
                            repository.setProtected(clipId, false)
                            repository.setNotes(clipId, notes)
                            editedContent?.let { repository.setContent(clipId, it) }
                            persistOcrField()
                        }
                        // If protection remains enabled, never write the revealed notes back to the DB.
                    } else {
                        repository.setNotes(clipId, notes)
                        editedContent?.let { repository.setContent(clipId, it) }
                        persistOcrField()
                        repository.setPinned(clipId, pinned)
                        if (wantsProtected) repository.setProtected(clipId, true)
                    }
                }.isSuccess
                if (saved) {
                    Toast.makeText(this@ClipDetailActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@ClipDetailActivity, R.string.action_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
        findViewById<Button>(R.id.detail_add_tag).setOnClickListener {
            lifecycleScope.launch {
                val input = findViewById<EditText>(R.id.detail_new_tag)
                repository.addTag(clipId, input.text.toString())
                refreshTags()
                input.text.clear()
            }
        }
        findViewById<Button>(R.id.detail_trash).setOnClickListener {
            lifecycleScope.launch { repository.moveToTrash(clipId); finish() }
        }
    }

    private suspend fun refreshTags() {
        TagChipUi.bind(
            attachedGroup = findViewById(R.id.detail_tags),
            emptyView = findViewById(R.id.detail_no_tags),
            suggestedGroup = findViewById(R.id.detail_suggested_tags),
            attached = repository.tagsForClip(clipId),
            onRemove = { tag ->
                lifecycleScope.launch {
                    repository.removeTag(clipId, tag.id)
                    refreshTags()
                }
            },
            onAddSuggested = { name ->
                lifecycleScope.launch {
                    repository.addTag(clipId, name)
                    refreshTags()
                }
            }
        )
    }

    private fun authenticateAndReveal() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    lifecycleScope.launch {
                        val clip = runCatching { repository.revealed(clipId) }.getOrNull()
                        if (clip == null) {
                            Toast.makeText(this@ClipDetailActivity, R.string.unlock_failed, Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        findViewById<TextView>(R.id.detail_content).apply {
                            text = clip.content
                            visibility = View.VISIBLE
                        }
                        findViewById<EditText>(R.id.detail_notes).apply {
                            setText(clip.notes)
                            // Protected notes remain read-only until the user explicitly unprotects.
                            isEnabled = false
                        }
                        protectedUnlocked = true
                        loadedContentType = clip.contentType
                        bindOcrAndWriting(clip, unlocked = true)
                        findViewById<CheckBox>(R.id.detail_protected).isEnabled = true
                        findViewById<Button>(R.id.detail_unlock).visibility = View.GONE
                        val asset = assetStore.resolve(clip.localAssetPath)
                        if (clip.contentType == "IMAGE" && asset != null) {
                            val (previewWidth, previewHeight) = SampledBitmapDecoder.previewBounds(resources)
                            val bitmap = runCatching {
                                if (vaultCrypto.isEncryptedFile(asset)) {
                                    val bytes = vaultCrypto.decryptFile(asset)
                                    SampledBitmapDecoder.decodeByteArray(bytes, previewWidth, previewHeight)
                                } else SampledBitmapDecoder.decodeFile(asset.absolutePath, previewWidth, previewHeight)
                            }.getOrNull()
                            bitmap?.let {
                                findViewById<ImageView>(R.id.detail_image).apply {
                                    setImageBitmap(it)
                                    visibility = View.VISIBLE
                                }
                            }
                        }
                    }
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.unlock_title))
                .setSubtitle(getString(R.string.unlock_subtitle))
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    private fun bindOcrAndWriting(clip: Clip, unlocked: Boolean) {
        val extract = findViewById<Button>(R.id.detail_extract_text)
        val status = findViewById<TextView>(R.id.detail_ocr_status)
        val ocrField = findViewById<EditText>(R.id.detail_ocr)
        val saveText = findViewById<Button>(R.id.detail_save_ocr_clip)
        val check = findViewById<Button>(R.id.detail_check_writing)
        val asset = assetStore.resolve(clip.localAssetPath)
        val block = OcrEligibility.blockReason(
            contentType = clip.contentType,
            hasReadableAsset = asset != null,
            protected = clip.protected,
            unlocked = unlocked,
        )
        extract.visibility = if (block == null) View.VISIBLE else View.GONE
        extract.setOnClickListener {
            if (asset == null) return@setOnClickListener
            extractText(clip, asset, extract, status, ocrField, saveText)
        }
        val stored = clip.ocrText?.takeIf { it.isNotBlank() && !clip.protected }
        if (stored != null) {
            ocrField.setText(stored)
            ocrField.visibility = View.VISIBLE
            saveText.visibility = View.VISIBLE
            status.visibility = View.VISIBLE
            status.setText(R.string.ocr_review_hint)
        }
        saveText.setOnClickListener { saveOcrAsTextClip(ocrField.text?.toString().orEmpty()) }
        val canCheck = when (clip.contentType) {
            ClipContentType.TEXT.name -> !clip.protected || unlocked
            ClipContentType.IMAGE.name -> block == null || stored != null || ocrField.visibility == View.VISIBLE
            else -> false
        }
        check.visibility = if (canCheck) View.VISIBLE else View.GONE
        check.setOnClickListener { checkWriting(ocrField) }
        if (block == OcrBlock.MISSING_ASSET && clip.contentType == ClipContentType.IMAGE.name) {
            status.visibility = View.VISIBLE
            status.setText(R.string.extract_text_missing_asset)
        }
    }

    private fun extractText(
        clip: Clip,
        asset: java.io.File,
        extract: Button,
        status: TextView,
        ocrField: EditText,
        saveText: Button,
    ) {
        extract.isEnabled = false
        status.visibility = View.VISIBLE
        status.setText(R.string.extract_text_working)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val bitmap = runCatching {
                    if (clip.protected && vaultCrypto.isEncryptedFile(asset)) {
                        OcrBitmaps.decodeBytes(vaultCrypto.decryptFile(asset))
                    } else {
                        OcrBitmaps.decodeFile(asset.absolutePath)
                    }
                }.getOrNull()
                if (bitmap == null) {
                    OcrExtractResult.Failed
                } else {
                    try {
                        VaultImageOcr(MlKitOnDeviceTextRecognizer(this@ClipDetailActivity)).extract(bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            extract.isEnabled = true
            when (result) {
                is OcrExtractResult.Text -> {
                    ocrField.setText(result.text)
                    ocrField.visibility = View.VISIBLE
                    saveText.visibility = View.VISIBLE
                    findViewById<Button>(R.id.detail_check_writing).visibility = View.VISIBLE
                    if (OcrEligibility.persistExtractedText(clip.protected)) {
                        repository.setOcrText(clipId, result.text)
                        status.setText(R.string.ocr_review_hint)
                    } else {
                        status.setText(R.string.extract_text_not_saved_protected)
                    }
                }
                OcrExtractResult.NoText -> status.setText(R.string.extract_text_empty)
                OcrExtractResult.Unavailable -> status.setText(R.string.extract_text_unavailable)
                OcrExtractResult.Failed -> status.setText(R.string.extract_text_failed)
            }
        }
    }

    private fun saveOcrAsTextClip(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.extract_text_empty, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val id = repository.capture(
                ClipCaptureRequest(
                    content = text,
                    captureMethod = ClipCaptureMethod.OCR,
                    sourceConfidence = ClipSourceConfidence.INFERRED,
                )
            )
            val message = if (id != null) R.string.ocr_saved_clip else R.string.action_failed
            Toast.makeText(this@ClipDetailActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun persistOcrField() {
        val ocrField = findViewById<EditText>(R.id.detail_ocr)
        if (ocrField.visibility != View.VISIBLE) return
        repository.setOcrText(clipId, ocrField.text?.toString())
    }

    private fun checkWriting(ocrField: EditText) {
        val source = when {
            loadedContentType == ClipContentType.IMAGE.name && ocrField.visibility == View.VISIBLE ->
                ocrField.text?.toString().orEmpty()
            loadedContentType == ClipContentType.TEXT.name && (!initiallyProtected || protectedUnlocked) ->
                editedContent ?: findViewById<TextView>(R.id.detail_content).text?.toString().orEmpty()
            else -> {
                Toast.makeText(this, R.string.writing_nothing_to_check, Toast.LENGTH_SHORT).show()
                return
            }
        }
        if (source.isBlank() || source == getString(R.string.protected_clip)) {
            Toast.makeText(this, R.string.writing_nothing_to_check, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val assistant = withContext(Dispatchers.IO) {
                WritingAssistant(BundledSpellingDictionary.get(this@ClipDetailActivity))
            }
            val fixes = WritingFixes.from(source, assistant.review(source))
            if (fixes.isEmpty()) {
                Toast.makeText(this@ClipDetailActivity, R.string.writing_no_issues, Toast.LENGTH_SHORT).show()
                return@launch
            }
            AlertDialog.Builder(this@ClipDetailActivity)
                .setTitle(R.string.writing_check_button)
                .setItems(fixes.map { it.label }.toTypedArray()) { _, which ->
                    val fix = fixes[which]
                    val revised = WritingReplacement.apply(source, fix.issue, fix.suggestion)
                    applyWritingRevision(revised, ocrField)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun applyWritingRevision(revised: String, ocrField: EditText) {
        if (loadedContentType == ClipContentType.IMAGE.name) {
            ocrField.setText(revised)
            if (!initiallyProtected) {
                lifecycleScope.launch { repository.setOcrText(clipId, revised) }
            }
        } else {
            findViewById<TextView>(R.id.detail_content).text = revised
            editedContent = revised
            if (!initiallyProtected) {
                lifecycleScope.launch { repository.setContent(clipId, revised) }
            }
        }
        Toast.makeText(this, R.string.writing_applied, Toast.LENGTH_SHORT).show()
    }

    private fun launchExternal(intent: Intent) {
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(Intent.createChooser(intent, null)) }
            .onFailure { Toast.makeText(this, R.string.no_app_for_asset, Toast.LENGTH_SHORT).show() }
    }

    companion object { const val EXTRA_CLIP_ID = "clip_id" }
}
