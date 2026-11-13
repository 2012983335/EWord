package com.chethan616.clearpdf.checks

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import com.chethan616.clearpdf.engine.Engine
import com.chethan616.clearpdf.core.RenderedImage
import com.chethan616.clearpdf.ui.viewer.SignatureRaster
import com.chethan616.clearpdf.ui.viewer.SignatureTransform
import com.chethan616.clearpdf.ui.viewer.SignatureErase
import com.chethan616.clearpdf.ui.viewer.SignatureEraseHistory
import com.chethan616.clearpdf.ui.viewer.eraseSignature
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import java.io.File
import java.util.zip.ZipFile
import org.json.JSONObject

/** Headless checks run in cn.jiandoc.app.debug, never the user's production package. */
class SignatureRasterChecks : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val report = Bundle()
        try {
            val dir = File(targetContext.cacheDir, "signature-check-${System.nanoTime()}").apply { mkdirs() }
            val source = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(source)
            canvas.drawRect(10f, 5f, 90f, 45f, Paint().apply { color = Color.BLACK })
            val history = SignatureEraseHistory()
            history.add(SignatureErase(listOf(Offset(40f, 20f), Offset(60f, 20f)), 8f))
            eraseSignature(source, history.actions)!!.let { cleaned ->
                check(Color.alpha(cleaned.getPixel(50, 20)) == 0) { "Eraser did not clear alpha" }
                check(Color.alpha(cleaned.getPixel(50, 35)) > 240) { "Eraser damaged other ink" }
                check(Color.alpha(source.getPixel(50, 20)) > 240) { "Eraser changed source" }
                cleaned.recycle()
            }
            history.undo()
            eraseSignature(source, history.actions)!!.let { check(Color.alpha(it.getPixel(50, 20)) > 240); it.recycle() }
            history.redo()
            eraseSignature(source, history.actions)!!.let { check(Color.alpha(it.getPixel(50, 20)) == 0); it.recycle() }
            history.add(SignatureErase(emptyList(), 0f, clearAll = true))
            check(eraseSignature(source, history.actions) == null) { "Blank signatures must be rejected" }
            history.undo()
            history.add(SignatureErase(listOf(Offset(75f, 30f)), 10f))
            eraseSignature(source, history.actions)!!.let { check(Color.alpha(it.getPixel(75, 30)) == 0); it.recycle() }
            report.putInt("eraserChecks", 5)
            val sourceFile = File(dir, "source.png")
            // The erased alpha hole must survive PNG export, rotation and both document formats.
            // Keep the transparent centre wider than one rendered page pixel; subpixel circular
            // edges mix with surrounding ink when the document renderer downsamples the image.
            val cleaned = eraseSignature(source, listOf(SignatureErase(listOf(Offset(20f, 10f)), 8f)))!!
            sourceFile.outputStream().use { check(cleaned.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            cleaned.recycle()
            val rotated = File(dir, "rotation90.png")
            SignatureRaster.write(sourceFile, SignatureTransform(Rect(0f, 0f, 200f, 50f), 90f), rotated)
            BitmapFactory.decodeFile(rotated.path).let { image ->
                check(image.width == 200 && image.height == 800) { "Wrong rotated dimensions" }
                check(Color.alpha(image.getPixel(0, 0)) == 0) { "Transparent border lost" }
                check(Color.alpha(image.getPixel(160, 160)) == 0) { "Rotation direction/notch mismatch" }
                check(Color.alpha(image.getPixel(100, 400)) > 240) { "Ink missing" }
                image.recycle()
            }
            val stretched = SignatureRaster.render(source, 200f, 50f, 0f)
            check(stretched.width == 800 && stretched.height == 200) { "Side stretch not applied" }
            check(Color.alpha(stretched.getPixel(100, 100)) > 240 && Color.alpha(stretched.getPixel(40, 100)) == 0)
            stretched.recycle()
            val diagonal = SignatureRaster.render(source, 200f, 50f, 45f)
            check(diagonal.width == diagonal.height && diagonal.width in 705..709) { "Arbitrary rotation dimensions wrong" }
            check(Color.alpha(diagonal.getPixel(0, 0)) == 0)
            diagonal.recycle(); source.recycle()
            report.putInt("rasterChecks", 3)

            val session = Engine.open().session
            fun execute(tool: String, args: Map<String, Any?>) = JSONObject(session.execute(tool, Engine.toJson(args).toString()))
            val pdf = execute("doc_create", mapOf("from" to "blank")).getLong("doc").toULong()
            execute("page_add_image", mapOf("doc" to pdf, "page" to 1, "path" to rotated.path, "rect" to listOf(100, 100, 150, 300)))
            val pdfFile = File(dir, "signed.pdf")
            execute("doc_save", mapOf("doc" to pdf, "path" to pdfFile.path))
            val pdfAgain = session.open(pdfFile.path, null).id
            verifyInk(session.renderPage(pdfAgain, 0u, 1f, null), null)
            report.putInt("pdfChecks", 1)

            val wordFile = File(dir, "signed.docx")
            context.assets.open("chinese-notice.docx").use { input -> wordFile.outputStream().use { input.copyTo(it) } }
            val word = session.open(wordFile.path, null).id
            val before = session.renderPage(word, 0u, 1f, null)
            session.wordCommand(word, "caret.docEnd", "{}")
            session.wordCommand(word, "insert.picture", Engine.toJson(mapOf("path" to rotated.path, "width" to 50)).toString())
            session.wordCommand(word, "arrange.wrap", "{\"wrap\":\"inFrontOfText\"}")
            session.wordCommand(word, "arrange.bounds", "{\"page\":0,\"x\":100,\"y\":100,\"width\":50,\"height\":200}")
            session.saveOffice(word, wordFile.path)
            val wordAgain = session.open(wordFile.path, null).id
            verifyInk(session.renderPage(wordAgain, 0u, 1f, null), before)
            ZipFile(wordFile).use { zip ->
                check(zip.entries().asSequence().filter { it.name.startsWith("word/media/") }.any { entry ->
                    val image = zip.getInputStream(entry).use { BitmapFactory.decodeStream(it) } ?: return@any false
                    val valid = image.width == 200 && image.height == 800 && Color.alpha(image.getPixel(0, 0)) == 0
                    image.recycle(); valid
                }) { "Transformed transparent signature not embedded in Word" }
            }
            report.putInt("wordChecks", 1)
            report.putString("stream", "SIGNATURE_CHECKS_OK: eraser alpha/undo/redo, raster, PDF and Word reopen passed\n")
            finish(Activity.RESULT_OK, report)
        } catch (failure: Throwable) {
            report.putString("stream", failure.stackTraceToString())
            finish(Activity.RESULT_CANCELED, report)
        }
    }

    private fun verifyInk(image: RenderedImage, before: RenderedImage?) {
        fun rgb(pixels: RenderedImage, x: Int, y: Int) = (0..2).map { pixels.rgba[((y * pixels.width.toInt() + x) * 4) + it].toInt() and 255 }
        for ((x, y) in listOf(125 to 200, 135 to 230)) check(rgb(image, x, y).all { it < 80 }) { "Saved ink position mismatch" }
        val expected = before?.let { rgb(it, 140, 140) } ?: listOf(255, 255, 255)
        val actual = rgb(image, 140, 140)
        check(actual.zip(expected).all { (a, b) -> kotlin.math.abs(a - b) <= 3 }) { "Transparent notch not preserved: expected=$expected actual=$actual" }
    }
}
