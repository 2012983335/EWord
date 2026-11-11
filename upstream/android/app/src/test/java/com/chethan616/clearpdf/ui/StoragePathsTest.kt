package com.chethan616.clearpdf.ui

import com.chethan616.clearpdf.engine.StoragePaths
import org.junit.Assert.*
import org.junit.Test

class StoragePathsTest {
    private val roots = mapOf("primary" to "/storage/emulated/0", "external_primary" to "/storage/emulated/0", "ABCD-1234" to "/storage/ABCD-1234")
    @Test fun externalDocumentPreservesTheFullFilenameAndLiteralPercent() {
        assertEquals("/storage/emulated/0/Download/通知%20稿.docx", StoragePaths.document("com.android.externalstorage.documents", "primary:Download/通知%20稿.docx", roots))
    }
    @Test fun removableStorageUsesItsKnownRoot() {
        assertEquals("/storage/ABCD-1234/文档/a.pdf", StoragePaths.document("com.android.externalstorage.documents", "ABCD-1234:文档/a.pdf", roots))
    }
    @Test fun downloadsRawIdIsAPathButNumericIdIsNot() {
        assertEquals("/storage/emulated/0/Download/a.pdf", StoragePaths.document("com.android.providers.downloads.documents", "raw:/storage/emulated/0/Download/a.pdf", roots))
        assertNull(StoragePaths.document("com.android.providers.downloads.documents", "msf:123", roots))
    }
    @Test fun mediaRelativePathIncludesTheFilename() {
        assertEquals("/storage/emulated/0/Download/sub/a.docx", StoragePaths.relative(roots["external_primary"], "Download/sub/", "a.docx"))
        assertEquals("/storage/emulated/0/a.pdf", StoragePaths.relative(roots["external_primary"], "", "a.pdf"))
    }
    @Test fun unknownVolumesAndProvidersAreNotGuessed() {
        assertNull(StoragePaths.document("chat.provider", "primary:Download/a.pdf", roots))
        assertNull(StoragePaths.document("com.android.externalstorage.documents", "unknown:a.pdf", roots))
        assertNull(StoragePaths.relative(null, "Download/", "a.pdf"))
        assertNull(StoragePaths.document("com.android.externalstorage.documents", "primary:../a.pdf", roots))
    }
    @Test fun fileManagerUsesItsDeclaredRootRatherThanTheUriRootAlias() {
        assertEquals("/storage/emulated/0/Download/通知 1.pdf", StoragePaths.externalProvider("/storage/emulated/0", ".", listOf("Download", "通知 1.pdf")))
        assertEquals("/storage/emulated/0/Documents/a.pdf", StoragePaths.externalProvider("/storage/emulated/0", "Documents/", listOf("a.pdf")))
        assertNull(StoragePaths.externalProvider("/storage/emulated/0", "../", listOf("a.pdf")))
    }
}
