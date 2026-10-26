package com.nullverse.nullkeyai.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 4-row, 5-row (+ terminal row), and Full layouts. */
class KeyboardLayoutModesTest {

    private fun spec(
        mode: KeyboardLayoutMode,
        orientation: LayoutOrientation = LayoutOrientation.PORTRAIT,
        layer: KeyboardLayer = KeyboardLayer.LETTERS,
        terminalRow: Boolean = false,
        force: Boolean = false,
    ): KeyboardLayoutSpec {
        val config = KeyboardLayoutConfig(
            portrait = mode,
            landscape = mode,
            terminalRow = terminalRow,
            forceTerminalRow = force,
        )
        return DefaultKeyboardLayoutProvider { config }.spec(layer, orientation)
    }

    private fun codes(spec: KeyboardLayoutSpec) = spec.rows.flatMap { row -> row.keys.map { it.code } }

    private val letterRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

    private fun assertHasAllLetterRows(spec: KeyboardLayoutSpec) {
        val rowStrings = spec.rows.map { row ->
            row.keys.map { it.code.toChar() }.filter { it.isLetter() }.joinToString("")
        }
        for (letters in letterRows) assertTrue("${spec.id} missing $letters", letters in rowStrings)
    }

    @Test
    fun defaultsMatchTheKeyboardBeforeLayoutModes() {
        val provider = DefaultKeyboardLayoutProvider()
        assertSame(
            DefaultKeyboardLayoutProvider.LETTERS_PORTRAIT,
            provider.spec(KeyboardLayer.LETTERS, LayoutOrientation.PORTRAIT),
        )
        assertSame(
            DefaultKeyboardLayoutProvider.LETTERS_LANDSCAPE,
            provider.spec(KeyboardLayer.LETTERS, LayoutOrientation.LANDSCAPE),
        )
        assertSame(DefaultKeyboardLayoutProvider.SYMBOLS, provider.spec(KeyboardLayer.SYMBOLS, LayoutOrientation.PORTRAIT))
        assertEquals(KeyboardLayoutMode.FOUR_ROW, KeyboardLayoutConfig.defaultModeFor(LayoutOrientation.PORTRAIT))
        assertEquals(KeyboardLayoutMode.FIVE_ROW, KeyboardLayoutConfig.defaultModeFor(LayoutOrientation.LANDSCAPE))
    }

    @Test
    fun fourRowHasFourRowsInEitherOrientation() {
        assertEquals(4, spec(KeyboardLayoutMode.FOUR_ROW).rows.size)
        assertEquals(4, spec(KeyboardLayoutMode.FOUR_ROW, LayoutOrientation.LANDSCAPE).rows.size)
    }

    @Test
    fun fiveRowAddsANumberRow() {
        val s = spec(KeyboardLayoutMode.FIVE_ROW)
        assertEquals(5, s.rows.size)
        assertEquals("1234567890", s.rows.first().keys.joinToString("") { it.label })
        assertHasAllLetterRows(s)
    }

    @Test
    fun terminalRowIsOptionalOnFiveRow() {
        val s = spec(KeyboardLayoutMode.FIVE_ROW, terminalRow = true)
        assertEquals(6, s.rows.size)
        assertEquals(
            listOf(
                KeyCodes.ESCAPE, KeyCodes.TAB, KeyCodes.CTRL, KeyCodes.ALT,
                KeyCodes.ARROW_LEFT, KeyCodes.ARROW_DOWN, KeyCodes.ARROW_UP, KeyCodes.ARROW_RIGHT,
                '-'.code, '/'.code, '|'.code, '~'.code,
            ),
            s.rows.first().keys.map { it.code },
        )
        assertTrue(s.rows.first().heightWeight < 1f)
        assertNotEquals(spec(KeyboardLayoutMode.FIVE_ROW).id, s.id)
        // Terminal row setting alone does not change the 4-row layout.
        assertEquals(4, spec(KeyboardLayoutMode.FOUR_ROW, terminalRow = true).rows.size)
    }

