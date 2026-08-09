package com.zlight106.nvvocab.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveUiScaleTest {
    @Test
    fun enlargesCommonLargeLandscapeScreens() {
        val sixteenTen = responsiveUiScale(widthDp = 1142f, heightDp = 686f)
        assertTrue(sixteenTen.isLargeLandscape)
        assertEquals(1.06f, sixteenTen.densityMultiplier, 0.001f)
        assertEquals(1.10f, sixteenTen.fontMultiplier, 0.001f)
    }

    @Test
    fun leavesRegularPortraitDensityUnchanged() {
        val portrait = responsiveUiScale(widthDp = 686f, heightDp = 1142f)
        assertFalse(portrait.isLargeLandscape)
        assertEquals(1f, portrait.densityMultiplier, 0.001f)
        assertEquals(1f, portrait.fontMultiplier, 0.001f)
    }
}
