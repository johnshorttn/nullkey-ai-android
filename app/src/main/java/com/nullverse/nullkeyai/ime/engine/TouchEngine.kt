package com.nullverse.nullkeyai.ime.engine

fun interface Cancellable {
    fun cancel()
}

interface TaskScheduler {
    fun nowMs(): Long
    fun schedule(delayMs: Long, action: () -> Unit): Cancellable
}

/**
 * Pointer tracker for a single active finger: hit-testing, press/release,
 * sliding between keys, long-press, and key-repeat.
 *
 * Two-finger chording: when a second finger lands on a key while the active
 * finger is still resting on a modifier ([Listener.isChordModifier]) and has
 * not slid off it, the first finger becomes a held modifier
 * ([Listener.onChordHold]) and the second finger becomes the active one. Every
 * key typed while it is held gets the modifier. Lifting the held finger calls
 * [Listener.onChordRelease] and never produces a tap, so it does not toggle
 * one-shot or lock. A key whose finger landed while the modifier was held still
 * gets it if the modifier finger lifts first (a rolling chord). A modifier
 * touched by one finger alone still taps, double-taps and long-presses as
 * before. Swipe typing only starts from single-finger gestures.
 *
 * Geometry is supplied through [Listener.hitTest] so this class stays free of
 * Android view types and is unit-testable with a fake [TaskScheduler].
 */
