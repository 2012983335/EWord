package com.chethan616.clearpdf.ui.viewer

import android.graphics.Bitmap
import android.graphics.Canvas as BitmapCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
fun SignatureEraser(image: Bitmap, actions: List<SignatureErase>, widthDp: Float, enabled: Boolean,
    onErasing: (Boolean) -> Unit, onErase: (SignatureErase) -> Unit) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var active by remember(image) { mutableStateOf<SignatureErase?>(null) }
    val density = LocalDensity.current
    val ratio = minOf(viewSize.width.toFloat() / image.width, viewSize.height.toFloat() / image.height).coerceAtLeast(0.001f)
    val origin = Offset((viewSize.width - image.width * ratio) / 2, (viewSize.height - image.height * ratio) / 2)
    val diameter = with(density) { widthDp.dp.toPx() } / ratio
    val bitmap = remember(image) { image.asImageBitmap() }
    Canvas(Modifier.fillMaxSize().onSizeChanged { viewSize = it }
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .pointerInput(image, viewSize, widthDp, enabled) {
            awaitEachGesture {
                val down = awaitFirstDown()
                if (!enabled) return@awaitEachGesture
                val point = (down.position - origin) / ratio
                if (point.x !in 0f..image.width.toFloat() || point.y !in 0f..image.height.toFloat()) return@awaitEachGesture
                down.consume()
                active = SignatureErase(listOf(point), diameter)
                onErasing(true)
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val next = (change.position - origin) / ratio
                        if ((next - active!!.points.last()).getDistance() * ratio >= 1f) active = active!!.copy(points = active!!.points + next)
                        change.consume()
                        if (!change.pressed) {
                            onErase(active!!)
                            break
                        }
                    }
                } finally { active = null; onErasing(false) }
            }
        }) {
        translate(origin.x, origin.y) {
            scale(ratio, pivot = Offset.Zero) {
                drawImage(bitmap)
                for (action in actions + listOfNotNull(active)) {
                    if (action.clearAll) drawRect(Color.Transparent, size = androidx.compose.ui.geometry.Size(image.width.toFloat(), image.height.toFloat()), blendMode = BlendMode.Clear)
                    else if (action.points.size == 1) drawCircle(Color.Transparent, action.width / 2, action.points[0], blendMode = BlendMode.Clear)
                    else {
                        val path = androidx.compose.ui.graphics.Path()
                        action.points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                        drawPath(path, Color.Transparent, style = Stroke(action.width, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Clear)
                    }
                }
            }
        }
        active?.points?.lastOrNull()?.let { drawCircle(Color(0xFF448AFF), diameter * ratio / 2, origin + it * ratio, style = Stroke(1.dp.toPx())) }
    }
}

/** Clear alpha, rather than painting white, so cleaned signatures still overlay document text. */
fun eraseSignature(source: Bitmap, actions: List<SignatureErase>): Bitmap? {
    val output = source.copy(Bitmap.Config.ARGB_8888, true)
    try {
        val canvas = BitmapCanvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        for (action in actions) {
            if (action.clearAll) canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            else {
                paint.strokeWidth = action.width
                if (action.points.size == 1) {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(action.points[0].x, action.points[0].y, action.width / 2, paint)
                    paint.style = Paint.Style.STROKE
                } else {
                    val path = Path()
                    action.points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                    canvas.drawPath(path, paint)
                }
            }
        }
        val row = IntArray(output.width)
        for (y in 0 until output.height) {
            output.getPixels(row, 0, output.width, 0, y, output.width, 1)
            if (row.any { android.graphics.Color.alpha(it) > 16 }) return output
        }
        output.recycle()
        return null
    } catch (failure: Throwable) {
        output.recycle()
        throw failure
    }
}
