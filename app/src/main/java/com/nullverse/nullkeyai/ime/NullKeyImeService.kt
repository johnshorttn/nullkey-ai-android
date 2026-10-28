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
import android.view.inputmethod.EditorInfo.TYPE_CLASS_NUMBER
import android.view.inputmethod.EditorInfo.TYPE_CLASS_TEXT
import android.view.inputmethod.EditorInfo.TYPE_MASK_CLASS
import android.view.inputmethod.EditorInfo.TYPE_MASK_VARIATION
import android.view.inputmethod.EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD
import android.view.inputmethod.EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
import android.view.inputmethod.EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
import android.view.inputmethod.EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.clipboard.ClipRepository
import com.nullverse.nullkeyai.ui.SystemBarInsets
import com.nullverse.nullkeyai.db.NullKeyDatabase
import com.nullverse.nullkeyai.ime.engine.KeyboardEnginePreferences
import com.nullverse.nullkeyai.ime.engine.TerminalMode
import com.nullverse.nullkeyai.ime.engine.KeyFeedback
import com.nullverse.nullkeyai.ime.engine.KeyCodes
import com.nullverse.nullkeyai.ime.engine.KeyEventSpec
import com.nullverse.nullkeyai.ime.engine.KeyStroke
import com.nullverse.nullkeyai.ime.engine.KeyStrokeEvents
import com.nullverse.nullkeyai.ime.engine.KeyStrokePlan
import com.nullverse.nullkeyai.ime.engine.KeyStrokePlanner
import com.nullverse.nullkeyai.ime.engine.LayoutOrientation
import com.nullverse.nullkeyai.ime.engine.NullKeyKeyboardView
import com.nullverse.nullkeyai.ime.engine.TrackpadPointer
import com.nullverse.nullkeyai.ime.engine.preferredKeyboardHeightPx
import com.nullverse.nullkeyai.sync.DeviceIdentity
import com.nullverse.nullkeyai.ui.VaultEmptyCopy
import com.nullverse.nullkeyai.writing.BundledSpellingDictionary
import com.nullverse.nullkeyai.writing.SuggestionPlan
import com.nullverse.nullkeyai.writing.WritingAssistant
import com.nullverse.nullkeyai.writing.WritingIssue
import com.nullverse.nullkeyai.writing.WritingReplacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The NullKey IME. Renders a QWERTY keyboard (with a symbols/numbers layer) plus
 * a word-suggestion strip backed by an on-device [WordSuggester], with offline
 * spelling corrections when the typed token is not a completion.
 *
 * The suggestion strip's Tools button opens the clip vault (search, a "Files
 * only" filter, and tap-to-paste). That panel stays hidden on the default
 * keyboard so the IME height is only the strip plus keys until the user asks
 * for clips.
 *
 * The default renderer is the custom [NullKeyKeyboardView] engine (hit-testing,
 * press/release, long-press, shift/caps, portrait/landscape layouts). The
 * legacy [KeyboardView] remains wired as a fallback while the engine is
 * developed; toggle it from the NullKey app.
 */
class NullKeyImeService : InputMethodService(), KeyboardView.OnKeyboardActionListener, ShowKeyboardBridge.Target {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: ClipRepository
    private lateinit var suggester: WordSuggester

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboardEngineView: NullKeyKeyboardView
    private lateinit var qwerty: Keyboard
    private lateinit var symbols: Keyboard
    private lateinit var searchBox: EditText
    private lateinit var vaultPanel: View
    private lateinit var toolsButton: ImageButton
    private lateinit var trackpadButton: TextView
    private lateinit var trackpadSurface: TextView
    private lateinit var filesOnly: CheckBox
    private lateinit var clipsList: RecyclerView
    private lateinit var clipsEmpty: TextView
    private lateinit var adapter: ClipAdapter
    private lateinit var suggestionViews: List<TextView>
    private var refreshClipsJob: Job? = null

    private val currentWord = StringBuilder()
    private var writing: WritingAssistant? = null
    private var proof: ProofSession? = null
    private var proofGeneration = 0
    private var caps = false
    private var symbolsMode = false
    private var usingEngine = true
    private var pendingSwipeCommit: String? = null

