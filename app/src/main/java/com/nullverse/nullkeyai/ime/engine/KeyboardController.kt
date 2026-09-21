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
        fun onKey(code: Int)
        fun onLongPress(code: Int, popupCharacters: String)
        fun onPopupCharacter(code: Int) { onKey(code) }
        fun onGestureWord(path: String) {}
        fun onGestureProgress(keys: List<PlacedKey>) {}
        fun onPressFeedback() {}
    }

    val modifiers = ModifierController()

    var swipeTypingEnabled: Boolean = true

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

    val touch: TouchEngine = TouchEngine(
        scheduler = scheduler,
        longPressMs = longPressMs,
        repeatStartMs = repeatStartMs,
        repeatIntervalMs = repeatIntervalMs,
        listener = object : TouchEngine.Listener {
            override fun hitTest(x: Float, y: Float): PlacedKey? =
                geometry.hitTest(x, y, slopPx)

            override fun onPress(key: PlacedKey) {
                host.onPressFeedback()
                host.requestRedraw()
            }

            override fun onRelease(key: PlacedKey?) {
                host.requestRedraw()
            }

            override fun onTap(key: PlacedKey) {
                handleTap(key)
            }

            override fun onLongPress(key: PlacedKey): Boolean {
                if (key.spec.code == KeyCodes.SHIFT) {
                    modifiers.onShiftLongPress()
                    host.requestRedraw()
                    return true
                }
                host.onLongPress(key.spec.code, key.spec.popupCharacters)
                // Popup-capable keys consume release so selecting an accent does
                // not also commit the base character underneath the picker.
                return key.spec.popupCharacters.isNotBlank()
            }

            override fun onRepeat(key: PlacedKey) {
                host.onKey(key.spec.code)
            }

            override fun onGestureProgress(keys: List<PlacedKey>) {
                host.onGestureProgress(keys)
                host.requestRedraw()
            }

            override fun onGesturePath(keys: List<PlacedKey>) {
                if (!swipeTypingEnabled) {
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
            val rows = layouts.spec(modifiers.layer, orientation).rows.size.coerceAtLeast(1)
            (heightPx / rows) * 0.45f
        } else 0f)
        rebuildGeometry(widthPx, heightPx)
        host.requestRedraw()
    }

    fun applyTypingSettings(swipeTypingEnabled: Boolean, longPressMs: Long) {
        this.swipeTypingEnabled = swipeTypingEnabled
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
            else -> false
        }
    }

    fun commitPopupCharacter(character: Char) {
        touch.cancel()
        val code = modifiers.applyToCode(character.code)
        modifiers.onLetterCommitted()
        host.onPopupCharacter(code)
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
            else -> {
                val code = modifiers.applyToCode(key.spec.code)
                if (key.spec.code.toChar().isLetter()) {
                    modifiers.onLetterCommitted()
                    host.requestRedraw()
                }
                host.onKey(code)
            }
        }
    }

    private fun rebuildGeometry(widthPx: Float, heightPx: Float) {
        val spec = layouts.spec(modifiers.layer, orientation)
        if (heightPx > 0f) {
            slopPx = (heightPx / spec.rows.size.coerceAtLeast(1)) * 0.45f
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
