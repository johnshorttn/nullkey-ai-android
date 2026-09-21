package com.nullverse.nullkeyai.ime

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.clipboard.ClipRepository
import com.nullverse.nullkeyai.db.NullKeyDatabase
import com.nullverse.nullkeyai.ime.engine.KeyboardEnginePreferences
import com.nullverse.nullkeyai.ime.engine.KeyCodes
import com.nullverse.nullkeyai.ime.engine.NullKeyKeyboardView
import com.nullverse.nullkeyai.sync.DeviceIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The NullKey IME. Renders a QWERTY keyboard (with a symbols/numbers layer) plus:
 *  - a "clip vault" panel to search captured clips and tap one to paste it
 *    (with a "Files only" filter), and
 *  - a word-suggestion strip backed by an on-device [WordSuggester].
 *
 * The default renderer is the custom [NullKeyKeyboardView] engine (hit-testing,
 * press/release, long-press, shift/caps, portrait/landscape layouts). The
 * legacy [KeyboardView] remains wired as a fallback while the engine is
 * developed; toggle it from the NullKey app.
 */
class NullKeyImeService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: ClipRepository
    private lateinit var suggester: WordSuggester

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboardEngineView: NullKeyKeyboardView
    private lateinit var qwerty: Keyboard
    private lateinit var symbols: Keyboard
    private lateinit var searchBox: EditText
    private lateinit var filesOnly: CheckBox
    private lateinit var clipsList: RecyclerView
    private lateinit var adapter: ClipAdapter
    private lateinit var suggestionViews: List<TextView>

    private val currentWord = StringBuilder()
    private var caps = false
    private var symbolsMode = false
    private var usingEngine = true
    private var pendingSwipeCommit: String? = null

    override fun onCreate() {
        super.onCreate()
        repository = ClipRepository(NullKeyDatabase.get(this).clipDao(), deviceIdentity = DeviceIdentity.from(this))
        suggester = WordSuggester.get(this)
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard, null)

        qwerty = Keyboard(this, R.xml.qwerty)
        symbols = Keyboard(this, R.xml.symbols)
        keyboardView = root.findViewById(R.id.keyboard_view)
        keyboardEngineView = root.findViewById(R.id.keyboard_engine_view)
        keyboardView.keyboard = qwerty
        keyboardView.setOnKeyboardActionListener(this)
        keyboardView.isPreviewEnabled = false
        keyboardEngineView.listener = object : NullKeyKeyboardView.Listener {
            override fun onKey(code: Int) = handleKey(code, alreadyCased = true)
            override fun onGestureWord(path: String) = handleGestureWord(path)
        }
        applyRendererPreference()

        searchBox = root.findViewById(R.id.clip_search)
        filesOnly = root.findViewById(R.id.files_only)
        clipsList = root.findViewById(R.id.clips_list)

        adapter = ClipAdapter { clip ->
            currentInputConnection?.commitText(clip.content, 1)
        }
        clipsList.layoutManager = LinearLayoutManager(this)
        clipsList.adapter = adapter

        suggestionViews = listOf(
            root.findViewById(R.id.suggestion_0),
            root.findViewById(R.id.suggestion_1),
            root.findViewById(R.id.suggestion_2)
        )
        suggestionViews.forEachIndexed { index, view ->
            view.setOnClickListener { pickSuggestion(index) }
        }

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
        applyRendererPreference()
        if (usingEngine) keyboardEngineView.resetEngine()
        currentWord.setLength(0)
        pendingSwipeCommit = null
        updateSuggestions()
        refreshClips()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::keyboardEngineView.isInitialized) {
            keyboardEngineView.requestLayout()
        }
    }

    private fun applyRendererPreference() {
        if (!::keyboardEngineView.isInitialized) return
        usingEngine = KeyboardEnginePreferences.useCustomEngine(this)
        keyboardEngineView.visibility = if (usingEngine) View.VISIBLE else View.GONE
        keyboardView.visibility = if (usingEngine) View.GONE else View.VISIBLE
        if (!usingEngine) {
            keyboardView.keyboard = if (symbolsMode) symbols else qwerty
            qwerty.isShifted = caps
            keyboardView.invalidateAllKeys()
        }
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

    // region suggestions
    private fun updateSuggestions() {
        if (pendingSwipeCommit != null) return
        val results = suggester.suggest(currentWord.toString(), suggestionViews.size)
        suggestionViews.forEachIndexed { index, view ->
            view.text = results.getOrNull(index).orEmpty()
        }
    }

    private fun pickSuggestion(index: Int) {
        val word = suggestionViews.getOrNull(index)?.text?.toString().orEmpty()
        if (word.isBlank()) return
        val ic = currentInputConnection ?: return
        val swipeWord = pendingSwipeCommit
        if (swipeWord != null) {
            if (word != swipeWord) {
                ic.deleteSurroundingText(swipeWord.length + 1, 0)
                ic.commitText("$word ", 1)
                suggester.learn(word)
                pendingSwipeCommit = word
            }
            return
        }
        if (currentWord.isNotEmpty()) ic.deleteSurroundingText(currentWord.length, 0)
        ic.commitText("$word ", 1)
        suggester.learn(word)
        currentWord.setLength(0)
        updateSuggestions()
    }

    private fun handleGestureWord(path: String) {
        val ranked = suggester.suggestGesture(path, suggestionViews.size)
        val resolved = SwipeCommit.resolve(path, ranked) ?: return
        currentInputConnection?.commitText("${resolved.committed} ", 1)
        suggester.learn(ranked.first())
        currentWord.setLength(0)
        pendingSwipeCommit = resolved.committed
        suggestionViews.forEachIndexed { index, view ->
            view.text = resolved.suggestions.getOrNull(index).orEmpty()
        }
    }

    private fun flushWord() {
        pendingSwipeCommit = null
        if (currentWord.isNotEmpty()) {
            suggester.learn(currentWord.toString())
            currentWord.setLength(0)
        }
        updateSuggestions()
    }
    // endregion

    // region KeyboardView.OnKeyboardActionListener
    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        handleKey(primaryCode, alreadyCased = false)
    }

    private fun handleKey(primaryCode: Int, alreadyCased: Boolean) {
        val ic = currentInputConnection ?: return
        pendingSwipeCommit = null
        when (primaryCode) {
            KeyCodes.DELETE -> {
                ic.deleteSurroundingText(1, 0)
                if (currentWord.isNotEmpty()) currentWord.deleteCharAt(currentWord.length - 1)
                updateSuggestions()
            }
            KeyCodes.SHIFT -> {
                if (alreadyCased) return
                caps = !caps
                if (!symbolsMode) {
                    qwerty.isShifted = caps
                    keyboardView.invalidateAllKeys()
                }
            }
            KeyCodes.MODE_CHANGE -> {
                if (alreadyCased) return
                toggleSymbols()
            }
            KeyCodes.DONE -> {
                flushWord()
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            KeyCodes.SPACE -> {
                ic.commitText(" ", 1)
                flushWord()
            }
            else -> {
                var code = primaryCode.toChar()
                if (Character.isLetter(code)) {
                    if (!alreadyCased && caps) code = Character.toUpperCase(code)
                    ic.commitText(code.toString(), 1)
                    currentWord.append(code)
                    updateSuggestions()
                } else {
                    ic.commitText(code.toString(), 1)
                    flushWord()
                }
            }
        }
    }

    private fun toggleSymbols() {
        symbolsMode = !symbolsMode
        keyboardView.keyboard = if (symbolsMode) symbols else qwerty
        keyboardView.invalidateAllKeys()
    }

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

    override fun onFinishInputView(finishingInput: Boolean) {
        if (::keyboardEngineView.isInitialized) keyboardEngineView.resetEngine()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        if (::keyboardEngineView.isInitialized) keyboardEngineView.resetEngine()
        scope.cancel()
        super.onDestroy()
    }
}
