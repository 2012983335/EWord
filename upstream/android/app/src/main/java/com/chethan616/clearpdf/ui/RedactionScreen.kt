package com.chethan616.clearpdf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.engine.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

private data class RedactArea(val page: Int, val rect: List<Float>)

@Composable fun RedactionScreen(vm: AppViewModel, doc: OpenDocument) {
    var page by remember { mutableIntStateOf(doc.viewer.currentPage) }
    val marks = remember { mutableStateListOf<RedactArea>() }
    var query by remember { mutableStateOf("") }
    var range by remember { mutableStateOf("") }
    var sanitize by remember { mutableStateOf(true) }
    var confirming by remember { mutableStateOf(false) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var start by remember { mutableStateOf<Offset?>(null) }
    var end by remember { mutableStateOf<Offset?>(null) }
    val eng = vm.engine ?: return
    val size = doc.info.pages.getOrNull(page) ?: return
    LaunchedEffect(page, doc.info.generation) {
        image = null; error = null
        try { image = withContext(eng.renders) { eng.session.renderPage(doc.id, page.toUInt(), minOf(1.8f, 1500f / maxOf(size.width, size.height)), null).toBitmap().asImageBitmap() } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.userMessage }
    }
    ToolScaffold("永久擦除敏感内容", { vm.toolScreen = null }, "生成安全擦除副本", marks.isNotEmpty() || query.isNotBlank(), { confirming = true }) {
        Text("拖动框选要永久移除的文字或图像区域。红框只是待处理标记。", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            val factor = minOf(constraints.maxWidth / size.width, constraints.maxHeight / size.height)
            val origin = Offset((constraints.maxWidth - size.width * factor) / 2, (constraints.maxHeight - size.height * factor) / 2)
            fun point(offset: Offset) = Offset(((offset.x - origin.x) / factor).coerceIn(0f, size.width), ((offset.y - origin.y) / factor).coerceIn(0f, size.height))
            image?.let { Image(it, "待擦除页面", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            if (error != null) Text(error!!)
            Canvas(Modifier.fillMaxSize().pointerInput(page, factor, origin) {
                detectDragGestures(onDragStart = { start = point(it); end = start }, onDragEnd = {
                    val a = start; val b = end
                    if (a != null && b != null && kotlin.math.abs(a.x - b.x) > 2 && kotlin.math.abs(a.y - b.y) > 2) {
                        marks.add(RedactArea(page + 1, listOf(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))))
                    }
                    start = null; end = null
                }, onDragCancel = { start = null; end = null }) { change, _ -> change.consume(); end = point(change.position) }
            }) {
                val rectangles = marks.filter { it.page == page + 1 }.map { Rect(it.rect[0], it.rect[1], it.rect[2], it.rect[3]) }.toMutableList()
                if (start != null && end != null) rectangles.add(Rect(minOf(start!!.x, end!!.x), minOf(start!!.y, end!!.y), maxOf(start!!.x, end!!.x), maxOf(start!!.y, end!!.y)))
                rectangles.forEach { r -> drawRect(Color.Red.copy(alpha = 0.4f), origin + r.topLeft * factor, r.size * factor) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { page-- }, enabled = page > 0) { Text("上一页") }
            Text("${page + 1} / ${doc.pageCount} · ${marks.size} 个框选")
            TextButton(onClick = { page++ }, enabled = page + 1 < doc.pageCount) { Text("下一页") }
            TextButton(onClick = { if (marks.isNotEmpty()) marks.removeAt(marks.lastIndex) }, enabled = marks.isNotEmpty()) { Text("撤销框选") }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            OutlinedTextField(query, { query = it.take(300) }, label = { Text("也可输入关键词，擦除所有匹配") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(range, { range = it }, label = { Text("关键词搜索范围，留空为全部页") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(sanitize, { sanitize = it }); Text("清除隐藏信息、附件、批注、书签并平铺表单") }
        }
    }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("确认生成永久擦除副本？") },
        text = { Text("新导出的 PDF 将真正移除指定内容，并完整重写。原文件、历史版本与回收站仍可能保留原内容，需另行管理。请在预览中核对擦除范围。") },
        confirmButton = { TextButton(onClick = {
            val pages = runCatching { pageRange(range, doc.pageCount) }.getOrElse { vm.tell(it.userMessage); return@TextButton }
            confirming = false
            vm.preparePdfResult(doc, "安全擦除") { engine, id ->
                engine.tool("redact_clear", mapOf("doc" to id))
                for (mark in marks.toList()) engine.tool("redact_mark", mapOf("doc" to id, "page" to mark.page, "rect" to mark.rect))
                if (query.isNotBlank()) {
                    val found = engine.tool("redact_mark", mapOf("doc" to id, "pages" to pages, "find" to query))
                    if (marks.isEmpty()) check(found.optInt("marked") > 0) { "未找到可擦除的匹配，请检查关键词" }
                }
                engine.tool("redact_apply", mapOf("doc" to id))
                if (sanitize) engine.tool("doc_remove_hidden", mapOf("doc" to id))
            }
        }) { Text("确认处理") } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("取消") } })
}
