package com.chethan616.clearpdf.ui.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.core.CellSpan
import com.chethan616.clearpdf.core.CellView
import com.chethan616.clearpdf.core.SheetInfo
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.OpenDocument
import com.chethan616.clearpdf.ui.glass.GlassLoader
import com.chethan616.clearpdf.ui.theme.clear
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import com.chethan616.clearpdf.engine.Engine
import com.chethan616.clearpdf.ui.glass.glass
import com.chethan616.clearpdf.ui.pdfColors
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** Rows and columns fetched together from the engine. */
private const val BLOCK_ROWS = 50
private const val BLOCK_COLS = 20

/** How many rows and columns past the used area the grid still shows (room to look around). */
private const val SPARE_ROWS = 40
private const val SPARE_COLS = 8

/** The heading strips: column letters on top, row numbers at the left (points). */
private const val HEAD_H = 20f
private const val HEAD_W = 36f

/** One sheet's layout: where every row and column starts (points from A1), its merges and freeze. */
internal class SheetLayout(val info: SheetInfo, val colX: FloatArray, val rowY: FloatArray, val merges: List<CellSpan>) {
    val cols get() = colX.size - 1
    val rows get() = rowY.size - 1

    /** The first index whose start is past [v] in [edges], less one: the line [v] falls in. */
    fun indexAt(edges: FloatArray, v: Float): Int {
        var lo = 0
        var hi = edges.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (edges[mid] <= v) lo = mid else hi = mid - 1
        }
        return lo.coerceAtMost(edges.size - 2).coerceAtLeast(0)
    }
}

/** "A", "B", …, "Z", "AA", … */
private fun columnName(c: Int): String {
    var n = c + 1
    val sb = StringBuilder()
    while (n > 0) {
        val r = (n - 1) % 26
        sb.insert(0, 'A' + r)
        n = (n - 1) / 26
    }
    return sb.toString()
}

private fun rgb(c: UInt): Color = Color(0xFF000000.toInt() or c.toInt())

/**
 * One workbook's grid as the screen holds it: the sheets, the one shown, its layout and the cells
 * fetched so far (in blocks), the zoom and scroll, the chosen cell and the search hits.
 */
@androidx.compose.runtime.Stable
class SheetGridState internal constructor(private val eng: Engine, private val docId: ULong) {
    var sheets by mutableStateOf<List<SheetInfo>>(emptyList())
        internal set
    var current by mutableIntStateOf(0)
    internal var layout by mutableStateOf<SheetLayout?>(null)
    internal val blocks = mutableStateMapOf<Triple<Int, Int, Int>, List<CellView>>()
    var scale by mutableStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
    /** The chosen cell, if one is. */
    var selected by mutableStateOf<CellSpan?>(null)
    var hits by mutableStateOf<List<CellSpan>>(emptyList())
    var hit by mutableIntStateOf(0)
    /** Bumped when the workbook changed: the layout and cells are fetched again. */
    var version by mutableIntStateOf(0)

    internal suspend fun loadSheets() {
        sheets = runCatching { eng.command { sheets(docId) } }.getOrDefault(emptyList())
        if (sheets.getOrNull(current)?.hidden != false) current = sheets.indexOfFirst { !it.hidden }.coerceAtLeast(0)
    }

    /** Show sheet [index]: the view goes back to A1, nothing chosen, nothing found. */
    fun show(index: Int) {
        if (index == current) return
        layout = null
        selected = null
        hits = emptyList()
        pan = Offset.Zero
        current = index
    }

    internal suspend fun loadLayout() {
        val info = sheets.getOrNull(current) ?: return
        val sheet = current
        val rows = min(info.rows.toInt() + SPARE_ROWS, 20_000)
        val cols = min(info.cols.toInt() + SPARE_COLS, 2_000)
        val loaded = runCatching {
            eng.command {
                val axes = sheetAxes(docId, sheet.toUInt(), 0u, rows.toUInt(), 0u, cols.toUInt())
                val colX = FloatArray(cols + 1).also { a -> axes.colWidths.forEachIndexed { i, w -> a[i + 1] = a[i] + w } }
                val rowY = FloatArray(rows + 1).also { a -> axes.rowHeights.forEachIndexed { i, h -> a[i + 1] = a[i] + h } }
                SheetLayout(info, colX, rowY, sheetMerges(docId, sheet.toUInt()))
            }
        }.getOrNull()
        // a sheet chosen meanwhile wins
        if (sheet == current) layout = loaded
    }

