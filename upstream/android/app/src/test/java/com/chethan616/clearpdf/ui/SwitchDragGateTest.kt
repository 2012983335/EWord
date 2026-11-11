package com.chethan616.clearpdf.ui

import com.chethan616.clearpdf.ui.glass.SwitchDragGate
import org.junit.Assert.*
import org.junit.Test

class SwitchDragGateTest {
    @Test fun verticalScrollWithHorizontalJitterNeverCommits() {
        val gesture = SwitchDragGate(8f)
        gesture.move(2f, 20f)
        assertFalse(gesture.shouldCommit)
        gesture.move(50f, 0f)
        assertFalse(gesture.shouldCommit)
    }
    @Test fun horizontalDragRequiresTheMovementThreshold() {
        val gesture = SwitchDragGate(8f)
        gesture.move(3f, 1f)
        assertFalse(gesture.shouldCommit)
        gesture.move(10f, 0f)
        assertTrue(gesture.shouldCommit)
    }
    @Test fun cancelledHorizontalDragDoesNotCommit() {
        val gesture = SwitchDragGate(8f)
        gesture.move(20f, 0f)
        gesture.cancel()
        assertFalse(gesture.shouldCommit)
    }
    @Test fun theNextGestureCanStartNormally() {
        val gesture = SwitchDragGate(8f)
        gesture.move(0f, 30f)
        gesture.reset()
        gesture.move(-20f, 0f)
        assertTrue(gesture.shouldCommit)
    }
}
