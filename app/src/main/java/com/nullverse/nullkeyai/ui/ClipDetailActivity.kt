package com.nullverse.nullkeyai.ui

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.view.View
import android.graphics.BitmapFactory
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.clipboard.ClipRepository
import com.nullverse.nullkeyai.clipboard.VaultAssetStore
import com.nullverse.nullkeyai.db.NullKeyDatabase
import kotlinx.coroutines.launch

class ClipDetailActivity : AppCompatActivity() {
    private lateinit var repository: ClipRepository
    private var clipId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clip_detail)
        val db = NullKeyDatabase.get(this)
        val assetStore = VaultAssetStore(this)
        repository = ClipRepository(db.clipDao(), assetStore, db.tagDao())
        clipId = intent.getLongExtra(EXTRA_CLIP_ID, 0L)
        if (clipId == 0L) { finish(); return }

        lifecycleScope.launch {
            val clip = db.clipDao().byId(clipId) ?: run { finish(); return@launch }
            val contentView = findViewById<TextView>(R.id.detail_content)
            val imageView = findViewById<ImageView>(R.id.detail_image)
            val assetStatus = findViewById<TextView>(R.id.detail_asset_status)
            val asset = assetStore.resolve(clip.localAssetPath)
            if (!clip.protected && clip.contentType == "IMAGE" && asset != null) {
                val bitmap = runCatching { BitmapFactory.decodeFile(asset.absolutePath) }.getOrNull()
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
            contentView.text = if (clip.protected) getString(R.string.protected_clip) else clip.content
            findViewById<TextView>(R.id.detail_meta).text = buildString {
                append(clip.contentType)
                clip.mimeType?.let { append(" • ").append(it) }
                clip.sourceAppLabel?.let { append(" • ").append(it) }
                    ?: clip.sourcePackage?.let { append(" • ").append(it) }
                append(" • ").append(clip.captureMethod)
            }
            findViewById<EditText>(R.id.detail_notes).setText(clip.notes)
            findViewById<CheckBox>(R.id.detail_pinned).isChecked = clip.pinned
            findViewById<CheckBox>(R.id.detail_protected).isChecked = clip.protected
            findViewById<TextView>(R.id.detail_tags).text =
                repository.tagsForClip(clipId).joinToString(" • ") { it.name }
                    .ifBlank { getString(R.string.no_tags) }
        }

        findViewById<Button>(R.id.detail_save).setOnClickListener {
            lifecycleScope.launch {
                repository.setNotes(clipId, findViewById<EditText>(R.id.detail_notes).text.toString())
                repository.setPinned(clipId, findViewById<CheckBox>(R.id.detail_pinned).isChecked)
                repository.setProtected(clipId, findViewById<CheckBox>(R.id.detail_protected).isChecked)
                Toast.makeText(this@ClipDetailActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        findViewById<Button>(R.id.detail_add_tag).setOnClickListener {
            lifecycleScope.launch {
                val input = findViewById<EditText>(R.id.detail_new_tag)
                repository.addTag(clipId, input.text.toString())
                findViewById<TextView>(R.id.detail_tags).text =
                    repository.tagsForClip(clipId).joinToString(" • ") { it.name }
                        .ifBlank { getString(R.string.no_tags) }
                input.text.clear()
            }
        }
        findViewById<Button>(R.id.detail_trash).setOnClickListener {
            lifecycleScope.launch { repository.moveToTrash(clipId); finish() }
        }
    }

    companion object { const val EXTRA_CLIP_ID = "clip_id" }
}