    internal suspend fun loadBlock(br: Int, bc: Int) {
        val key = Triple(current, br, bc)
        if (key in blocks) return
        blocks[key] = emptyList()
        blocks[key] = runCatching {
            eng.command { sheetCells(docId, current.toUInt(), (br * BLOCK_ROWS).toUInt(), BLOCK_ROWS.toUInt(), (bc * BLOCK_COLS).toUInt(), BLOCK_COLS.toUInt()) }
        }.getOrDefault(emptyList())
    }

    /** After an edit: everything shown is fetched again (sizes and values may all have moved). */
    fun changed() {
        blocks.clear()
        version++
    }
}

@Composable
fun rememberSheetGrid(eng: Engine, docId: ULong): SheetGridState {
    val state = remember(docId) { SheetGridState(eng, docId) }
    LaunchedEffect(state, state.version) { state.loadSheets() }
    LaunchedEffect(state, state.sheets, state.current, state.version) {
        if (state.sheets.isEmpty()) return@LaunchedEffect
        state.loadLayout()
    }
    return state
}

/**
 * The grid itself: pans and pinch-zooms, a tap chooses a cell ([onCell]; [onDoubleTapCell] for
 * the editor), the heading strips and frozen panes held in place. [top] and [bottom] are the
 * room the bars over it take (pixels): the grid runs on beneath them for their glass to refract.
 */
@Composable
fun SheetGrid(
    state: SheetGridState,
    modifier: Modifier = Modifier,
    top: Float = 0f,
    onCell: ((CellSpan?) -> Unit)? = null,
    onDoubleTapCell: ((CellSpan) -> Unit)? = null,
) {
    val c = pdfColors
    val sheetSurface = if (c.dark) Color(0xFF18192F) else Color.White
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 256)
    val l = state.layout
    Box(modifier.fillMaxSize().background(c.surface)) {
        if (l == null) {
            Box(Modifier.fillMaxSize().padding(top = with(density) { top.toDp() } + 40.dp), contentAlignment = Alignment.TopCenter) { GlassLoader(size = 64.dp) }
            return@Box
        }
        val px = density.density
        // the blocks in view, asked for as the grid moves
        LaunchedEffect(l) {
            snapshotFlow { Triple(state.pan, state.scale, l) }.distinctUntilChanged().collect { (p, s, lay) ->
                val k = px * s
                val c0 = lay.indexAt(lay.colX, (-p.x / k).coerceAtLeast(0f)) / BLOCK_COLS
                val r0 = lay.indexAt(lay.rowY, (-p.y / k).coerceAtLeast(0f)) / BLOCK_ROWS
                for (br in r0..r0 + 1) for (bc in c0..c0 + 1) {
                    if (br * BLOCK_ROWS < lay.rows && bc * BLOCK_COLS < lay.cols) state.loadBlock(br, bc)
                }
            }
        }
        // a hit brought into view: its cell near the top left, under the heading strips
        LaunchedEffect(state.hit, state.hits) {
            val h = state.hits.getOrNull(state.hit) ?: return@LaunchedEffect
            val k = px * state.scale
            val r = h.row0.toInt().coerceIn(0, l.rows - 1)
            val col = h.col0.toInt().coerceIn(0, l.cols - 1)
            val fr = l.info.frozenRows.toInt()
            val fc = l.info.frozenCols.toInt()
            val x = if (col < fc) state.pan.x else -(l.colX[col] - l.colX[minOf(fc, l.cols)]) * k + 24 * px
            val y = if (r < fr) state.pan.y else -(l.rowY[r] - l.rowY[minOf(fr, l.rows)]) * k + 24 * px
            state.pan = Offset(x.coerceAtMost(0f), y.coerceAtMost(0f))
            state.selected = h
        }
        // the cell under a point of the canvas (null: a heading strip)
        fun cellAt(p: Offset): CellSpan? {
            val k = px * state.scale
            val head = Offset(HEAD_W * k.coerceAtMost(px * 1.5f), HEAD_H * k.coerceAtMost(px * 1.5f))
            val y = p.y - top
            if (p.x < head.x || y < head.y) return null
            val fr = minOf(l.info.frozenRows.toInt(), l.rows)
            val fc = minOf(l.info.frozenCols.toInt(), l.cols)
            val gx = p.x - head.x
            val gy = y - head.y
            val col = if (gx < l.colX[fc] * k) l.indexAt(l.colX, gx / k) else l.indexAt(l.colX, (gx - state.pan.x) / k)
            val row = if (gy < l.rowY[fr] * k) l.indexAt(l.rowY, gy / k) else l.indexAt(l.rowY, (gy - state.pan.y) / k)
            val m = l.merges.firstOrNull { row.toUInt() in it.row0..it.row1 && col.toUInt() in it.col0..it.col1 }
            return m ?: CellSpan(row.toUInt(), col.toUInt(), row.toUInt(), col.toUInt())
        }
        val flinger = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
        Canvas(
            Modifier
                .fillMaxSize()
                .panZoomFling(state, l, px, flinger)
                .pointerInput(l, onCell, onDoubleTapCell) {
                    detectTapGestures(
                        onPress = { flinger[0]?.cancel() },
                        onTap = { p -> onCell?.invoke(cellAt(p)) },
                        onDoubleTap = { p ->
                            val cell = cellAt(p)
                            if (cell != null && onDoubleTapCell != null) onDoubleTapCell(cell)
                            else {
                                state.scale = if (state.scale > 1.2f) 1f else 2f
                                state.pan = Offset.Zero
                            }
                        },
                    )
                },
        ) {
            translate(top = top) {
                drawSheet(l, state.blocks, state.current, px * state.scale, state.pan, measurer, sheetSurface, c.text, c.dark, state.hits.getOrNull(state.hit), state.selected)
            }
        }
    }
}

