package com.zlight106.nvvocab.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WordTextParserTest {
    @Test
    fun parsesPhoneticAndPlainLines() {
        val result = WordTextParser.parse(
            """
            abandon [əˈbændən] vt. 放弃
            ability n. 能力
            """.trimIndent(),
        )

        assertEquals(2, result.size)
        assertEquals("abandon", result[0].spelling)
        assertEquals("əˈbændən", result[0].phonetic)
        assertEquals("ability", result[1].spelling)
    }
}
