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
import com.nullverse.nullkeyai.clipboard.ClipCaptureRequest
import com.nullverse.nullkeyai.clipboard.VaultAssetStore
import com.nullverse.nullkeyai.clipboard.ClipboardMonitorService
import com.nullverse.nullkeyai.db.NullKeyDatabase
import com.nullverse.nullkeyai.db.ClipCaptureMethod
import com.nullverse.nullkeyai.db.ClipContentType
import com.nullverse.nullkeyai.db.ClipSourceConfidence
import com.nullverse.nullkeyai.diagnostics.ClipboardLabActivity
import com.nullverse.nullkeyai.ime.ClipAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val exportClips =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { writeExport(it) }
        }

    private val importClips =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { readImport(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        repository = ClipRepository(NullKeyDatabase.get(this).clipDao(), VaultAssetStore(this))

        search = findViewById(R.id.search)
        filesOnly = findViewById(R.id.files_only)
        empty = findViewById(R.id.empty)

        val list = findViewById<RecyclerView>(R.id.clips)
        adapter = ClipAdapter { clip ->
            startActivity(
                Intent(this, ClipDetailActivity::class.java)
                    .putExtra(ClipDetailActivity.EXTRA_CLIP_ID, clip.id)
            )
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
        findViewById<Button>(R.id.btn_trash).setOnClickListener {
            startActivity(Intent(this, TrashActivity::class.java))
        }
        findViewById<Button>(R.id.btn_clipboard_lab).setOnClickListener {
            startActivity(Intent(this, ClipboardLabActivity::class.java))
        }
        findViewById<Button>(R.id.btn_export).setOnClickListener {
            exportClips.launch("nullkey-clips-${System.currentTimeMillis()}.json")
        }
        findViewById<Button>(R.id.btn_import).setOnClickListener {
            importClips.launch(arrayOf("application/json", "text/*", "*/*"))
        }

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
                val mimeType = clip.description?.getMimeType(0)
                val uri = item.uri!!
                val assetPath = withContext(Dispatchers.IO) {
                    runCatching { VaultAssetStore(this@MainActivity).importUri(uri, mimeType).relativePath }
                        .getOrNull()
                }
                repository.capture(
                    ClipCaptureRequest(
                        content = uri.toString(),
                        contentType = if (mimeType?.startsWith("image/") == true) ClipContentType.IMAGE else ClipContentType.FILE,
                        isFile = true,
                        mimeType = mimeType,
                        localAssetPath = assetPath,
                        sourceUri = uri.toString(),
                        captureMethod = ClipCaptureMethod.MANUAL,
                        sourceConfidence = ClipSourceConfidence.UNKNOWN
                    )
                )
            } else {
                repository.capture(
                    ClipCaptureRequest(
                        content = item.text?.toString().orEmpty(),
                        captureMethod = ClipCaptureMethod.MANUAL
                    )
                )
            }
        }
    }

    private fun writeExport(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val json = repository.exportJson()
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw java.io.IOException("Could not open output stream")
                }
                Toast.makeText(this@MainActivity, R.string.export_ok, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.export_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun readImport(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: throw java.io.IOException("Could not open input stream")
                }
                val count = repository.importJson(json)
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.import_ok, count),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: IllegalArgumentException) {
                Toast.makeText(this@MainActivity, R.string.import_invalid, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.import_failed, Toast.LENGTH_LONG).show()
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