    /** Termux/JuiceSSH/ConnectBot or a raw field: see [TerminalMode]. */
    private var terminalMode = false
    private var previewPath: String? = null
    private var previewRank: List<String>? = null
    /** True after the user taps the in-keyboard vault search field. */
    private var vaultSearchActive = false
    /** True while the Tools button is showing the vault panel above the keys. */
    private var vaultPanelOpen = false
    /** True while the key area is the trackpad instead of keys. */
    private var trackpadActive = false
    private lateinit var trackpadPointer: TrackpadPointer

    /** Set by the notification's Show keyboard action; see [showWhenEditorReady]. */
    private var pendingHelperShow = false
    /** Last editor Android bound us to; currentInputEditorInfo can lag behind it. */
    private var lastStartedEditor: EditorInfo? = null
    private val helperHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val helperTimeout = Runnable { finishHelperShow(timedOut = true) }

    override fun onCreate() {
        super.onCreate()
        ShowKeyboardBridge.attach(this)
        repository = ClipRepository(NullKeyDatabase.get(this).clipDao(), deviceIdentity = DeviceIdentity.from(this))
        suggester = WordSuggester.get(this)
        scope.launch(Dispatchers.IO) {
            val assistant = WritingAssistant(BundledSpellingDictionary.get(this@NullKeyImeService))
            withContext(Dispatchers.Main) { writing = assistant }
        }
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard, null)
        SystemBarInsets.pad(root)

        qwerty = Keyboard(this, R.xml.qwerty)
        symbols = Keyboard(this, R.xml.symbols)
        keyboardView = root.findViewById(R.id.keyboard_view)
        keyboardEngineView = root.findViewById(R.id.keyboard_engine_view)
        keyboardView.keyboard = qwerty
        keyboardView.setOnKeyboardActionListener(this)
        keyboardView.isPreviewEnabled = false
        keyboardEngineView.listener = object : NullKeyKeyboardView.Listener {
            override fun onKey(code: Int) = handleKey(code, alreadyCased = true)
            override fun onKeyStroke(stroke: KeyStroke) = handleKeyStroke(stroke)
            override fun onGestureWord(path: String) = handleGestureWord(path)
            override fun onGesturePreview(path: String) = previewGestureWord(path)
            override fun onCursorSteps(steps: Int) = handleCursorSteps(steps)
        }
        applyRendererPreference()

