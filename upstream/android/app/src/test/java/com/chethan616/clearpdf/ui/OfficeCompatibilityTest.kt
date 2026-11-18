package com.chethan616.clearpdf.ui

import com.chethan616.clearpdf.engine.OfficeCompatibility
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfficeCompatibilityTest {
    @get:Rule val folder = TemporaryFolder()
    private fun file(name: String, data: ByteArray) = folder.newFile(name).apply { writeBytes(data) }
    private val compound = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())

    @Test fun legacySlidesUseFullReaderButRenamedPdfDoesNot() {
        for (name in listOf("课件.PPT", "show.pps", "template.pot")) assertTrue(OfficeCompatibility.needsReader(file(name, compound)))
        assertFalse(OfficeCompatibility.needsReader(file("renamed.ppt", "%PDF-1.7".toByteArray())))
    }

    @Test fun modernOfficeKeepsItsExistingEditor() {
        for (name in listOf("slides.pptx", "sheet.xlsx", "notice.docx")) assertFalse(OfficeCompatibility.needsReader(file(name, "PK\u0003\u0004rest".toByteArray())))
    }

    @Test fun openDocumentPresentationsAndTruncatedInputs() {
        assertTrue(OfficeCompatibility.needsReader(file("deck.odp", "PK\u0003\u0004rest".toByteArray())))
        assertFalse(OfficeCompatibility.needsReader(file("broken.ppt", compound.take(3).toByteArray())))
        assertFalse(OfficeCompatibility.needsReader(file("broken.odp", "bad".toByteArray())))
    }
}
