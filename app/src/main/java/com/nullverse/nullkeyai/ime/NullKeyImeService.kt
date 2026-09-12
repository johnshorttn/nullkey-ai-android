package com.nullverse.nullkeyai.ime

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.clipboard.ClipRepository
import com.nullverse.nullkeyai.db.NullKeyDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The NullKey IME. Renders a standard QWERTY keyboard plus a "clip vault" panel
 * that lets the user search previously captured clips and tap one to paste it
 * into the current field. A "Files only" toggle narrows results to file/image
 * clips, matching the product's core end goal.
 */
class NullKeyImeService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: ClipRepository

    private lateinit var keyboardView: KeyboardView
    private lateinit var qwerty: Keyboard
    private lateinit var searchBox: EditText
    private lateinit var filesOnly: CheckBox
    private lateinit var clipsList: RecyclerView
    private lateinit var adapter: ClipAdapter

    private var caps = false

    override fun onCreate() {
        super.onCreate()
        repository = ClipRepository(NullKeyDatabase.get(this).clipDao())
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard, null)

        qwerty = Keyboard(this, R.xml.qwerty)
        keyboardView = root.findViewById(R.id.keyboard_view)
        keyboardView.keyboard = qwerty
        keyboardView.setOnKeyboardActionListener(this)
        keyboardView.isPreviewEnabled = false

        searchBox = root.findViewById(R.id.clip_search)
        filesOnly = root.findViewById(R.id.files_only)
        clipsList = root.findViewById(R.id.clips_list)

        adapter = ClipAdapter { clip ->
            currentInputConnection?.commitText(clip.content, 1)
        }
        clipsList.layoutManager = LinearLayoutManager(this)
        clipsList.adapter = adapter

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = refreshClips()
        })
        filesOnly.setOnCheckedChangeListener { _, _ -> refreshClips() }

        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        refreshClips()
    }

    private fun refreshClips() {
        val query = searchBox.text?.toString().orEmpty()
        val onlyFiles = filesOnly.isChecked
        scope.launch {
            val results = withContext(Dispatchers.IO) {
                repository.searchOnce(query, onlyFiles)
            }
            adapter.submit(results)
        }
    }

    // region KeyboardView.OnKeyboardActionListener
    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> ic.deleteSurroundingText(1, 0)
            Keyboard.KEYCODE_SHIFT -> {
                caps = !caps
                qwerty.isShifted = caps
                keyboardView.invalidateAllKeys()
            }
            Keyboard.KEYCODE_DONE -> ic.sendKeyEvent(enterKeyDown())
            CODE_SPACE -> ic.commitText(" ", 1)
            else -> {
                var code = primaryCode.toChar()
                if (Character.isLetter(code) && caps) code = Character.toUpperCase(code)
                ic.commitText(code.toString(), 1)
            }
        }
    }

    private fun enterKeyDown() =
        android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER)

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {
        if (text != null) currentInputConnection?.commitText(text, 1)
    }
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
    // endregion

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CODE_SPACE = 32
    }
}
