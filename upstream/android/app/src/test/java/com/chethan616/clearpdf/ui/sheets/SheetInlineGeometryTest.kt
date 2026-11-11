package com.chethan616.clearpdf.ui.sheets

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.chethan616.clearpdf.core.CellSpan
import com.chethan616.clearpdf.core.SheetInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class SheetInlineGeometryTest {
    private fun layout() = SheetLayout(SheetInfo("Sheet", 2u, 2u, 1u, 1u, true, false, null),
        floatArrayOf(0f, 50f, 120f), floatArrayOf(0f, 20f, 50f), emptyList())

    @Test fun frozenCellDoesNotMoveWithPanning() {
        assertEquals(Rect(72f, 40f, 172f, 80f), layout().cellBounds(CellSpan(0u, 0u, 0u, 0u), 1f, Offset(-30f, -10f), 2f))
    }

    @Test fun regularCellFollowsZoomAndPan() {
        assertEquals(Rect(142f, 70f, 282f, 130f), layout().cellBounds(CellSpan(1u, 1u, 1u, 1u), 1f, Offset(-30f, -10f), 2f))
        assertEquals(Rect(200f, 120f, 480f, 240f), layout().cellBounds(CellSpan(1u, 1u, 1u, 1u), 2f, Offset(-108f, -20f), 2f))
    }
}
