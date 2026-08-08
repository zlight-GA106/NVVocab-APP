package com.zlight106.nvvocab.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPromptTest {
    @Test
    fun defaultPromptRequiresRawQuizXmlWithoutPassword() {
        assertTrue(DEFAULT_AI_PROMPT.contains("<quiz>"))
        assertTrue(DEFAULT_AI_PROMPT.contains("<question score=\"10\">"))
        assertTrue(DEFAULT_AI_PROMPT.contains("不要生成 password 字段"))
        assertFalse(DEFAULT_AI_PROMPT.contains("```xml"))
    }
}
