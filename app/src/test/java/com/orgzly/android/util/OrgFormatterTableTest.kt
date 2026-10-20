package com.orgzly.android.util

import android.text.Spanned
import com.orgzly.android.ui.views.style.BoldSpan
import com.orgzly.android.ui.views.style.TableSpan
import com.orgzly.android.ui.views.style.UrlLinkSpan
import com.orgzly.android.ui.views.style.VerbatimSpan
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OrgFormatterTableTest : OrgFormatterTest() {

    private fun Spanned.tables(): List<String> =
        getSpans(0, length, TableSpan::class.java)
            .sortedBy { getSpanStart(it) }
            .map { subSequence(getSpanStart(it), getSpanEnd(it)).toString() }

    @Test
    fun testTableCoversItsLinesAndTheirNewlines() {
        val parsed = OrgFormatter.parse("a\n| x | y |\n|---+---|\n  | 1 | 2 |\nb", context)

        assertThat(parsed.tables(), `is`(listOf("| x | y |\n|---+---|\n  | 1 | 2 |\n")))
    }

    @Test
    fun testTablesSeparatedByText() {
        val parsed = OrgFormatter.parse("| a |\ntext\n| b |", context)

        assertThat(parsed.tables(), `is`(listOf("| a |\n", "| b |")))
    }

    @Test
    fun testBarAfterTextIsNotATable() {
        val parsed = OrgFormatter.parse("a | b |", context)

        assertThat(parsed.tables(), `is`(emptyList()))
    }

    @Test
    fun testMarkersStayInsideTable() {
        val parsed = OrgFormatter.parse("| =v= | *b* |", context)

        assertThat(parsed.toString(), `is`("| =v= | *b* |"))

        val verbatim = parsed.getSpans(0, parsed.length, VerbatimSpan::class.java).single()
        assertThat(verbatim.characterOffset, `is`(0))
        assertThat(parsed.getSpanStart(verbatim), `is`(2))
        assertThat(parsed.getSpanEnd(verbatim), `is`(5))

        val bold = parsed.getSpans(0, parsed.length, BoldSpan::class.java).single()
        assertThat(bold.characterOffset, `is`(0))
    }

    @Test
    fun testMarkersHiddenOutsideTable() {
        val parsed = OrgFormatter.parse("=v= outside\n| =t= |", context)

        assertThat(parsed.toString(), `is`("v outside\n| =t= |"))

        val offsets = parsed.getSpans(0, parsed.length, VerbatimSpan::class.java)
            .sortedBy { parsed.getSpanStart(it) }
            .map { it.characterOffset }
        assertThat(offsets, `is`(listOf(2, 0)))
        assertThat(parsed.tables(), `is`(listOf("| =t= |")))
    }

    @Test
    fun testLinkInsideTableShowsDescription() {
        val parsed = OrgFormatter.parse("| [[https://x.org][X]] |", context)

        assertThat(parsed.toString(), `is`("| X |"))
        assertThat(parsed.tables(), `is`(listOf("| X |")))
        assertThat(parsed.getSpans(0, parsed.length, UrlLinkSpan::class.java).size, `is`(1))
    }

    @Test
    fun testTableWithoutStyling() {
        val parsed = OrgFormatter.parse("| =v= |")

        assertThat(parsed.toString(), `is`("| =v= |"))
        assertThat(parsed.tables(), `is`(listOf("| =v= |")))
    }
}
