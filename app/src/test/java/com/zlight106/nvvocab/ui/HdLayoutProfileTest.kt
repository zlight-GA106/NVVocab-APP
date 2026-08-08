package com.zlight106.nvvocab.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HdLayoutProfileTest {
    @Test
    fun detectsCommonLandscapeTabletRatios() {
        assertTrue(isHdLandscape(widthDp = 960f, heightDp = 540f))
        assertTrue(isHdLandscape(widthDp = 960f, heightDp = 600f))
    }

    @Test
    fun rejectsPhonesPortraitAndUltraWideWindows() {
        assertFalse(isHdLandscape(widthDp = 720f, heightDp = 405f))
        assertFalse(isHdLandscape(widthDp = 600f, heightDp = 960f))
        assertFalse(isHdLandscape(widthDp = 1_200f, heightDp = 500f))
    }
}
