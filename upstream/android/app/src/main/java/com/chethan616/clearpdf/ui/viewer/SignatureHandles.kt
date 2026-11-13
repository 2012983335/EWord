package com.chethan616.clearpdf.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.ui.OpenDocument
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SignatureHandles(doc: OpenDocument, page: Int, source: SignatureTransform, shown: SignatureTransform,
    onPreview: (SignatureTransform?) -> Unit, onCommit: (SignatureTransform) -> Unit) {
    val density = LocalDensity.current
    val ppp = doc.viewer.pixelsPerPoint(page).coerceAtLeast(0.01f)
    val origin = doc.viewer.pageToScreen(page, 0f, 0f)
    fun screen(p: Offset) = origin + p * ppp
    val stem = with(density) { 32.dp.toPx() }
    val hitRadius = with(density) { 20.dp.toPx() }
    val rotationPoint = source.point(Offset(0f, -source.rect.height / 2 - stem / ppp))
    val initialBounds = source.bounds
    val area = Rect(screen(initialBounds.topLeft), screen(initialBounds.bottomRight))
        .expandTo(screen(rotationPoint)).inflate(hitRadius)
    val blue = Color(0xFF448AFF)
    Canvas(Modifier.fillMaxSize()) {
        val c = screen(shown.center)
        val w = shown.rect.width * ppp; val h = shown.rect.height * ppp
        val rect = Rect(c.x - w / 2, c.y - h / 2, c.x + w / 2, c.y + h / 2)
        rotate(shown.rotation, c) {
            drawRect(blue, rect.topLeft, rect.size, style = Stroke(1.5.dp.toPx()))
            SignatureHandle.entries.forEach { handle ->
                val p = Offset(c.x + handle.x * w / 2, c.y + handle.y * h / 2)
                drawCircle(Color.White, 5.dp.toPx(), p)
                drawCircle(blue, 5.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
            }
            val top = Offset(c.x, rect.top)
            val knob = top - Offset(0f, stem)
            drawLine(blue, top, knob, 1.5.dp.toPx())
            drawCircle(Color.White, 12.dp.toPx(), knob)
            drawCircle(blue, 12.dp.toPx(), knob, style = Stroke(1.5.dp.toPx()))
            drawArc(blue, -70f, 270f, false, knob - Offset(6.dp.toPx(), 6.dp.toPx()), Size(12.dp.toPx(), 12.dp.toPx()), style = Stroke(1.8.dp.toPx()))
            drawLine(blue, knob + Offset(-6.dp.toPx(), 2.dp.toPx()), knob + Offset(-7.dp.toPx(), -2.dp.toPx()), 1.8.dp.toPx())
        }
    }
    Box(Modifier.offset { IntOffset(area.left.roundToInt(), area.top.roundToInt()) }
        .size(with(density) { area.width.toDp() }, with(density) { area.height.toDp() })
        .semantics { contentDescription = "拖动签名移动；四角等比缩放；侧边拉伸；上方手柄旋转" }
        .pointerInput(source, origin, ppp) {
            var delta = Offset.Zero
            var start = Offset.Zero
            var selected: SignatureHandle? = null
            var rotating = false
            var moving = false
            var preview = source
            detectDragGestures(
                onDragStart = { position ->
                    delta = Offset.Zero
                    start = (area.topLeft + position - origin) / ppp
                    rotating = (start - rotationPoint).getDistance() * ppp <= hitRadius
                    val local = rotateVector(start - source.center, -source.rotation)
                    val innerX = (source.rect.width / 2 - minOf(hitRadius / ppp, source.rect.width / 4)).coerceAtLeast(0f)
                    val innerY = (source.rect.height / 2 - minOf(hitRadius / ppp, source.rect.height / 4)).coerceAtLeast(0f)
                    moving = !rotating && abs(local.x) < innerX && abs(local.y) < innerY
                    selected = if (rotating || moving) null else SignatureHandle.entries.minByOrNull { (source.handle(it) - start).getDistance() }
                        ?.takeIf { (source.handle(it) - start).getDistance() * ppp <= hitRadius }
                    if (!rotating && selected == null && abs(local.x) <= source.rect.width / 2 && abs(local.y) <= source.rect.height / 2) moving = true
                    preview = source
                },
                onDrag = { change, amount ->
                    if (rotating || moving || selected != null) {
                        change.consume(); delta += amount / ppp
                        val next = when {
                            rotating -> source.rotated(start, start + delta)
                            selected != null -> source.resized(selected!!, delta)
                            else -> source.moved(delta)
                        }
                        val info = doc.info.pages[page]
                        preview = next.constrained(info.width, info.height)
                        onPreview(preview)
                    }
                },
                onDragEnd = { if (rotating || moving || selected != null) onCommit(preview); onPreview(null) },
                onDragCancel = { onPreview(null) },
            )
        })
}

private fun Rect.expandTo(point: Offset) = Rect(minOf(left, point.x), minOf(top, point.y), maxOf(right, point.x), maxOf(bottom, point.y))
