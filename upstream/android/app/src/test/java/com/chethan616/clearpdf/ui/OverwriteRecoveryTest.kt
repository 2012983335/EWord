package com.chethan616.clearpdf.ui

import com.chethan616.clearpdf.engine.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class OverwriteRecoveryTest {
    private fun scenario(failing: String?): Triple<File, File, FileVault> {
        val dir = Files.createTempDirectory("eword-overwrite-").toFile()
        val original = File(dir, "original.docx").apply { writeText("original document") }
        val edited = File(dir, "edited.docx").apply { writeText("edited document") }
        val vault = FileVault(File(dir, "vault"))
        var writes = 0
        val result = runCatching {
            vault.overwriteWithRecovery(edited, original.name, "source", original::inputStream) { copy ->
                writes++
                if (failing != null && (writes == 1 || failing == "both")) {
                    original.writeText("partial")
                    if (failing != "silent") throw IOException("provider failed")
                } else FileVault.copyDurably(copy, original)
            }
        }
        if (failing == null) assertTrue(result.isSuccess) else assertTrue(result.isFailure)
        assertEquals("original document", vault.list(VaultKind.Version).single().file.readText())
        assertEquals("edited document", edited.readText())
        return Triple(original, edited, vault)
    }
    @Test fun savingKeepsThePreviousOriginal() { assertEquals("edited document", scenario(null).first.readText()) }
    @Test fun failedWriteRestoresTheOriginal() { assertEquals("original document", scenario("throw").first.readText()) }
    @Test fun silentTruncationIsDetectedAndRestored() { assertEquals("original document", scenario("silent").first.readText()) }
    @Test fun failedRollbackStillKeepsADurableBackup() { assertEquals("original document", scenario("both").third.list(VaultKind.Version).single().file.readText()) }
}
