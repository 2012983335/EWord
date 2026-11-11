package com.chethan616.clearpdf.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.chethan616.clearpdf.core.CellSpan

sealed interface OfficeInlineTarget {
    data class Cell(val sheet: Int, val span: CellSpan) : OfficeInlineTarget
    data class Slide(val page: Int, val index: Int, val rect: Rect, val size: Float) : OfficeInlineTarget
}

class OfficeInlineDraft(val docId: ULong, val target: OfficeInlineTarget, initial: String) {
    var value by mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    var persisted = initial
        private set
    val dirty get() = value.text != persisted
    val canAutoSave get() = value.composition == null
    fun acceptInput(input: TextFieldValue, active: OfficeInlineDraft?): Boolean {
        if (this !== active) return false
        value = input
        return true
    }
    // A native write may finish after more characters have been typed. Only acknowledge
    // the snapshot that was actually saved, leaving the newer draft pending.
    fun acknowledge(text: String) { persisted = text }
}
