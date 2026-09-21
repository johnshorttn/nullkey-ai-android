package com.nullverse.nullkeyai.ui

import android.os.Bundle
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
import com.nullverse.nullkeyai.clipboard.ClipRepository
import com.nullverse.nullkeyai.clipboard.VaultAssetStore
import com.nullverse.nullkeyai.db.NullKeyDatabase
import com.nullverse.nullkeyai.security.VaultCrypto
import com.nullverse.nullkeyai.sync.DeviceIdentity
import kotlinx.coroutines.launch

class ClipDetailActivity : AppCompatActivity() {
    private lateinit var repository: ClipRepository
    private var clipId: Long = 0L
    private lateinit var assetStore: VaultAssetStore
    private lateinit var vaultCrypto: VaultCrypto
    private var initiallyProtected = false
    private var protectedUnlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clip_detail)
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
            findViewById<TextView>(R.id.detail_meta).text = buildString {
                append(clip.contentType)
                clip.mimeType?.let { append(" • ").append(it) }
                clip.sourceAppLabel?.let { append(" • ").append(it) }
                    ?: clip.sourcePackage?.let { append(" • ").append(it) }
                append(" • ").append(clip.captureMethod)
            }
            findViewById<EditText>(R.id.detail_notes).apply {
                setText(if (clip.protected) "" else clip.notes)
                isEnabled = !clip.protected
            }
            findViewById<CheckBox>(R.id.detail_pinned).isChecked = clip.pinned
            findViewById<CheckBox>(R.id.detail_protected).apply {
                isChecked = clip.protected
                isEnabled = !clip.protected
            }
            refreshTags()
        }

        findViewById<Button>(R.id.detail_save).setOnClickListener {
            lifecycleScope.launch {
                val wantsProtected = findViewById<CheckBox>(R.id.detail_protected).isChecked
                val pinned = findViewById<CheckBox>(R.id.detail_pinned).isChecked
                val notes = findViewById<EditText>(R.id.detail_notes).text.toString()
                if (initiallyProtected) {
                    repository.setPinned(clipId, pinned)
                    if (protectedUnlocked && !wantsProtected) {
                        // Decrypt the row first; only then persist the revealed notes as plaintext.
                        repository.setProtected(clipId, false)
                        repository.setNotes(clipId, notes)
                    }
                    // If protection remains enabled, never write the revealed notes back to the DB.
                } else {
                    repository.setNotes(clipId, notes)
                    repository.setPinned(clipId, pinned)
                    if (wantsProtected) repository.setProtected(clipId, true)
                }
                Toast.makeText(this@ClipDetailActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                finish()
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

    private fun launchExternal(intent: Intent) {
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(Intent.createChooser(intent, null)) }
            .onFailure { Toast.makeText(this, R.string.no_app_for_asset, Toast.LENGTH_SHORT).show() }
    }

    companion object { const val EXTRA_CLIP_ID = "clip_id" }
}
