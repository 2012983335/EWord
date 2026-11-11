package com.chethan616.clearpdf.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.engine.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable fun VaultScreen(vm: AppViewModel, kind: VaultKind, doc: OpenDocument?) {
    LaunchedEffect(Unit) { vm.safety.refresh() }
    val entries = vm.safety.entries.filter { it.kind == kind && (doc == null || it.source == vm.safety.key(doc)) }
    var erasing by remember { mutableStateOf<VaultEntry?>(null) }
    ToolScaffold(when (kind) { VaultKind.Trash -> "回收站"; VaultKind.Version -> "历史版本"; VaultKind.Draft -> "可恢复的编辑草稿" }, { vm.toolScreen = null }, null, false, {}) {
        Text(when (kind) {
            VaultKind.Trash -> "恢复到下载目录，不覆盖同名文件。彻底删除只清除此处的回收副本，无法撤销。"
            VaultKind.Version -> "保留最近 5 份检查点，每份文档历史最多 200 MB。恢复会创建独立副本。"
            VaultKind.Draft -> "此处为未导出的私有编辑。系统关闭应用后可从这里恢复，原文件不受影响。"
        }, Modifier.padding(20.dp))
        if (entries.isEmpty()) Text("暂无记录", Modifier.padding(24.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(entries, key = { it.id }) { entry ->
                com.chethan616.clearpdf.ui.glass.WashPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(entry.name)
                        Text(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(entry.time)) + " · " + (entry.file.length() / 1024) + " KB")
                        Row {
                            TextButton(onClick = { vm.safety.restore(entry) }) { Text(if (kind == VaultKind.Trash) "恢复" else "恢复为副本") }
                            TextButton(onClick = { erasing = entry }) { Text(if (kind == VaultKind.Trash) "彻底删除" else "移除此检查点") }
                        }
                    }
                }
            }
        }
    }
    erasing?.let { entry -> AlertDialog(onDismissRequest = { erasing = null }, title = { Text("确认永久移除此备份？") },
        text = { Text("${entry.name}\n此备份将无法恢复。原位置文件不会因此被删除。") },
        confirmButton = { TextButton(onClick = { vm.safety.erase(entry); erasing = null }) { Text("确认彻底删除") } },
        dismissButton = { TextButton(onClick = { erasing = null }) { Text("取消") } }) }
}
