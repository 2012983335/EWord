package com.chethan616.clearpdf.ui

import com.chethan616.clearpdf.engine.*
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class FileVaultTest {
    @Test fun renameKeepsDraftAndVersionAvailableWithoutMovingTrash() {
        val folder = Files.createTempDirectory("eword-rename-").toFile()
        val source = File(folder, "source.docx").apply { writeText("contents") }
        val vault = FileVault(File(folder, "vault"))
        VaultKind.entries.forEach { vault.capture(source, source.name, "old", it) }
        vault.relocateSource("old", "new", "renamed.docx")
        assertEquals(2, vault.list(source = "new").size)
        assertTrue(vault.list(source = "new").all { it.name == "renamed.docx" && it.file.readText() == "contents" })
        assertEquals(VaultKind.Trash, vault.list(source = "old").single().kind)
    }
    @Test fun completeCopiesSurviveReloadAndDraftReplacement() {
        val folder = Files.createTempDirectory("eword-vault-").toFile()
        val source = File(folder, "source.docx").apply { writeText("original") }
        val vault = FileVault(File(folder, "vault"))
        val backup = vault.capture(source, source.name, "source", VaultKind.Trash)
        source.writeText("modified")
        assertEquals("original", backup.file.readText())
        assertEquals(backup.id, FileVault(File(folder, "vault")).list().single().id)
        vault.capture(source, source.name, "source", VaultKind.Draft)
        source.writeText("newest")
        vault.capture(source, source.name, "source", VaultKind.Draft)
        assertEquals("newest", vault.list(VaultKind.Draft).single().file.readText())
        assertEquals(1, vault.list(VaultKind.Trash).size)
    }

    @Test fun versionsAreBoundedAndIncompleteStagingIsInvisible() {
        val folder = Files.createTempDirectory("eword-vault-").toFile()
        val root = File(folder, "vault")
        val source = File(folder, "source.pdf").apply { writeText("one") }
        val vault = FileVault(root)
        repeat(8) { source.writeText("version-$it"); vault.capture(source, source.name, "source", VaultKind.Version) }
        assertEquals(5, vault.list(VaultKind.Version).size)
        File(root, ".incomplete.part").mkdirs()
        assertEquals(5, vault.list().size)
        val latest = vault.list().first()
        val recovered = File(folder, "restored.pdf")
        FileVault.copyDurably(latest.file, recovered)
        assertEquals(latest.file.readText(), recovered.readText())
        assertEquals("version-7", source.readText())
    }
}
