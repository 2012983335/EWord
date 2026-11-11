package com.chethan616.clearpdf

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import ja.burhanrashid52.photoeditor.PhotoEditor
import ja.burhanrashid52.photoeditor.PhotoEditorView
import ja.burhanrashid52.photoeditor.SaveSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageActivity : ComponentActivity() {
    private lateinit var editorView: PhotoEditorView
    private lateinit var editor: PhotoEditor
    private var dirty by mutableStateOf(false)
    private var askToLeave by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var savedUri by mutableStateOf<Uri?>(null)
    private val saveCopy = registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) lifecycleScope.launch {
            busy = true
            try {
                val bitmap = editor.saveAsBitmap(SaveSettings.Builder().setClearViewsEnabled(false).build())
                withContext(Dispatchers.IO) {
                    requireNotNull(contentResolver.openOutputStream(uri)).use { stream -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) }
                }
                savedUri = uri
                dirty = false
            } catch (e: Exception) { error = e.message ?: "保存失败" }
            finally { busy = false }
        }
    }
    private val crop = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            editor.clearAllViews()
            editorView.source.setImageURI(result.uriContent)
            dirty = true
        } else if (result.error != null) error = result.error?.message
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent.data ?: run { finish(); return }
        editorView = PhotoEditorView(this)
        editor = PhotoEditor.Builder(this, editorView).setPinchTextScalable(true).build()
        editorView.source.setImageURI(source)
        if (editorView.source.drawable == null) error = "无法读取图片"
        editor.brushSize = 6f
        editor.brushColor = android.graphics.Color.RED
        setContent {
            MaterialTheme {
                BackHandler { if (dirty) askToLeave = true else finish() }
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { if (dirty) askToLeave = true else finish() }) { Text("返回") }
                        Text(intent.getStringExtra("name") ?: "图片", modifier = Modifier.weight(1f), maxLines = 1)
                        TextButton(enabled = !busy, onClick = { saveCopy.launch("图片副本.png") }) { Text("另存副本") }
                    }
                    AndroidView(factory = { editorView }, modifier = Modifier.weight(1f).fillMaxWidth())
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(enabled = !busy, onClick = { editor.setBrushDrawingMode(true); dirty = true }) { Text("画笔") }
                        TextButton(enabled = !busy, onClick = { editor.undo() }) { Text("撤销") }
                        TextButton(enabled = !busy, onClick = { cropImage() }) { Text("裁剪 / 旋转") }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    savedUri?.let { uri ->
                        TextButton(onClick = {
                            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "分享图片"))
                        }) { Text("副本已保存 · 分享") }
                    }
                }
                if (askToLeave) AlertDialog(onDismissRequest = { askToLeave = false }, title = { Text("保存修改") }, text = { Text("是否将图片修改另存为副本？") }, confirmButton = { TextButton(onClick = { askToLeave = false; saveCopy.launch("图片副本.png") }) { Text("另存副本") } }, dismissButton = { TextButton(onClick = { finish() }) { Text("放弃修改") } })
            }
        }
    }

    private fun cropImage() = lifecycleScope.launch {
        busy = true
        try {
            val bitmap = editor.saveAsBitmap(SaveSettings.Builder().setClearViewsEnabled(false).build())
            val file = File(cacheDir, "share/crop.png")
            withContext(Dispatchers.IO) {
                file.parentFile?.mkdirs()
                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            }
            val uri = FileProvider.getUriForFile(this@ImageActivity, "$packageName.files", file)
            crop.launch(CropImageContractOptions(uri, CropImageOptions(activityTitle = "裁剪与旋转", outputCompressFormat = Bitmap.CompressFormat.PNG)))
        } catch (e: Exception) { error = e.message ?: "无法打开图片" }
        finally { busy = false }
    }
}
