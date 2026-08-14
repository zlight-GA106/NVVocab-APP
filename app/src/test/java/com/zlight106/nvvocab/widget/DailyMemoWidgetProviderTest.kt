package com.zlight106.nvvocab.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyMemoWidgetProviderTest {
    @Test
    fun singleTaskWidget_usesLargestReadableText() {
        assertEquals(WidgetTextScale(38f, 28f), resolveWidgetTextScale(280, 110, 1))
    }

    @Test
    fun threeTaskWidget_keepsAllRowsVisible() {
        assertEquals(WidgetTextScale(28f, 21f), resolveWidgetTextScale(280, 110, 3))
    }

    @Test
    fun compactWidget_usesSafeMinimumTextSize() {
        assertEquals(WidgetTextScale(24f, 18f), resolveWidgetTextScale(220, 90, 1))
    }
}
