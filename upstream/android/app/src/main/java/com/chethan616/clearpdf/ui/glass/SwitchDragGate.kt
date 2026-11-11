package com.chethan616.clearpdf.ui.glass

import kotlin.math.abs

/** Vertical scrolling permanently rejects this switch gesture; jitter is not a drag. */
internal class SwitchDragGate(private val slop: Float) {
    private var x = 0f
    private var y = 0f
    private var horizontal = false
    private var rejected = false
    val shouldCommit: Boolean get() = horizontal && !rejected
    fun reset() { x = 0f; y = 0f; horizontal = false; rejected = false }
    fun move(dx: Float, dy: Float) {
        x += dx; y += dy
        if (abs(y) >= slop && abs(y) > abs(x)) rejected = true
        if (abs(x) >= slop && abs(x) > abs(y)) horizontal = true
    }
    fun cancel() { rejected = true }
}
