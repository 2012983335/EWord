package com.chethan616.clearpdf.ui

import org.junit.Assert.*
import org.junit.Test

class RecentGestureGateTest {
    @Test fun aSmallFingerWobbleStillCountsAsAHold() {
        val gesture = RecentGestureGate()
        gesture.start()
        gesture.move(2f, 3f, 8f)
        assertFalse(gesture.hasDragged)
        assertTrue(gesture.finish(1000, false))
        assertFalse(gesture.allowsClick(1001))
    }
    @Test fun aSlowPressBelowTheLongPressThresholdIsNotAFileClick() {
        val gesture = RecentGestureGate()
        gesture.press(1000)
        assertTrue(gesture.allowsClick(1100))
        assertFalse(gesture.allowsClick(1500))
        gesture.release(1500)
        assertFalse(gesture.allowsClick(1501))
        gesture.press(2000)
        assertTrue(gesture.allowsClick(2100))
    }
    @Test fun aHoldSelectsWithoutOpeningOnRelease() {
        val gesture = RecentGestureGate()
        assertTrue(gesture.allowsClick(0))
        gesture.start()
        assertFalse(gesture.allowsClick(1000))
        assertTrue(gesture.finish(1000, false))
        assertFalse(gesture.allowsClick(1000))
        assertTrue(gesture.allowsClick(1400))
    }
    @Test fun draggingEvenBackToTheSamePlaceDoesNotSelectOrOpen() {
        val gesture = RecentGestureGate()
        gesture.start()
        gesture.move(0f, 20f, 12f)
        gesture.move(0f, -20f, 12f)
        assertFalse(gesture.finish(1000, false))
        assertFalse(gesture.allowsClick(1100))
    }
    @Test fun reorderingAndCancelledHoldsNeverBecomeSelection() {
        val gesture = RecentGestureGate()
        gesture.start()
        assertFalse(gesture.finish(1000, true))
        gesture.start()
        gesture.cancel()
        assertFalse(gesture.finish(2000, false))
    }
}
