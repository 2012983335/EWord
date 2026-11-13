package com.chethan616.clearpdf.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.chethan616.clearpdf.ui.viewer.SignatureHandle
import com.chethan616.clearpdf.ui.viewer.SignatureTransform
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class SignatureTransformTest {
    private fun box(rotation: Float = 0f) = SignatureTransform(Rect(100f, 150f, 300f, 250f), rotation)
    private fun same(a: Offset, b: Offset) { assertEquals(a.x, b.x, 0.01f); assertEquals(a.y, b.y, 0.01f) }
    @Test fun allCornersKeepAspectAndTheOppositeCornerFixedAfterRotation() {
        val opposite = mapOf(SignatureHandle.TopLeft to SignatureHandle.BottomRight, SignatureHandle.TopRight to SignatureHandle.BottomLeft,
            SignatureHandle.BottomRight to SignatureHandle.TopLeft, SignatureHandle.BottomLeft to SignatureHandle.TopRight)
        for (angle in listOf(0f, 37f, 90f, -130f)) for ((handle, anchor) in opposite) {
            val before = box(angle); val after = before.resized(handle, Offset(handle.x * 30f, handle.y * 20f))
            assertEquals(2f, after.rect.width / after.rect.height, 0.001f)
            same(before.handle(anchor), after.handle(anchor))
        }
    }
    @Test fun sideHandlesChangeOnlyOneDimensionAndKeepOppositeSideFixed() {
        for (angle in listOf(0f, 45f, 90f)) {
            val before = box(angle)
            val right = before.resized(SignatureHandle.Right, Offset(40f, 50f))
            assertEquals(before.rect.height, right.rect.height, 0.001f)
            same(before.handle(SignatureHandle.Left), right.handle(SignatureHandle.Left))
            val top = before.resized(SignatureHandle.Top, Offset(10f, -30f))
            assertEquals(before.rect.width, top.rect.width, 0.001f)
            same(before.handle(SignatureHandle.Bottom), top.handle(SignatureHandle.Bottom))
        }
    }
    @Test fun rotationKeepsTheCenterAndSwapsExtentsAtNinetyDegrees() {
        val before = box(); val after = before.rotated(Offset(200f, 100f), Offset(300f, 200f))
        same(before.center, after.center)
        assertEquals(90f, after.rotation, 0.01f)
        assertEquals(100f, after.bounds.width, 0.01f)
        assertEquals(200f, after.bounds.height, 0.01f)
    }
    @Test fun rotationAcrossAngleBoundaryIsContinuous() {
        val before = box(170f)
        val after = before.rotated(Offset(100f, 201f), Offset(100f, 199f))
        assertTrue(abs(after.rotation - before.rotation) < 5f)
    }
    @Test fun transformsStayInsidePageAndCannotFlipThroughTheirAnchor() {
        val flipped = box().resized(SignatureHandle.Right, Offset(-1000f, 0f))
        assertTrue(flipped.rect.width >= 12f && flipped.rect.height > 0)
        val bounded = box(45f).moved(Offset(-500f, 500f)).constrained(180f, 240f)
        assertTrue(bounded.bounds.left >= -0.01f && bounded.bounds.top >= -0.01f)
        assertTrue(bounded.bounds.right <= 180.01f && bounded.bounds.bottom <= 240.01f)
        assertEquals(2f, bounded.rect.width / bounded.rect.height, 0.001f)
    }
}
