package com.chethan616.clearpdf.ui.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.OfficeInlineInput
import com.chethan616.clearpdf.ui.OfficeInlineTarget
import com.chethan616.clearpdf.ui.OpenDocument

@Composable
fun SlideInlineLayer(vm: AppViewModel, doc: OpenDocument) {
    val draft = vm.inlineOffice?.takeIf { it.docId == doc.id } ?: return
    val target = draft.target as? OfficeInlineTarget.Slide ?: return
    val v = doc.viewer
    val density = LocalDensity.current
    val a = v.pageToScreen(target.page, target.rect.left, target.rect.top)
    val b = v.pageToScreen(target.page, target.rect.right, target.rect.bottom)
    val ime = WindowInsets.ime.getBottom(density)
    LaunchedEffect(draft, ime) {
        val room = v.viewH - ime - with(density) { 80.dp.toPx() }
        if (b.y > room) v.setScroll(v.scrollX, v.scrollY + b.y - room)
    }
    with(density) {
        val height = (b.y - a.y).toDp().coerceAtLeast(26.dp)
        OfficeInlineInput(vm, doc, draft,
            Modifier.offset { IntOffset(a.x.toInt(), a.y.toInt()) }
                .width((b.x - a.x).toDp().coerceAtLeast(32.dp) + 6.dp)
                .heightIn(min = height + 4.dp, max = maxOf(height + 4.dp, 160.dp)),
            fontSize = (target.size * v.pixelsPerPoint(target.page)).toSp(),
        )
    }
}

@Composable
fun SlideInlineDock(vm: AppViewModel, doc: OpenDocument) {
    val keyboard = LocalSoftwareKeyboardController.current
    Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        TextButton(onClick = { keyboard?.hide(); vm.finishOfficeInline(doc) }) { Text("完成") }
        TextButton(onClick = { keyboard?.hide(); vm.saveCopyAs(doc) }) { Text("另存为") }
    }
}
