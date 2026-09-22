package com.nullverse.nullkeyai.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.text.InputType
import android.app.AlertDialog
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.clipboard.ClipRepository
import com.nullverse.nullkeyai.clipboard.ClipSwipeAction
import com.nullverse.nullkeyai.clipboard.ClipSwipeApplyResult
import com.nullverse.nullkeyai.clipboard.ClipSwipeCoordinator
import com.nullverse.nullkeyai.clipboard.ClipSwipePresentation
import com.nullverse.nullkeyai.clipboard.ClipSwipePreferences
import com.nullverse.nullkeyai.clipboard.labelRes
import com.nullverse.nullkeyai.clipboard.ClipCaptureRequest
import com.nullverse.nullkeyai.clipboard.DeviceBoundProtectedImportException
import com.nullverse.nullkeyai.clipboard.ImportFailureText
import com.nullverse.nullkeyai.clipboard.VaultAssetStore
import com.nullverse.nullkeyai.clipboard.ClipboardMonitorService
import com.nullverse.nullkeyai.db.Clip
import com.nullverse.nullkeyai.db.NullKeyDatabase
import com.nullverse.nullkeyai.db.ClipCaptureMethod
import com.nullverse.nullkeyai.db.ClipContentType
import com.nullverse.nullkeyai.db.ClipSourceConfidence
import com.nullverse.nullkeyai.diagnostics.ClipboardLabActivity
import com.nullverse.nullkeyai.ocr.MlKitOnDeviceTextRecognizer
import com.nullverse.nullkeyai.ocr.OcrBitmaps
import com.nullverse.nullkeyai.ocr.OcrExtractResult
import com.nullverse.nullkeyai.ocr.VaultImageOcr
import com.nullverse.nullkeyai.security.VaultCrypto
import com.nullverse.nullkeyai.sync.DeviceIdentity
import com.nullverse.nullkeyai.ime.ClipAdapter
import com.nullverse.nullkeyai.ime.engine.KeyboardEnginePreferences
import com.nullverse.nullkeyai.ime.engine.KeyboardInputSettings
import com.nullverse.nullkeyai.ime.engine.KeyboardThemeId
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
    private lateinit var imeStatus: TextView
    private var observeJob: Job? = null
    private var pendingBackupPassword: CharArray? = null
    private var pendingRestorePassword: CharArray? = null

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

    private val secureExport =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val password = pendingBackupPassword
            pendingBackupPassword = null
            if (uri != null && password != null) writeSecureExport(uri, password)
            else password?.fill('\u0000')
        }

    private val secureImport =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val password = pendingRestorePassword
            pendingRestorePassword = null
            when {
                uri != null && password != null -> readSecureImport(uri, password)
                uri != null -> {
                    // The document picker can recreate this activity and drop the
                    // in-memory password. Ask again instead of ignoring the file.
                    persistReadPermission(uri)
                    askBackupPassword(confirm = false) { replacement ->
                        readSecureImport(uri, replacement)
                    }
                }
                else -> password?.fill('\u0000')
            }
        }

    private val scanImage =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importImageAndExtract(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        SystemBarInsets.applyToActivity(this)
        val db = NullKeyDatabase.get(this)
        repository = ClipRepository(db.clipDao(), VaultAssetStore(this), db.tagDao(), VaultCrypto(), DeviceIdentity.from(this))

        search = findViewById(R.id.search)
        filesOnly = findViewById(R.id.files_only)
        empty = findViewById(R.id.empty)
        imeStatus = findViewById(R.id.ime_status)

        savedInstanceState?.let { state ->
            search.setText(state.getString(STATE_SEARCH).orEmpty())
            filesOnly.isChecked = state.getBoolean(STATE_FILES_ONLY, false)
        }

        val list = findViewById<RecyclerView>(R.id.clips)
        adapter = ClipAdapter { clip ->
            startActivity(
                Intent(this, ClipDetailActivity::class.java)
                    .putExtra(ClipDetailActivity.EXTRA_CLIP_ID, clip.id)
            )
        }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val clip = adapter.clipAt(position)
                // ItemTouchHelper already dismissed the row. Restore first so
                // cancel/failure never leave a hole in the Vault list.
                adapter.notifyItemChanged(position)
                if (clip == null) return
                val action = if (direction == ItemTouchHelper.RIGHT) {
                    ClipSwipePreferences.right(this@MainActivity)
                } else {
                    ClipSwipePreferences.left(this@MainActivity)
                }
                when (ClipSwipeCoordinator.presentation(action)) {
                    ClipSwipePresentation.APPLY -> applySwipeAction(clip, action)
                    ClipSwipePresentation.CONFIRM -> confirmSwipeAction(clip, action)
                    ClipSwipePresentation.PROMPT_TAG -> showSwipeTagDialog(clip)
                }
            }
        }).attachToRecyclerView(list)

        findViewById<Button>(R.id.btn_enable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btn_switch).setOnClickListener {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showInputMethodPicker()
        }
        findViewById<CheckBox>(R.id.use_custom_keyboard_engine).apply {
            isChecked = KeyboardEnginePreferences.useCustomEngine(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                KeyboardEnginePreferences.setUseCustomEngine(this@MainActivity, checked)
            }
        }
        findViewById<CheckBox>(R.id.swipe_typing_enabled).apply {
            isChecked = KeyboardEnginePreferences.swipeTypingEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                KeyboardEnginePreferences.setSwipeTypingEnabled(this@MainActivity, checked)
            }
        }
        bindKeyboardInputSettings()
        findViewById<CheckBox>(R.id.incognito_enabled).apply {
            isChecked = KeyboardEnginePreferences.incognitoEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                KeyboardEnginePreferences.setIncognitoEnabled(this@MainActivity, checked)
            }
        }
        val labButton = findViewById<Button>(R.id.btn_clipboard_lab)
        fun refreshDeveloperTools() {
            labButton.visibility = if (KeyboardEnginePreferences.developerOptionsEnabled(this)) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        findViewById<CheckBox>(R.id.developer_options_enabled).apply {
            isChecked = KeyboardEnginePreferences.developerOptionsEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                KeyboardEnginePreferences.setDeveloperOptionsEnabled(this@MainActivity, checked)
                refreshDeveloperTools()
            }
        }
        refreshDeveloperTools()
        bindSwipeActionButton(R.id.btn_swipe_left_action, true)
        bindSwipeActionButton(R.id.btn_swipe_right_action, false)
        findViewById<Button>(R.id.btn_start_monitor).setOnClickListener {
            val started = runCatching { ClipboardMonitorService.start(this) }.isSuccess
            Toast.makeText(
                this,
                if (started) R.string.monitor_started else R.string.monitor_start_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
        findViewById<Button>(R.id.btn_stop_monitor).setOnClickListener {
            ClipboardMonitorService.stop(this)
            Toast.makeText(this, R.string.monitor_stopped, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btn_capture).setOnClickListener { captureSystemClip() }
        findViewById<Button>(R.id.btn_scan_image).setOnClickListener {
            scanImage.launch(arrayOf("image/*"))
        }
        findViewById<Button>(R.id.btn_about_support).setOnClickListener {
            startActivity(Intent(this, AboutSupportActivity::class.java))
        }
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
        findViewById<Button>(R.id.btn_secure_export).setOnClickListener {
            askBackupPassword(confirm = true) { password ->
                pendingBackupPassword = password
                secureExport.launch("nullkey-secure-${System.currentTimeMillis()}.nkbackup")
            }
        }
        findViewById<Button>(R.id.btn_secure_import).setOnClickListener {
            askBackupPassword(confirm = false) { password ->
                pendingRestorePassword = password
                secureImport.launch(arrayOf("*/*"))
            }
        }

        search.addTextChangedListener(SimpleWatcher { observeClips() })
        filesOnly.setOnCheckedChangeListener { _, _ -> observeClips() }

        lifecycleScope.launch { repository.ensureDefaultTags() }
        requestNotifPermissionIfNeeded()
        refreshImeStatus()
        observeClips()
    }

    override fun onResume() {
        super.onResume()
        if (::imeStatus.isInitialized) refreshImeStatus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::search.isInitialized) {
            outState.putString(STATE_SEARCH, search.text?.toString().orEmpty())
            outState.putBoolean(STATE_FILES_ONLY, filesOnly.isChecked)
        }
    }

    private fun refreshImeStatus() {
        imeStatus.setText(
            ImeSetupStatus.messageRes(
                enabled = ImeSetupStatus.isEnabled(this),
                selected = ImeSetupStatus.isSelected(this),
            )
        )
    }

    private fun observeClips() {
        observeJob?.cancel()
        val query = search.text?.toString().orEmpty()
        val onlyFiles = filesOnly.isChecked
        empty.setText(VaultEmptyCopy.messageRes(query, onlyFiles))
        observeJob = lifecycleScope.launch {
            repository.search(query, onlyFiles).collectLatest { clips ->
                adapter.submit(clips)
                empty.visibility = if (clips.isEmpty()) android.view.View.VISIBLE
                else android.view.View.GONE
            }
        }
    }

    private fun bindKeyboardInputSettings() {
        val themeButton = findViewById<Button>(R.id.btn_keyboard_theme)
        fun refreshTheme() {
            val themeId = KeyboardEnginePreferences.themeId(this)
            themeButton.text = getString(R.string.keyboard_theme_label, getString(themeId.labelRes))
        }
        themeButton.setOnClickListener {
            val themes = KeyboardThemeId.entries.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle(R.string.keyboard_theme_title)
                .setItems(themes.map { getString(it.labelRes) }.toTypedArray()) { _, which ->
                    KeyboardEnginePreferences.setThemeId(this, themes[which])
                    refreshTheme()
                }
                .show()
        }
        refreshTheme()

        val heightLabel = findViewById<TextView>(R.id.keyboard_height_label)
        val heightSeek = findViewById<SeekBar>(R.id.keyboard_height_seek)
        heightSeek.max = KeyboardInputSettings.HEIGHT_PROGRESS_MAX
        fun refreshHeight(scale: Float) {
            heightLabel.text = getString(
                R.string.keyboard_height_label,
                KeyboardInputSettings.heightPercent(scale),
            )
        }
        val heightScale = KeyboardEnginePreferences.heightScale(this)
        heightSeek.progress = KeyboardInputSettings.heightScaleToProgress(heightScale)
        refreshHeight(heightScale)
        heightSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val next = KeyboardInputSettings.progressToHeightScale(progress)
                if (fromUser) KeyboardEnginePreferences.setHeightScale(this@MainActivity, next)
                refreshHeight(next)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        findViewById<CheckBox>(R.id.haptics_enabled).apply {
            isChecked = KeyboardEnginePreferences.hapticsEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                KeyboardEnginePreferences.setHapticsEnabled(this@MainActivity, checked)
            }
        }
        findViewById<CheckBox>(R.id.key_sound_enabled).apply {
            isChecked = KeyboardEnginePreferences.soundEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                KeyboardEnginePreferences.setSoundEnabled(this@MainActivity, checked)
            }
        }

        val longPressLabel = findViewById<TextView>(R.id.long_press_label)
        val longPressSeek = findViewById<SeekBar>(R.id.long_press_seek)
        longPressSeek.max = KeyboardInputSettings.LONG_PRESS_PROGRESS_MAX
        fun refreshLongPress(ms: Int) {
            longPressLabel.text = getString(R.string.long_press_label, ms)
        }
        val longPressMs = KeyboardEnginePreferences.longPressMs(this)
        longPressSeek.progress = KeyboardInputSettings.longPressMsToProgress(longPressMs)
        refreshLongPress(longPressMs)
        longPressSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val next = KeyboardInputSettings.progressToLongPressMs(progress)
                if (fromUser) KeyboardEnginePreferences.setLongPressMs(this@MainActivity, next)
                refreshLongPress(next)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun bindSwipeActionButton(buttonId: Int, left: Boolean) {
        val button = findViewById<Button>(buttonId)
        fun refresh() {
            val action = if (left) ClipSwipePreferences.left(this) else ClipSwipePreferences.right(this)
            button.text = getString(
                R.string.swipe_action_label,
                getString(if (left) R.string.swipe_direction_left else R.string.swipe_direction_right),
                getString(action.labelRes)
            )
        }
        button.setOnClickListener {
            val actions = ClipSwipeAction.entries.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle(if (left) R.string.swipe_left_title else R.string.swipe_right_title)
                .setItems(actions.map { getString(it.labelRes) }.toTypedArray()) { _, which ->
                    if (left) ClipSwipePreferences.setLeft(this, actions[which])
                    else ClipSwipePreferences.setRight(this, actions[which])
                    refresh()
                }
                .show()
        }
        refresh()
    }

    private fun applySwipeAction(clip: Clip, action: ClipSwipeAction, tagName: String? = null) {
        lifecycleScope.launch {
            when (ClipSwipeCoordinator.apply(repository, action, clip, tagName)) {
                ClipSwipeApplyResult.Success, ClipSwipeApplyResult.Cancelled -> Unit
                is ClipSwipeApplyResult.Failure -> {
                    Toast.makeText(this@MainActivity, R.string.swipe_action_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun confirmSwipeAction(clip: Clip, action: ClipSwipeAction) {
        AlertDialog.Builder(this)
            .setTitle(R.string.swipe_delete_confirm_title)
            .setMessage(R.string.swipe_delete_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.move_to_trash) { _, _ ->
                applySwipeAction(clip, action)
            }
            .show()
    }

    private fun showSwipeTagDialog(clip: Clip) {
        val input = EditText(this).apply {
            hint = getString(R.string.tag_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.add_tag_title)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val tag = ClipSwipeCoordinator.parseTagInput(input.text?.toString())
                if (tag == null) return@setPositiveButton
                applySwipeAction(clip, ClipSwipeAction.TAG, tag)
            }
            .show()
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
            val captured = runCatching {
                if (item.uri != null) {
                    val mimeType = clip.description?.getMimeType(0)
                    val uri = item.uri!!
                    val assetPath = withContext(Dispatchers.IO) {
                        runCatching { VaultAssetStore(this@MainActivity).importUri(uri, mimeType).relativePath }
                            .getOrNull()
                    }
                    if (assetPath == null) {
                        Toast.makeText(this@MainActivity, R.string.capture_asset_failed, Toast.LENGTH_LONG).show()
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
                    val text = item.text?.toString().orEmpty()
                    if (text.isBlank()) {
                        Toast.makeText(this@MainActivity, R.string.nothing_to_capture, Toast.LENGTH_SHORT).show()
                        return@runCatching null
                    }
                    repository.capture(
                        ClipCaptureRequest(
                            content = text,
                            captureMethod = ClipCaptureMethod.MANUAL
                        )
                    )
                }
            }.onFailure {
                Toast.makeText(this@MainActivity, R.string.capture_failed, Toast.LENGTH_LONG).show()
            }.getOrNull()
            if (captured != null) {
                Toast.makeText(this@MainActivity, R.string.capture_ok, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun importImageAndExtract(uri: android.net.Uri) {
        lifecycleScope.launch {
            val mime = contentResolver.getType(uri) ?: "image/*"
            if (!mime.startsWith("image/")) {
                Toast.makeText(this@MainActivity, R.string.scan_not_image, Toast.LENGTH_LONG).show()
                return@launch
            }
            Toast.makeText(this@MainActivity, R.string.extract_text_working, Toast.LENGTH_SHORT).show()
            val assetPath = withContext(Dispatchers.IO) {
                runCatching { VaultAssetStore(this@MainActivity).importUri(uri, mime).relativePath }.getOrNull()
            }
            if (assetPath == null) {
                Toast.makeText(this@MainActivity, R.string.capture_asset_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            val id = repository.capture(
                ClipCaptureRequest(
                    content = uri.toString(),
                    contentType = ClipContentType.IMAGE,
                    isFile = true,
                    mimeType = mime,
                    localAssetPath = assetPath,
                    sourceUri = uri.toString(),
                    captureMethod = ClipCaptureMethod.OCR,
                    sourceConfidence = ClipSourceConfidence.INFERRED,
                )
            )
            if (id == null) {
                Toast.makeText(this@MainActivity, R.string.capture_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            val asset = VaultAssetStore(this@MainActivity).resolve(assetPath)
            val result = if (asset == null) {
                OcrExtractResult.Failed
            } else {
                withContext(Dispatchers.IO) {
                    val bitmap = OcrBitmaps.decodeFile(asset.absolutePath)
                    if (bitmap == null) {
                        OcrExtractResult.Failed
                    } else {
                        try {
                            VaultImageOcr(MlKitOnDeviceTextRecognizer(this@MainActivity)).extract(bitmap)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
            when (result) {
                is OcrExtractResult.Text -> repository.setOcrText(id, result.text)
                OcrExtractResult.NoText ->
                    Toast.makeText(this@MainActivity, R.string.extract_text_empty, Toast.LENGTH_LONG).show()
                OcrExtractResult.Unavailable ->
                    Toast.makeText(this@MainActivity, R.string.extract_text_unavailable, Toast.LENGTH_LONG).show()
                OcrExtractResult.Failed ->
                    Toast.makeText(this@MainActivity, R.string.extract_text_failed, Toast.LENGTH_LONG).show()
            }
            startActivity(
                Intent(this@MainActivity, ClipDetailActivity::class.java)
                    .putExtra(ClipDetailActivity.EXTRA_CLIP_ID, id)
            )
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
                persistReadPermission(uri)
                val count = withContext(Dispatchers.IO) {
                    val json = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: throw java.io.IOException("Could not open the backup file")
                    repository.importJson(json)
                }
                val message = if (count == 0) {
                    getString(R.string.import_none_new)
                } else {
                    getString(R.string.import_ok, count)
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            } catch (e: DeviceBoundProtectedImportException) {
                Toast.makeText(this@MainActivity, R.string.import_protected_requires_secure, Toast.LENGTH_LONG).show()
            } catch (e: IllegalArgumentException) {
                showFailureToast(R.string.import_invalid, R.string.import_invalid_detail, e)
            } catch (e: Exception) {
                showFailureToast(R.string.import_failed, R.string.import_failed_detail, e)
            }
        }
    }

    private fun askBackupPassword(confirm: Boolean, accepted: (CharArray) -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 0)
        }
        val password = EditText(this).apply {
            hint = getString(R.string.backup_password_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        box.addView(password)
        val confirmation = if (confirm) EditText(this).apply {
            hint = getString(R.string.backup_password_confirm_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            box.addView(this)
        } else null
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.backup_password_title)
            .setView(box)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val first = password.text.toString()
                if (first.length < 8 || (confirmation != null && first != confirmation.text.toString())) {
                    Toast.makeText(this, R.string.backup_password_mismatch, Toast.LENGTH_LONG).show()
                } else {
                    dialog.dismiss()
                    accepted(first.toCharArray())
                }
            }
        }
        dialog.show()
    }

    private fun writeSecureExport(uri: android.net.Uri, password: CharArray) {
        lifecycleScope.launch {
            try {
                val encrypted = repository.exportPortableEncrypted(password)
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(encrypted.toByteArray(Charsets.UTF_8)) }
                        ?: throw java.io.IOException("Could not open output stream")
                }
                Toast.makeText(this@MainActivity, R.string.secure_export_ok, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this@MainActivity, R.string.secure_backup_failed, Toast.LENGTH_LONG).show()
            } finally {
                password.fill('\u0000')
            }
        }
    }

    private fun readSecureImport(uri: android.net.Uri, password: CharArray) {
        lifecycleScope.launch {
            try {
                persistReadPermission(uri)
                val count = withContext(Dispatchers.IO) {
                    val document = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: throw java.io.IOException("Could not open the backup file")
                    repository.importPortableEncrypted(document, password)
                }
                val message = if (count == 0) {
                    getString(R.string.import_none_new)
                } else {
                    getString(R.string.secure_restore_ok, count)
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            } catch (_: javax.crypto.AEADBadTagException) {
                Toast.makeText(this@MainActivity, R.string.secure_restore_bad_password, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                showFailureToast(R.string.secure_backup_failed, R.string.secure_backup_failed_detail, e)
            } finally {
                password.fill('\u0000')
            }
        }
    }

    private fun persistReadPermission(uri: android.net.Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    private fun showFailureToast(fallbackRes: Int, detailRes: Int, error: Throwable) {
        val detail = ImportFailureText.detail(error)
        val text = if (detail.isNullOrBlank()) getString(fallbackRes) else getString(detailRes, detail)
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
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

    companion object {
        private const val STATE_SEARCH = "search_query"
        private const val STATE_FILES_ONLY = "files_only"
    }
}
