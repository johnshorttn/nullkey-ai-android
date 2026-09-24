package com.nullverse.nullkeyai.ime.engine

/**
 * Pixel geometry for one rendered keyboard: each [PlacedKey] has a hit-test
 * [PlacedKey.slot] (the full allocated cell) and a smaller [PlacedKey.visual]
 * rectangle used for drawing so adjacent keys show a gap.
 */
data class KeyboardGeometry(
    val spec: KeyboardLayoutSpec,
    val orientation: LayoutOrientation,
    val widthPx: Float,
    val heightPx: Float,
    val placedKeys: List<PlacedKey>,
) {
    fun keyById(id: Int): PlacedKey? = placedKeys.firstOrNull { it.id == id }

    fun hitTest(
        x: Float,
        y: Float,
        maxSlopPx: Float = 0f,
        preferred: PlacedKey? = null,
    ): PlacedKey? {
        // Samples along a swipe are usually still inside the key under the finger.
        if (preferred != null && preferred.slot.contains(x, y)) return preferred
        keyContaining(x, y)?.let { return it }
        if (maxSlopPx <= 0f) return null
        var best: PlacedKey? = null
        var bestDistance = Float.MAX_VALUE
        for (key in placedKeys) {
            val distance = key.slot.distanceTo(x, y)
            if (distance < bestDistance) {
                bestDistance = distance
                best = key
            }
        }
        return if (best != null && bestDistance <= maxSlopPx) best else null
    }

    /**
     * Keys are stored row by row with a shared top edge. A point inside the
     * board only needs the keys on that row.
     */
    private fun keyContaining(x: Float, y: Float): PlacedKey? {
        val keys = placedKeys
        var index = 0
        while (index < keys.size) {
            val rowTop = keys[index].slot.top
            val rowBottom = keys[index].slot.bottom
            if (y >= rowBottom) {
                val row = rowTop
                while (index < keys.size && keys[index].slot.top == row) index++
                continue
            }
            if (y < rowTop) return null
            while (index < keys.size && keys[index].slot.top == rowTop) {
                val key = keys[index]
                if (key.slot.contains(x, y)) return key
                index++
            }
            return null
        }
        return null
    }

    companion object {
        fun place(
            spec: KeyboardLayoutSpec,
            orientation: LayoutOrientation,
            widthPx: Float,
            heightPx: Float,
            paddingHorizontalPx: Float,
            paddingVerticalPx: Float,
            gapPx: Float,
        ): KeyboardGeometry {
            if (widthPx <= 0f || heightPx <= 0f || spec.rows.isEmpty()) {
                return KeyboardGeometry(spec, orientation, widthPx, heightPx, emptyList())
            }
            val innerWidth = (widthPx - 2f * paddingHorizontalPx).coerceAtLeast(1f)
            val innerHeight = (heightPx - 2f * paddingVerticalPx).coerceAtLeast(1f)
            val rowHeight = innerHeight / spec.rows.size
            val inset = (gapPx / 2f).coerceAtLeast(0f)
            val placed = ArrayList<PlacedKey>()
            var id = 0
            spec.rows.forEachIndexed { rowIndex, row ->
                val totalWeight = row.leadingGapWeight + row.trailingGapWeight +
                    row.keys.sumOf { it.widthWeight.toDouble() }.toFloat()
                val unit = if (totalWeight > 0f) innerWidth / totalWeight else 0f
                var x = paddingHorizontalPx + row.leadingGapWeight * unit
                val top = paddingVerticalPx + rowIndex * rowHeight
                val bottom = top + rowHeight
                for (key in row.keys) {
                    val width = key.widthWeight * unit
                    val slot = KeyBounds(x, top, x + width, bottom)
                    val visual = slot.inset(inset, inset).let { insetRect ->
                        if (insetRect.width <= 0f || insetRect.height <= 0f) slot else insetRect
                    }
                    placed += PlacedKey(id = id, spec = key, slot = slot, visual = visual)
                    id += 1
                    x += width
                }
            }
            return KeyboardGeometry(spec, orientation, widthPx, heightPx, placed)
        }
    }
}

interface KeyboardLayoutProvider {
    fun spec(layer: KeyboardLayer, orientation: LayoutOrientation): KeyboardLayoutSpec
}

fun preferredKeyboardHeightPx(
    density: Float,
    orientation: LayoutOrientation,
    rowCount: Int,
    heightScale: Float = KeyboardInputSettings.HEIGHT_SCALE_DEFAULT,
): Int {
    val keyHeightDp = when (orientation) {
        LayoutOrientation.PORTRAIT -> KeyboardEngineDefaults.PORTRAIT_KEY_HEIGHT_DP
        LayoutOrientation.LANDSCAPE -> KeyboardEngineDefaults.LANDSCAPE_KEY_HEIGHT_DP
    }
    val paddingDp = KeyboardEngineDefaults.VERTICAL_PADDING_DP
    val scale = KeyboardInputSettings.clampHeightScale(heightScale)
    return ((rowCount * keyHeightDp + paddingDp) * density * scale).toInt().coerceAtLeast(1)
}

fun keyboardGapPx(density: Float, orientation: LayoutOrientation): Float {
    val dp = when (orientation) {
        LayoutOrientation.PORTRAIT -> KeyboardEngineDefaults.PORTRAIT_GAP_DP
        LayoutOrientation.LANDSCAPE -> KeyboardEngineDefaults.LANDSCAPE_GAP_DP
    }
    return dp * density
}

fun keyboardPaddingHorizontalPx(density: Float): Float =
    KeyboardEngineDefaults.HORIZONTAL_PADDING_DP * density

fun keyboardPaddingVerticalPx(density: Float): Float =
    KeyboardEngineDefaults.VERTICAL_PADDING_DP * density / 2f
