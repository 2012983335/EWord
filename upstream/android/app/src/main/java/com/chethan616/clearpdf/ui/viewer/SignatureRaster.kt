package com.chethan616.clearpdf.ui.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.io.File
import kotlin.math.ceil
import kotlin.math.sqrt

/** Bake the preview's transform into transparent pixels so Word and PDF use identical ink. */
object SignatureRaster {
    fun render(source: Bitmap, width: Float, height: Float, rotation: Float): Bitmap {
        require(width > 0 && height > 0 && width.isFinite() && height.isFinite() && rotation.isFinite())
        val bounds = SignatureTransform(androidx.compose.ui.geometry.Rect(0f, 0f, width, height), rotation).bounds
        val scale = minOf(4f, 4096f / bounds.width, 4096f / bounds.height, sqrt(8_000_000f / (bounds.width * bounds.height)))
        val output = Bitmap.createBitmap(ceil(bounds.width * scale).toInt().coerceAtLeast(1), ceil(bounds.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            translate(output.width / 2f, output.height / 2f)
            rotate(rotation)
            drawBitmap(source, null, RectF(-width * scale / 2, -height * scale / 2, width * scale / 2, height * scale / 2), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        return output
    }

    fun write(source: File, transform: SignatureTransform, target: File) {
        val input = decodeSignature(source, 2048) ?: error("无法读取签名")
        val output = try { render(input, transform.rect.width, transform.rect.height, transform.rotation) } finally { input.recycle() }
        try {
            target.outputStream().use { stream -> check(output.compress(Bitmap.CompressFormat.PNG, 100, stream)); stream.fd.sync() }
        } finally { output.recycle() }
    }
}
