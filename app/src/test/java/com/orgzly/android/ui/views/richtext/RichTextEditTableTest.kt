package com.orgzly.android.ui.views.richtext

import android.text.Spanned
import androidx.test.core.app.ApplicationProvider
import com.orgzly.android.ui.views.style.TableSpan
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RichTextEditTableTest {

    private val edit = RichTextEdit(ApplicationProvider.getApplicationContext())

    private fun tables(): List<String> =
        (edit.text as Spanned).let { text ->
            text.getSpans(0, text.length, TableSpan::class.java)
                .sortedBy { text.getSpanStart(it) }
                .map { text.subSequence(text.getSpanStart(it), text.getSpanEnd(it)).toString() }
        }

    @Test
    fun testMarksTablesWhenEditingStarts() {
        edit.setText("a\n| x |")

        edit.activate(0)

        assertEquals(listOf("| x |"), tables())
    }

    @Test
    fun testMarksTablesAsTheUserTypes() {
        edit.setText("a\n")
        edit.activate(2)

        edit.text!!.append("| x |")

        assertEquals(listOf("| x |"), tables())
    }

    @Test
    fun testStopsMarkingWhenEditingEnds() {
        edit.setText("a\n")
        edit.activate(2)
        edit.deactivate()

        edit.text!!.append("| x |")

        assertEquals(emptyList<String>(), tables())
    }

    @Test
    fun testRefusesDeletingTheNewlineThatEndsATable() {
        edit.setText("| x |\nb")
        edit.activate(7)

        edit.text!!.delete(5, 6)

        assertEquals("| x |\nb", edit.text.toString())
    }

    @Test
    fun testRefusesTypingInsideATable() {
        edit.setText("| x |\n| y |\n")
        edit.activate(0)

        edit.text!!.insert(2, "z")
        edit.text!!.insert(6, "z")

        assertEquals("| x |\n| y |\n", edit.text.toString())
    }

    @Test
    fun testAcceptsReplacingAWholeTableAndTypingAroundIt() {
        edit.setText("a\n| x |\nb")
        edit.activate(0)

        edit.text!!.replace(2, 8, "| y |\n")
        edit.text!!.insert(2, "c\n")
        edit.text!!.append("d")

        assertEquals("a\nc\n| y |\nbd", edit.text.toString())
    }

    @Test
    fun testMovesACursorInsideATableToItsEnd() {
        edit.setText("a\n| x |\n| y |\nb")
        edit.activate(0)

        edit.setSelection(4)
        assertEquals(14, edit.selectionStart)

        edit.setSelection(2)
        assertEquals(2, edit.selectionStart)
    }

    @Test
    fun testRefusesTypingOnTheLastRowOfATableEndingTheText() {
        edit.setText("a\n| x |")
        edit.activate(0)

        edit.text!!.append("y")
        edit.text!!.append("\nz")

        assertEquals("a\n| x |\nz", edit.text.toString())
    }

    @Test
    fun testCursorMovingBackOverATableLandsOnItsStart() {
        edit.setText("a\n| x |\n| y |\nb")
        edit.activate(0)

        edit.setSelection(14)
        edit.setSelection(13)
        assertEquals(2, edit.selectionStart)

        edit.setSelection(1)
        edit.setSelection(3)
        assertEquals(14, edit.selectionStart)
    }
}