/**
 * A workbook in the app: its grid under liquid glass. The top row has Back, the title and sheet,
 * Find and More (Export as PDF, Save as, Print, Share). A tap chooses a cell: the formula bar shows
 * what it holds and takes a new value (a double tap edits at once), and the dock formats it with
 * GridCraft's Home commands. The sheet tabs ride above the dock.
 */
@Composable
fun SheetViewer(vm: AppViewModel, doc: OpenDocument) {
    val eng = vm.engine ?: return
    val grid = rememberSheetGrid(eng, doc.id)
    val top = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this) + 56.dp.toPx() }
    androidx.compose.material3.MaterialTheme {
        Box(Modifier.fillMaxSize()) {
            SheetGrid(grid, top = top, onCell = { grid.selected = it })
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.White).statusBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.TextButton(onClick = { vm.closeDocument() }) { Text("返回") }
                    Text(doc.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("只读", modifier = Modifier.padding(16.dp))
                }
            }
            LazyRow(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.White).navigationBarsPadding()) {
                items(grid.sheets.size) { index ->
                    androidx.compose.material3.TextButton(onClick = { grid.show(index) }) {
                        Text(grid.sheets[index].name, color = if (grid.current == index) Color(0xFF345CDA) else Color.Gray)
                    }
                }
            }
        }
    }
}
private fun DrawScope.drawSheet(
    l: SheetLayout,
    blocks: Map<Triple<Int, Int, Int>, List<CellView>>,
    sheet: Int,
    k: Float,
    pan: Offset,
    measurer: TextMeasurer,
    sheetSurface: Color,
    ink: Color,
    dark: Boolean,
    found: CellSpan?,
    selected: CellSpan?,
) {
    drawRect(sheetSurface)
    val head = Offset(HEAD_W * k.coerceAtMost(density * 1.5f), HEAD_H * k.coerceAtMost(density * 1.5f))
    val fr = min(l.info.frozenRows.toInt(), l.rows)
    val fc = min(l.info.frozenCols.toInt(), l.cols)
    val frozenW = l.colX[fc] * k
    val frozenH = l.rowY[fr] * k
    val body = Rect(head.x + frozenW, head.y + frozenH, size.width, size.height)
    // a region of the sheet: rows/columns from the given starts, drawn from [origin]
    fun region(clip: Rect, origin: Offset, rowsFrom: Int, rowsTo: Int, colsFrom: Int, colsTo: Int, scrollX: Boolean, scrollY: Boolean) {
        if (clip.width <= 0f || clip.height <= 0f) return
        val ox = origin.x + (if (scrollX) pan.x else 0f)
        val oy = origin.y + (if (scrollY) pan.y else 0f)
        // the lines in view
        val c0 = max(colsFrom, l.indexAt(l.colX, (clip.left - ox) / k))
        val c1 = min(colsTo, l.indexAt(l.colX, (clip.right - ox) / k) + 1)
        val r0 = max(rowsFrom, l.indexAt(l.rowY, (clip.top - oy) / k))
        val r1 = min(rowsTo, l.indexAt(l.rowY, (clip.bottom - oy) / k) + 1)
        if (c0 >= c1 || r0 >= r1) return
        clipRect(clip.left, clip.top, clip.right, clip.bottom) {
            val cell = { r: Int, c: Int -> Rect(ox + l.colX[c] * k, oy + l.rowY[r] * k, ox + l.colX[c + 1] * k, oy + l.rowY[r + 1] * k) }
            // grid lines
            if (l.info.gridlines) {
                val line = if (dark) Color(0xFF343650) else Color(0xFFE1E4EA)
                for (c in c0..c1) drawLine(line, Offset(ox + l.colX[c] * k, clip.top), Offset(ox + l.colX[c] * k, clip.bottom), 1f)
                for (r in r0..r1) drawLine(line, Offset(clip.left, oy + l.rowY[r] * k), Offset(clip.right, oy + l.rowY[r] * k), 1f)
            }
            // the cells of the blocks in view: fills, then borders, then text
            val shown = ArrayList<CellView>()
            for (br in r0 / BLOCK_ROWS..(r1 - 1) / BLOCK_ROWS) for (bc in c0 / BLOCK_COLS..(c1 - 1) / BLOCK_COLS) {
                blocks[Triple(sheet, br, bc)]?.forEach { v -> if (v.row.toInt() in r0 until r1 && v.col.toInt() in c0 until c1) shown += v }
            }
            fun spanOf(v: CellView): Rect {
                val m = l.merges.firstOrNull { it.row0 == v.row && it.col0 == v.col }
                return if (m == null) cell(v.row.toInt(), v.col.toInt())
                else cell(m.row0.toInt(), m.col0.toInt()).let { a ->
                    val b = cell(min(m.row1.toInt(), l.rows - 1), min(m.col1.toInt(), l.cols - 1))
                    Rect(a.left, a.top, b.right, b.bottom)
                }
            }
            for (v in shown) v.fill?.let { drawRect(rgb(it), spanOf(v).topLeft, spanOf(v).size) }
            for (v in shown) {
                val r = spanOf(v)
                fun edge(e: com.chethan616.clearpdf.core.CellEdge, a: Offset, b: Offset) {
                    if (e.weight > 0u) drawLine(rgb(e.color), a, b, e.weight.toFloat() * density * 0.6f)
                }
                edge(v.left, r.topLeft, r.bottomLeft)
                edge(v.top, r.topLeft, r.topRight)
                edge(v.right, r.topRight, r.bottomRight)
                edge(v.bottom, r.bottomLeft, r.bottomRight)
            }
            // the chosen cell (or its merged area): a ring in the accent, as a spreadsheet marks it
            selected?.let { f ->
                val r = f.row0.toInt()
                val col = f.col0.toInt()
                if (r in r0 until r1 && col in c0 until c1) {
                    val a = cell(r, col)
                    val b = cell(minOf(f.row1.toInt(), l.rows - 1), minOf(f.col1.toInt(), l.cols - 1))
                    val box = Rect(a.left, a.top, b.right, b.bottom)
                    drawRect(Color(0xFF0A84FF), box.topLeft, box.size, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f * density))
                }
            }
            // the search hit in this region: an accent ring round its cell
            found?.let { f ->
                val r = f.row0.toInt()
                val col = f.col0.toInt()
                if (r in r0 until r1 && col in c0 until c1) {
                    val box = cell(r, col)
                    drawRect(Color(0x330A84FF), box.topLeft, box.size)
                    drawRect(Color(0xFF0A84FF), box.topLeft, box.size, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f * density))
                }
            }
            for (v in shown) {
                if (v.text.isEmpty()) continue
                val r = spanOf(v)
                val pad = 2f * k
                val maxW = (r.width - 2 * pad).toInt()
                if (maxW <= 0) continue
                val style = TextStyle(
                    color = if (v.ink == 0u) {
                        val cellSurface = v.fill?.let(::rgb) ?: sheetSurface
                        if (cellSurface.luminance() > 0.5f) Color(0xFF15171C) else ink
                    } else rgb(v.ink),
                    fontSize = (v.size * k / density / fontScale).sp,
                    fontWeight = if (v.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (v.italic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = when {
                        v.underline && v.strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                        v.underline -> TextDecoration.Underline
                        v.strike -> TextDecoration.LineThrough
                        else -> null
                    },
                )
                val t = measurer.measure(
                    v.text, style,
                    overflow = TextOverflow.Clip,
                    softWrap = v.wrap, maxLines = if (v.wrap) Int.MAX_VALUE else 1,
                    constraints = Constraints(maxWidth = if (v.wrap) maxW else Constraints.Infinity, maxHeight = max(1, r.height.toInt())),
                )
                val w = t.size.width.toFloat()
                val x = when (v.align) {
                    "c" -> r.left + (r.width - w) / 2f
                    "r" -> r.right - pad - w
                    else -> r.left + pad
                }
                val y = when (v.valign) {
                    "t" -> r.top + pad / 2
                    "c" -> r.top + (r.height - t.size.height) / 2f
                    else -> r.bottom - pad / 2 - t.size.height
                }
                // text stays within its cell (a neighbour's text would overlap it otherwise)
                clipRect(r.left, r.top, r.right, r.bottom) { drawText(t, topLeft = Offset(x, y)) }
            }
        }
    }
    // body, then the frozen strips, then the corner where both are frozen
    region(body, head, fr, l.rows, fc, l.cols, scrollX = true, scrollY = true)
    region(Rect(head.x + frozenW, head.y, size.width, head.y + frozenH), head, 0, fr, fc, l.cols, scrollX = true, scrollY = false)
    region(Rect(head.x, head.y + frozenH, head.x + frozenW, size.height), head, fr, l.rows, 0, fc, scrollX = false, scrollY = true)
    region(Rect(head.x, head.y, head.x + frozenW, head.y + frozenH), head, 0, fr, 0, fc, scrollX = false, scrollY = false)
    val frozenDivider = if (dark) Color(0xFF777D9A) else Color(0xFF9AA3B2)
    if (fr > 0) drawLine(frozenDivider, Offset(head.x, head.y + frozenH), Offset(size.width, head.y + frozenH), density)
    if (fc > 0) drawLine(frozenDivider, Offset(head.x + frozenW, head.y), Offset(head.x + frozenW, size.height), density)
    // the heading strips
    val strip = if (dark) Color(0xFF20213B) else Color(0xFFF2F4F8)
    val label = TextStyle(color = ink.copy(alpha = 0.6f), fontSize = (11 * min(k / density, 1.5f) / fontScale).sp)
    drawRect(strip, Offset(0f, 0f), Size(size.width, head.y))
    drawRect(strip, Offset(0f, 0f), Size(head.x, size.height))
    val lineC = if (dark) Color(0xFF343650) else Color(0xFFD5D9E0)
    clipRect(head.x, 0f, size.width, head.y) {
        for (c in 0 until l.cols) {
            val x0 = head.x + l.colX[c] * k + (if (c >= fc) pan.x else 0f)
            val x1 = head.x + l.colX[c + 1] * k + (if (c >= fc) pan.x else 0f)
            if (x1 < head.x + (if (c >= fc) frozenW else 0f) || x0 > size.width || x1 - x0 < 1f) continue
            drawLine(lineC, Offset(x1, 0f), Offset(x1, head.y), 1f)
            val t = measurer.measure(columnName(c), label)
            if (t.size.width < x1 - x0) drawText(t, topLeft = Offset((x0 + x1 - t.size.width) / 2f, (head.y - t.size.height) / 2f))
        }
    }
    clipRect(0f, head.y, head.x, size.height) {
        for (r in 0 until l.rows) {
            val y0 = head.y + l.rowY[r] * k + (if (r >= fr) pan.y else 0f)
            val y1 = head.y + l.rowY[r + 1] * k + (if (r >= fr) pan.y else 0f)
            if (y1 < head.y + (if (r >= fr) frozenH else 0f) || y0 > size.height || y1 - y0 < 1f) continue
            drawLine(lineC, Offset(0f, y1), Offset(head.x, y1), 1f)
            val t = measurer.measure((r + 1).toString(), label)
            if (t.size.height < y1 - y0 + 2) drawText(t, topLeft = Offset((head.x - t.size.width) / 2f, (y0 + y1 - t.size.height) / 2f))
        }
    }
    drawLine(lineC, Offset(0f, head.y), Offset(size.width, head.y), 1f)
    drawLine(lineC, Offset(head.x, 0f), Offset(head.x, size.height), 1f)
}

