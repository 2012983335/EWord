package com.chethan616.clearpdf.checks

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import java.io.File

/** A read-only synthetic hierarchy, installed only in the test APK. */
class CourseDocumentsProvider : DocumentsProvider() {
    override fun onCreate() = true
    override fun isChildDocument(parentDocumentId: String, documentId: String) = parentDocumentId == "root" ||
        (parentDocumentId.toIntOrNull() != null && documentId.toIntOrNull()?.let { it > parentDocumentId.toInt() } == true)
    override fun queryRoots(projection: Array<out String>?) = MatrixCursor(projection ?: arrayOf("root_id"))
    private fun row(id: String): Array<Any> = when (id) {
        "root" -> arrayOf(id, "Course", Document.MIME_TYPE_DIR, 0L)
        "pdf" -> arrayOf(id, "Notes.pdf", "application/pdf", 32L)
        "ppt" -> arrayOf(id, "Slides.ppt", "application/vnd.ms-powerpoint", 32L)
        "ignored" -> arrayOf(id, "archive.zip", "application/zip", 32L)
        else -> arrayOf(id, "Chapter $id", Document.MIME_TYPE_DIR, 0L)
    }
    private val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE)
    private fun cursor(ids: List<String>, projection: Array<out String>?): Cursor {
        val selected = projection ?: columns
        return MatrixCursor(selected).apply {
            for (id in ids) { val values = columns.zip(row(id)).toMap(); addRow(selected.map { values[it] }.toTypedArray()) }
        }
    }
    override fun queryDocument(documentId: String, projection: Array<out String>?) = cursor(listOf(documentId), projection)
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        cursor(if (parentDocumentId == "root") listOf("pdf", "1", "ignored") else if (parentDocumentId == "10") listOf("ppt") else listOf(((parentDocumentId.toIntOrNull() ?: 0) + 1).toString()), projection)
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        check(mode == "r")
        val file = File(requireNotNull(context).cacheDir, "synthetic-$documentId.pdf")
        file.writeText("%PDF-1.7\nsynthetic test only\n%%EOF\n")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
