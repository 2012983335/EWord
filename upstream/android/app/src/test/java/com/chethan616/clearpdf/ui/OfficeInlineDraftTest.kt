package com.chethan616.clearpdf.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.chethan616.clearpdf.core.CellSpan
import org.junit.Assert.*
import org.junit.Test

class OfficeInlineDraftTest {
    private fun draft() = OfficeInlineDraft(1uL, OfficeInlineTarget.Cell(0, CellSpan(0u, 0u, 0u, 0u)), "旧文字")

    @Test fun lateNativeSaveDoesNotDiscardNewerInput() {
        val draft = draft()
        draft.value = TextFieldValue("第一版")
        val snapshot = draft.value.text
        draft.value = TextFieldValue("第二版")
        draft.acknowledge(snapshot)
        assertTrue(draft.dirty)
        assertEquals("第二版", draft.value.text)
        draft.acknowledge("第二版")
        assertFalse(draft.dirty)
    }

    @Test fun composingChineseIsNotEligibleForIdleAutosave() {
        val draft = draft()
        draft.value = TextFieldValue("zhong", composition = TextRange(0, 5))
        assertTrue(draft.dirty)
        assertFalse(draft.canAutoSave)
        draft.value = TextFieldValue("中", selection = TextRange(1))
        assertTrue(draft.canAutoSave)
        assertEquals(TextRange(1), draft.value.selection)
    }

    @Test fun clearingContentRemainsADirtyEdit() {
        val draft = draft()
        draft.value = TextFieldValue("")
        assertTrue(draft.dirty)
        draft.acknowledge("")
        assertFalse(draft.dirty)
    }

    @Test fun lateImeCallbackCannotWriteIntoAnotherCell() {
        val old = draft()
        val active = OfficeInlineDraft(1uL, OfficeInlineTarget.Cell(1, CellSpan(3u, 2u, 3u, 2u)), "另一个单元格")
        assertFalse(old.acceptInput(TextFieldValue("迟到的输入"), active))
        assertEquals("另一个单元格", active.value.text)
        assertTrue(active.acceptInput(TextFieldValue("新内容"), active))
        assertEquals("新内容", active.value.text)
    }
}
