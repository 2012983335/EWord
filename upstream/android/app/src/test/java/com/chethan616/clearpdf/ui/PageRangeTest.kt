package com.chethan616.clearpdf.ui

import org.junit.Assert.*
import org.junit.Test

class PageRangeTest {
    @Test fun blankMeansAllAndOverlapsAreDeduplicatedInDocumentOrder() {
        assertEquals(listOf(1, 2, 3), pageRange("", 3))
        assertEquals(listOf(1, 2, 3, 5), pageRange("5, 1-3，2", 5))
    }
    @Test fun invalidRangesAreRejectedBeforeProcessing() {
        listOf("0", "4", "3-1", "1-2-3", "1,", "text").forEach { range ->
            assertTrue(range, runCatching { pageRange(range, 3) }.isFailure)
        }
    }
}
