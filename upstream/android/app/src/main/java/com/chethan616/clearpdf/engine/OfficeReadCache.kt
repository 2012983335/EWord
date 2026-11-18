package com.chethan616.clearpdf.engine

import java.io.File
import java.security.MessageDigest

/** Immutable, content-addressed PDFs. Editing always uses a separate working copy. */
class OfficeReadCache(private val root: File) {
    data class Prepared(val source: File, val pdf: File)

    fun prepare(source: File): Prepared {
        val hash = MessageDigest.getInstance("SHA-256")
        source.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
        }
        val key = hash.digest().joinToString("") { "%02x".format(it) }
        val folder = File(root, "lo31fe13-pdf1/$key").apply { check(mkdirs() || isDirectory) }
        val pdf = File(folder, source.nameWithoutExtension.take(60) + ".pdf")
        val copy = File(folder, "source.${source.extension}")
        if (!valid(pdf)) {
            pdf.delete()
            val pending = File(folder, "source.pending")
            try {
                source.inputStream().use { input -> pending.outputStream().use { output -> input.copyTo(output); output.fd.sync() } }
                val copiedHash = MessageDigest.getInstance("SHA-256")
                pending.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; copiedHash.update(buffer, 0, n) } }
                check(copiedHash.digest().joinToString("") { "%02x".format(it) } == key) { "讲义正在修改，请稍后重新打开" }
                java.nio.file.Files.move(pending.toPath(), copy.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            } finally { pending.delete() }
        }
        return Prepared(copy, pdf)
    }

    companion object {
        fun valid(file: File): Boolean = runCatching {
            if (!file.isFile || file.length() < 16) return false
            java.io.RandomAccessFile(file, "r").use { input ->
                val header = ByteArray(5); input.readFully(header)
                if (!header.contentEquals("%PDF-".toByteArray())) return false
                input.seek((input.length() - 1024).coerceAtLeast(0))
                val tail = ByteArray((input.length() - input.filePointer).toInt()); input.readFully(tail)
                String(tail, Charsets.ISO_8859_1).contains("%%EOF")
            }
        }.getOrDefault(false)
    }
}