        searchBox = root.findViewById(R.id.clip_search)
        searchBox.showSoftInputOnFocus = false
        searchBox.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                activateVaultSearch()
            }
            false
        }
        searchBox.setOnFocusChangeListener { _, hasFocus ->
            // Clipboard paste/cut already reach this focused field. Keep key
            // routing on for as long as that focus lasts, including across the
            // input restart that focusing an IME-owned EditText triggers.
            if (hasFocus) activateVaultSearch()
            else if (vaultSearchActive) {
                vaultSearchActive = false
                searchBox.isActivated = false
            }
        }
        vaultPanel = root.findViewById(R.id.vault_panel)
        toolsButton = root.findViewById(R.id.keyboard_tools)
        toolsButton.setOnClickListener { setVaultPanelOpen(!vaultPanelOpen) }
        trackpadButton = root.findViewById(R.id.keyboard_trackpad)
        trackpadSurface = root.findViewById(R.id.keyboard_trackpad_surface)
        trackpadPointer = TrackpadPointer.forDensity(resources.displayMetrics.density)
        trackpadButton.setOnClickListener {
            if (trackpadActive) setVaultPanelOpen(false) else enterTrackpad()
        }
        trackpadSurface.setOnTouchListener { _, event ->
            if (!trackpadActive) return@setOnTouchListener false
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN ->
                    trackpadPointer.down(event.x, event.y)
                android.view.MotionEvent.ACTION_MOVE -> {
                    val history = event.historySize
                    for (index in 0 until history) {
                        applyTrackpadStep(
                            trackpadPointer.move(
                                event.getHistoricalX(index),
                                event.getHistoricalY(index),
                            ),
                        )
                    }
                    applyTrackpadStep(trackpadPointer.move(event.x, event.y))
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL,
                -> trackpadPointer.reset()
            }
            true
        }
        filesOnly = root.findViewById(R.id.files_only)
        clipsList = root.findViewById(R.id.clips_list)
        clipsEmpty = root.findViewById(R.id.clips_empty)

        adapter = ClipAdapter { clip ->
            when (val decision = ClipPastePolicy.decide(clip)) {
                is ClipPastePolicy.Decision.Commit ->
                    currentInputConnection?.commitText(decision.text, 1)
                ClipPastePolicy.Decision.BlockProtected ->
                    Toast.makeText(this, R.string.ime_protected_clip_blocked, Toast.LENGTH_SHORT).show()
                ClipPastePolicy.Decision.SkipEmpty -> Unit
            }
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
        root.findViewById<TextView>(R.id.suggestion_check).setOnClickListener { proofread() }

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = refreshClips()
        })
        filesOnly.setOnCheckedChangeListener { _, _ -> refreshClips() }

        return root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (!restarting) resetComposition()
        lastStartedEditor = attribute
        if (pendingHelperShow && ShowKeyboardBridge.isTextEditor(attribute)) {
            // Focus came back to the app's text field after the helper's
            // trampoline closed: show now instead of waiting for the timeout.
            helperHandler.post { finishHelperShow(timedOut = false) }
        }
    }

    /**
     * Notification helper: the trampoline activity has just taken focus, so the
     * field the user was in is not bound yet. Wait for it to come back (onStartInput)
     * or for a short timeout, then requestShowSelf, or tell the user there is no
     * text field to type into.
     */
    override fun showWhenEditorReady() {
        pendingHelperShow = true
        helperHandler.removeCallbacks(helperTimeout)
        helperHandler.postDelayed(helperTimeout, HELPER_SHOW_TIMEOUT_MS)
    }

    private fun finishHelperShow(timedOut: Boolean) {
        if (!pendingHelperShow) return
        val editor = currentInputEditorInfo ?: lastStartedEditor
        if (!ShowKeyboardBridge.isTextEditor(editor) || currentInputConnection == null) {
            if (!timedOut) return
            pendingHelperShow = false
            Toast.makeText(this, R.string.show_keyboard_no_field, Toast.LENGTH_LONG).show()
            return
        }
        pendingHelperShow = false
        helperHandler.removeCallbacks(helperTimeout)
        showSelfFromHelper()
    }

    private fun showSelfFromHelper() {
        if (ShowKeyboardBridge.usesRequestShowSelf()) {
            requestShowSelf(0)
        } else {
            val token = window?.window?.attributes?.token ?: return
            @Suppress("DEPRECATION")
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showSoftInputFromInputMethod(token, 0)
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (::searchBox.isInitialized && editorIsVaultSearch(info)) {
            activateVaultSearch()
        }
        applyRendererPreference()
        terminalMode = TerminalMode.detect(
            packageName = info?.packageName,
            inputType = info?.inputType ?: 0,
            enabled = KeyboardEnginePreferences.terminalModeAuto(this),
        ) && !vaultSearchActive
        if (::keyboardEngineView.isInitialized) keyboardEngineView.setTerminalMode(terminalMode)
        if (usingEngine) keyboardEngineView.resetEngine()
        if (!restarting) resetComposition()
        else updateSuggestions()
        refreshClips()
    }

    override fun onFinishInput() {
        lastStartedEditor = null
        resetComposition()
        super.onFinishInput()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::keyboardEngineView.isInitialized) {
            keyboardEngineView.resetEngine()
            keyboardEngineView.requestLayout()
        }
    }

    private fun applyRendererPreference() {
        if (!::keyboardEngineView.isInitialized) return
        usingEngine = KeyboardEnginePreferences.useCustomEngine(this)
        if (trackpadActive) {
            keyboardEngineView.visibility = View.GONE
            keyboardView.visibility = View.GONE
            return
        }
        keyboardEngineView.visibility = if (usingEngine) View.VISIBLE else View.GONE
        keyboardView.visibility = if (usingEngine) View.GONE else View.VISIBLE
        if (!usingEngine) {
            keyboardView.keyboard = if (symbolsMode) symbols else qwerty
            qwerty.isShifted = caps
            keyboardView.invalidateAllKeys()
        }
    }

    private fun refreshClips() {
        if (!::searchBox.isInitialized) return
        val query = searchBox.text?.toString().orEmpty()
        val onlyFiles = filesOnly.isChecked
        refreshClipsJob?.cancel()
        refreshClipsJob = scope.launch {
            val results = runCatching {
                withContext(Dispatchers.IO) {
                    repository.searchOnce(query, onlyFiles)
                }
            }.getOrDefault(emptyList())
            if (!isActive) return@launch
            adapter.submit(results)
            clipsEmpty.setText(VaultEmptyCopy.messageRes(query, onlyFiles))
            clipsEmpty.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun resetComposition() {
        currentWord.setLength(0)
        pendingSwipeCommit = null
        proof = null
        proofGeneration++
        if (::suggestionViews.isInitialized) updateSuggestions()
    }

    // region suggestions
    private fun updateSuggestions() {
        if (terminalMode) {
            // Terminals get no word suggestions.
            publishSwipeSuggestions(emptyList())
            return
        }
        if (pendingSwipeCommit != null || proof != null) return
        val typed = currentWord.toString()
        val completions = suggester.suggest(typed, suggestionViews.size)
        val spelling = if (completions.isEmpty()) {
            writing?.suggestionsFor(typed, suggestionViews.size).orEmpty()
        } else {
            emptyList()
        }
        val results = SuggestionPlan.forTypedWord(typed, completions, spelling, suggestionViews.size)
        suggestionViews.forEachIndexed { index, view ->
            val word = results.getOrNull(index).orEmpty()
            view.text = word
            view.contentDescription = if (word.isBlank()) {
                getString(R.string.suggestion_empty, index + 1)
            } else {
                getString(R.string.suggestion_word, word)
            }
        }
    }

    private fun pickSuggestion(index: Int) {
        val session = proof
        if (session != null) {
            val suggestion = session.suggestions.getOrNull(index) ?: return
            applyProof(session, suggestion)
            return
        }
        val word = suggestionViews.getOrNull(index)?.text?.toString().orEmpty()
        if (word.isBlank()) return
        val sink = keySink() ?: return
        val swipeWord = pendingSwipeCommit
        if (swipeWord != null) {
            if (word != swipeWord) {
                sink.deleteBeforeCursor(swipeWord.length + 1)
                sink.commitText("$word ")
                learnTyped(word)
                pendingSwipeCommit = word
            }
            return
        }
        if (currentWord.isNotEmpty()) sink.deleteBeforeCursor(currentWord.length)
        sink.commitText("$word ")
        learnTyped(word)
        currentWord.setLength(0)
        updateSuggestions()
    }

    /**
     * Show the leading candidate while the finger is still down. Uses the
     * seeded lexicon only: building the spelling-list weight map on this
     * thread stalls the gesture. The lift commits the same ranking when the
     * path has not grown.
     */
    private fun previewGestureWord(path: String) {
        if (trackpadActive || !::suggestionViews.isInitialized) return
        val ranked = suggester.suggestGesture(path, suggestionViews.size)
        previewPath = path
        previewRank = ranked
        val resolved = SwipeCommit.resolve(path, ranked)
        publishSwipeSuggestions(resolved?.suggestions.orEmpty())
    }

    private fun handleGestureWord(path: String) {
        if (trackpadActive || terminalMode) return
        val ranked = if (path == previewPath) {
            previewRank
        } else {
            null
        } ?: suggester.suggestGesture(path, suggestionViews.size)
        previewPath = null
        previewRank = null
        val resolved = SwipeCommit.resolve(path, ranked) ?: return
        val sink = keySink() ?: return
        sink.commitText("${resolved.committed} ")
        learnTyped(ranked.first())
        currentWord.setLength(0)
        pendingSwipeCommit = resolved.committed
        publishSwipeSuggestions(resolved.suggestions)
    }

    private fun publishSwipeSuggestions(suggestions: List<String>) {
        if (!::suggestionViews.isInitialized) return
        suggestionViews.forEachIndexed { index, view ->
            val word = suggestions.getOrNull(index).orEmpty()
            view.text = word
            view.contentDescription = if (word.isBlank()) {
                getString(R.string.suggestion_empty, index + 1)
            } else {
                getString(R.string.suggestion_word, word)
            }
        }
    }

    private fun flushWord() {
        pendingSwipeCommit = null
        if (currentWord.isNotEmpty()) {
            learnTyped(currentWord.toString())
            currentWord.setLength(0)
        }
        updateSuggestions()
    }

    private fun learnTyped(word: String) {
        if (terminalMode) return
        suggester.learn(word, enabled = KeyboardEnginePreferences.shouldLearn(this))
    }

    private fun proofread() {
        if (vaultSearchActive) {
            val source = searchBox.text?.toString().orEmpty()
            if (source.isBlank()) {
                Toast.makeText(this, R.string.writing_nothing_to_check, Toast.LENGTH_SHORT).show()
                return
            }
            review(source, ProofKind.VAULT_SEARCH)
            return
        }
        if (isSensitiveField()) {
            Toast.makeText(this, R.string.writing_skipped_password, Toast.LENGTH_SHORT).show()
            return
        }
        val ic = currentInputConnection
        if (ic == null) {
            Toast.makeText(this, R.string.writing_nothing_to_check, Toast.LENGTH_SHORT).show()
            return
        }
        val selected = ic.getSelectedText(0)?.toString()
        val (source, kind) = when {
            !selected.isNullOrEmpty() -> selected to ProofKind.SELECTION
            currentWord.isNotEmpty() -> currentWord.toString() to ProofKind.CURRENT_WORD
            else -> {
                val before = ic.getTextBeforeCursor(400, 0)?.toString().orEmpty()
                if (before.isBlank()) {
                    Toast.makeText(this, R.string.writing_nothing_to_check, Toast.LENGTH_SHORT).show()
                    return
                }
                before to ProofKind.BEFORE_CURSOR
            }
        }
        review(source, kind)
    }

    private fun review(source: String, kind: ProofKind) {
        val generation = ++proofGeneration
        scope.launch {
            val assistant = writing ?: withContext(Dispatchers.IO) {
                WritingAssistant(BundledSpellingDictionary.get(this@NullKeyImeService))
            }.also { loaded ->
                if (generation == proofGeneration) writing = loaded
            }
            if (generation != proofGeneration) return@launch
            val issue = assistant.review(source).firstOrNull { it.suggestions.isNotEmpty() }
            if (issue == null) {
                proof = null
                updateSuggestions()
                Toast.makeText(this@NullKeyImeService, R.string.writing_no_issues, Toast.LENGTH_SHORT).show()
                return@launch
            }
            proof = ProofSession(source, issue, issue.suggestions.take(suggestionViews.size), kind)
            pendingSwipeCommit = null
            suggestionViews.forEachIndexed { index, view ->
                val suggestion = proof?.suggestions?.getOrNull(index)
                val label = when {
                    suggestion == null -> ""
                    suggestion.isEmpty() -> getString(R.string.writing_delete_repeat)
                    else -> suggestion
                }
                view.text = label
                view.contentDescription = if (label.isBlank()) {
                    getString(R.string.suggestion_empty, index + 1)
                } else {
                    getString(R.string.suggestion_word, label)
                }
            }
        }
    }

    private fun applyProof(session: ProofSession, suggestion: String) {
        val revised = WritingReplacement.apply(session.source, session.issue, suggestion)
        if (session.kind == ProofKind.VAULT_SEARCH) {
            if (!vaultSearchActive) return
            if (searchBox.text?.toString() != session.source) return
            applyVaultSearchEdit(VaultSearchInput.Edit(revised, revised.length))
            proof = null
            currentWord.setLength(0)
            updateSuggestions()
            return
        }
        val ic = currentInputConnection ?: return
        when (session.kind) {
            ProofKind.SELECTION -> {
                if (ic.getSelectedText(0)?.toString() != session.source) return
                ic.commitText(revised, 1)
            }
            ProofKind.CURRENT_WORD -> {
                if (currentWord.toString() != session.source) return
                ic.deleteSurroundingText(currentWord.length, 0)
                ic.commitText("$revised ", 1)
                if (revised.isNotBlank()) learnTyped(revised)
                currentWord.setLength(0)
            }
            ProofKind.BEFORE_CURSOR -> {
                val before = ic.getTextBeforeCursor(session.source.length, 0)?.toString()
                if (before != session.source) return
                ic.deleteSurroundingText(session.source.length, 0)
                ic.commitText(revised, 1)
            }
            ProofKind.VAULT_SEARCH -> return
        }
        proof = null
        updateSuggestions()
    }

    private fun isSensitiveField(): Boolean {
        val type = currentInputEditorInfo?.inputType ?: return false
        val variation = type and TYPE_MASK_VARIATION
        return when (type and TYPE_MASK_CLASS) {
            TYPE_CLASS_TEXT -> variation == TYPE_TEXT_VARIATION_PASSWORD ||
                variation == TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            TYPE_CLASS_NUMBER -> variation == TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private data class ProofSession(
        val source: String,
        val issue: WritingIssue,
        val suggestions: List<String>,
        val kind: ProofKind,
    )

    private enum class ProofKind { SELECTION, CURRENT_WORD, BEFORE_CURSOR, VAULT_SEARCH }
    // endregion

    // region KeyboardView.OnKeyboardActionListener
    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        handleKey(primaryCode, alreadyCased = false)
    }

    private fun handleKey(primaryCode: Int, alreadyCased: Boolean) {
        if (trackpadActive) return
        val editingSearch = vaultSearchActive
        val sink = keySink() ?: return
        pendingSwipeCommit = null
        val hadProof = proof != null
        proof = null
        proofGeneration++
        if (hadProof) updateSuggestions()
        when (primaryCode) {
            KeyCodes.DELETE -> {
                sink.deleteBeforeCursor(1)
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
                if (editingSearch) deactivateVaultSearch()
                else sink.enter()
            }
            KeyCodes.SPACE -> {
                sink.commitText(" ")
                flushWord()
            }
            else -> {
                var code = primaryCode.toChar()
                if (Character.isLetter(code)) {
                    if (!alreadyCased && caps) code = Character.toUpperCase(code)
                    sink.commitText(code.toString())
                    currentWord.append(code)
                    updateSuggestions()
                } else {
                    sink.commitText(code.toString())
                    flushWord()
                }
            }
        }
    }

    /**
     * Terminal keys and Ctrl/Alt/Meta chords leave as real KeyEvents with meta
     * state. Plain keys still go through [handleKey].
     */
    private fun handleKeyStroke(stroke: KeyStroke) {
        if (trackpadActive) return
        when (val plan = KeyStrokePlanner.plan(stroke, allowCtrlA = ctrlAAllowed())) {
            KeyStrokePlan.PassThrough -> handleKey(stroke.code, alreadyCased = true)
            KeyStrokePlan.Blocked -> Unit
            is KeyStrokePlan.Text -> handleKey(stroke.code, alreadyCased = true)
            is KeyStrokePlan.Events -> {
                val sink = keySink() ?: return
                flushWord()
                sink.sendKeyEvents(plan.events)
            }
        }
    }

    /** Raw terminal views always get Ctrl+A; normal fields only by opt-in. */
    private fun ctrlAAllowed(): Boolean {
        if (terminalMode || KeyboardEnginePreferences.ctrlAInTextFields(this)) return true
        val info = currentInputEditorInfo ?: return false
        return (info.inputType and android.text.InputType.TYPE_MASK_CLASS) ==
            android.text.InputType.TYPE_NULL
    }

    /**
     * Tools toggles the vault above the keys. Opening grows the IME by the
     * panel height (the host already pads for that inset). Closing returns
     * key routing to the host field and leaves the query in place.
     */
    private fun setVaultPanelOpen(open: Boolean) {
        if (!::vaultPanel.isInitialized || !::toolsButton.isInitialized) return
        if (vaultPanelOpen == open) return
        vaultPanelOpen = open
        vaultPanel.visibility = if (open) View.VISIBLE else View.GONE
        if (open) {
            toolsButton.setImageResource(R.drawable.ic_tools_back)
            toolsButton.contentDescription = getString(R.string.keyboard_tools_close)
            refreshClips()
        } else {
            leaveTrackpadSurface()
            toolsButton.setImageResource(R.drawable.ic_tools_grid)
            toolsButton.contentDescription = getString(R.string.keyboard_tools_open)
            deactivateVaultSearch()
        }
        (vaultPanel.parent as? View)?.requestLayout()
    }

    /**
     * Trackpad replaces the keys until Trackpad is tapped again or Back closes
     * the tools panel. Touches on the surface only move the caret.
     */
    private fun enterTrackpad() {
        if (trackpadActive) return
        if (!vaultPanelOpen) setVaultPanelOpen(true)
        val height = keyboardAreaHeight()
        trackpadActive = true
        trackpadPointer.reset()
        keyboardEngineView.visibility = View.GONE
        keyboardView.visibility = View.GONE
        trackpadSurface.layoutParams = trackpadSurface.layoutParams.apply { this.height = height }
        trackpadSurface.visibility = View.VISIBLE
        trackpadButton.setBackgroundColor(getColor(R.color.kb_key_modifier_active))
        trackpadButton.contentDescription = getString(R.string.keyboard_trackpad_exit)
        (trackpadSurface.parent as? View)?.requestLayout()
    }

    private fun leaveTrackpadSurface() {
        if (!trackpadActive && trackpadSurface.visibility != View.VISIBLE) {
            styleTrackpadIdle()
            return
        }
        trackpadActive = false
        if (::trackpadPointer.isInitialized) trackpadPointer.reset()
        trackpadSurface.visibility = View.GONE
        styleTrackpadIdle()
        applyRendererPreference()
    }

    private fun styleTrackpadIdle() {
        if (!::trackpadButton.isInitialized) return
        trackpadButton.setBackgroundColor(getColor(R.color.kb_key))
        trackpadButton.contentDescription = getString(R.string.keyboard_trackpad)
    }

    private fun keyboardAreaHeight(): Int {
        val measured = maxOf(keyboardEngineView.height, keyboardEngineView.measuredHeight, keyboardView.height, keyboardView.measuredHeight)
        if (measured > 0) return measured
        return keyboardEngineView.estimatedHeightPx()
    }

    private fun applyTrackpadStep(step: Pair<Int, Int>) {
        val (dx, dy) = step
        if (dx == 0 && dy == 0) return
        val sink = keySink() ?: return
        sink.moveCursor(dx, dy)
        flushWord()
        val ticks = (kotlin.math.abs(dx) + kotlin.math.abs(dy)).coerceAtMost(12)
        repeat(ticks) { KeyFeedback.cursorTick(trackpadSurface) }
    }

    /**
     * Vault search is an [android.widget.EditText] inside this IME. Once it is
     * focused, clipboard paste and cut edit its buffer, but
     * [currentInputConnection] commit and delete calls do not. Keys edit that
     * same buffer until Enter or the keyboard closes.
     */
    private fun activateVaultSearch() {
        if (!::searchBox.isInitialized) return
        val firstActivation = !vaultSearchActive
        vaultSearchActive = true
        searchBox.isActivated = true
        if (firstActivation && ::suggestionViews.isInitialized) resetComposition()
    }

    private fun deactivateVaultSearch() {
        if (!::searchBox.isInitialized) return
        if (!vaultSearchActive && !searchBox.isActivated) return
        vaultSearchActive = false
        searchBox.isActivated = false
        if (searchBox.isFocused) searchBox.clearFocus()
        resetComposition()
    }

    private fun editorIsVaultSearch(info: EditorInfo?): Boolean {
        if (info == null || !::searchBox.isInitialized) return false
        return info.fieldId == searchBox.id
    }

    private fun vaultSearchIsDirectTarget(): Boolean {
        if (!::searchBox.isInitialized) return false
        if (searchBox.isFocused || vaultSearchActive) return true
        return editorIsVaultSearch(currentInputEditorInfo)
    }

    private fun handleCursorSteps(steps: Int) {
        if (trackpadActive || steps == 0) return
        val sink = keySink() ?: return
        sink.moveCursor(steps)
        flushWord()
    }

    private fun keySink(): ImeKeyOutput? {
        if (vaultSearchIsDirectTarget()) return vaultSearchSink
        val ic = currentInputConnection ?: return null
        return InputConnectionKeyOutput(ic, allowRead = !isSensitiveField())
    }

    private val vaultSearchSink = object : ImeKeyOutput {
        override fun commitText(text: CharSequence) {
            if (!::searchBox.isInitialized) return
            val current = searchBox.text?.toString().orEmpty()
            applyVaultSearchEdit(
                VaultSearchInput.commit(
                    current,
                    searchBox.selectionStart,
                    searchBox.selectionEnd,
                    text.toString(),
                ),
            )
        }

        override fun deleteBeforeCursor(count: Int) {
            if (!::searchBox.isInitialized) return
            val current = searchBox.text?.toString().orEmpty()
            applyVaultSearchEdit(
                VaultSearchInput.deleteBefore(
                    current,
                    searchBox.selectionStart,
                    searchBox.selectionEnd,
                    count,
                ),
            )
        }

        override fun enter() {
            deactivateVaultSearch()
        }

        override fun moveCursor(delta: Int) {
            moveCursor(delta, 0)
        }

        override fun sendKeyEvents(events: List<KeyEventSpec>) {
            // The in-keyboard search box is a plain EditText: honour the
            // keys that make sense there and ignore the rest.
            for (event in events) {
                if (event.action != KeyEvent.ACTION_DOWN) continue
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> moveCursor(-1, 0)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> moveCursor(1, 0)
                    KeyEvent.KEYCODE_DEL -> deleteBeforeCursor(1)
                    KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_ENTER -> enter()
                }
            }
        }

        override fun moveCursor(dx: Int, dy: Int) {
            if (!::searchBox.isInitialized) return
            val current = searchBox.text?.toString().orEmpty()
            applyVaultSearchEdit(
                VaultSearchInput.moveCursor(
                    current,
                    searchBox.selectionStart,
                    searchBox.selectionEnd,
                    dx,
                    dy,
                ),
            )
        }
    }

    private fun applyVaultSearchEdit(edit: VaultSearchInput.Edit) {
        val editable = searchBox.text
        if (editable == null) {
            searchBox.setText(edit.text)
        } else if (editable.toString() != edit.text) {
            // Keep the Editable the clipboard and the partial InputConnection
            // already share. Replacing the whole buffer detaches paste/cut.
            editable.replace(0, editable.length, edit.text)
        }
        val cursor = edit.cursor.coerceIn(0, edit.text.length)
        runCatching { searchBox.setSelection(cursor) }
    }

    private class InputConnectionKeyOutput(
        private val connection: android.view.inputmethod.InputConnection,
        private val allowRead: Boolean,
    ) : ImeKeyOutput {
        override fun commitText(text: CharSequence) {
            connection.commitText(text, 1)
        }

        override fun deleteBeforeCursor(count: Int) {
            connection.deleteSurroundingText(count, 0)
        }

        override fun enter() {
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }

        override fun moveCursor(delta: Int) {
            CursorStep.apply(InputConnectionCursor(connection, allowRead), delta)
        }

        override fun moveCursor(dx: Int, dy: Int) {
            CursorStep.apply2D(InputConnectionCursor(connection, allowRead), dx, dy)
        }

        override fun sendKeyEvents(events: List<KeyEventSpec>) {
            val downTime = android.os.SystemClock.uptimeMillis()
            for (event in events) {
                connection.sendKeyEvent(KeyStrokeEvents.toKeyEvent(event, downTime))
            }
        }
    }

    private fun toggleSymbols() {
        symbolsMode = !symbolsMode
        keyboardView.keyboard = if (symbolsMode) symbols else qwerty
        keyboardView.invalidateAllKeys()
    }

    override fun onPress(primaryCode: Int) {
        if (::keyboardView.isInitialized) KeyFeedback.play(keyboardView)
    }
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {
        if (text != null) keySink()?.commitText(text)
    }
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
    // endregion

    override fun onFinishInputView(finishingInput: Boolean) {
        refreshClipsJob?.cancel()
        // Focusing the search box restarts input and finishes the previous
        // target with finishingInput=false. Only a real close should drop
        // search routing; focus itself keeps keys on that field.
        if (finishingInput && vaultPanelOpen) {
            setVaultPanelOpen(false)
        } else if (finishingInput && ::searchBox.isInitialized) {
            vaultSearchActive = false
            searchBox.isActivated = false
        }
        if (::keyboardEngineView.isInitialized) keyboardEngineView.resetEngine()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        ShowKeyboardBridge.detach(this)
        helperHandler.removeCallbacks(helperTimeout)
        refreshClipsJob?.cancel()
        if (::keyboardEngineView.isInitialized) keyboardEngineView.resetEngine()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** How long the helper waits for the app's field to be bound again. */
        const val HELPER_SHOW_TIMEOUT_MS = 1_200L
    }
}
