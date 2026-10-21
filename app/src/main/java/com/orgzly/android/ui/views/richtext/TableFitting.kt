package com.orgzly.android.ui.views.richtext

import android.text.Layout
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.widget.TextView
import com.orgzly.android.ui.views.style.MIN_TABLE_SCALE
import com.orgzly.android.ui.views.style.TableRowClipSpan
import com.orgzly.android.ui.views.style.TableScaleSpan
import com.orgzly.android.ui.views.style.TableSpan
import com.orgzly.android.ui.views.style.removeTableFitting
import com.orgzly.android.ui.views.style.tableScale

/**
 * Scales each table down so its widest row fits the view's width, and cuts off each row still too
 * wide at the minimum scale. Safe to call repeatedly.
 */
fun TextView.fitTables() {
    val spannable = text as? Spannable ?: return
    val available = width - totalPaddingLeft - totalPaddingRight

    if (available <= 0) {
        return
    }

    removeTableFitting(spannable)

    spannable.getSpans(0, spannable.length, TableSpan::class.java).forEach { table ->
        val start = spannable.getSpanStart(table)
        val end = spannable.getSpanEnd(table)

        val rows = rows(spannable, start, end)
        val scale = fittingScale(spannable, rows, available.toFloat())

        // Measured before the scale span is set, which the measurement would apply a second time.
        val cutOffRows = if (scale == MIN_TABLE_SCALE) {
            rows.zip(rowWidths(spannable, rows, scale)).filter { (_, rowWidth) -> rowWidth > available }.map { it.first }
        } else {
            emptyList()
        }

        if (scale < 1f) {
            spannable.setSpan(TableScaleSpan(scale), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        cutOffRows.forEach { (rowStart, rowEnd) ->
            spannable.setSpan(TableRowClipSpan(available), rowStart, rowEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}

private fun TextView.rowWidths(text: CharSequence, rows: List<Pair<Int, Int>>, scale: Float): List<Float> {
    val scaledPaint = TextPaint(paint).apply { textSize = paint.textSize * scale }

    return rows.map { (rowStart, rowEnd) -> Layout.getDesiredWidth(text, rowStart, rowEnd, scaledPaint) }
}

private fun TextView.fittingScale(text: CharSequence, rows: List<Pair<Int, Int>>, available: Float): Float {
    fun widestRowAt(scale: Float): Float = rowWidths(text, rows, scale).max()

    var scale = tableScale(widestRowAt(1f), available)

    // Glyph widths are rounded at each text size, so a row can measure wider than its scale implies.
    while (scale > MIN_TABLE_SCALE) {
        val widest = widestRowAt(scale)

        if (widest <= available) {
            break
        }

        scale = maxOf(MIN_TABLE_SCALE, minOf(scale - 0.01f, scale * available / widest))
    }

    return scale
}

private fun rows(text: CharSequence, start: Int, end: Int): List<Pair<Int, Int>> {
    val rows = mutableListOf<Pair<Int, Int>>()
    var rowStart = start

    for (i in start until end) {
        if (text[i] == '\n') {
            rows.add(rowStart to i)
            rowStart = i + 1
        }
    }

    if (rowStart < end) {
        rows.add(rowStart to end)
    }

    return rows
}
