package com.chethan616.clearpdf.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable fun OfflineTranslationScreen(vm: AppViewModel, source: String) {
    var englishToChinese by remember { mutableStateOf(source.none { it.code in 0x3400..0x9fff }) }
    var result by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var task by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    ToolScaffold("完全离线翻译", { task?.cancel(); vm.toolScreen = null }, if (running) "取消" else "翻译", true, {
        if (running) task?.cancel() else task = scope.launch {
            running = true; error = null
            try { result = (vm.getApplication<android.app.Application>() as com.chethan616.clearpdf.ArtefactApplication).offlineTranslation.translate(source, englishToChinese) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "离线翻译失败" }
            finally { running = false }
        }
    }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("模型已随安装包提供，翻译在手机本地执行。首次使用会准备本地资源。")
            Row { FilterChip(englishToChinese, { if (!running) englishToChinese = true }, { Text("英文 → 中文") }); Spacer(Modifier.width(12.dp)); FilterChip(!englishToChinese, { if (!running) englishToChinese = false }, { Text("中文 → 英文") }) }
            Text("原文（${source.length} 字）"); Text(source)
            if (running) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("正在本地处理；较长选区可能需要等待。") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (result.isNotEmpty()) { Text("译文"); Text(result); TextButton(onClick = { vm.copyText(result) }) { Text("复制译文") } }
        }
    }
}
