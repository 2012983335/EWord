package com.chethan616.clearpdf.ui.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.*

enum class SignatureHandle(val x: Int, val y: Int) {
    TopLeft(-1, -1), Top(0, -1), TopRight(1, -1), Right(1, 0),
    BottomRight(1, 1), Bottom(0, 1), BottomLeft(-1, 1), Left(-1, 0),
}

data class SignatureTransform(val rect: Rect, val rotation: Float = 0f) {
    val center get() = rect.center
    fun point(local: Offset): Offset = center + rotateVector(local, rotation)
    fun handle(handle: SignatureHandle): Offset = point(Offset(handle.x * rect.width / 2f, handle.y * rect.height / 2f))
    val bounds: Rect get() {
        val radians = Math.toRadians(rotation.toDouble())
        val w = (abs(cos(radians)) * rect.width + abs(sin(radians)) * rect.height).toFloat()
        val h = (abs(sin(radians)) * rect.width + abs(cos(radians)) * rect.height).toFloat()
        return Rect(center.x - w / 2, center.y - h / 2, center.x + w / 2, center.y + h / 2)
    }

    fun moved(delta: Offset) = copy(rect = rect.translate(delta))

    fun resized(handle: SignatureHandle, delta: Offset): SignatureTransform {
        val anchor = point(Offset(-handle.x * rect.width / 2, -handle.y * rect.height / 2))
        val displacement = rotateVector(this.handle(handle) + delta - anchor, -rotation)
        val corner = handle.x != 0 && handle.y != 0
        val width: Float
        val height: Float
        if (corner) {
            val diagonal = Offset(handle.x * rect.width, handle.y * rect.height)
            val scale = ((displacement.x * diagonal.x + displacement.y * diagonal.y) / (rect.width * rect.width + rect.height * rect.height))
                .coerceAtLeast(max(12f / rect.width, 12f / rect.height))
            width = rect.width * scale; height = rect.height * scale
        } else {
            width = if (handle.x == 0) rect.width else (displacement.x * handle.x).coerceAtLeast(12f)
            height = if (handle.y == 0) rect.height else (displacement.y * handle.y).coerceAtLeast(12f)
        }
        val c = anchor + rotateVector(Offset(handle.x * width / 2, handle.y * height / 2), rotation)
        return copy(rect = Rect(c.x - width / 2, c.y - height / 2, c.x + width / 2, c.y + height / 2))
    }

    fun rotated(start: Offset, end: Offset): SignatureTransform {
        val a = start - center; val b = end - center
        if (a.getDistance() < 1f || b.getDistance() < 1f) return this
        val angle = rotation + Math.toDegrees((atan2(b.y, b.x) - atan2(a.y, a.x)).toDouble()).toFloat()
        return copy(rotation = ((angle + 180f) % 360f + 360f) % 360f - 180f)
    }

    fun constrained(pageWidth: Float, pageHeight: Float): SignatureTransform {
        val extent = bounds
        val scale = minOf(1f, pageWidth / extent.width, pageHeight / extent.height)
        val w = rect.width * scale; val h = rect.height * scale
        val resized = copy(rect = Rect(center.x - w / 2, center.y - h / 2, center.x + w / 2, center.y + h / 2))
        val box = resized.bounds
        val dx = when { box.left < 0 -> -box.left; box.right > pageWidth -> pageWidth - box.right; else -> 0f }
        val dy = when { box.top < 0 -> -box.top; box.bottom > pageHeight -> pageHeight - box.bottom; else -> 0f }
        return resized.moved(Offset(dx, dy))
    }
}

internal fun rotateVector(vector: Offset, degrees: Float): Offset {
    val r = Math.toRadians(degrees.toDouble()); val c = cos(r).toFloat(); val s = sin(r).toFloat()
    return Offset(vector.x * c - vector.y * s, vector.x * s + vector.y * c)
}
