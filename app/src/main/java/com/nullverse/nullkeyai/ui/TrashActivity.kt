package com.nullverse.nullkeyai.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.clipboard.ClipRepository
import com.nullverse.nullkeyai.clipboard.ImportFailureText
import com.nullverse.nullkeyai.clipboard.VaultAssetStore
import com.nullverse.nullkeyai.sync.DeviceIdentity
import com.nullverse.nullkeyai.db.NullKeyDatabase
import com.nullverse.nullkeyai.ime.ClipAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TrashActivity : AppCompatActivity() {
    private lateinit var repository: ClipRepository
    private lateinit var adapter: ClipAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trash)
        SystemBarInsets.applyToActivity(this)
        val db = NullKeyDatabase.get(this)
        repository = ClipRepository(db.clipDao(), VaultAssetStore(this), db.tagDao(), deviceIdentity = DeviceIdentity.from(this))
        val empty = findViewById<TextView>(R.id.trash_empty)
        val emptyTrash = findViewById<Button>(R.id.btn_empty_trash)
        emptyTrash.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.empty_trash_title)
                .setMessage(R.string.empty_trash_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.empty_trash) { _, _ ->
                    lifecycleScope.launch {
                        try {
                            val removed = repository.emptyTrash()
                            Toast.makeText(
                                this@TrashActivity,
                                getString(R.string.trash_purged, removed),
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (error: Exception) {
                            val detail = ImportFailureText.detail(error)
                            val text = if (detail.isNullOrBlank()) {
                                getString(R.string.trash_purge_failed)
                            } else {
                                getString(R.string.trash_purge_failed_detail, detail)
                            }
                            Toast.makeText(this@TrashActivity, text, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .show()
        }
        adapter = ClipAdapter { clip ->
            AlertDialog.Builder(this)
                .setTitle(R.string.restore_clip_title)
                .setMessage(R.string.restore_clip_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.restore) { _, _ ->
                    lifecycleScope.launch {
                        repository.restore(clip.id)
                        Toast.makeText(this@TrashActivity, R.string.restored, Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }
        findViewById<RecyclerView>(R.id.trash_list).apply {
            layoutManager = LinearLayoutManager(this@TrashActivity)
            adapter = this@TrashActivity.adapter
        }
        lifecycleScope.launch {
            repository.trash().collectLatest { clips ->
                adapter.submit(clips)
                val isEmpty = clips.isEmpty()
                empty.visibility = if (isEmpty) android.view.View.VISIBLE else android.view.View.GONE
                emptyTrash.isEnabled = !isEmpty
            }
        }
    }
}
