package com.chethan616.clearpdf.engine

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.chethan616.clearpdf.R
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Where the app keeps the files it works on. The engine reads and writes real paths, so a
 * document opened from another app (a `content://` URI) is copied into the app's files first, and
 * saving writes the result back to that URI when the app may write there.
 */
class DocumentFiles(private val context: Context) {
    private val resolver: ContentResolver get() = context.contentResolver

    /** Delete the original address only when the user explicitly chose local-file deletion. */
    fun deleteOriginal(uri: Uri) {
        val deleted = when {
            uri.scheme == "file" -> File(requireNotNull(uri.path)).delete()
            uri.authority == MediaStore.AUTHORITY -> resolver.delete(uri, null, null) > 0
            else -> DocumentsContract.deleteDocument(resolver, uri)
        }
        require(deleted) { context.getString(R.string.msg_delete_failed) }
    }

    /** Creates a distinct destination, streams the bytes, and verifies a read-back SHA-256. */
    fun copyToDirectory(source: Uri, tree: Uri, name: String): Uri {
        keepAccess(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val target = requireNotNull(DocumentsContract.createDocument(resolver, parent, DocumentTypes.mimeOf(name), safeName(name))) { "无法在所选目录创建文件" }
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            resolver.openInputStream(source).use { input ->
                requireNotNull(input) { "无法读取原文件" }
                resolver.openOutputStream(target, "w").use { out ->
                    requireNotNull(out) { "无法写入目标文件" }
                    java.security.DigestInputStream(input, digest).copyTo(out)
                }
            }
            val expected = digest.digest()
            digest.reset()
            resolver.openInputStream(target).use { input ->
                requireNotNull(input) { "无法校验目标文件" }
                val buffer = ByteArray(64 * 1024)
                var count = input.read(buffer)
                while (count >= 0) { if (count > 0) digest.update(buffer, 0, count); count = input.read(buffer) }
            }
            check(expected.contentEquals(digest.digest())) { "复制校验失败，原文件未删除" }
            return target
        } catch (failure: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, target) }
            throw failure
        }
    }

    /** Working copies of opened documents, one folder per source. */
    private val workDir = File(context.filesDir, "Documents").apply { mkdirs() }

    /** Files handed to other apps (share, print), through the FileProvider. */
    private val shareDir = File(context.cacheDir, "share").apply { mkdirs() }

    /** Files the app creates itself (combine, create, scans) before the user saves them somewhere. */
    val createdDir = File(context.filesDir, "Created").apply { mkdirs() }

    /** The working copy of [uri]: copied in (again) unless it is a file the app already owns. */
    fun workingCopy(uri: Uri): File {
        val name = displayName(uri)
        val dir = File(workDir, sha(uri.toString())).apply { mkdirs() }
        val target = File(dir, name)
        val tmp = File(dir, ".$name.part")
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { context.getString(R.string.common_file_unreadable) }
            tmp.outputStream().use { input.copyTo(it) }
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        // the copy keeps the source's date, which the More sheet shows as when it was modified
        lastModified(uri)?.let { target.setLastModified(it) }
        return target
    }

    /** When [uri] was last modified (ms), when its provider says: a document's or a media file's date. */
    fun lastModified(uri: Uri): Long? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.path?.let { File(it).lastModified() }?.takeIf { it > 0 }
        fun column(name: String): Long? = runCatching {
            resolver.query(uri, arrayOf(name), null, null, null)?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
        }.getOrNull()
        return column(DocumentsContract.Document.COLUMN_LAST_MODIFIED)?.takeIf { it > 0 }
            ?: column(MediaStore.MediaColumns.DATE_MODIFIED)?.takeIf { it > 0 }?.times(1000)
    }

    /**
     * Throw away the working copy of [uri], its document closed, when the app keeps access to
     * [uri]: it is copied in again the next time it opens. A file another app lent for one visit
     * keeps its copy, which is what Recent opens once Android takes that access back.
     */
    fun dropCopyIfReachable(uri: Uri) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return
        if (resolver.persistedUriPermissions.none { it.uri == uri && it.isReadPermission }) return
        File(workDir, sha(uri.toString())).deleteRecursively()
    }

    /**
     * Remove the working copies no longer needed: those of documents that left [recent], and of
     * those in it whose original the app can read again. The [open] document's copy stays, and so
     * does one written in the last few minutes (a document being opened right now).
     */
    fun pruneCopies(recent: Collection<String>, open: String?) {
        val readable = resolver.persistedUriPermissions.filter { it.isReadPermission }.mapTo(HashSet()) { it.uri.toString() }
        val wanted = recent.filter { it !in readable }.mapTo(HashSet()) { sha(it) }
        open?.let { wanted += sha(it) }
        val settled = System.currentTimeMillis() - 10 * 60 * 1000L
        workDir.listFiles()?.forEach { dir ->
            if (dir.name !in wanted && dir.lastModified() < settled) dir.deleteRecursively()
        }
    }

    /** The working copy made earlier for [uri], if there is one (for thumbnails). */
    fun existingCopy(uri: Uri): File? =
        File(workDir, sha(uri.toString())).listFiles()?.firstOrNull { it.isFile && !it.name.startsWith(".") }

    /** The file name another app gives [uri], ending in `.pdf` (or a Word document's own extension). */
    fun displayName(uri: Uri): String {
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Document.pdf"
        return safeName(name)
    }

    /** The file name another app gives [uri], as it is (any extension). */
    fun displayNameRaw(uri: Uri): String {
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "File"
        return name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().ifEmpty { "File" }
    }

    /** The size of [uri] in bytes, when its provider says. */
    fun size(uri: Uri): Long? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.path?.let { File(it).length() }
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
        }.getOrNull()
    }

    /** Whether the app may write to [uri] (a document the user picked with write access). */
    fun canWrite(uri: Uri): Boolean {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.path?.let { File(it).canWrite() } == true
        // a provider that says its document can't be written refuses whatever access the picker
        // granted (Android's media documents, which the picker's Recent shows: "Media is read-only")
        if (DocumentsContract.isDocumentUri(context, uri) && documentFlags(uri)?.let { it and DocumentsContract.Document.FLAG_SUPPORTS_WRITE == 0 } == true) return false
        val granted = resolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
        return granted || runCatching { resolver.openFileDescriptor(uri, "wa")?.close(); true }.getOrDefault(false)
    }

    /** A document's flags (DocumentsContract.Document.FLAG_*), when its provider says. */
    private fun documentFlags(uri: Uri): Int? = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getInt(0) else null }
    }.getOrNull()

    /** Write [file] over [uri] (truncating it). */
    fun overwriteWithRecovery(file: File, uri: Uri, vault: FileVault, name: String) {
        vault.overwriteWithRecovery(file, name, uri.toString(),
            read = { requireNotNull(resolver.openInputStream(uri)) { "无法读取原件，未执行覆盖" } },
            write = { replacement ->
                if (uri.scheme == ContentResolver.SCHEME_FILE) FileVault.copyDurably(replacement, File(requireNotNull(uri.path)))
                else writeBack(replacement, uri)
            })
    }

    fun writeBack(file: File, uri: Uri) {
        val out = resolver.openOutputStream(uri, "wt") ?: error(context.getString(R.string.files_error_write))
        out.use { o -> file.inputStream().use { it.copyTo(o) } }
    }

    /** Keep access to [uri] across restarts, for Recent files (when the provider allows it). */
    fun keepAccess(uri: Uri) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return
        runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            .recoverCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    /** A copy of [file] under [name] that other apps may read, and its URI. */
    fun shareable(file: File, name: String = file.name): Uri = shareableAll(listOf(file to safeName(name))).first()

    /**
     * Copies of [files] that other apps may read (a document, pictures of pages), each under its
     * own name, and their URIs. The last ones handed out are cleared first. When the copies can't
     * be made (a full phone has no room for them), the error says so in words.
     */
    fun shareableAll(files: List<Pair<File, String>>): List<Uri> {
        shareDir.listFiles()?.forEach { it.deleteRecursively() }
        val dir = File(shareDir, System.nanoTime().toString())
        try {
            if (!dir.mkdirs()) throw IOException("can't make $dir")
            return files.map { (file, name) ->
                val copy = File(dir, name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").ifBlank { file.name })
                file.copyTo(copy, overwrite = true)
                FileProvider.getUriForFile(context, "${context.packageName}.files", copy)
            }
        } catch (e: IOException) {
            dir.deleteRecursively()
            val full = shareDir.usableSpace < files.sumOf { it.first.length() } + 1024 * 1024
            throw IOException(context.getString(if (full) R.string.files_error_share_no_space else R.string.files_error_share), e)
        }
    }

    /** A new file in the app's own space for a document it makes, named [name] (made unique). */
    fun newCreated(name: String): File {
        val base = safeName(name).removeSuffix(".pdf")
        var f = File(createdDir, "$base.pdf")
        var n = 1
        while (f.exists()) f = File(createdDir, "$base (${n++}).pdf")
        return f
    }

    /**
     * Save [file] into the phone's Downloads as [name] (made unique by the system if taken) and
     * return its URI; no storage permission is needed for the app's own downloads.
     */
    fun saveToDownloads(file: File, name: String, mime: String = "application/pdf"): Uri {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: error(context.getString(R.string.files_error_save_downloads))
        try {
            resolver.openOutputStream(uri).use { out ->
                requireNotNull(out) { context.getString(R.string.files_error_save_downloads) }
                file.inputStream().use { it.copyTo(out) }
            }
            resolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /** Save an image into Pictures/ClearPDF (exported pages). */
    fun saveToPictures(file: File, name: String, mime: String): Uri {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/EWord")
        }
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error(context.getString(R.string.files_error_save_image))
        resolver.openOutputStream(uri).use { out ->
            requireNotNull(out) { context.getString(R.string.files_error_save_image) }
            file.inputStream().use { it.copyTo(out) }
        }
        return uri
    }

    /** Where [uri] is, as a person would say it: a folder path, or the app that provided it. */
    fun displayLocation(uri: Uri): String = when (uri.scheme) {
        ContentResolver.SCHEME_FILE -> uri.path?.substringBeforeLast('/')?.replace("/storage/emulated/0", context.getString(R.string.files_location_internal_storage)) ?: context.getString(R.string.files_location_this_device)
        else -> {
            val auth = uri.authority.orEmpty()
            when {
                auth.contains("downloads") -> context.getString(R.string.files_location_downloads)
                auth.contains("externalstorage") -> context.getString(R.string.files_location_internal_storage) + (Uri.decode(uri.lastPathSegment ?: "").substringAfter(':', "").substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "/$it" })
                auth.contains("media") -> context.getString(R.string.files_location_media)
                else -> context.packageManager.resolveContentProvider(auth, 0)?.loadLabel(context.packageManager)?.toString() ?: context.getString(R.string.files_location_another_app)
            }
        }
    }

    /** A real path from an allowed provider field or a known storage-volume document ID. */
    fun actualPath(uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.path
        fun column(name: String, target: Uri = uri): String? = runCatching {
            resolver.query(target, arrayOf(name), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getColumnIndex(name).takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString) else null
            }
        }.getOrNull()
        column("_data")?.takeIf { it.startsWith('/') }?.let { return it }
        @Suppress("DEPRECATION")
        val primary = android.os.Environment.getExternalStorageDirectory().absolutePath
        val roots = mutableMapOf("primary" to primary, "external_primary" to primary)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            context.getSystemService(android.os.storage.StorageManager::class.java)?.storageVolumes?.forEach { volume ->
                volume.directory?.absolutePath?.let { path -> volume.uuid?.let { roots[it] = path } }
            }
        }
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
        if (id != null) StoragePaths.document(uri.authority, id, roots)?.let { return it }
        val sharedPath = runCatching {
            val provider = context.packageManager.resolveContentProvider(uri.authority.orEmpty(), android.content.pm.PackageManager.GET_META_DATA)
            provider?.loadXmlMetaData(context.packageManager, "android.support.FILE_PROVIDER_PATHS")?.use { xml ->
                var resolved: String? = null
                while (xml.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && xml.name == "external-path" &&
                        xml.getAttributeValue(null, "name") == uri.pathSegments.firstOrNull()) {
                        resolved = StoragePaths.externalProvider(primary, xml.getAttributeValue(null, "path").orEmpty(), uri.pathSegments.drop(1))
                        break
                    }
                }
                resolved
            }
        }.getOrNull()
        if (sharedPath != null) return sharedPath
        val media = if (uri.authority == MediaStore.AUTHORITY) uri else runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull()
        if (media != null) {
            column("_data", media)?.takeIf { it.startsWith('/') }?.let { return it }
            val volume = column("volume_name", media) ?: media.pathSegments.firstOrNull()
            val root = roots.entries.firstOrNull { it.key.equals(volume, ignoreCase = true) }?.value
            return StoragePaths.relative(root, column(MediaStore.MediaColumns.RELATIVE_PATH, media), column(OpenableColumns.DISPLAY_NAME, media))
        }
        return null
    }

    companion object {
        /**
         * A file name without path separators or control characters, ending in `.pdf` unless it
         * is a Word document's, which keeps its own extension (the engine reads it by it).
         */
        fun safeName(name: String): String {
            val clean = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimStart('.').ifEmpty { "Document" }
            return if (clean.substringAfterLast('.', "").lowercase() in setOf("pdf", "jpg", "jpeg", "png", "zip", "html", "htm") || DocumentTypes.isOfficeName(clean)) clean else "$clean.pdf"
        }

        private fun sha(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    }
}
