package com.orgzly.android.ui.views

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.orgzly.android.ui.views.richtext.RichTextView
import com.orgzly.android.ui.views.style.MIN_TABLE_SCALE
import com.orgzly.android.ui.views.style.TableOpenSpan
import com.orgzly.android.ui.views.style.TableRowClipSpan
import com.orgzly.android.ui.views.style.TableScaleSpan
import com.orgzly.android.ui.views.style.TableSpan
import com.orgzly.android.ui.views.style.removeTableFitting
import com.orgzly.android.util.OrgFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextViewTableTest {

    private val width = 600

    private fun onView(content: (charWidth: Float) -> String, check: (RichTextView) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            val view = RichTextView(instrumentation.targetContext).apply {
                layoutParams = ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            val charWidth = view.paint.apply { typeface = Typeface.MONOSPACE }.measureText("x").also {
                view.paint.typeface = Typeface.DEFAULT
            }

            view.setText(OrgFormatter.parse(content(charWidth)), TextView.BufferType.SPANNABLE)
            layOut(view)

            view.fitTables()
            layOut(view)

            check(view)
        }
    }

    private fun layOut(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    /** A table row of about [fraction] of the view's width. */
    private fun row(charWidth: Float, fraction: Float): String {
        val cells = ((width * fraction / charWidth).toInt() - 4).coerceAtLeast(1)
        return "| " + "x".repeat(cells) + " |"
    }

    private fun RichTextView.scales(): List<Float> =
        (text as Spanned).let { spanned ->
            spanned.getSpans(0, spanned.length, TableScaleSpan::class.java).map { it.sizeChange }
        }

    private fun RichTextView.lineCount(row: String): Int {
        val start = text.indexOf(row)
        return layout.getLineForOffset(start + row.length - 1) - layout.getLineForOffset(start) + 1
    }

    @Test
    fun testNarrowTableKeepsItsSize() {
        onView({ "Intro\n" + row(it, 0.5f) + "\nOutro" }) { view ->
            assertEquals(emptyList<Float>(), view.scales())
        }
    }

    @Test
    fun testWideTableShrinksOntoOneLinePerRow() {
        lateinit var rows: List<String>

        onView({ charWidth ->
            rows = listOf(row(charWidth, 1.3f), row(charWidth, 0.4f))
            "Intro text\n" + rows.joinToString("\n") + "\nOutro text"
        }) { view ->
            val scale = view.scales().single()
            assertTrue("scale $scale", scale >= MIN_TABLE_SCALE && scale < 1f)

            rows.forEach { assertEquals(it, 1, view.lineCount(it)) }

            val spanned = view.text as Spanned
            val span = spanned.getSpans(0, spanned.length, TableScaleSpan::class.java).single()
            assertEquals("Intro text\n".length, spanned.getSpanStart(span))
            assertEquals(spanned.length - "Outro text".length, spanned.getSpanEnd(span))
        }
    }

    @Test
    fun testShrunkTableHasEvenRows() {
        lateinit var rows: List<String>

        onView({ charWidth ->
            rows = listOf(row(charWidth, 1.3f), row(charWidth, 0.4f), row(charWidth, 0.3f))
            "Intro text\n" + rows.joinToString("\n") + "\nOutro text"
        }) { view ->
            val heights = rows.map { row ->
                val line = view.layout.getLineForOffset(view.text.indexOf(row))
                view.layout.getLineBottom(line) - view.layout.getLineTop(line)
            }

            assertEquals(heights.toString(), 1, heights.distinct().size)
        }
    }

    @Test
    fun testSlightlyWideTableShrinksOntoOneLinePerRow() {
        listOf(1.01f, 1.02f, 1.05f, 1.1f).forEach { fraction ->
            lateinit var row: String

            onView({ charWidth ->
                row = row(charWidth, fraction)
                row + "\n" + row
            }) { view ->
                assertEquals("fraction $fraction, scale ${view.scales()}", 1, view.lineCount(row))
            }
        }
    }

    private inline fun <reified T> RichTextView.spansOn(row: String): List<T> =
        (text as Spanned).let { spanned ->
            val start = text.indexOf(row)
            spanned.getSpans(start, start + row.length, T::class.java)
                .filter { spanned.getSpanStart(it) == start && spanned.getSpanEnd(it) == start + row.length }
        }

    @Test
    fun testVeryWideTableStopsAtMinimumAndCutsOffTooWideRows() {
        lateinit var wide: String
        lateinit var narrow: String

        onView({ charWidth ->
            wide = row(charWidth, 3f)
            narrow = row(charWidth, 0.5f)
            "$wide\n$narrow"
        }) { view ->
            assertEquals(listOf(MIN_TABLE_SCALE), view.scales())

            assertEquals(1, view.lineCount(wide))
            assertEquals(1, view.spansOn<TableRowClipSpan>(wide).size)
            assertEquals(1, view.spansOn<TableOpenSpan>(wide).size)

            assertEquals(0, view.spansOn<TableRowClipSpan>(narrow).size)
            assertEquals(0, view.spansOn<TableOpenSpan>(narrow).size)
        }
    }

    @Test
    fun testTableTwiceTheWidthCutsOffItsRows() {
        lateinit var wide: String

        onView({ charWidth ->
            wide = row(charWidth, 2f)
            wide
        }) { view ->
            assertEquals(listOf(MIN_TABLE_SCALE), view.scales())
            assertEquals(1, view.lineCount(wide))
            assertEquals(1, view.spansOn<TableRowClipSpan>(wide).size)
        }
    }

    @Test
    fun testCutOffRowWithMarkupStaysOnOneLineWithinTheView() {
        lateinit var wide: String

        onView({ charWidth ->
            wide = "| *b* =v= " + row(charWidth, 3f).drop(2)
            wide
        }) { view ->
            val line = view.layout.getLineForOffset(view.text.indexOf(wide))

            assertEquals(1, view.lineCount(wide))
            assertTrue(view.layout.getLineWidth(line) <= width - view.totalPaddingLeft - view.totalPaddingRight)
        }
    }

    @Test
    fun testRemovingTheFittingKeepsOnlyTheTable() {
        onView({ row(it, 3f) }) { view ->
            val spanned = view.text as Spanned
            val copy = SpannableString(spanned)

            removeTableFitting(copy)

            assertEquals(0, copy.getSpans(0, copy.length, TableScaleSpan::class.java).size)
            assertEquals(0, copy.getSpans(0, copy.length, TableRowClipSpan::class.java).size)
            assertEquals(0, copy.getSpans(0, copy.length, TableOpenSpan::class.java).size)
            assertEquals(1, copy.getSpans(0, copy.length, TableSpan::class.java).size)
        }
    }

    @Test
    fun testFittingAgainKeepsOneScalePerTable() {
        onView({ row(it, 1.3f) + "\ntext\n" + row(it, 1.5f) }) { view ->
            view.fitTables()
            assertEquals(2, view.scales().size)
        }
    }
}
