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
}
