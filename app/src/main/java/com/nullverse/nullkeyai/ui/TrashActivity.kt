package com.nullverse.nullkeyai.ui

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.clipboard.ClipRepository
import com.nullverse.nullkeyai.clipboard.VaultAssetStore
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
        val db = NullKeyDatabase.get(this)
        repository = ClipRepository(db.clipDao(), VaultAssetStore(this), db.tagDao())
        val empty = findViewById<TextView>(R.id.trash_empty)
        adapter = ClipAdapter(showRestore = true) { clip ->
            lifecycleScope.launch {
                repository.restore(clip.id)
                Toast.makeText(this@TrashActivity, R.string.restored, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<RecyclerView>(R.id.trash_list).apply {
            layoutManager = LinearLayoutManager(this@TrashActivity)
            adapter = this@TrashActivity.adapter
        }
        lifecycleScope.launch {
            repository.trash().collectLatest { clips ->
                adapter.submit(clips)
                empty.visibility = if (clips.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }
}
