package com.chethan616.clearpdf.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.engine.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileSafety(private val vm: AppViewModel, private val app: Application) {
    val vault = FileVault(File(app.filesDir, "Vault"))
    var entries by mutableStateOf<List<VaultEntry>>(emptyList())
        private set
    private val versionTimes = mutableMapOf<String, Long>()

    init { refresh() }
    fun refresh() { vm.viewModelScope.launch { entries = withContext(Dispatchers.IO) { vault.list() } } }
    fun key(doc: OpenDocument) = doc.source?.toString() ?: Uri.fromFile(doc.file).toString()

    suspend fun sourceCopy(uri: Uri): File = withContext(Dispatchers.IO) {
        val draft = vault.list(VaultKind.Draft, uri.toString()).firstOrNull()
        if (draft == null) vm.files.workingCopy(uri) else {
            val copy = File(app.filesDir, "Recovered/${draft.id}/${DocumentFiles.safeName(draft.name)}")
            FileVault.copyDurably(draft.file, copy)
            copy
        }
    }

    suspend fun opened(doc: OpenDocument) {
        val source = key(doc)
        val recovered = withContext(Dispatchers.IO) {
            val hasDraft = vault.list(VaultKind.Draft, source).isNotEmpty()
            hasDraft
        }
        if (recovered) { doc.notWrittenBack = true; vm.tell("已恢复上次未导出的编辑，原文件未改动，请另存副本") }
        refresh()
    }

    suspend fun beforeSave(doc: OpenDocument) = withContext(Dispatchers.IO) {
        val source = key(doc)
        if (vault.list(VaultKind.Version, source).isEmpty()) vault.capture(doc.file, doc.name, source, VaultKind.Version)
    }

    suspend fun checkpoint(doc: OpenDocument) = withContext(Dispatchers.IO) {
        val source = key(doc)
        vault.capture(doc.file, doc.name, source, VaultKind.Draft)
        val now = System.currentTimeMillis()
        if (now - (versionTimes[source] ?: 0) >= 60_000) {
            vault.capture(doc.file, doc.name, source, VaultKind.Version)
            versionTimes[source] = now
        }
        refresh()
    }

    suspend fun clearDraft(doc: OpenDocument) { withContext(Dispatchers.IO) { vault.removeDrafts(key(doc)) }; refresh() }

    /** A complete backup is published before the original deletion is attempted. */
    fun trash(uri: Uri, name: String) {
        val source = vm.files.workingCopy(uri)
        vault.capture(source, name, uri.toString(), VaultKind.Trash)
        vm.files.deleteOriginal(uri)
    }

    fun restore(entry: VaultEntry) = vm.viewModelScope.launch {
        vm.busy = "正在恢复文件副本"
        try {
            if (entry.kind == VaultKind.Trash) {
                val uri = withContext(Dispatchers.IO) { vm.files.saveToDownloads(entry.file, entry.name, DocumentTypes.mimeOf(entry.name)) }
                withContext(Dispatchers.IO) {
                    vm.recents.touch(RecentFile(uri.toString(), entry.name, System.currentTimeMillis(), 0, entry.file.length()))
                    vault.remove(entry)
                }
                vm.refreshRecents()
                vm.tell("已恢复到下载目录，未覆盖同名文件")
            } else {
                val name = entry.name.substringBeforeLast('.') + "-恢复-${System.currentTimeMillis()}." + entry.name.substringAfterLast('.', "pdf")
                val copy = File(vm.files.createdDir, DocumentFiles.safeName(name))
                withContext(Dispatchers.IO) { FileVault.copyDurably(entry.file, copy) }
                vm.toolScreen = null
                vm.open(Uri.fromFile(copy))
                vm.tell("已恢复为独立副本，当前文件和历史版本保留")
            }
        } catch (failure: Exception) { vm.tell("恢复失败，备份仍保留：${failure.userMessage}") }
        finally { vm.busy = null; refresh() }
    }

    fun erase(entry: VaultEntry) = vm.viewModelScope.launch {
        try { withContext(Dispatchers.IO) { vault.remove(entry) }; refresh() }
        catch (failure: Exception) { vm.tell(failure.userMessage) }
    }
}
