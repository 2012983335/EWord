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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

data class SignaturePlacement(val doc: OpenDocument, val file: File, val page: Int, val rect: Rect)

fun AppViewModel.requestSignatureCamera(pad: SignaturePad) {
    signatureMenu = null
    importedSignature = null
    signaturePad = pad
    val file = File(getApplication<android.app.Application>().cacheDir, "share/signature-${System.nanoTime()}.jpg")
    file.parentFile?.mkdirs()
    signatureCameraShots.trySend(file)
}

fun AppViewModel.showSignaturePlacement(doc: OpenDocument, file: File) {
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
    val center = doc.viewer.screenToPage(Offset(doc.viewer.viewW / 2f, (doc.viewer.padTop + doc.viewer.viewH - doc.viewer.padBottom) / 2f), nearest = true)
    val page = center?.page ?: doc.viewer.currentPage
    val info = doc.info.pages[page]
    val width = minOf(180f, info.width / 2f)
    val height = width * options.outHeight / options.outWidth
    val x = center?.x ?: info.width / 2f
    val y = center?.y ?: info.height / 2f
    signaturePlacement = SignaturePlacement(doc, file, page, boundedSignature(Rect(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f), info.width, info.height))
}

private fun boundedSignature(rect: Rect, pageWidth: Float, pageHeight: Float): Rect {
    val scale = minOf(1f, pageWidth / rect.width, pageHeight / rect.height)
    val width = rect.width * scale
    val height = rect.height * scale
    val left = rect.left.coerceIn(0f, pageWidth - width)
    val top = rect.top.coerceIn(0f, pageHeight - height)
    return Rect(left, top, left + width, top + height)
}

fun AppViewModel.moveSignature(dx: Float, dy: Float) {
    val placement = signaturePlacement ?: return
    val info = placement.doc.info.pages[placement.page]
    signaturePlacement = placement.copy(rect = boundedSignature(placement.rect.translate(Offset(dx, dy)), info.width, info.height))
}

fun AppViewModel.resizeSignature(points: List<Float>) {
    val placement = signaturePlacement ?: return
    val old = placement.rect
    val requested = Rect(points[0], points[1], points[2], points[3])
    val widthScale = requested.width / old.width
    val heightScale = requested.height / old.height
    val scale = if (abs(widthScale - 1f) > abs(heightScale - 1f)) widthScale else heightScale
    val width = (old.width * scale).coerceAtLeast(12f)
    val height = width * old.height / old.width
    val x = if (requested.left != old.left) old.right - width else old.left
    val y = if (requested.top != old.top) old.bottom - height else old.top
    val info = placement.doc.info.pages[placement.page]
    signaturePlacement = placement.copy(rect = boundedSignature(Rect(x, y, x + width, y + height), info.width, info.height))
}

fun AppViewModel.confirmSignaturePlacement() {
    val placement = signaturePlacement ?: return
    val eng = engine ?: return
    signaturePlacement = null
    if (document !== placement.doc) return
    busy = "正在插入签名"
    viewModelScope.launch {
        val doc = placement.doc
        val rect = placement.rect
        try {
            if (doc.isOffice) {
                eng.command {
                    wordCommand(doc.id, "caret.docEnd", "{}")
                    var applied = 0
                    try {
                        wordCommand(doc.id, "insert.picture", Engine.toJson(mapOf("path" to placement.file.absolutePath, "width" to rect.width, "alt" to "EWord handwritten signature")).toString())
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
                eng.tool("page_add_image", mapOf("doc" to doc.id, "page" to placement.page + 1, "path" to placement.file.absolutePath, "rect" to listOf(rect.left, rect.top, rect.right, rect.bottom)))
            }
            refresh(doc)
            tell("签名已插入，另存副本即可保留")
        } catch (failure: Exception) {
            refresh(doc)
            signaturePlacement = placement
            tell(failure.message ?: "签名插入失败")
        } finally {
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
                TextButton(enabled = vm.signatures.signature.isFile, onClick = {
                    vm.signatureMenu = null
                    vm.showSignaturePlacement(doc, vm.signatures.signature)
                }) { Text("使用已保存签名") }
                TextButton(onClick = { vm.signatureMenu = null }) { Text("取消") }
            }
        },
    )
}

@Composable
fun PhotoSignatureOverlay(vm: AppViewModel, placement: SignaturePlacement) {
    val doc = placement.doc
    val bitmap = remember(placement.file, placement.file.lastModified()) { BitmapFactory.decodeFile(placement.file.absolutePath) } ?: return
    val density = LocalDensity.current
    val rect = placement.rect
    val a = doc.viewer.pageToScreen(placement.page, rect.left, rect.top)
    val b = doc.viewer.pageToScreen(placement.page, rect.right, rect.bottom)
    BackHandler { vm.signaturePlacement = null }
    Box(Modifier.fillMaxSize().zIndex(20f)) {
        Image(bitmap.asImageBitmap(), "签名预览", modifier = Modifier.offset { IntOffset(a.x.roundToInt(), a.y.roundToInt()) }.size(with(density) { (b.x - a.x).toDp() }, with(density) { (b.y - a.y).toDp() }))
        PageRectangleHandles(doc, placement.page, placement.file.absolutePath, listOf(rect.left, rect.top, rect.right, rect.bottom), onMove = vm::moveSignature, onResize = vm::resizeSignature)
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.White).navigationBarsPadding().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("拖动签名移动位置，拖动四角等比缩放")
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                TextButton(onClick = { vm.signaturePlacement = null }) { Text("取消") }
                Button(onClick = vm::confirmSignaturePlacement) { Text("确认签名") }
            }
        }
    }
}
