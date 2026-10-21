package com.orgzly.android.ui.views

import android.graphics.Typeface
import android.text.Spannable
import android.text.Spanned
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.orgzly.android.ui.views.richtext.RichTextEdit
import com.orgzly.android.ui.views.richtext.RichTextView
import com.orgzly.android.ui.views.richtext.fitTables
import com.orgzly.android.ui.views.style.MIN_TABLE_SCALE
import com.orgzly.android.ui.views.style.TableRowClipSpan
import com.orgzly.android.ui.views.style.TableScaleSpan
import com.orgzly.android.ui.views.style.markTables
import org.junit.Assert.assertEquals
import org.junit.Test

class RichTextEditTableFittingTest {

    private val width = 600

    private fun layOut(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun lineHeights(view: TextView): List<Int> =
        (0 until view.layout.lineCount).map { view.layout.getLineBottom(it) - view.layout.getLineTop(it) }

    @Test
    fun testShrunkRowsAreAsTallAsInViewMode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val text = "Intro\n| a | " + "x".repeat(200) + " |\n| b | c |\nOutro"

        instrumentation.runOnMainSync {
            val view = RichTextView(instrumentation.targetContext).apply {
                layoutParams = ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)
                setText(text, TextView.BufferType.SPANNABLE)
                markTables(this.text as Spannable)
            }
            layOut(view)
            view.fitTables()
            layOut(view)

            val edit = RichTextEdit(instrumentation.targetContext).apply {
                layoutParams = ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, view.textSize)
                setText(text)
            }
            layOut(edit)
            edit.activate(-1)
            layOut(edit)

            assertEquals(lineHeights(view), lineHeights(edit))
        }
    }

    @Test
    fun testTypingATooWideTableShrinksItAndCutsOffItsRow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            val edit = RichTextEdit(instrumentation.targetContext).apply {
                layoutParams = ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)
                setText("Intro\n")
            }

            layOut(edit)

            // Outside the text, so the keyboard stays closed.
            edit.activate(-1)

            val charWidth = edit.paint.apply { typeface = Typeface.MONOSPACE }.measureText("x").also {
                edit.paint.typeface = Typeface.DEFAULT
            }

            edit.text!!.append("| " + "x".repeat((3 * width / charWidth).toInt()) + " |")

            val text = edit.text as Spanned
            assertEquals(listOf(MIN_TABLE_SCALE), text.getSpans(0, text.length, TableScaleSpan::class.java).map { it.sizeChange })
            assertEquals(1, text.getSpans(0, text.length, TableRowClipSpan::class.java).size)
        }
    }
}