    @Test
    fun terminalModeForcesTheTerminalRowOnAnyMode() {
        assertEquals(KeyCodes.ESCAPE, spec(KeyboardLayoutMode.FOUR_ROW, force = true).rows.first().keys.first().code)
        assertEquals(
            KeyCodes.ESCAPE,
            spec(KeyboardLayoutMode.FOUR_ROW, layer = KeyboardLayer.SYMBOLS, force = true).rows.first().keys.first().code,
        )
    }

    @Test
    fun fullLayoutHasPcKeys() {
        val s = spec(KeyboardLayoutMode.FULL)
        val all = codes(s)
        val required = listOf(
            KeyCodes.ESCAPE, KeyCodes.TAB, KeyCodes.CTRL, KeyCodes.ALT, KeyCodes.META,
            KeyCodes.ARROW_LEFT, KeyCodes.ARROW_RIGHT, KeyCodes.ARROW_UP, KeyCodes.ARROW_DOWN,
            KeyCodes.HOME, KeyCodes.END, KeyCodes.PAGE_UP, KeyCodes.PAGE_DOWN,
            KeyCodes.SHIFT, KeyCodes.DELETE, KeyCodes.SPACE, KeyCodes.DONE, KeyCodes.MODE_CHANGE,
        ) + KeyCodes.FUNCTION_KEYS
        for (code in required) assertTrue("missing $code", code in all)
        assertHasAllLetterRows(s)
        assertEquals(6, s.rows.size)
        // Full is the same in both orientations, with distinct ids.
        assertEquals(all, codes(spec(KeyboardLayoutMode.FULL, LayoutOrientation.LANDSCAPE)))
        assertNotEquals(s.id, spec(KeyboardLayoutMode.FULL, LayoutOrientation.LANDSCAPE).id)
    }

    @Test
    fun fullSymbolsKeepTheTerminalRow() {
        val s = spec(KeyboardLayoutMode.FULL, layer = KeyboardLayer.SYMBOLS)
        assertEquals(KeyCodes.ESCAPE, s.rows.first().keys.first().code)
    }

    @Test
    fun everyNewKeyHasALabel() {
        for (mode in KeyboardLayoutMode.entries) {
            for (layer in KeyboardLayer.entries) {
                spec(mode, layer = layer, terminalRow = true).rows.flatMap { it.keys }.forEach {
                    assertTrue("${mode.name} ${it.code}", it.label.isNotBlank())
                }
            }
        }
    }

    @Test
    fun shortRowsGetLessHeight() {
        val s = spec(KeyboardLayoutMode.FIVE_ROW, terminalRow = true)
        val geometry = KeyboardGeometry.place(s, LayoutOrientation.PORTRAIT, 1000f, 580f, 0f, 0f, 0f)
        val esc = geometry.placedKeys.first { it.spec.code == KeyCodes.ESCAPE }
        val q = geometry.placedKeys.first { it.spec.code == 'q'.code }
        assertEquals(80f, esc.slot.height, 0.01f)
        assertEquals(100f, q.slot.height, 0.01f)
        assertEquals(geometry.placedKeys.maxOf { it.slot.bottom }, 580f, 0.01f)
        // Every point hits a key in the right row.
        assertEquals(KeyCodes.ESCAPE, geometry.hitTest(10f, 79f)?.spec?.code)
        assertEquals('1'.code, geometry.hitTest(10f, 81f)?.spec?.code)
    }

    @Test
    fun heightCapLeavesRoomForTheTextField() {
        assertEquals(500, cappedKeyboardHeightPx(preferredPx = 500, defaultLayoutPx = 400, screenHeightPx = 2000))
        assertEquals(1000, cappedKeyboardHeightPx(preferredPx = 1400, defaultLayoutPx = 400, screenHeightPx = 2000))
        // The old default height is never shrunk, even on a short screen.
        assertEquals(400, cappedKeyboardHeightPx(preferredPx = 600, defaultLayoutPx = 400, screenHeightPx = 600))
    }

