package com.orgzly.android.ui.views.richtext

import android.annotation.SuppressLint
import android.content.Context
import android.text.Layout
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ClickableSpan
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.GestureDetectorCompat
import com.orgzly.BuildConfig
import com.orgzly.R
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.SpanUtils
import com.orgzly.android.ui.util.styledAttributes
import com.orgzly.android.ui.views.style.CheckboxSpan
import com.orgzly.android.ui.views.style.DrawerMarkerSpan
import com.orgzly.android.ui.views.style.MIN_TABLE_SCALE
import com.orgzly.android.ui.views.style.Offsetting
import com.orgzly.android.ui.views.style.TableRowClipSpan
import com.orgzly.android.ui.views.style.TableScaleSpan
import com.orgzly.android.ui.views.style.TableSpan
import com.orgzly.android.ui.views.style.removeTableFitting
import com.orgzly.android.ui.views.style.tableAt
import com.orgzly.android.ui.views.style.tableScale
import com.orgzly.android.util.LogUtils

class RichTextView : AppCompatTextView, ActionableRichTextView {

    fun interface OnTapUpListener {
        fun onTapUp(x: Float, y: Float, charOffset: Int)
    }

    fun interface OnTableTapListener {
        fun onTableTap(table: TableSpan)
    }

    private data class Listeners(
        var onTapUp: OnTapUpListener? = null,
        var onTableTap: OnTableTapListener? = null,
        var onActionListener: ActionableRichTextView? = null)

    private val listeners = Listeners()

    fun setOnTapUpListener(listener: OnTapUpListener) {
        listeners.onTapUp = listener
    }

    fun setOnTableTapListener(listener: OnTableTapListener) {
        listeners.onTableTap = listener
    }

    fun setOnActionListener(listener: ActionableRichTextView) {
        listeners.onActionListener = listener
    }