class TouchEngine(
    private val scheduler: TaskScheduler,
    var longPressMs: Long = KeyboardEngineDefaults.LONG_PRESS_MS,
    private val repeatStartMs: Long = KeyboardEngineDefaults.REPEAT_START_MS,
    private val repeatIntervalMs: Long = KeyboardEngineDefaults.REPEAT_INTERVAL_MS,
    private val listener: Listener,
) {
    interface Listener {
        fun hitTest(x: Float, y: Float): PlacedKey?

        /** True when [x], [y] is still inside [key], so a swipe sample can skip a full scan. */
        fun containsKey(key: PlacedKey, x: Float, y: Float): Boolean = false
        fun onPress(key: PlacedKey)
        fun onRelease(key: PlacedKey?)
        fun onTap(key: PlacedKey)
        fun onLongPress(key: PlacedKey): Boolean
        fun onRepeat(key: PlacedKey)
        fun onGesturePath(keys: List<PlacedKey>) {}
        fun onGestureProgress(keys: List<PlacedKey>) {}
        fun onCursorSteps(steps: Int) {}

        /** Shift, Ctrl, Alt or Meta: keys one finger can hold while another types. */
        fun isChordModifier(key: PlacedKey): Boolean = false

        /**
         * [key]'s finger is now holding it for a chord. [longPressFired] is true
         * when its long-press already ran (for a modifier, a lock to undo).
         */
        fun onChordHold(key: PlacedKey, longPressFired: Boolean) {}

        /** The finger holding [key] lifted, or the touch was cancelled. */
        fun onChordRelease(key: PlacedKey) {}

        /** A move may cross several keys. Hosts should redraw once, after [endGestureFrame]. */
        fun beginGestureFrame() {}
        fun endGestureFrame() {}
    }

    var activePointerId: Int? = null
        private set

    var pressed: PlacedKey? = null
        private set

    /** Off: a second finger is ignored, as before chording existed. */
    var chordingEnabled: Boolean = true

    /** Modifier keys held by fingers other than the active one, by pointer id. */
    private val heldChords = linkedMapOf<Int, PlacedKey>()

    /** Held fingers that lifted while a chorded key was still down. */
    private val pendingChordReleases = mutableListOf<PlacedKey>()

    /** The active finger landed while a modifier was held. */
    private var chordedGesture = false

    val heldChordKeys: List<PlacedKey> get() = heldChords.values.toList()

    private var longPressConsumed: Boolean = false
    private var longPressFired: Boolean = false
    private var longPressTask: Cancellable? = null
    private var repeatTask: Cancellable? = null
    private val gesturePath = mutableListOf<PlacedKey>()
    private var gestureDistancePx = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var gestureStarted = false
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var cursorDrag: SpaceCursorDrag? = null
    private var gestureFrameDirty = false

    fun down(pointerId: Int, x: Float, y: Float) {
        if (pointerId in heldChords) return
        if (activePointerId != null && !holdActiveForChord(x, y)) return
        val key = listener.hitTest(x, y) ?: return
        activePointerId = pointerId
        chordedGesture = heldChords.isNotEmpty()
        gesturePath.clear()
        gesturePath += key
        gestureFrameDirty = false
        gestureDistancePx = 0f
        gestureStarted = false
        lastX = x
        lastY = y
        gestureStartX = x
        gestureStartY = y
        longPressFired = false
        cursorDrag = null
        press(key)
        if (key.spec.code == KeyCodes.SPACE) {
            cursorDrag = SpaceCursorDrag(SpaceCursorDrag.stepPxFor(key)).also { it.begin(x, y) }
        }
    }

    fun move(pointerId: Int, x: Float, y: Float) {
        if (activePointerId != pointerId) return
        val originX = lastX
        val originY = lastY
        val dx = x - originX
        val dy = y - originY
        val segment = kotlin.math.sqrt(dx * dx + dy * dy)
        val drag = cursorDrag
        if (drag != null) {
            when (drag.axis(x, y)) {
                SpaceCursorDrag.Axis.VERTICAL -> cursorDrag = null
                SpaceCursorDrag.Axis.UNDECIDED,
                SpaceCursorDrag.Axis.HORIZONTAL,
                -> {
                    val steps = drag.move(x, y)
                    lastX = x
                    lastY = y
                    if (drag.engaged) {
                        longPressTask?.cancel()
                        longPressTask = null
                        if (steps != 0) listener.onCursorSteps(steps)
                    }
                    return
                }
            }
        }
        gestureDistancePx += segment
        lastX = x
        lastY = y
        // One frame for every key this segment enters. Haptics and accessibility
        // rebuilds stay off the per-key path so the trail can keep up with the finger.
        listener.beginGestureFrame()
        val samples = sampleCount(segment)
        for (index in 1..samples) {
            val t = index.toFloat() / samples.toFloat()
            visit(originX + dx * t, originY + dy * t, endpoint = index == samples)
        }
        if (gestureFrameDirty) {
            gestureFrameDirty = false
            listener.onGestureProgress(gesturePath.toList())
        }
        listener.endGestureFrame()
    }

    private fun sampleCount(segment: Float): Int {
        if (segment <= 0f) return 1
        val key = pressed ?: gesturePath.lastOrNull() ?: return 1
        val step = kotlin.math.min(key.slot.width, key.slot.height) * SAMPLE_STEP_FRACTION
        if (step <= 0f) return 1
        return kotlin.math.ceil(segment / step).toInt().coerceIn(1, MAX_SWIPE_SAMPLES)
    }

    private fun locate(x: Float, y: Float): PlacedKey? {
        val current = pressed
        if (current != null && listener.containsKey(current, x, y)) return current
        return listener.hitTest(x, y)
    }

    private fun visit(x: Float, y: Float, endpoint: Boolean) {
        val key = locate(x, y)
        // A chord can clip a gap between keys. Keep going until the endpoint,
        // which still releases the pressed key when the finger leaves the board.
        if (key == null && !endpoint) return
        if (key?.id == pressed?.id) return
        if (key != null && key.spec.code.toChar().isLetter() && gesturePath.firstOrNull()?.spec?.code?.toChar()?.isLetter() == true) {
            val startKey = gesturePath.first()
            val threshold = kotlin.math.min(startKey.slot.width, startKey.slot.height) * 0.35f
            val fromStartX = x - gestureStartX
            val fromStartY = y - gestureStartY
            gestureStarted = gestureStarted || kotlin.math.sqrt(fromStartX * fromStartX + fromStartY * fromStartY) >= threshold

            // Once a swipe crosses into another letter, long-press must not fire
            // on keys visited while the finger is still moving.
            if (gestureStarted) {
                longPressTask?.cancel()
                longPressTask = null
            }
        }
        listener.onRelease(pressed)
        if (key == null) {
            cancelTasks()
            pressed = null
            longPressConsumed = false
        } else {
            if (gesturePath.lastOrNull()?.id != key.id) {
                gesturePath += key
                gestureFrameDirty = true
            }
            press(key)
            if (gestureStarted) {
                longPressTask?.cancel()
                longPressTask = null
            }
        }
    }

    /**
     * A second finger is landing at [x], [y]. If the active finger rests on a
     * modifier, keep that modifier held and free the engine for the new finger.
     */
    private fun holdActiveForChord(x: Float, y: Float): Boolean {
        if (!chordingEnabled) return false
        val holder = activePointerId ?: return false
        val modifierKey = pressed ?: return false
        if (gesturePath.size != 1 || cursorDrag != null) return false
        if (!listener.isChordModifier(modifierKey)) return false
        if (listener.hitTest(x, y) == null) return false
        val fired = longPressFired
        cancelTasks()
        heldChords[holder] = modifierKey
        listener.onRelease(modifierKey)
        resetPointer()
        listener.onChordHold(modifierKey, fired)
        return true
    }

    fun up(pointerId: Int, x: Float, y: Float) {
        heldChords.remove(pointerId)?.let { key ->
            // A key already down with this modifier keeps it until it is typed.
            if (activePointerId != null && chordedGesture) pendingChordReleases += key
            else listener.onChordRelease(key)
            return
        }
        if (activePointerId != pointerId) return
        if (cursorDrag?.engaged == true) {
            cancelTasks()
            listener.onRelease(pressed)
            resetPointer()
            flushChordReleases()
            return
        }
        // A sideways drift that never reached a cursor step is still a space tap.
        val stickToSpace = cursorDrag != null
        // A flick's last MOVE often stops short of the key under the lift.
        // Sample that gap so the trail's endpoint is the letter that was released.
        if (!stickToSpace && (x != lastX || y != lastY)) {
            move(pointerId, x, y)
        }
        // Allow a small lift-outside-key still to count as the pressed key.
        val key = if (stickToSpace) pressed else (locate(x, y) ?: pressed)
        if (key != null && key.id != pressed?.id) {
            listener.onRelease(pressed)
            press(key)
        }
        val current = pressed
        val consumed = longPressConsumed
        val wasRepeatable = current?.spec?.isRepeatable == true
        val heldLongPress = longPressFired
        cancelTasks()
        listener.onRelease(current)
        val minGestureDistance = current?.let { kotlin.math.min(it.slot.width, it.slot.height) * 0.75f } ?: Float.MAX_VALUE
        val letterTrail = gesturePath.size >= 2 && gesturePath.all { it.spec.code.toChar().isLetter() }
        val farEnough = gestureDistancePx >= minGestureDistance
        // A finger that rests on the first key long enough for the accent timer
        // still means to swipe if it then travels across several letters. A
        // one-key slip after that timer stays a long-press, not a word.
        val continuedPastHold = gesturePath.size >= CONTINUED_SWIPE_KEYS ||
            gestureDistancePx >= minGestureDistance * CONTINUED_SWIPE_DISTANCE
        val isSwipe = !chordedGesture && letterTrail && farEnough && (!heldLongPress || continuedPastHold)
        when {
            isSwipe -> listener.onGesturePath(gesturePath.toList())
            current != null && !wasRepeatable && !consumed && !(heldLongPress && gestureStarted) -> {
                listener.onTap(current)
            }
        }
        resetPointer()
        flushChordReleases()
    }

    fun cancel() {
        val current = pressed
        cancelTasks()
        if (current != null) listener.onRelease(current)
        resetPointer()
        val held = heldChords.values.toList()
        heldChords.clear()
        pendingChordReleases += held
        flushChordReleases()
    }

    private fun flushChordReleases() {
        if (pendingChordReleases.isEmpty()) return
        val keys = pendingChordReleases.toList()
        pendingChordReleases.clear()
        keys.forEach(listener::onChordRelease)
    }

    private fun press(key: PlacedKey) {
        cancelTasks()
        pressed = key
        if (!longPressFired) longPressConsumed = false
        listener.onPress(key)
        // A key entered after the finger is already moving should not arm another
        // accent timer or the main thread pays a schedule/cancel on every letter.
        if (longPressFired || gestureStarted) return
        if (key.spec.isRepeatable) {
            listener.onRepeat(key)
            scheduleRepeat(repeatStartMs)
        } else {
            longPressTask = scheduler.schedule(longPressMs) {
                val current = pressed ?: return@schedule
                if (current.id == key.id && !current.spec.isRepeatable) {
                    longPressFired = true
                    longPressConsumed = listener.onLongPress(current)
                }
            }
        }
    }

    private fun scheduleRepeat(delayMs: Long) {
        repeatTask = scheduler.schedule(delayMs) {
            val current = pressed ?: return@schedule
            if (!current.spec.isRepeatable) return@schedule
            listener.onRepeat(current)
            scheduleRepeat(repeatIntervalMs)
        }
    }

    private fun cancelTasks() {
        longPressTask?.cancel()
        longPressTask = null
        repeatTask?.cancel()
        repeatTask = null
    }

    private fun resetPointer() {
        activePointerId = null
        pressed = null
        longPressConsumed = false
        longPressFired = false
        gesturePath.clear()
        gestureFrameDirty = false
        gestureDistancePx = 0f
        gestureStarted = false
        gestureStartX = 0f
        gestureStartY = 0f
        cursorDrag = null
        chordedGesture = false
        listener.onGestureProgress(emptyList())
    }

    private companion object {
        const val SAMPLE_STEP_FRACTION = 0.25f
        const val MAX_SWIPE_SAMPLES = 48
        const val CONTINUED_SWIPE_KEYS = 3
        const val CONTINUED_SWIPE_DISTANCE = 2f
    }
}

/**
 * Deterministic scheduler for tests. [advance] runs every due task in time
 * order so newly scheduled repeats still fire.
 */
class ManualScheduler : TaskScheduler {
    private var now: Long = 0L
    private val tasks = mutableListOf<Scheduled>()

    private class Scheduled(
        val at: Long,
        val action: () -> Unit,
        var cancelled: Boolean = false,
    )

    override fun nowMs(): Long = now

    override fun schedule(delayMs: Long, action: () -> Unit): Cancellable {
        val task = Scheduled(now + delayMs, action)
        tasks += task
        return Cancellable { task.cancelled = true }
    }

    fun advance(ms: Long) {
        val target = now + ms
        while (true) {
            val next = tasks
                .filter { !it.cancelled && it.at <= target }
                .minByOrNull { it.at }
                ?: break
            tasks.remove(next)
            now = next.at
            next.action()
        }
        now = target
        tasks.removeAll { it.cancelled }
    }
}
