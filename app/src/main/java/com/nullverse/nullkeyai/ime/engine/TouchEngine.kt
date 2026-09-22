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
        fun onPress(key: PlacedKey)
        fun onRelease(key: PlacedKey?)
        fun onTap(key: PlacedKey)
        fun onLongPress(key: PlacedKey): Boolean
        fun onRepeat(key: PlacedKey)
        fun onGesturePath(keys: List<PlacedKey>) {}
        fun onGestureProgress(keys: List<PlacedKey>) {}
    }

    var activePointerId: Int? = null
        private set

    var pressed: PlacedKey? = null
        private set

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

    fun down(pointerId: Int, x: Float, y: Float) {
        if (activePointerId != null) return
        activePointerId = pointerId
        val key = listener.hitTest(x, y)
        if (key == null) {
            activePointerId = null
            return
        }
        gesturePath.clear()
        gesturePath += key
        gestureDistancePx = 0f
        gestureStarted = false
        lastX = x
        lastY = y
        gestureStartX = x
        gestureStartY = y
        longPressFired = false
        press(key)
    }

    fun move(pointerId: Int, x: Float, y: Float) {
        if (activePointerId != pointerId) return
        val originX = lastX
        val originY = lastY
        val dx = x - originX
        val dy = y - originY
        val segment = kotlin.math.sqrt(dx * dx + dy * dy)
        gestureDistancePx += segment
        lastX = x
        lastY = y
        // A batched MOVE often jumps several keys. Sample the segment so the
        // trail contains the letters the finger crossed, including the endpoint.
        val samples = sampleCount(segment)
        for (index in 1..samples) {
            val t = index.toFloat() / samples.toFloat()
            visit(originX + dx * t, originY + dy * t, endpoint = index == samples)
        }
    }

    private fun sampleCount(segment: Float): Int {
        if (segment <= 0f) return 1
        val key = pressed ?: gesturePath.lastOrNull() ?: return 1
        val step = kotlin.math.min(key.slot.width, key.slot.height) * SAMPLE_STEP_FRACTION
        if (step <= 0f) return 1
        return kotlin.math.ceil(segment / step).toInt().coerceIn(1, MAX_SWIPE_SAMPLES)
    }

    private fun visit(x: Float, y: Float, endpoint: Boolean) {
        val key = listener.hitTest(x, y)
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
                listener.onGestureProgress(gesturePath.toList())
            }
            press(key)
            if (gestureStarted) {
                longPressTask?.cancel()
                longPressTask = null
            }
        }
    }

    fun up(pointerId: Int, x: Float, y: Float) {
        if (activePointerId != pointerId) return
        // Allow a small lift-outside-key still to count as the pressed key.
        val key = listener.hitTest(x, y) ?: pressed
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
        val isSwipe = !heldLongPress &&
            gesturePath.size >= 2 &&
            gestureDistancePx >= minGestureDistance &&
            gesturePath.all { it.spec.code.toChar().isLetter() }
        when {
            isSwipe -> listener.onGesturePath(gesturePath.toList())
            current != null && !wasRepeatable && !consumed && !(heldLongPress && gestureStarted) -> {
                listener.onTap(current)
            }
        }
        resetPointer()
    }

    fun cancel() {
        val current = pressed
        cancelTasks()
        if (current != null) listener.onRelease(current)
        resetPointer()
    }

    private fun press(key: PlacedKey) {
        cancelTasks()
        pressed = key
        if (!longPressFired) longPressConsumed = false
        listener.onPress(key)
        if (longPressFired) return
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
        gestureDistancePx = 0f
        gestureStarted = false
        gestureStartX = 0f
        gestureStartY = 0f
        listener.onGestureProgress(emptyList())
    }

    private companion object {
        const val SAMPLE_STEP_FRACTION = 0.25f
        const val MAX_SWIPE_SAMPLES = 48
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
