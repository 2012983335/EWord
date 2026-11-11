package com.chethan616.clearpdf.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.engine.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun pageRange(text: String, count: Int): List<Int> {
    if (text.isBlank()) return (1..count).toList()
    val result = linkedSetOf<Int>()
    for (part in text.replace('，', ',').split(',')) {
        val ends = part.trim().split('-').map { it.trim().toIntOrNull() ?: error("页码格式应为 1-3,5") }
        require(ends.size in 1..2) { "页码范围无效" }
        val a = ends[0]; val b = ends.last()
        require(a in 1..count && b in a..count) { "页码必须在 1 到 $count 之间，且起止顺序正确" }
        result.addAll(a..b)
    }
    return result.sorted()
}

internal fun textPicture(text: String, directory: File): File {
    require(text.isNotBlank() && text.length <= 300) { "请输入 1 到 300 字的文字" }
    directory.mkdirs()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 64f; color = android.graphics.Color.BLACK }
    val lines = mutableListOf<String>()
    for (line in text.lines()) {
        var rest = line
        if (rest.isEmpty()) lines.add("")
        while (rest.isNotEmpty()) {
            val count = paint.breakText(rest, true, 2000f, null).coerceAtLeast(1)
            lines.add(rest.take(count)); rest = rest.drop(count)
        }
    }
    require(lines.size <= 14) { "水印换行过多，请减少到 14 行以内" }
    val width = (lines.maxOf { paint.measureText(it) }.toInt() + 24).coerceIn(32, 2048)
    val height = lines.size * 84 + 24
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    lines.forEachIndexed { i, line -> canvas.drawText(line, 12f, 76f + i * 84, paint) }
    val file = File(directory, "text-${System.nanoTime()}.png")
    try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } } finally { bitmap.recycle() }
    return file
}

fun AppViewModel.preparePdfResult(doc: OpenDocument, suffix: String, apply: suspend (Engine, Long) -> Unit) = viewModelScope.launch {
    val eng = engine ?: return@launch
    busy = "正在处理并准备预览"
    val output = File(getApplication<Application>().cacheDir, "results/${System.nanoTime()}.pdf").apply { parentFile?.mkdirs() }
    try {
        save(doc)
        check(doc.saveFailure == null) { doc.saveFailure ?: "工作副本保存失败" }
        val id = eng.tool("doc_open", mapOf("path" to doc.file.absolutePath, "password" to doc.password)).getLong("doc")
        try {
            apply(eng, id)
            eng.tool("doc_save", mapOf("doc" to id, "path" to output.absolutePath, "full" to true))
        } finally { eng.tool("doc_close", mapOf("doc" to id, "discard_changes" to true)) }
        toolScreen = ToolScreen.Result(DocumentResult(output, "${doc.title}-$suffix.pdf", doc))
    } catch (failure: Exception) { output.delete(); tell(failure.userMessage) }
    finally { busy = null }
}

