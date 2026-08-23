package com.zlight106.nvvocab.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParaphraseSeedBatchParserTest {
    @Test
    fun parsesRequiredAndOptionalFields() {
        val entries = ParaphraseSeedBatchParser.parse(
            text = "regular => almost on a daily basis | She exercises regularly. | 2025-06 CET4 听力 Q17",
            userId = "user-1",
            now = 100L,
        )

        assertEquals(1, entries.size)
        assertEquals("regular", entries.single().sourceText)
        assertEquals("almost on a daily basis", entries.single().targetText)
        assertEquals("She exercises regularly.", entries.single().contextText)
        assertEquals("2025-06 CET4 听力 Q17", entries.single().sourceReference)
        assertNull(entries.single().notes)
    }

    @Test
    fun appliesBatchDefaultSourceWhenLineOmitsSource() {
        val entries = ParaphraseSeedBatchParser.parse(
            text = "take part in => participate in\nregular => almost on a daily basis | context only",
            defaultSourceReference = "2025-06 CET4 听力",
            now = 100L,
        )

        assertEquals(2, entries.size)
        assertEquals("2025-06 CET4 听力", entries[0].sourceReference)
        assertEquals("context only", entries[1].contextText)
        assertEquals("2025-06 CET4 听力", entries[1].sourceReference)
    }

    @Test
    fun explicitSourceOverridesBatchDefault() {
        val entry = ParaphraseSeedBatchParser.parse(
            text = "regular => almost on a daily basis | | 2025-12 CET4 听力 Q8",
            defaultSourceReference = "2025-06 CET4 听力",
            now = 100L,
        ).single()

        assertNull(entry.contextText)
        assertEquals("2025-12 CET4 听力 Q8", entry.sourceReference)
    }
}
