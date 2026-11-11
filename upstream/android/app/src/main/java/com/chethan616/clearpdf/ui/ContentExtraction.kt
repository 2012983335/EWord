package com.chethan616.clearpdf.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.engine.*
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.*

@Composable fun ContentExtractionScreen(vm: AppViewModel, doc: OpenDocument) {
    var mode by remember { mutableIntStateOf(0) }
    var range by remember { mutableStateOf("") }
    val choices = listOf("正文文字（TXT）", "原始图片（ZIP）", "段落与表格结构（HTML）", "检测到的表格与页面内容（XLSX，仅 PDF）")
    ToolScaffold("提取文档内容", { vm.toolScreen = null }, "提取并预览", mode != 3 || !doc.isOffice, { vm.extractContent(doc, mode, range) }) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("提取不会修改原文件。扫描件需具备文本层才能提取文字；表格识别与结构以实际结果为准。")
            choices.forEachIndexed { index, label -> FilterChip(mode == index, { mode = index }, { Text(label) }) }
            if (!doc.isOffice) OutlinedTextField(range, { range = it }, label = { Text("页码范围，留空为全部") })
            else Text("图片从原 DOCX 包提取，结构以 HTML 导出。")
        }
    }
}

fun AppViewModel.extractContent(doc: OpenDocument, mode: Int, range: String) = viewModelScope.launch {
    val eng = engine ?: return@launch
    val pages = runCatching { pageRange(if (doc.isOffice) "" else range, doc.pageCount) }.getOrElse { tell(it.userMessage); return@launch }
    busy = "正在提取文档内容"
    val dir = File(getApplication<Application>().cacheDir, "extract/${System.nanoTime()}").apply { mkdirs() }
    val ext = listOf("txt", "zip", "html", "xlsx")[mode]
    val out = File(dir, "content.$ext")
    try {
        check(!doc.info.encrypted || doc.info.allowsCopying) { "此文档不允许提取或复制内容" }
        check(wordEditing !== doc || commitWordPara(doc)) { "文字编辑未能提交，请重试" }
        save(doc)
        check(doc.saveFailure == null) { doc.saveFailure ?: "工作副本保存失败" }
        var listing: String? = null
        var notice = "提取结果请核对原文。"
        when (mode) {
            0 -> {
                withContext(Dispatchers.IO) {
                    out.bufferedWriter(Charsets.UTF_8).use { writer ->
                        for (page in pages) {
                            val text = eng.command { pagePlainText(doc.id, (page - 1).toUInt()) }
                            writer.write("第 $page 页\n"); writer.write(text); writer.write("\n\n")
                        }
                    }
                }
                listing = withContext(Dispatchers.IO) { out.bufferedReader().use { reader -> val buffer = CharArray(30_000); val count = reader.read(buffer); if (count > 0) String(buffer, 0, count) else "未提取到文本层内容。" } }
                notice = "预览最多 30000 字，导出文件包含完整提取结果。"
            }
            1 -> {
                val names = mutableListOf<String>()
                if (doc.isOffice) {
                    val word = File(dir, "source.docx")
                    eng.command { exportOffice(doc.id, "docx", word.absolutePath) }
                    withContext(Dispatchers.IO) {
                        ZipFile(word).use { zip -> ZipOutputStream(out.outputStream()).use { target ->
                            zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith("word/media/") }.forEach { entry ->
                                val name = entry.name.substringAfterLast('/')
                                target.putNextEntry(ZipEntry(name)); zip.getInputStream(entry).use { it.copyTo(target) }; target.closeEntry(); names.add(name)
                            }
                        } }
                    }
                    word.delete()
                } else {
                    val images = File(dir, "images").apply { mkdirs() }
                    val response = eng.tool("doc_export_all_images", mapOf("doc" to doc.id, "pages" to pages, "folder" to images.absolutePath))
                    withContext(Dispatchers.IO) { ZipOutputStream(out.outputStream()).use { target ->
                        images.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.forEach { image ->
                            target.putNextEntry(ZipEntry(image.name)); image.inputStream().use { it.copyTo(target) }; target.closeEntry(); names.add(image.name)
                        }
                    } }
                    val skipped = response.optJSONArray("skipped")?.length() ?: 0
                    if (skipped > 0) notice = "有 $skipped 项图片因解码限制未导出，请核对。"
                    images.listFiles()?.forEach { it.delete() }; images.delete()
                }
                check(names.isNotEmpty()) { "未找到可导出的原始图片" }
                listing = "已提取 ${names.size} 张图片\n\n" + names.joinToString("\n")
            }
            2, 3 -> {
                if (doc.isOffice) eng.command { exportOffice(doc.id, "html", out.absolutePath) }
                else {
                    var extracted: Long? = null
                    try {
                        val id = if (pages.size == doc.pageCount) doc.id.toLong() else {
                            val subset = File(dir, "selected.pdf")
                            eng.tool("page_extract", mapOf("doc" to doc.id, "pages" to pages, "out" to subset.absolutePath))
                            eng.tool("doc_open", mapOf("path" to subset.absolutePath)).getLong("doc").also { extracted = it }
                        }
                        eng.tool("doc_export_office", mapOf("doc" to id, "path" to out.absolutePath))
                    } finally { extracted?.let { eng.tool("doc_close", mapOf("doc" to it, "discard_changes" to true)) }; File(dir, "selected.pdf").delete() }
                }
                if (mode == 2) {
                    listing = withContext(Dispatchers.IO) { out.bufferedReader().use { reader -> val chars = CharArray(40000); val count = reader.read(chars); if (count > 0) android.text.Html.fromHtml(String(chars, 0, count), android.text.Html.FROM_HTML_MODE_COMPACT).toString() else "没有可提取的结构化内容" } }
                    notice = "预览显示文本摘要；保存的 HTML 保留导出引擎识别的段落、表格与内嵌图片。"
                }
            }
        }
        toolScreen = ToolScreen.Result(DocumentResult(out, "${doc.title}-提取.$ext", doc, notice, listing))
    } catch (failure: Exception) { out.delete(); tell("提取失败：${failure.userMessage}") }
    finally { busy = null }
}
