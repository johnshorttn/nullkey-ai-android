package com.nullverse.nullkeyai.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.clipboard.ClipRepository
import com.nullverse.nullkeyai.clipboard.ClipboardMonitorService
import com.nullverse.nullkeyai.db.NullKeyDatabase
import com.nullverse.nullkeyai.ime.ClipAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Setup + management screen. Guides the user to enable and select the NullKey
 * keyboard, toggles the background clipboard monitor, and shows the searchable
 * clip vault with the "Files only" filter.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var repository: ClipRepository
    private lateinit var adapter: ClipAdapter
    private lateinit var search: EditText
    private lateinit var filesOnly: CheckBox
    private lateinit var empty: TextView
    private var observeJob: Job? = null

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        repository = ClipRepository(NullKeyDatabase.get(this).clipDao())

        search = findViewById(R.id.search)
        filesOnly = findViewById(R.id.files_only)
        empty = findViewById(R.id.empty)

        val list = findViewById<RecyclerView>(R.id.clips)
        adapter = ClipAdapter { clip ->
            copyToSystemClipboard(clip.content)
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<Button>(R.id.btn_enable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btn_switch).setOnClickListener {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showInputMethodPicker()
        }
        findViewById<Button>(R.id.btn_start_monitor).setOnClickListener {
            ClipboardMonitorService.start(this)
            Toast.makeText(this, R.string.monitor_started, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btn_stop_monitor).setOnClickListener {
            ClipboardMonitorService.stop(this)
            Toast.makeText(this, R.string.monitor_stopped, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btn_capture).setOnClickListener { captureSystemClip() }

        search.addTextChangedListener(SimpleWatcher { observeClips() })
        filesOnly.setOnCheckedChangeListener { _, _ -> observeClips() }

        requestNotifPermissionIfNeeded()
        observeClips()
    }

    private fun observeClips() {
        observeJob?.cancel()
        val query = search.text?.toString().orEmpty()
        val onlyFiles = filesOnly.isChecked
        observeJob = lifecycleScope.launch {
            repository.search(query, onlyFiles).collectLatest { clips ->
                adapter.submit(clips)
                empty.visibility = if (clips.isEmpty()) android.view.View.VISIBLE
                else android.view.View.GONE
            }
        }
    }

    private fun captureSystemClip() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(this, R.string.nothing_to_capture, Toast.LENGTH_SHORT).show()
            return
        }
        val item = clip.getItemAt(0)
        lifecycleScope.launch {
            if (item.uri != null) {
                repository.capture(
                    item.uri.toString(), isFile = true,
                    mimeType = clip.description?.getMimeType(0)
                )
            } else {
                repository.capture(item.text?.toString().orEmpty())
            }
        }
    }

    private fun copyToSystemClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("nullkey", text))
    }

    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