    @Test
    fun weightedHeightMatchesIntRowsForPlainLayouts() {
        assertEquals(
            preferredKeyboardHeightPx(2f, LayoutOrientation.PORTRAIT, 4),
            preferredKeyboardHeightPx(2f, LayoutOrientation.PORTRAIT, 4f),
        )
        assertTrue(
            preferredKeyboardHeightPx(2f, LayoutOrientation.PORTRAIT, 4.8f) >
                preferredKeyboardHeightPx(2f, LayoutOrientation.PORTRAIT, 4),
        )
    }
}

/** Controller behaviour on the Full and 5-row layouts. */
class FullLayoutControllerTest {
    private class Host : KeyboardController.Host {
        val keys = mutableListOf<Int>()
        val strokes = mutableListOf<KeyStroke>()
        val words = mutableListOf<String>()
        override fun requestRedraw() {}
        override fun onKey(code: Int) { keys += code }
        override fun onKeyStroke(stroke: KeyStroke) { strokes += stroke }
        override fun onLongPress(code: Int, popupCharacters: String) {}
        override fun onGestureWord(path: String) { words += path }
    }

    private var config = KeyboardLayoutConfig(portrait = KeyboardLayoutMode.FULL)
    private val scheduler = ManualScheduler()
    private val host = Host()
    private val controller = KeyboardController(
        layouts = DefaultKeyboardLayoutProvider { config },
        scheduler = scheduler,
        host = host,
    ).also { it.resize(1080f, 700f, LayoutOrientation.PORTRAIT, 0f, 0f, 0f) }

    private fun key(code: Int) = controller.geometry.placedKeys.first { it.spec.code == code }

    private fun tap(code: Int) {
        val k = key(code)
        controller.down(0, k.slot.centerX, k.slot.centerY)
        controller.up(0, k.slot.centerX, k.slot.centerY)
        scheduler.advance(1_000)
    }

    @Test
    fun shiftedNumberRowTypesTheSymbolAndReleasesShift() {
        tap(KeyCodes.SHIFT)
        assertEquals("!", controller.labelFor(key('1'.code)))
        tap('1'.code)
        tap('1'.code)
        assertEquals(listOf('!'.code, '1'.code), host.keys)
        assertFalse(controller.modifiers.isShifted)
    }

    @Test
    fun ctrlKeyOnFullLayoutSendsChord() {
        tap(KeyCodes.CTRL)
        tap('c'.code)
        assertEquals(listOf(KeyStroke('c'.code, setOf(StickyModifier.CTRL))), host.strokes)
    }

    @Test
    fun swipeStillTypesOnLetterRows() {
        val q = key('q'.code)
        val w = key('w'.code)
        val e = key('e'.code)
        controller.down(0, q.slot.centerX, q.slot.centerY)
        var t = 0L
        for (k in listOf(q, w, e)) {
            for (step in 0..4) {
                t += 16
                scheduler.advance(16)
                controller.move(0, k.slot.left + (k.slot.width * step / 4f).coerceAtMost(k.slot.width - 1f), k.slot.centerY)
            }
        }
        controller.up(0, e.slot.centerX, e.slot.centerY)
        assertEquals(listOf("qwe"), host.words)
    }

    @Test
    fun switchingLayoutRebuildsGeometry() {
        assertTrue(controller.geometry.placedKeys.any { it.spec.code == KeyCodes.F1 })
        config = KeyboardLayoutConfig()
        controller.layoutsChanged()
        assertFalse(controller.geometry.placedKeys.any { it.spec.code == KeyCodes.F1 })
        assertEquals(4, controller.geometry.spec.rows.size)
    }
}
