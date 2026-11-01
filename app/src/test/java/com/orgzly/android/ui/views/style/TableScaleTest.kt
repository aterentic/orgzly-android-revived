package com.orgzly.android.ui.views.style

import org.junit.Assert.assertEquals
import org.junit.Test

class TableScaleTest {

    @Test
    fun testTableThatFitsKeepsItsSize() {
        assertEquals(1f, tableScale(100f, 200f))
    }

    @Test
    fun testWideTableShrinksToFit() {
        assertEquals(0.75f, tableScale(200f, 150f))
    }

    @Test
    fun testVeryWideTableStopsAtMinimum() {
        assertEquals(MIN_TABLE_SCALE, tableScale(400f, 100f))
    }

    @Test
    fun testUnknownWidthKeepsSize() {
        assertEquals(1f, tableScale(100f, 0f))
    }
}
