package com.nullverse.nullkeyai.ime.engine

/**
 * Owns layout selection, geometry, modifiers, and the touch engine. The custom
 * [NullKeyKeyboardView] is a thin host that measures, draws, and forwards
 * pointer events here.
 */
class KeyboardController(
    private val layouts: KeyboardLayoutProvider = DefaultKeyboardLayoutProvider(),
    private val scheduler: TaskScheduler,
    private val host: Host,
    longPressMs: Long = KeyboardEngineDefaults.LONG_PRESS_MS,
    repeatStartMs: Long = KeyboardEngineDefaults.REPEAT_START_MS,
    repeatIntervalMs: Long = KeyboardEngineDefaults.REPEAT_INTERVAL_MS,
) {
    interface Host {
        fun requestRedraw()

        /** Trail/highlight update. Must not rebuild the accessibility tree. */
        fun requestGestureFrame() { requestRedraw() }

        fun onKey(code: Int)

        /**
         * A terminal/PC key, or any key pressed while Ctrl/Alt/Meta is on.
         * Hosts that cannot send key events fall back to [onKey].
         */
        fun onKeyStroke(stroke: KeyStroke) { onKey(stroke.code) }
        fun onLongPress(code: Int, popupCharacters: String)
        fun onPopupCharacter(code: Int) { onKey(code) }
        fun onGestureWord(path: String) {}
        fun onGestureProgress(keys: List<PlacedKey>) {}
        fun onCursorSteps(steps: Int) {}
        fun onPressFeedback() {}
    }

    val modifiers = ModifierController()

    var swipeTypingEnabled: Boolean = true

    /** Hold a modifier with one finger and type with another. On by default. */
    var chordingEnabled: Boolean
        get() = touch.chordingEnabled
        set(value) {
            touch.chordingEnabled = value
        }

    /** State to restore if a modifier's long-press lock turns into a chord hold. */
    private var modifierLongPressUndo: Pair<Int, ShiftState>? = null

    var orientation: LayoutOrientation = LayoutOrientation.PORTRAIT
        private set

    var geometry: KeyboardGeometry = KeyboardGeometry.place(
        spec = layouts.spec(KeyboardLayer.LETTERS, LayoutOrientation.PORTRAIT),
        orientation = LayoutOrientation.PORTRAIT,
        widthPx = 0f,
        heightPx = 0f,
        paddingHorizontalPx = 0f,
        paddingVerticalPx = 0f,
        gapPx = 0f,
    )
        private set

    private var paddingHorizontalPx: Float = 0f
    private var paddingVerticalPx: Float = 0f
    private var gapPx: Float = 0f
    private var slopPx: Float = 0f
    private var batchingGestureFrame = false
    private var gestureFramePending = false

    /** A repeating key was sent with one-shot modifiers; clear them on release. */
    private var consumeOnRelease = false

    val touch: TouchEngine = TouchEngine(
        scheduler = scheduler,
        longPressMs = longPressMs,
        repeatStartMs = repeatStartMs,
        repeatIntervalMs = repeatIntervalMs,
        listener = object : TouchEngine.Listener {
            override fun hitTest(x: Float, y: Float): PlacedKey? =
                geometry.hitTest(x, y, slopPx)

            override fun containsKey(key: PlacedKey, x: Float, y: Float): Boolean =
                key.slot.contains(x, y)

            override fun beginGestureFrame() {
                batchingGestureFrame = true
            }

            override fun endGestureFrame() {
                batchingGestureFrame = false
                if (gestureFramePending) {
                    gestureFramePending = false
                    host.requestGestureFrame()
                }
            }

            override fun onPress(key: PlacedKey) {
                if (batchingGestureFrame) {
                    gestureFramePending = true
                    return
                }
                host.onPressFeedback()
                host.requestRedraw()
            }

            override fun onRelease(key: PlacedKey?) {
                if (consumeOnRelease) {
                    consumeOnRelease = false
                    modifiers.consumeOneShot()
                }
                if (batchingGestureFrame) gestureFramePending = true
                else host.requestRedraw()
            }

            override fun onTap(key: PlacedKey) {
                handleTap(key)
            }

            override fun onLongPress(key: PlacedKey): Boolean {
                if (key.spec.code == KeyCodes.SHIFT) {
                    modifierLongPressUndo = KeyCodes.SHIFT to modifiers.shift
                    modifiers.onShiftLongPress()
                    host.requestRedraw()
                    return true
                }
                KeyCodes.stickyModifierFor(key.spec.code)?.let { modifier ->
                    modifierLongPressUndo = key.spec.code to modifiers.stickyState(modifier)
                    modifiers.onStickyLongPress(modifier)
                    host.requestRedraw()
                    return true
                }
                host.onLongPress(key.spec.code, key.spec.popupCharacters)
                // Popup-capable keys consume release so selecting an accent does
                // not also commit the base character underneath the picker.
                return key.spec.popupCharacters.isNotBlank()
            }

            override fun onRepeat(key: PlacedKey) {
                if (needsKeyStroke(key.spec.code)) {
                    // Keep Ctrl/Shift held for the whole repeat (Shift+arrow
                    // selection, Ctrl+Backspace); clear one-shots on release.
                    host.onKeyStroke(strokeFor(key.spec))
                    consumeOnRelease = true
                } else {
                    host.onKey(key.spec.code)
                }
            }

            override fun onGestureProgress(keys: List<PlacedKey>) {
                host.onGestureProgress(keys)
                if (batchingGestureFrame) gestureFramePending = true
                else host.requestGestureFrame()
            }

            override fun onCursorSteps(steps: Int) {
                host.onCursorSteps(steps)
            }

            override fun isChordModifier(key: PlacedKey): Boolean =
                key.spec.code == KeyCodes.SHIFT || KeyCodes.stickyModifierFor(key.spec.code) != null

            override fun onChordHold(key: PlacedKey, longPressFired: Boolean) {
                val code = key.spec.code
                // Resting on the modifier long enough to lock it, then typing with
                // another finger, is a chord: put the lock back the way it was.
                val undo = modifierLongPressUndo
                if (longPressFired && undo != null && undo.first == code) {
                    if (code == KeyCodes.SHIFT) modifiers.restoreShift(undo.second)
                    else KeyCodes.stickyModifierFor(code)?.let { modifiers.restoreSticky(it, undo.second) }
                }
                modifierLongPressUndo = null
                if (code == KeyCodes.SHIFT) modifiers.holdShift()
                else KeyCodes.stickyModifierFor(code)?.let(modifiers::holdSticky)
                host.requestRedraw()
            }

            override fun onChordRelease(key: PlacedKey) {
                val code = key.spec.code
                if (code == KeyCodes.SHIFT) modifiers.releaseShift()
                else KeyCodes.stickyModifierFor(code)?.let(modifiers::releaseSticky)
                host.requestRedraw()
            }

            override fun onGesturePath(keys: List<PlacedKey>) {
                if (!swipeTypingEnabled || modifiers.activeStickyModifiers().isNotEmpty()) {
                    // Ctrl+swipe has no meaning; treat it as a tap on the last key.
                    keys.lastOrNull()?.let(::handleTap)
                    return
                }
                val path = keys.mapNotNull { key ->
                    key.spec.code.toChar().takeIf { it.isLetter() }
                }.joinToString(separator = "")
                if (path.length >= 2) {
                    val casedPath = if (modifiers.isShifted) {
                        path.replaceFirstChar { it.uppercaseChar() }
                    } else path
                    modifiers.onLetterCommitted()
                    host.onGestureWord(casedPath)
                    host.requestRedraw()
                }
            }
        },
    )

    fun resize(
        widthPx: Float,
        heightPx: Float,
        orientation: LayoutOrientation,
        paddingHorizontalPx: Float,
        paddingVerticalPx: Float,
        gapPx: Float,
    ) {
        this.orientation = orientation
        this.paddingHorizontalPx = paddingHorizontalPx
        this.paddingVerticalPx = paddingVerticalPx
        this.gapPx = gapPx
        this.slopPx = (if (heightPx > 0f) {
            val rows = layouts.spec(modifiers.layer, orientation).totalRowWeight.coerceAtLeast(1f)
            (heightPx / rows) * 0.45f
        } else 0f)
        rebuildGeometry(widthPx, heightPx)
        host.requestRedraw()
    }

    fun applyTypingSettings(swipeTypingEnabled: Boolean, longPressMs: Long, chordingEnabled: Boolean = true) {
        this.swipeTypingEnabled = swipeTypingEnabled
        this.chordingEnabled = chordingEnabled
        touch.longPressMs = longPressMs.coerceAtLeast(1L)
    }

    fun down(pointerId: Int, x: Float, y: Float) = touch.down(pointerId, x, y)
    fun move(pointerId: Int, x: Float, y: Float) = touch.move(pointerId, x, y)
    fun up(pointerId: Int, x: Float, y: Float) = touch.up(pointerId, x, y)
    fun cancel() = touch.cancel()

    fun labelFor(key: PlacedKey): String = modifiers.labelFor(key.spec)

    fun isPressed(key: PlacedKey): Boolean = touch.pressed?.id == key.id

    fun isModifierActive(key: PlacedKey): Boolean {
        return when (key.spec.code) {
            KeyCodes.SHIFT -> modifiers.isShifted
            KeyCodes.MODE_CHANGE -> modifiers.layer == KeyboardLayer.SYMBOLS
            else -> KeyCodes.stickyModifierFor(key.spec.code)?.let(modifiers::isStickyActive) ?: false
        }
    }

    fun commitPopupCharacter(character: Char) {
        touch.cancel()
        val code = modifiers.applyToCode(character.code)
        modifiers.onLetterCommitted()
        host.onPopupCharacter(code)
        host.requestRedraw()
    }

    /** The layout provider's config changed (layout mode, terminal row). */
    fun layoutsChanged() {
        touch.cancel()
        rebuildGeometry(geometry.widthPx, geometry.heightPx)
        host.requestRedraw()
    }

    fun reset() {
        touch.cancel()
        modifiers.reset()
        rebuildGeometry(geometry.widthPx, geometry.heightPx)
        host.requestRedraw()
    }

    private fun handleTap(key: PlacedKey) {
        when (key.spec.code) {
            KeyCodes.SHIFT -> {
                modifiers.onShiftTap(scheduler.nowMs())
                host.requestRedraw()
            }
            KeyCodes.MODE_CHANGE -> {
                modifiers.toggleLayer()
                rebuildGeometry(geometry.widthPx, geometry.heightPx)
                host.requestRedraw()
            }
            KeyCodes.CTRL, KeyCodes.ALT, KeyCodes.META -> {
                modifiers.onStickyTap(KeyCodes.stickyModifierFor(key.spec.code)!!, scheduler.nowMs())
                host.requestRedraw()
            }
            else -> {
                if (needsKeyStroke(key.spec.code)) {
                    host.onKeyStroke(strokeFor(key.spec))
                    modifiers.consumeOneShot()
                    host.requestRedraw()
                    return
                }
                val code = modifiers.applyToKey(key.spec)
                if (modifiers.consumesShift(key.spec)) {
                    modifiers.onLetterCommitted()
                    host.requestRedraw()
                }
                host.onKey(code)
            }
        }
    }

    private fun needsKeyStroke(code: Int): Boolean =
        KeyCodes.isKeyEventKey(code) || modifiers.activeStickyModifiers().isNotEmpty()

    private fun strokeFor(spec: KeySpec): KeyStroke = KeyStroke(
        code = modifiers.applyToKey(spec),
        modifiers = modifiers.activeStickyModifiers(),
        shift = modifiers.isShifted,
    )

    private fun rebuildGeometry(widthPx: Float, heightPx: Float) {
        val spec = layouts.spec(modifiers.layer, orientation)
        if (heightPx > 0f) {
            slopPx = (heightPx / spec.totalRowWeight.coerceAtLeast(1f)) * 0.45f
        }
        geometry = KeyboardGeometry.place(
            spec = spec,
            orientation = orientation,
            widthPx = widthPx,
            heightPx = heightPx,
            paddingHorizontalPx = paddingHorizontalPx,
            paddingVerticalPx = paddingVerticalPx,
            gapPx = gapPx,
        )
    }
}
