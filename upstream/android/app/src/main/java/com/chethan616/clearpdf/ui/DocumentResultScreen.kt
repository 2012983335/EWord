package com.chethan616.clearpdf.ui

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.core.DocumentInfo
import com.chethan616.clearpdf.engine.*
import java.io.File
import kotlinx.coroutines.*

data class DocumentResult(val file: File, val name: String, val before: OpenDocument? = null, val notice: String = "请核对文字、图片、表格与分页。", val listing: String? = null)

@Composable fun DocumentResultScreen(vm: AppViewModel, result: DocumentResult) {
    val engine = vm.engine ?: return
    var info by remember(result) { mutableStateOf<DocumentInfo?>(null) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var original by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    LaunchedEffect(result) {
        if (result.listing != null) return@LaunchedEffect
        var opened: DocumentInfo? = null
        try {
            opened = engine.command { open(result.file.absolutePath, result.before?.password) }
            info = opened
            awaitCancellation()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.userMessage }
        finally { opened?.let { withContext(NonCancellable) { engine.command { close(it.id, true) } } } }
    }
    val shown = if (original) result.before?.info else info
    LaunchedEffect(shown?.id, page) {
        bitmap = null
        val doc = shown ?: return@LaunchedEffect
        if (doc.kind == com.chethan616.clearpdf.core.DocumentKind.SHEETS) return@LaunchedEffect
        if (doc.pages.isEmpty()) return@LaunchedEffect
        val p = page.coerceIn(doc.pages.indices)
        val size = doc.pages[p]
        try { bitmap = withContext(engine.renders) { engine.session.renderPage(doc.id, p.toUInt(), minOf(1.6f, 1400f / maxOf(size.width, size.height)), null).toBitmap().asImageBitmap() } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.userMessage }
    }
    fun leave() { result.file.delete(); vm.toolScreen = null }
    ToolScaffold("结果预览", ::leave, "保存副本", info != null || result.listing != null, {
        vm.viewModelScope.launch {
            vm.busy = "正在保存结果副本"
            try {
                val uri = withContext(Dispatchers.IO) { vm.files.saveToDownloads(result.file, DocumentFiles.safeName(result.name), DocumentTypes.mimeOf(result.name)) }
                vm.tell("已保存到下载目录，可在文档中分享")
                vm.toolScreen = null
                vm.open(uri)
            } catch (failure: Exception) { vm.tell("保存失败，预览仍保留：${failure.userMessage}") }
            finally { vm.busy = null }
        }
    }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(result.name)
            Text(result.notice)
            if (result.before != null && result.listing == null) {
                Text("页数 ${result.before.pageCount} → ${info?.pages?.size ?: "…"}")
                Row {
                    FilterChip(original, { original = true; page = 0 }, { Text("转换前 / 原文") })
                    Spacer(Modifier.width(12.dp))
                    FilterChip(!original, { original = false; page = 0 }, { Text("处理结果") })
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            val image = bitmap
            if (result.listing != null) Text(result.listing, Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
            else if (shown?.kind == com.chethan616.clearpdf.core.DocumentKind.SHEETS) {
                val grid = com.chethan616.clearpdf.ui.sheets.rememberSheetGrid(engine, shown.id)
                Column(Modifier.fillMaxSize()) {
                    com.chethan616.clearpdf.ui.sheets.SheetGrid(grid, Modifier.weight(1f).fillMaxWidth())
                    Row(Modifier.horizontalScroll(rememberScrollState())) { grid.sheets.forEachIndexed { index, sheet -> TextButton(onClick = { grid.show(index) }) { Text(sheet.name) } } }
                }
            }
            else if (image != null) Image(image, "文档预览", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else if (error != null) Text("无法生成预览：$error") else CircularProgressIndicator()
        }
        if (result.listing == null && shown?.kind != com.chethan616.clearpdf.core.DocumentKind.SHEETS) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { page-- }, enabled = page > 0) { Text("上一页") }
            Text("${page + 1} / ${shown?.pages?.size ?: 0}")
            TextButton(onClick = { page++ }, enabled = page + 1 < (shown?.pages?.size ?: 0)) { Text("下一页") }
        }
    }
}
