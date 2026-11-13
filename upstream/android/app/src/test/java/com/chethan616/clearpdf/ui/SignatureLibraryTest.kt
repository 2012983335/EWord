package com.chethan616.clearpdf.ui

import com.chethan616.clearpdf.ui.viewer.Signatures
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SignatureLibraryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun folder() = temporary.newFolder()
    @Test fun legacyAndNewSignaturesSurviveReopeningWithoutOverwriting() {
        val dir = folder()
        val legacy = File(dir, "signatures/signature.png").apply { parentFile!!.mkdirs(); writeText("legacy") }
        val store = Signatures(dir)
        val a = store.add(false) { it.writeText("first") }
        val b = store.add(false) { it.writeText("second") }
        val reopened = Signatures(dir)
        assertEquals(3, reopened.saved().size)
        assertEquals("legacy", legacy.readText())
        assertEquals("first", a.readText())
        assertEquals("second", b.readText())
        assertEquals(b, reopened.signature)
    }
    @Test fun deleteRemovesOnlyTheChosenSignatureAndDoesNotResurrectLegacy() {
        val dir = folder(); val store = Signatures(dir)
        val legacy = File(dir, "signatures/signature.png").apply { writeText("legacy") }
        val a = store.add(false) { it.writeText("keep") }
        store.remove(legacy)
        assertEquals(listOf(a), Signatures(dir).saved())
        store.remove(a)
        assertTrue(Signatures(dir).saved().isEmpty())
        assertFalse(store.signature.exists())
    }
    @Test fun cancelledWriteKeepsPreviousRecordsAndHidesPartialFiles() {
        val store = Signatures(folder())
        val before = store.add(false) { it.writeText("before") }
        runCatching { store.add(false) { it.writeText("partial"); error("disk failure") } }
        assertEquals(listOf(before), store.saved())
        assertEquals("before", before.readText())
    }
    @Test fun initialsAreSeparateAndOutsideFilesCannotBeDeleted() {
        val dir = folder(); val store = Signatures(dir)
        val sig = store.add(false) { it.writeText("signature") }
        val initials = store.add(true) { it.writeText("initials") }
        val other = File(dir, "other.png").apply { writeText("unrelated") }
        assertTrue(runCatching { store.remove(other) }.isFailure)
        assertTrue(runCatching { store.remove(initials, false) }.isFailure)
        assertEquals(listOf(sig), store.saved(false))
        assertEquals(listOf(initials), store.saved(true))
        assertTrue(other.isFile)
    }
}
