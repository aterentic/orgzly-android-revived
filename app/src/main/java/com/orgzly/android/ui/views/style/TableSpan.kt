package com.orgzly.android.ui.views.style

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.os.Build
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ReplacementSpan
import android.text.style.TypefaceSpan
import android.view.View
import com.orgzly.android.ui.views.richtext.RichTextView
import java.util.regex.Pattern
import kotlin.math.ceil

class TableSpan : TypefaceSpan("monospace")

// The newline ending the last row belongs to the table, because it sets that line's height.
private val TABLE_PATTERN = Pattern.compile("""^[ \t]*\|.*(?:\n[ \t]*\|.*)*\n?""", Pattern.MULTILINE)

/** Marks each run of org table lines in [text] with a [TableSpan], replacing the ones it had. */
fun markTables(text: Spannable) {
    text.getSpans(0, text.length, TableSpan::class.java).forEach { text.removeSpan(it) }

    val m = TABLE_PATTERN.matcher(text)

    while (m.find()) {
        text.setSpan(TableSpan(), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

class TableScaleSpan(scale: Float) : RelativeSizeSpan(scale)

const val MIN_TABLE_SCALE = 0.6f

/** The scale at which a table whose widest row is [widestRow] fits [available], never below [MIN_TABLE_SCALE]. */
fun tableScale(widestRow: Float, available: Float): Float =
    if (available <= 0 || widestRow <= available) 1f else maxOf(MIN_TABLE_SCALE, available / widestRow)

/**
 * Draws a table row on one line, cut off at [maxWidth] with a fade, because a wrapped row breaks the
 * table's columns.
 */
class TableRowClipSpan(private val maxWidth: Int) : ReplacementSpan() {

    private val fadePaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val row = row(text, start, end)

        if (row == null) {
            fm?.let { paint.getFontMetricsInt(it) }
            return 0
        }

        val layout = rowLayout(row, paint)

        // The row's own layout, so the line is as tall as an uncut row, fallback fonts included.
        fm?.apply {
            top = -layout.getLineBaseline(0)
            ascent = top
            bottom = layout.getLineBottom(0) - layout.getLineBaseline(0)
            descent = bottom
        }

        return minOf(ceil(layout.getLineWidth(0)).toInt(), maxWidth)
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val row = row(text, start, end) ?: return
        val layout = rowLayout(row, paint)
        val right = x + maxWidth
        val layer = canvas.saveLayer(x, top.toFloat(), right, bottom.toFloat(), null)

        canvas.save()
        canvas.translate(x, (y - layout.getLineBaseline(0)).toFloat())
        layout.draw(canvas)
        canvas.restore()

        if (layout.getLineWidth(0) > maxWidth) {
            val fadeStart = right - minOf(layout.paint.textSize * 3, maxWidth / 4f)
            fadePaint.shader = LinearGradient(fadeStart, 0f, right, 0f, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
            canvas.drawRect(fadeStart, top.toFloat(), right, bottom.toFloat(), fadePaint)
        }

        canvas.restoreToCount(layer)
    }

    /**
     * The whole row, or null for every piece after the first: a span inside the row splits it into
     * pieces that are measured and drawn one at a time, so the first one stands for the whole row.
     */
    private fun row(text: CharSequence, start: Int, end: Int): Spannable? {
        val (rowStart, rowEnd) = (text as? Spanned)
            ?.let { it.getSpanStart(this) to it.getSpanEnd(this) }
            ?.takeIf { (rowStart, _) -> rowStart >= 0 }
            ?: (start to end)

        if (start != rowStart) {
            return null
        }

        return SpannableString(text.subSequence(rowStart, rowEnd)).also(::removeTableFitting)
    }

    private fun rowLayout(row: CharSequence, paint: Paint): StaticLayout {
        val rowPaint = if (paint is TextPaint) TextPaint().apply { set(paint) } else TextPaint(paint)
        val width = ceil(Layout.getDesiredWidth(row, rowPaint)).toInt() + 1

        return StaticLayout.Builder.obtain(row, 0, row.length, rowPaint, width)
            .setIncludePad(false)
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setUseLineSpacingFromFallbacks(true) }
            .build()
    }
}

/** Opens [table] in full when one of its cut-off rows is tapped. */
class TableOpenSpan(private val table: TableSpan) : ClickableSpan() {
    override fun onClick(widget: View) {
        (widget as? RichTextView)?.openTable(table)
    }

    override fun updateDrawState(ds: TextPaint) {
    }
}

/** Removes the spans that fit tables to a view's width, leaving each table at its normal size. */
fun removeTableFitting(text: Spannable) {
    listOf(TableScaleSpan::class.java, TableRowClipSpan::class.java, TableOpenSpan::class.java).forEach { kind ->
        text.getSpans(0, text.length, kind).forEach { text.removeSpan(it) }
    }
}
