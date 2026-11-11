package com.chethan616.clearpdf.ui

import com.chethan616.clearpdf.engine.DocumentFiles
import com.chethan616.clearpdf.engine.DocumentTypes
import org.junit.Assert.*
import org.junit.Test

class DocumentFileNamesTest {
    @Test fun extractedFilesAndPicturesKeepTheirActualFormat() {
        val formats = mapOf("报告.docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "图片.zip" to "application/zip", "表格.html" to "text/html", "签名.png" to "image/png", "扫描.JPG" to "image/jpeg")
        formats.forEach { (name, mime) ->
            assertEquals(name, DocumentFiles.safeName(name))
            assertEquals(mime, DocumentTypes.mimeOf(name))
        }
        assertEquals("新文档.pdf", DocumentFiles.safeName("新文档"))
        assertFalse(DocumentFiles.safeName("../外部/报告.docx").contains('/'))
    }
}
