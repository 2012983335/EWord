package com.chethan616.clearpdf.ui.viewer

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.OpenDocument
import com.chethan616.clearpdf.ui.glass.Feel
import com.chethan616.clearpdf.ui.glass.GlassDialog
import com.chethan616.clearpdf.ui.glass.LocalHaptics
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SignatureLibraryRequest(val doc: OpenDocument, val initials: Boolean = false, val at: PagePoint? = null)

@Composable
fun SignatureLibrary(vm: AppViewModel, request: SignatureLibraryRequest) {
    val version = vm.signaturesChanged
    val files = remember(request.initials, version) { vm.signatures.saved(request.initials) }
    var deleting by remember(request) { mutableStateOf(false) }
    val haptics = LocalHaptics.current
    val gridHeight = (LocalConfiguration.current.screenHeightDp - 240).coerceIn(112, 400).dp
    GlassDialog(
        onDismiss = { vm.signatureLibrary = null },
        title = "已保存${if (request.initials) "缩写" else "签名"}（${files.size}）",
        confirm = "新增", onConfirm = { vm.signatureLibrary = null; vm.signaturePad = SignaturePad(request.initials, request.doc, request.at) },
        dismiss = if (deleting) "完成" else "关闭",
        onDismissButton = { if (deleting) deleting = false else vm.signatureLibrary = null },
    ) {
        Text(if (deleting) "点击右上角红叉删除签名" else "点击使用，长按管理已保存签名")
        if (files.isEmpty()) Text("还没有保存的签名，点击“新增”制作一份。", Modifier.padding(vertical = 16.dp))
        else LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxWidth().heightIn(max = gridHeight),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(files, key = { it.absolutePath }) { file ->
                val thumbnail by produceState<ImageBitmap?>(null, file.absolutePath) {
                    value = withContext(Dispatchers.IO) { decodeSignature(file, 256)?.asImageBitmap() }
                }
                Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                    .combinedClickable(hapticFeedbackEnabled = false, onClick = {
                        if (!deleting) {
                            vm.signatureLibrary = null
                            vm.showSignaturePlacement(request.doc, file, request.at)
                        }
                    }, onLongClick = { deleting = true; haptics?.play(Feel.Bloom) })) {
                    thumbnail?.let { Image(it, "已保存签名", Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 28.dp)) }
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(file.lastModified())),
                        Modifier.align(Alignment.BottomCenter).padding(5.dp), color = Color.DarkGray, maxLines = 1)
                    if (deleting) IconButton(onClick = {
                        runCatching { vm.signatures.remove(file, request.initials) }
                            .onSuccess { vm.signaturesChanged++ }
                            .onFailure { vm.tell(it.message ?: "删除签名失败") }
                    }, modifier = Modifier.align(Alignment.TopEnd).size(40.dp)) {
                        Icon(LucideIcons.X, "删除此签名", Modifier.size(22.dp).background(Color(0xFFDA3E48), CircleShape).padding(3.dp), tint = Color.White)
                    }
                }
            }
        }
    }
}

internal fun decodeSignature(file: File, maxSide: Int): android.graphics.Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    options.inSampleSize = 1
    while (options.outWidth / options.inSampleSize > maxSide || options.outHeight / options.inSampleSize > maxSide) options.inSampleSize *= 2
    options.inJustDecodeBounds = false
    return BitmapFactory.decodeFile(file.absolutePath, options)
}
