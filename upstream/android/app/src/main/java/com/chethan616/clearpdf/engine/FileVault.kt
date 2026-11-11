package com.chethan616.clearpdf.engine

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import org.json.JSONObject

enum class VaultKind { Trash, Version, Draft }
data class VaultEntry(val id: String, val name: String, val source: String, val kind: VaultKind, val time: Long, val file: File)

/** Private, complete copies. A record is visible only after its bytes and metadata are durable. */
class FileVault(private val root: File) {
    /** Keep a durable original, verify the overwritten bytes, and restore on write/read-back failure. */
    @Synchronized fun overwriteWithRecovery(file: File, name: String, source: String, read: () -> InputStream, write: (File) -> Unit) {
        root.mkdirs()
        val staging = File(root, ".overwrite-${UUID.randomUUID()}.part").apply { check(mkdir()) }
        val original = File(staging, "original")
        val replacement = File(staging, "replacement")
        fun verify(expected: File) {
            fun digest(input: InputStream): ByteArray = input.use {
                val hash = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(65536)
                var count = it.read(buffer)
                while (count >= 0) { if (count > 0) hash.update(buffer, 0, count); count = it.read(buffer) }
                hash.digest()
            }
            check(digest(expected.inputStream()).contentEquals(digest(read()))) { "写回文件校验失败" }
        }
        try {
            copyDurably(file, replacement)
            FileOutputStream(original).use { output -> read().use { it.copyTo(output) }; output.fd.sync() }
            val backup = capture(original, name, source, VaultKind.Version)
            try {
                write(replacement)
                verify(replacement)
            } catch (failure: Exception) {
                val restored = runCatching { verify(backup.file) }.isSuccess || runCatching { write(backup.file); verify(backup.file) }.isSuccess
                throw IOException(if (restored) "保存失败，原文件已保留或恢复，编辑草稿仍保留" else "保存和自动恢复失败；原始备份仍在历史版本中，请恢复为副本。原位置文件可能不完整。", failure)
            }
        } finally { original.delete(); replacement.delete(); staging.delete() }
    }

    @Synchronized fun list(kind: VaultKind? = null, source: String? = null): List<VaultEntry> = root.listFiles().orEmpty().mapNotNull { dir ->
        if (!dir.name.matches(Regex("[0-9a-f-]{36}"))) return@mapNotNull null
        runCatching {
            val json = JSONObject(File(dir, "entry.json").readText())
            val payload = File(dir, "document.${json.getString("ext")}")
            require(payload.isFile && payload.canonicalFile.parentFile == dir.canonicalFile)
            VaultEntry(dir.name, json.getString("name"), json.getString("source"), VaultKind.valueOf(json.getString("kind")), json.getLong("time"), payload)
        }.getOrNull()
    }.filter { (kind == null || it.kind == kind) && (source == null || it.source == source) }.sortedByDescending { it.time }

    @Synchronized fun capture(file: File, name: String, source: String, kind: VaultKind): VaultEntry {
        require(file.isFile && file.length() > 0) { "无法备份空文件或不存在的文件" }
        root.mkdirs()
        val id = UUID.randomUUID().toString()
        val staging = File(root, ".$id.part").apply { check(mkdir()) }
        val ext = name.substringAfterLast('.', "bin").lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,10}")) } ?: "bin"
        val now = maxOf(System.currentTimeMillis(), (list().firstOrNull()?.time ?: 0) + 1)
        try {
            val payload = File(staging, "document.$ext")
            copyDurably(file, payload)
            val data = JSONObject().put("name", name).put("source", source).put("kind", kind.name).put("time", now).put("ext", ext)
            FileOutputStream(File(staging, "entry.json")).use { out -> out.write(data.toString().toByteArray(Charsets.UTF_8)); out.fd.sync() }
            val target = File(root, id)
            check(staging.renameTo(target)) { "备份提交失败，原文件已保留" }
            val entry = VaultEntry(id, name, source, kind, now, File(target, payload.name))
            if (kind == VaultKind.Draft) list(kind, source).filter { it.id != id }.forEach { remove(it) }
            if (kind == VaultKind.Version) {
                var bytes = 0L
                list(kind, source).forEachIndexed { index, old ->
                    bytes += old.file.length()
                    if (index > 0 && (index >= 5 || bytes > 200L * 1024 * 1024)) remove(old)
                }
            }
            return entry
        } catch (failure: Exception) {
            staging.listFiles()?.forEach { it.delete() }
            staging.delete()
            throw failure
        }
    }

    @Synchronized fun remove(entry: VaultEntry) {
        require(entry.id.matches(Regex("[0-9a-f-]{36}")))
        val dir = File(root, entry.id).canonicalFile
        require(dir.parentFile == root.canonicalFile && dir.isDirectory)
        dir.listFiles().orEmpty().forEach { check(it.isFile && it.delete()) { "无法清理备份文件" } }
        check(dir.delete())
    }

    @Synchronized fun removeDrafts(source: String) { list(VaultKind.Draft, source).forEach { remove(it) } }

    @Synchronized fun relocateSource(oldSource: String, newSource: String, newName: String) {
        list(source = oldSource).filter { it.kind != VaultKind.Trash }.forEach { entry ->
            val metadata = File(entry.file.parentFile, "entry.json")
            val json = JSONObject(metadata.readText()).put("source", newSource).put("name", newName)
            writeTextAtomically(metadata, json.toString())
        }
    }

    companion object {
        fun writeTextAtomically(target: File, text: String) {
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, ".${target.name}.${UUID.randomUUID()}.part")
            try {
                FileOutputStream(temp).use { it.write(text.toByteArray(Charsets.UTF_8)); it.fd.sync() }
                try { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
                catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            } finally { temp.delete() }
        }
        fun copyDurably(source: File, target: File) {
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, ".${target.name}.${UUID.randomUUID()}.part")
            try {
                FileOutputStream(temp).use { out -> source.inputStream().use { it.copyTo(out) }; out.fd.sync() }
                check(temp.length() == source.length()) { "备份文件不完整" }
                try { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
                catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            } finally { temp.delete() }
        }
    }
}
