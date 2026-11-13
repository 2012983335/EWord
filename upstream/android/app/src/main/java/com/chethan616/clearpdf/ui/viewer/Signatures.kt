package com.chethan616.clearpdf.ui.viewer

import java.io.File
import java.util.Locale

/** Existing single-file signatures remain available alongside the new, separate records. */
class Signatures(dir: File) {
    private val root = File(dir, "signatures").apply { mkdirs() }
    private fun legacy(initials: Boolean) = File(root, if (initials) "initials.png" else "signature.png")
    private fun folder(initials: Boolean) = File(root, if (initials) "saved-initials" else "saved-signatures")
    val signature: File get() = fileFor(false)
    val initials: File get() = fileFor(true)

    @Synchronized fun saved(initials: Boolean = false): List<File> =
        (folder(initials).listFiles { f -> f.isFile && f.extension == "png" }.orEmpty().toList() + listOf(legacy(initials)).filter { it.isFile })
            .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.parentFile == folder(initials) }.thenByDescending { it.name })

    fun fileFor(initials: Boolean): File = saved(initials).firstOrNull() ?: legacy(initials)

    @Synchronized fun add(initials: Boolean, write: (File) -> Unit): File {
        val directory = folder(initials).apply { check(isDirectory || mkdirs()) { "无法创建签名库" } }
        val name = String.format(Locale.ROOT, "%019d-%019d", System.currentTimeMillis(), System.nanoTime())
        val target = File(directory, "$name.png")
        val pending = File(directory, "$name.pending")
        try {
            write(pending)
            check(pending.isFile && pending.length() > 0) { "签名内容为空" }
            check(!target.exists() && pending.renameTo(target)) { "无法保存签名" }
            return target
        } finally { pending.delete() }
    }

    @Synchronized fun remove(file: File, initials: Boolean = false) {
        check(saved(initials).any { it.absoluteFile.normalize() == file.absoluteFile.normalize() }) { "签名已不存在" }
        check(file.delete()) { "无法删除签名，请重试" }
    }
}
