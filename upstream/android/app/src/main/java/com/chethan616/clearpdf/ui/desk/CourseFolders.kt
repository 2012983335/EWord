package com.chethan616.clearpdf.ui.desk

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.engine.LocalFiles
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassPill
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** SAF directory navigation keeps course hierarchy and reads just one directory at a time. */
@Composable
fun CourseFolders(vm: AppViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tree by remember(vm.courseFolderStart) { mutableStateOf(vm.courseFolderStart) }
    var trail by remember(vm.courseFolderStart) { mutableStateOf(emptyList<Pair<String, String>>()) }
    var roots by remember { mutableStateOf(emptyList<Pair<Uri, String>>()) }
    var entries by remember { mutableStateOf(emptyList<LocalFiles.FolderItem>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val parentId = trail.lastOrNull()?.first ?: tree?.let(DocumentsContract::getTreeDocumentId)
    fun back() {
        when {
            trail.isNotEmpty() -> trail = trail.dropLast(1)
            tree != null -> tree = null
            else -> vm.courseFoldersOpen = false
        }
    }
    BackHandler(vm.document == null) { back() }
    LaunchedEffect(tree, parentId, reload) {
        loading = true
        error = null
        entries = emptyList()
        try {
            val selected = tree
            if (selected == null) roots = withContext(Dispatchers.IO) { vm.localFolders() }
            else entries = withContext(Dispatchers.IO) { LocalFiles.children(context, selected, requireNotNull(parentId)) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: context.getString(R.string.folder_unreadable) }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 16.dp)) {
        ScreenHeader(null, stringResource(R.string.course_folders), onBack = { back() }) {
            GlassPill(onClick = { vm.requestFileAccess() }) { Text(stringResource(R.string.home_add_folder)) }
        }
        if (tree != null) Text(
            (listOf(roots.firstOrNull { it.first == tree }?.second ?: vm.courseFolderStart?.lastPathSegment?.substringAfterLast(':') ?: "") + trail.map { it.second }).filter { it.isNotBlank() }.joinToString(" / "),
            style = ClearType.Caption, color = clear.inkSoft, modifier = Modifier.padding(22.dp),
        )
        when {
            loading -> Text(stringResource(R.string.folder_loading), modifier = Modifier.padding(24.dp), color = clear.inkSoft)
            error != null -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(error.orEmpty(), color = clear.ink)
                GlassPill(onClick = { reload++ }) { Text(stringResource(R.string.folder_retry)) }
            }
            else -> key(tree, parentId) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (tree == null) {
                        if (roots.isEmpty()) item { Text(stringResource(R.string.folder_roots_empty), color = clear.inkSoft) }
                        items(roots, key = { it.first.toString() }) { (uri, name) ->
                            FolderRow(name, true) { tree = uri; trail = emptyList() }
                        }
                    } else {
                        if (entries.isEmpty()) item { Text(stringResource(R.string.folder_documents_empty), color = clear.inkSoft) }
                        items(entries, key = { it.uri.toString() }) { entry ->
                            FolderRow(entry.name, entry.directory) {
                                if (entry.directory) trail = trail + (DocumentsContract.getDocumentId(entry.uri) to entry.name)
                                else vm.open(entry.uri)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(name: String, directory: Boolean, onClick: () -> Unit) {
    GlassPill(onClick, Modifier.fillMaxWidth(), height = 68.dp, label = name) {
        GlassIcon(if (directory) LucideIcons.FolderOpen else LucideIcons.FileText, null, Modifier.size(22.dp))
        Text(name, Modifier.weight(1f), style = ClearType.Body, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (directory) GlassIcon(LucideIcons.ChevronRight, null, Modifier.size(18.dp))
    }
}
