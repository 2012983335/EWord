package com.chethan616.clearpdf.ui

import com.chethan616.clearpdf.engine.RecentFile
import com.chethan616.clearpdf.engine.RecentFiles
import com.chethan616.clearpdf.engine.RecentOrder
import com.chethan616.clearpdf.engine.arranged
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class RecentFilesTest {
    @Test fun renamePreservesLockProgressAndOrderWithoutStaleRecord() {
        val store = store()
        val original = entry("old.docx").copy(locked = true, pinned = true, lastPage = 4, lastZoom = 2f, position = 3)
        store.touch(original)
        store.relocate(original.uri, entry("new.docx"))
        val moved = store.list().single()
        assertEquals("new.docx", moved.name)
        assertTrue(moved.locked && moved.pinned)
        assertEquals(4, moved.lastPage)
        assertEquals(3, moved.position)
        assertEquals(2f, moved.lastZoom)
    }
    private fun entry(name: String, opened: Long = 1) = RecentFile("file:///$name", name, opened, 1, 50)
    private fun store() = RecentFiles(File.createTempFile("eword-recents-", ".json").apply { deleteOnExit() })

    @Test fun lockSurvivesReopeningAndBlocksRemovalAndClear() {
        val store = store()
        val a = entry("a.pdf")
        store.touch(a)
        store.touch(entry("b.docx"))
        store.setFlags(setOf(a.uri), pinned = true, locked = true)
        store.touch(a.copy(opened = 20))
        store.remove(setOf(a.uri))
        assertTrue(store.list().first { it.uri == a.uri }.locked)
        assertTrue(store.list().first { it.uri == a.uri }.pinned)
        store.clear()
        assertEquals(listOf(a.uri), store.list().map { it.uri })
        store.setFlags(setOf(a.uri), locked = false)
        store.remove(setOf(a.uri))
        assertTrue(store.list().isEmpty())
    }

    @Test fun manualOrderPersistsAndPinsStayAboveSortedFiles() {
        val storage = File.createTempFile("eword-order-", ".json").apply { deleteOnExit() }
        val store = RecentFiles(storage)
        val a = entry("a.pdf", 100)
        val b = entry("b.docx", 200)
        val c = entry("c.pdf", 300)
        listOf(a, b, c).forEach(store::touch)
        store.setOrder(listOf(b.uri, c.uri, a.uri))
        assertEquals(listOf("b.docx", "c.pdf", "a.pdf"), RecentFiles(storage).list().arranged(RecentOrder.Manual).map { it.name })
        store.setFlags(setOf(a.uri), pinned = true)
        assertEquals(listOf("a.pdf", "c.pdf", "b.docx"), store.list().arranged(RecentOrder.Opened).map { it.name })
        assertEquals(listOf("a.pdf", "b.docx", "c.pdf"), store.list().arranged(RecentOrder.Name).map { it.name })
        store.touch(b.copy(opened = 500))
        assertEquals(0, store.list().first { it.uri == b.uri }.position)
    }

    @Test fun oldRecordsMigrateAndLockedFilesAreNotEvictedAtTheLimit() {
        val storage = File.createTempFile("eword-legacy-", ".json").apply { deleteOnExit() }
        storage.writeText("""[{"uri":"file:///old.pdf","name":"old.pdf","opened":1,"pages":1,"size":1}]""")
        val store = RecentFiles(storage)
        assertFalse(store.list().single().locked)
        assertNull(store.list().single().position)
        store.setFlags(setOf("file:///old.pdf"), locked = true)
        repeat(110) { store.touch(entry("$it.pdf", it.toLong())) }
        assertEquals(100, store.list().size)
        assertTrue(store.list().any { it.uri == "file:///old.pdf" && it.locked })
    }
}
