package com.chethan616.clearpdf.ui

/** A held or dragged row must never turn into a click when the finger is released. */
internal class RecentGestureGate {
    private var active = false
    private var cancelled = false
    private var dragged = false
    private var x = 0f
    private var y = 0f
    private var clicksBlockedUntil = Long.MIN_VALUE
    private var pressedAt: Long? = null
    val hasDragged: Boolean get() = dragged

    fun press(now: Long) { pressedAt = now }
    fun release(now: Long) {
        if (pressedAt?.let { now - it > 300 } == true) clicksBlockedUntil = maxOf(clicksBlockedUntil, now + 350)
        pressedAt = null
    }

    fun start() { active = true; cancelled = false; dragged = false; x = 0f; y = 0f }
    fun move(dx: Float, dy: Float, threshold: Float) {
        x += dx; y += dy
        if (x * x + y * y >= threshold * threshold) dragged = true
    }
    fun cancel() { cancelled = true }
    fun finish(now: Long, reordered: Boolean): Boolean {
        val select = active && !cancelled && !dragged && !reordered
        active = false
        clicksBlockedUntil = now + 350
        return select
    }
    fun allowsClick(now: Long) = !active && now >= clicksBlockedUntil && (pressedAt?.let { now - it <= 300 } ?: true)
}
