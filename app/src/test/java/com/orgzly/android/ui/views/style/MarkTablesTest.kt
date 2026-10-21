package com.orgzly.android.ui.views.style

import android.text.SpannableStringBuilder
import android.text.Spanned
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MarkTablesTest {

    private fun Spanned.tables(): List<String> =
        getSpans(0, length, TableSpan::class.java)
            .sortedBy { getSpanStart(it) }
            .map { subSequence(getSpanStart(it), getSpanEnd(it)).toString() }

    @Test
    fun testMarksEachTable() {
        val text = SpannableStringBuilder("a\n| x |\nb\n| y |\n| z |")

        markTables(text)

        assertEquals(listOf("| x |\n", "| y |\n| z |"), text.tables())
    }

    @Test
    fun testRemovesSpanThatNoLongerCoversATable() {
        val text = SpannableStringBuilder("| x |\nb")
        markTables(text)

        text.delete(0, 1)
        markTables(text)

        assertEquals(emptyList<String>(), text.tables())
    }

    @Test
    fun testExtendsTableToANewRow() {
        val text = SpannableStringBuilder("| x |\n")
        markTables(text)

        text.append("| y |")
        markTables(text)

        assertEquals(listOf("| x |\n| y |"), text.tables())
    }
}