@Composable
fun DocumentUtilityScreen(vm: AppViewModel, doc: OpenDocument, kind: UtilityKind) {
    var range by remember { mutableStateOf("") }
    var text by remember { mutableStateOf(if (kind == UtilityKind.Watermark) "仅供参考" else "{page} / {total}") }
    var image by remember { mutableStateOf(false) }
    var opacity by remember { mutableFloatStateOf(0.25f) }
    var scale by remember { mutableFloatStateOf(0.45f) }
    var angle by remember { mutableFloatStateOf(45f) }
    var x by remember { mutableFloatStateOf(0f) }
    var y by remember { mutableFloatStateOf(0f) }
    var start by remember { mutableStateOf("1") }
    var size by remember { mutableFloatStateOf(12f) }
    var place by remember { mutableIntStateOf(4) }
    val watermark = kind == UtilityKind.Watermark
    val positions = listOf("左上", "上方居中", "右上", "左下", "下方居中", "右下")
    ToolScaffold(if (watermark) "添加水印" else "添加页码", { vm.toolScreen = null }, "生成预览", true, {
        val pages = runCatching { pageRange(range, doc.pageCount) }.getOrElse { vm.tell(it.userMessage); return@ToolScaffold }
        if (watermark && image && vm.utilityImage == null) { vm.tell("请先选择水印图片"); return@ToolScaffold }
        val first = start.toIntOrNull()
        if (!watermark && (first == null || first < 1 || first > 999999)) { vm.tell("起始页码应为 1 到 999999"); return@ToolScaffold }
        vm.preparePdfResult(doc, if (watermark) "水印" else "页码") { eng, id ->
            val dir = File(vm.getApplication<Application>().cacheDir, "marks")
            if (watermark) {
                val picture = if (image) requireNotNull(vm.utilityImage) else withContext(Dispatchers.IO) { textPicture(text, dir) }
                val page = doc.info.pages[pages.first() - 1]
                eng.tool("doc_watermark", mapOf("doc" to id, "file" to picture.absolutePath, "pages" to pages, "scale" to scale, "rotation" to angle,
                    "opacity" to opacity, "offset_x" to x * page.width / 2, "offset_y" to y * page.height / 2))
            } else if (text.all { it.code in 32..126 }) {
                val token = text.replace("{page}", "<<1>>").replace("{total}", "<<n>>")
                val field = (if (place < 3) "header_" else "footer_") + listOf("left", "center", "right")[place % 3]
                eng.tool("doc_header_footer", mapOf("doc" to id, "pages" to pages, field to token, "font_size" to size, "start_number" to first))
            } else {
                for ((index, number) in pages.withIndex()) {
                    val picture = withContext(Dispatchers.IO) { textPicture(text.replace("{page}", (first!! + index).toString()).replace("{total}", doc.pageCount.toString()), dir) }
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(picture.absolutePath, bounds)
                    val page = doc.info.pages[number - 1]
                    val w = bounds.outWidth * size / 64; val h = bounds.outHeight * size / 64
                    val base = minOf(page.width, page.height * bounds.outWidth / bounds.outHeight)
                    val dx = when (place % 3) { 0 -> -page.width / 2 + 28 + w / 2; 2 -> page.width / 2 - 28 - w / 2; else -> 0f }
                    val dy = if (place < 3) page.height / 2 - 28 - h / 2 else -page.height / 2 + 28 + h / 2
                    eng.tool("doc_watermark", mapOf("doc" to id, "pages" to listOf(number), "file" to picture.absolutePath, "scale" to (w / base).coerceIn(0.01f, 1f), "rotation" to 0, "opacity" to 1, "offset_x" to dx, "offset_y" to dy))
                }
            }
        }
    }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("在副本中处理，预览确认后再保存。原文件保留。")
            OutlinedTextField(range, { range = it }, label = { Text("应用页码范围（留空为全部，如 1-3,5）") }, modifier = Modifier.fillMaxWidth())
            if (watermark) {
                Row { FilterChip(!image, { image = false }, { Text("文字水印") }); Spacer(Modifier.width(12.dp)); FilterChip(image, { image = true }, { Text("图片水印") }) }
                if (image) TextButton(onClick = { vm.pickFiles(FilePickKind.Watermark) }) { Text(vm.utilityImage?.name ?: "选择图片或 PDF 首页") }
                else OutlinedTextField(text, { text = it.take(300) }, label = { Text("水印文字") }, modifier = Modifier.fillMaxWidth())
                UtilitySlider("不透明度 ${(opacity * 100).toInt()}%", opacity, 0f..1f) { opacity = it }
                UtilitySlider("大小 ${(scale * 100).toInt()}%", scale, 0.05f..1f) { scale = it }
                UtilitySlider("角度 ${angle.toInt()}°", angle, -180f..180f) { angle = it }
                UtilitySlider("水平位置（左 — 右）", x, -0.9f..0.9f) { x = it }
                UtilitySlider("垂直位置（下 — 上）", y, -0.9f..0.9f) { y = it }
            } else {
                OutlinedTextField(start, { start = it.filter(Char::isDigit).take(6) }, label = { Text("起始页码") })
                OutlinedTextField(text, { text = it.take(100) }, label = { Text("格式：{page} 当前编号，{total} 总页数") }, modifier = Modifier.fillMaxWidth())
                UtilitySlider("字号 ${size.toInt()}", size, 8f..36f) { size = it }
                positions.forEachIndexed { index, label -> FilterChip(place == index, { place = index }, { Text(label) }) }
            }
        }
    }
}

@Composable internal fun UtilitySlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, change: (Float) -> Unit) {
    Text(label); Slider(value, change, valueRange = range)
}

@Composable fun PdfToolboxScreen(vm: AppViewModel, doc: OpenDocument) {
    ToolScaffold("PDF 工具箱", { vm.toolScreen = null }, null, false, {}) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            com.chethan616.clearpdf.ui.glass.GlassRow("文字 / 图片水印", { vm.utilityImage = null; vm.toolScreen = ToolScreen.Utility(doc, UtilityKind.Watermark) }, icon = LucideSet.Stamp)
            com.chethan616.clearpdf.ui.glass.GlassRow("添加页码", { vm.toolScreen = ToolScreen.Utility(doc, UtilityKind.PageNumbers) }, icon = LucideIcons.ListOrdered)
            com.chethan616.clearpdf.ui.glass.GlassRow("图片批量转 PDF", { vm.pickPhotosToPdf() }, icon = LucideIcons.Images)
            com.chethan616.clearpdf.ui.glass.GlassRow("转换 / 导出", { vm.openExport(doc) }, icon = LucideIcons.FileOutput)
            com.chethan616.clearpdf.ui.glass.GlassRow("提取文字 / 图片 / 表格", { vm.toolScreen = ToolScreen.Extract(doc) }, icon = LucideIcons.Copy)
            com.chethan616.clearpdf.ui.glass.GlassRow("永久擦除敏感内容", { vm.toolScreen = ToolScreen.Redact(doc) }, icon = LucideSet.Eraser)
        }
    }
}
