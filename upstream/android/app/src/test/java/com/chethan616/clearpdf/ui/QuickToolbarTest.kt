package com.chethan616.clearpdf.ui

import com.chethan616.clearpdf.ui.viewer.QuickTool
import com.chethan616.clearpdf.ui.viewer.QuickToolbarPrefs
import com.chethan616.clearpdf.ui.viewer.toolbarSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickToolbarTest {
    @Test
    fun slotsGrowWithTheScreen() {
        assertEquals(6, toolbarSlots(411))
        assertEquals(7, toolbarSlots(460))
        assertEquals(8, toolbarSlots(800))
        // the default bar fits a phone held upright
        assertTrue(QuickTool.DEFAULT_BAR.size <= toolbarSlots(360))
    }

    @Test
    fun moreToolsKeepsSupportedToolsAndDropsLegacyEntries() {
        val bar = listOf(QuickTool.Draw, QuickTool.SaveAs)
        val grid = QuickToolbarPrefs.complete(listOf(QuickTool.FillSign, QuickTool.Scan, QuickTool.FillSign), bar)
        assertEquals(listOf(QuickTool.FillSign, QuickTool.Highlight), grid)
        assertTrue(grid.none { it in bar })
        assertEquals(grid.size, grid.toSet().size)
    }

    @Test
    fun idsAreStable() {
        assertEquals(QuickTool.entries.size, QuickTool.entries.map { it.id }.toSet().size)
        for (t in QuickTool.entries) assertEquals(t, QuickTool.of(t.id))
    }
}
