package com.chethan616.clearpdf.ui.sheets

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.core.CellSpan
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.OfficeInlineDraft
import com.chethan616.clearpdf.ui.OfficeInlineInput
import com.chethan616.clearpdf.ui.OpenDocument
import kotlin.math.max

internal fun SheetLayout.cellBounds(cell: CellSpan, scale: Float, pan: Offset, density: Float): Rect {
    val k = density * scale
    val r0 = cell.row0.toInt().coerceIn(0, rows - 1)
    val c0 = cell.col0.toInt().coerceIn(0, cols - 1)
    val r1 = (cell.row1.toInt() + 1).coerceIn(r0 + 1, rows)
    val c1 = (cell.col1.toInt() + 1).coerceIn(c0 + 1, cols)
    val x = HEAD_W * k.coerceAtMost(density * 1.5f) + if (c0 < info.frozenCols.toInt()) 0f else pan.x
    val y = HEAD_H * k.coerceAtMost(density * 1.5f) + if (r0 < info.frozenRows.toInt()) 0f else pan.y
    return Rect(x + colX[c0] * k, y + rowY[r0] * k, x + colX[c1] * k, y + rowY[r1] * k)
}

@Composable
internal fun SheetInlineInput(vm: AppViewModel, doc: OpenDocument, grid: SheetGridState, draft: OfficeInlineDraft, cell: CellSpan) {
    val layout = grid.layout ?: return
    val density = LocalDensity.current
    val rect = layout.cellBounds(cell, grid.scale, grid.pan, density.density)
    val style = grid.blocks.values.asSequence().flatten().firstOrNull { it.row == cell.row0 && it.col == cell.col0 }
    val paper = style?.fill?.let { Color(0xFF000000.toInt() or it.toInt()) } ?: Color.White
    val ink = style?.ink?.takeIf { it != 0u }?.let { Color(0xFF000000.toInt() or it.toInt()) }
        ?: if (paper.luminance() > 0.5f) Color.Black else Color.White
    LaunchedEffect(draft, grid.viewport) {
        if (grid.viewport.height == 0) return@LaunchedEffect
        val bounds = layout.cellBounds(cell, grid.scale, grid.pan, density.density)
        val dx = if (cell.col0 >= layout.info.frozenCols) max(0f, bounds.right - grid.viewport.width + 12f) else 0f
        val dy = if (cell.row0 >= layout.info.frozenRows) max(0f, bounds.bottom - grid.viewport.height + 12f) else 0f
        if (dx > 0 || dy > 0) grid.pan -= Offset(dx, dy)
    }
    with(density) {
        val height = rect.height.toDp().coerceAtLeast(18.dp)
        OfficeInlineInput(vm, doc, draft,
            Modifier.offset { IntOffset(rect.left.toInt(), rect.top.toInt()) }
                .width(rect.width.toDp().coerceAtLeast(32.dp)).heightIn(min = height, max = maxOf(height, 120.dp)),
            fontSize = ((style?.size ?: 11f) * grid.scale * density.density).toSp(),
            bold = style?.bold == true,
            ink = ink, paper = paper,
        )
    }
}