    constructor(context: Context) : super(context) {
        // addTextChangedListener(EditTextWatcher())
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        // addTextChangedListener(EditTextWatcher())
        parseAttrs(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        // addTextChangedListener(EditTextWatcher())
        parseAttrs(attrs)
    }


    private var parseCheckboxes = true

    private fun parseAttrs(attrs: AttributeSet?) {
        if (attrs != null) {
            context.styledAttributes(attrs, R.styleable.RichText) { typedArray ->
                parseCheckboxes = typedArray.getBoolean(R.styleable.RichText_parse_checkboxes, true)
            }
        }
    }

    private val singleTapUpDetector = GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            return true
        }
    })

    /**
     * Added as setMovementMethod was preventing clicks outside of ClickableSpan.
     * https://stackoverflow.com/q/30452627
     */
    private fun interceptClickableSpan(event: MotionEvent): Boolean? {
        layout?.let { layout ->
            val line = layout.getLineForVertical(event.y.toInt() - totalPaddingTop)

            if (isEventOnText(event, layout, line) && !TextUtils.isEmpty(text) && text is Spanned) {
                val spanned = text as Spanned

                val charOffset = getOffsetForPosition(event.x, event.y)
                val clickableSpans = spanned.getSpans(charOffset, charOffset, ClickableSpan::class.java)

                if (clickableSpans.isNotEmpty()) {
                    return when (event.action) {
                        MotionEvent.ACTION_DOWN ->
                            true

                        MotionEvent.ACTION_UP -> {
                            clickableSpans[0].onClick(this)
                            super.onTouchEvent(event)
                        }

                        else ->
                            super.onTouchEvent(event)
                    }
                }
            }
        }

        return null
    }

    /** Hands a tap on a table row to the table listener, unless it lands on a link in a row drawn in full. */
    private fun interceptTableTap(event: MotionEvent): Boolean? {
        val listener = listeners.onTableTap ?: return null
        val spanned = text as? Spanned ?: return null
        val layout = layout ?: return null
        val line = layout.getLineForVertical(event.y.toInt() - totalPaddingTop)

        if (!isEventOnText(event, layout, line)) {
            return null
        }

        // The line's start, because a tap on a drawn row can map to the offset just past the table.
        val table = spanned.tableAt(layout.getLineStart(line)) ?: return null
        val offset = getOffsetForPosition(event.x, event.y)
        val cutOff = spanned.getSpans(offset, offset, TableRowClipSpan::class.java).isNotEmpty()

        if (!cutOff && spanned.getSpans(offset, offset, ClickableSpan::class.java).isNotEmpty()) {
            return null
        }

        if (event.action == MotionEvent.ACTION_UP) {
            listener.onTableTap(table)
        }

        return true
    }

    /**
     * Check if event's coordinates are within line's text.
     *
     * Needed as getOffsetForHorizontal will return closest character,
     * which is an issue when clicking the empty space next to the text.
     */
    private fun isEventOnText(event: MotionEvent, layout: Layout, line: Int): Boolean {
        val left = layout.getLineLeft(line) + totalPaddingLeft
        val right = layout.getLineRight(line) + totalPaddingRight
        val bottom = (layout.getLineBottom(line) + totalPaddingTop).toFloat()
        val top = (layout.getLineTop(line) + totalPaddingTop).toFloat()

        return event.x in left..right && event.y in top..bottom
    }

    private val hideRichTextSymbols = !AppPreferences.styledTextWithMarks(context)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        interceptTableTap(event)?.let { handled ->
            return handled
        }

        interceptClickableSpan(event)?.let { handled ->
            // if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, "ClickableSpan intercepted")
            return handled
        }

        val isSingleTapUp = singleTapUpDetector.onTouchEvent(event)

        if (isSingleTapUp) {
            val tapCharOffset = getOffsetForPosition(event.x, event.y)

            val spansChars = if (hideRichTextSymbols) {
                offsettingSpansOffset(tapCharOffset)
            } else {
                0
            }

            if (BuildConfig.LOG_DEBUG && event.action != MotionEvent.ACTION_MOVE) {
                LogUtils.d(TAG, tapCharOffset, spansChars, event)
            }

            listeners.onTapUp?.onTapUp(event.x, event.y, tapCharOffset + spansChars)
        }

        return super.onTouchEvent(event)
    }

    private fun offsettingSpansOffset(tapCharOffset: Int): Int {
        var spansChars = 0

        run allDone@ {
            SpanUtils.forEachSpan(text as Spannable, Offsetting::class.java) { span, curr, next ->
                if (tapCharOffset < next) {
                    return@allDone
                }
                spansChars += span.characterOffset
                LogUtils.d(TAG, span, curr, next, tapCharOffset, span.characterOffset)
            }
        }

        return spansChars
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        if (w != oldw) {
            // A span change requests a layout, which must not happen inside this one.
            post { fitTables() }
        }
    }

    /**
     * Scales each table down so its widest row fits the view's width, and cuts off each row still too
     * wide at the minimum scale. Safe to call repeatedly.
     */
    fun fitTables() {
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

    private fun rowWidths(text: CharSequence, rows: List<Pair<Int, Int>>, scale: Float): List<Float> {
        val scaledPaint = TextPaint(paint).apply { textSize = paint.textSize * scale }

        return rows.map { (rowStart, rowEnd) -> Layout.getDesiredWidth(text, rowStart, rowEnd, scaledPaint) }
    }

    private fun fittingScale(text: CharSequence, rows: List<Pair<Int, Int>>, available: Float): Float {
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

    fun activate() {
        visibility = View.VISIBLE
    }

    fun deactivate() {
        visibility = View.GONE
    }

    // Just pass to RichView
    override fun toggleDrawer(drawerSpan: DrawerMarkerSpan) {
        listeners.onActionListener?.toggleDrawer(drawerSpan)
    }

    // Just pass to RichView
    override fun toggleCheckbox(checkboxSpan: CheckboxSpan) {
        listeners.onActionListener?.toggleCheckbox(checkboxSpan)
    }

    // Just pass to RichView
    override fun followLinkToNoteOrBookWithProperty(name: String, value: String) {
        listeners.onActionListener?.followLinkToNoteOrBookWithProperty(name, value)
    }

    // Just pass to RichView
    override fun followLinkToFile(path: String) {
        listeners.onActionListener?.followLinkToFile(path)
    }

    companion object {
        val TAG: String = RichTextView::class.java.name
    }
}