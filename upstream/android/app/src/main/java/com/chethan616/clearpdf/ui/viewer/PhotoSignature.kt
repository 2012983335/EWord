package com.chethan616.clearpdf.ui.viewer

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.engine.Engine
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.OpenDocument
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

data class SignaturePlacement(val doc: OpenDocument, val file: File, val page: Int, val rect: Rect, val rotation: Float = 0f)

fun AppViewModel.requestSignatureCamera(pad: SignaturePad) {
    signatureMenu = null
    importedSignature = null
    signaturePad = pad
    val file = File(getApplication<android.app.Application>().cacheDir, "share/signature-${System.nanoTime()}.jpg")
    file.parentFile?.mkdirs()
    signatureCameraShots.trySend(file)
}

fun AppViewModel.showSignaturePlacement(doc: OpenDocument, file: File, at: PagePoint? = null) {
    if (document !== doc) {
        tell("签名已保存，请先打开需要签名的文档")
        return
    }
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) {
        tell("无法读取保存的签名")
        return
    }
    val center = at ?: doc.viewer.screenToPage(Offset(doc.viewer.viewW / 2f, (doc.viewer.padTop + doc.viewer.viewH - doc.viewer.padBottom) / 2f), nearest = true)
    val page = center?.page ?: doc.viewer.currentPage
    val info = doc.info.pages[page]
    val width = minOf(180f, info.width / 2f)
    val height = width * options.outHeight / options.outWidth
    val x = center?.x ?: info.width / 2f
    val y = center?.y ?: info.height / 2f
    signaturePlacement = SignaturePlacement(doc, file, page, SignatureTransform(Rect(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f)).constrained(info.width, info.height).rect)
}

fun AppViewModel.confirmSignaturePlacement() {
    val placement = signaturePlacement ?: return
    val eng = engine ?: return
    signaturePlacement = null
    if (document !== placement.doc) return
    busy = "正在插入签名"
    viewModelScope.launch {
        val doc = placement.doc
        val transform = SignatureTransform(placement.rect, placement.rotation)
        val rect = transform.bounds
        val rendered = File(getApplication<android.app.Application>().cacheDir, "signature-render-${System.nanoTime()}.png")
        try {
            if (wordEditing === doc) check(commitWordPara(doc)) { "正文修改尚未提交，请重试" }
            withContext(Dispatchers.IO) { SignatureRaster.write(placement.file, transform, rendered) }
            if (doc.isOffice) {
                eng.command {
                    wordCommand(doc.id, "caret.docEnd", "{}")
                    var applied = 0
                    try {
                        wordCommand(doc.id, "insert.picture", Engine.toJson(mapOf("path" to rendered.absolutePath, "width" to rect.width, "alt" to "EWord handwritten signature")).toString())
                        applied++
                        wordCommand(doc.id, "arrange.wrap", "{\"wrap\":\"inFrontOfText\"}")
                        applied++
                        wordCommand(doc.id, "arrange.bounds", Engine.toJson(mapOf("page" to placement.page, "x" to rect.left, "y" to rect.top, "width" to rect.width, "height" to rect.height)).toString())
                    } catch (failure: Exception) {
                        repeat(applied) { wordCommand(doc.id, "edit.undo", "{}") }
                        throw failure
                    }
                }
            } else {
                eng.tool("page_add_image", mapOf("doc" to doc.id, "page" to placement.page + 1, "path" to rendered.absolutePath, "rect" to listOf(rect.left, rect.top, rect.right, rect.bottom)))
            }
            refresh(doc)
            tell("签名已插入，另存副本即可保留")
        } catch (failure: Exception) {
            refresh(doc)
            signaturePlacement = placement
            tell(failure.message ?: "签名插入失败")
        } finally {
            rendered.delete()
            busy = null
        }
    }
}

@Composable
fun PhotoSignatureMenu(vm: AppViewModel, doc: OpenDocument) {
    AlertDialog(
        onDismissRequest = { vm.signatureMenu = null },
        title = { Text("签名") },
        text = { Text("在白纸上用深色笔签名，拍照后裁剪。已保存的签名可以重复使用。") },
        confirmButton = {
            TextButton(onClick = {
                vm.requestSignatureCamera(SignaturePad(false, doc, null))
            }) { Text("拍照签名") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    vm.signatureMenu = null
                    vm.signatureLibrary = SignatureLibraryRequest(doc)
                }) { Text("使用已保存签名") }
                TextButton(onClick = { vm.signatureMenu = null }) { Text("取消") }
            }
        },
    )
}

@Composable
fun PhotoSignatureOverlay(vm: AppViewModel, placement: SignaturePlacement) {
    val doc = placement.doc
    val bitmap = remember(placement.file, placement.file.lastModified()) { decodeSignature(placement.file, 2048) } ?: return
    val density = LocalDensity.current
    var preview by remember(placement.file, placement.page) { mutableStateOf<SignatureTransform?>(null) }
    val source = SignatureTransform(placement.rect, placement.rotation)
    val shown = preview ?: source
    val a = doc.viewer.pageToScreen(placement.page, shown.rect.left, shown.rect.top)
    val b = doc.viewer.pageToScreen(placement.page, shown.rect.right, shown.rect.bottom)
    BackHandler { vm.signaturePlacement = null }
    Box(Modifier.fillMaxSize().zIndex(20f)) {
        Image(bitmap.asImageBitmap(), "签名预览", contentScale = ContentScale.FillBounds,
            modifier = Modifier.offset { IntOffset(a.x.roundToInt(), a.y.roundToInt()) }
                .size(with(density) { (b.x - a.x).toDp() }, with(density) { (b.y - a.y).toDp() })
                .graphicsLayer { rotationZ = shown.rotation })
        SignatureHandles(doc, placement.page, source, shown, onPreview = { preview = it }, onCommit = {
            vm.signaturePlacement = placement.copy(rect = it.rect, rotation = it.rotation)
        })
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.White).navigationBarsPadding().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("四角等比缩放 · 侧边拉伸 · 上方手柄旋转")
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                TextButton(onClick = { vm.signaturePlacement = null }) { Text("取消") }
                Button(onClick = vm::confirmSignaturePlacement, enabled = preview == null) { Text("确认签名") }
            }
        }
    }
}
