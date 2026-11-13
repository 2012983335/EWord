package com.chethan616.clearpdf.ui

import androidx.compose.ui.geometry.Offset
import com.chethan616.clearpdf.ui.viewer.SignatureErase
import com.chethan616.clearpdf.ui.viewer.SignatureEraseHistory
import org.junit.Assert.*
import org.junit.Test

class SignatureEraseHistoryTest {
    private fun stroke(x: Float) = SignatureErase(listOf(Offset(x, 10f)), 12f)

    @Test fun eachGestureCanBeUndoneAndRedoneIncludingAtBothEnds() {
        val history = SignatureEraseHistory()
        history.undo(); history.redo()
        assertFalse(history.canUndo); assertFalse(history.canRedo)
        history.add(stroke(1f)); history.add(stroke(2f))
        history.undo()
        assertEquals(listOf(stroke(1f)), history.actions)
        assertTrue(history.canRedo)
        history.undo(); history.undo()
        assertTrue(history.actions.isEmpty())
        history.redo(); history.redo(); history.redo()
        assertEquals(listOf(stroke(1f), stroke(2f)), history.actions)
        assertFalse(history.canRedo)
    }

    @Test fun newGestureAfterUndoDropsOnlyTheRedoBranch() {
        val history = SignatureEraseHistory()
        history.add(stroke(1f)); history.add(stroke(2f)); history.undo(); history.add(stroke(3f))
        assertEquals(listOf(stroke(1f), stroke(3f)), history.actions)
        assertFalse(history.canRedo)
    }

    @Test fun clearIsReversibleAndGesturePointsAreCopied() {
        val points = mutableListOf(Offset(1f, 2f))
        val history = SignatureEraseHistory()
        history.add(SignatureErase(points, 8f)); points.clear()
        history.add(SignatureErase(emptyList(), 0f, clearAll = true))
        assertTrue(history.actions.last().clearAll)
        history.undo()
        assertEquals(listOf(Offset(1f, 2f)), history.actions.single().points)
        history.redo()
        assertTrue(history.actions.last().clearAll)
    }
}
