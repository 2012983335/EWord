package com.chethan616.clearpdf.ui.viewer

import androidx.compose.ui.geometry.Offset

/** Image coordinates; one gesture is one undo step. No bitmap copies in the history. */
data class SignatureErase(val points: List<Offset>, val width: Float, val clearAll: Boolean = false)

class SignatureEraseHistory {
    private val entries = mutableListOf<SignatureErase>()
    private var position = 0
    val actions: List<SignatureErase> get() = entries.take(position)
    val canUndo get() = position > 0
    val canRedo get() = position < entries.size

    fun add(action: SignatureErase) {
        if (!action.clearAll && (action.points.isEmpty() || action.width <= 0f)) return
        while (entries.size > position) entries.removeAt(entries.lastIndex)
        entries.add(action.copy(points = action.points.toList()))
        position++
    }

    fun undo() { if (canUndo) position-- }
    fun redo() { if (canRedo) position++ }
}
