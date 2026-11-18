package com.chethan616.clearpdf.ui

import com.chethan616.clearpdf.engine.OfficeReadCache
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfficeReadCacheTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun cachedPreviewIsReusedWithoutChangingOriginal() {
        val source = folder.newFile("lecture.ppt").apply { writeText("original slides") }
        val cache = OfficeReadCache(folder.newFolder("cache"))
        val first = cache.prepare(source)
        first.pdf.writeText("%PDF-1.7\npreview data\n%%EOF\n")
        assertEquals(first, cache.prepare(source))
        assertEquals("original slides", source.readText())
        assertEquals(source.readText(), first.source.readText())
    }

    @Test fun changedSourceGetsNewCache() {
        val source = folder.newFile("lecture.ppt").apply { writeText("first") }
        val cache = OfficeReadCache(folder.newFolder("cache"))
        val first = cache.prepare(source)
        source.writeText("second")
        val second = cache.prepare(source)
        assertNotEquals(first.pdf, second.pdf)
        assertEquals("second", second.source.readText())
    }

    @Test fun interruptedExportIsNeverAccepted() {
        val source = folder.newFile("lecture.ppt").apply { writeText("slides") }
        val cache = OfficeReadCache(folder.newFolder("cache"))
        val first = cache.prepare(source)
        first.pdf.writeText("%PDF-1.7\ntruncated export")
        assertFalse(OfficeReadCache.valid(first.pdf))
        cache.prepare(source)
        assertFalse(first.pdf.exists())
        assertEquals("slides", source.readText())
    }
}