/**
 * Pan and pinch-zoom the grid with a finger or two, and let it glide on when let go, slowing as a
 * list does (the platform's spline decay), exactly matching the fluid physics of the PDF & doc viewers.
 */
private fun Modifier.panZoomFling(
    state: SheetGridState,
    l: SheetLayout,
    px: Float,
    flinger: Array<kotlinx.coroutines.Job?>,
): Modifier = pointerInput(l) {
    val touchSlop = viewConfiguration.touchSlop
    fun clamp(p: Offset, s: Float): Offset {
        val k = px * s
        val maxX = (l.colX.last() * k - size.width + 120.dp.toPx()).coerceAtLeast(0f)
        val maxY = (l.rowY.last() * k - size.height + 160.dp.toPx()).coerceAtLeast(0f)
        return Offset(p.x.coerceIn(-maxX, 0f), p.y.coerceIn(-maxY, 0f))
    }

    kotlinx.coroutines.coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            flinger[0]?.cancel()
            val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var moved = Offset.Zero
            var zoomed = 1f
            var started = false
            var pinched = false
            var centroid = down.position

            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                val zoomChange = event.calculateZoom()
                val pan = event.calculatePan()

                if (!started) {
                    moved += pan
                    zoomed *= zoomChange
                    if (moved.getDistance() > touchSlop || (pressed.size > 1 && kotlin.math.abs(1f - zoomed) * size.width / 2f > touchSlop)) {
                        started = true
                    }
                }

                if (started) {
                    if (pressed.size > 1) {
                        pinched = true
                        val c = event.calculateCentroid()
                        centroid = c
                        if (zoomChange != 1f) {
                            val newScale = (state.scale * zoomChange).coerceIn(0.4f, 4f)
                            val factor = newScale / state.scale
                            val origin = if (c.isSpecified) c else Offset.Zero
                            val target = (state.pan - origin) * factor + origin + pan
                            state.pan = clamp(target, newScale)
                            state.scale = newScale
                        }
                    } else {
                        if (pan != Offset.Zero) {
                            state.pan = clamp(state.pan + pan, state.scale)
                        }
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }

                val lastChange = pressed.first()
                tracker.addPosition(lastChange.uptimeMillis, lastChange.position)
            }

            if (!started) return@awaitEachGesture
            val v = tracker.calculateVelocity()
            val velocity = if (pinched) Offset.Zero else Offset(v.x, v.y)
            if (velocity.getDistance() < 50f) return@awaitEachGesture

            flinger[0] = launch {
                val anim = androidx.compose.animation.core.Animatable(Offset.Zero, Offset.VectorConverter)
                var last = Offset.Zero
                val decay = androidx.compose.animation.splineBasedDecay<Offset>(androidx.compose.ui.unit.Density(density))
                anim.animateDecay(velocity, decay) {
                    val delta = value - last
                    last = value
                    val target = state.pan + delta
                    val clamped = clamp(target, state.scale)
                    val consumed = clamped - state.pan
                    val left = delta - consumed
                    state.pan = clamped
                    if (kotlin.math.abs(left.x) > 0.5f && kotlin.math.abs(left.y) > 0.5f) {
                        throw kotlinx.coroutines.CancellationException("at the edge")
                    }
                }
            }
        }
    }
}
