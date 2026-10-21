package com.orgzly.android.ui.views.richtext

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.text.Editable
import android.text.InputFilter
import android.text.Spanned
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.ancestors
import androidx.core.widget.NestedScrollView
import com.orgzly.BuildConfig
import com.orgzly.android.ui.util.KeyboardUtils
import com.orgzly.android.ui.views.style.TableSpan
import com.orgzly.android.ui.views.style.markTables
import com.orgzly.android.ui.views.style.removeTableFitting
import com.orgzly.android.ui.views.style.tableAt
import com.orgzly.android.util.LogUtils

class RichTextEdit : AppCompatEditText {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private val userEditingTextWatcher: TextWatcher = RichTextEditWatcher()

    private val tableMarker: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable) {
            markTables(s)
            fitTables()
        }
    }

    private var onTableTap: RichTextView.OnTableTapListener? = null

    init {
        filters += TableGuard

        // From API 35 an EditText keeps each line as tall as the locale's text, which spaces shrunk table
        // rows apart. View mode has no such minimum.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            isLocalePreferredLineHeightForMinimumUsed = false
        }
    }

    fun setOnTableTapListener(listener: RichTextView.OnTableTapListener) {
        onTableTap = listener
    }

    fun activate(charOffset: Int) {
        visibility = View.VISIBLE

        text?.let(::markTables)
        fitTables()

        // Position the cursor and open the keyboard
        if (charOffset in 0..(text?.length ?: 0)) {
            performClick()
            setSelection(charOffset)

            KeyboardUtils.openSoftKeyboard(this) {
                scrollForBetterCursorPosition(charOffset)
            }
        }

        addTextChangedListener(userEditingTextWatcher)
        addTextChangedListener(tableMarker)
    }

    // TODO: Handle closed drawers (and such)
    private fun scrollForBetterCursorPosition(charOffset: Int) {
        val scrollView = ancestors.firstOrNull { view -> view is NestedScrollView } as? NestedScrollView

        if (scrollView != null) {
            post {
                val richText = parent as RichText

                val line = layout.getLineForOffset(charOffset)
                val baseline = layout.getLineBaseline(line)
                val ascent = layout.getLineAscent(line)

                val cursorY = richText.top + (baseline + ascent)

                val visibleHeight = Rect().let { rect ->
                    scrollView.getDrawingRect(rect)
                    rect.bottom - rect.top
                }

                val scrollTopY = scrollView.scrollY
                val scroll75pY = scrollTopY + (visibleHeight*3/4)

                // Scroll unless cursor is already in the top part of the visible rect
                val scrollTo = if (cursorY < scrollTopY) { // Too high
                    cursorY
                } else if (cursorY > scroll75pY) {  // Too low
                    cursorY - (visibleHeight*3/4)
                } else {
                    -1
                }

                if (scrollTo != -1) {
                    scrollView.smoothScrollTo(0, scrollTo)
                }

                if (BuildConfig.LOG_DEBUG) {
                    LogUtils.d(TAG, scrollTo)
                }
            }
        }
    }

    fun deactivate() {
        removeTextChangedListener(userEditingTextWatcher)
        removeTextChangedListener(tableMarker)
        text?.let(::removeTableFitting)

        visibility = View.GONE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        if (w != oldw && visibility == View.VISIBLE) {
            // A span change requests a layout, which must not happen inside this one.
            post { fitTables() }
        }
    }

    /** A tap on a table line opens the table instead of placing the cursor in it. */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val listener = onTableTap
        val table = if (listener != null) tableUnder(event) else null

        if (listener == null || table == null) {
            return super.onTouchEvent(event)
        }

        if (event.action == MotionEvent.ACTION_UP) {
            listener.onTableTap(table)
        }

        return true
    }

    private fun tableUnder(event: MotionEvent): TableSpan? {
        val layout = layout ?: return null
        val line = layout.getLineForVertical(event.y.toInt() - totalPaddingTop + scrollY)

        return text?.tableAt(layout.getLineStart(line))
    }

    private var lastCursor = 0

    /**
     * Moves a cursor that lands inside a table, whose rows can be cut off, to the table's start when it came from
     * after the table and to its end otherwise, so arrow keys step over the table both ways.
     */
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)

        val text = text ?: return
        val table = text.tableAt(selStart)

        if (selStart == selEnd && table != null && selStart > text.getSpanStart(table)) {
            setSelection(if (lastCursor >= text.getSpanEnd(table)) text.getSpanStart(table) else text.getSpanEnd(table))
            return
        }

        lastCursor = selStart
    }

    /* Clear the focus on back press before letting IME handle the event. */
    override fun onKeyPreIme(keyCode: Int, event: KeyEvent?): Boolean {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, keyCode, event)

        if (keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_UP) {
            if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, "Clear focus before IME handling the event")
            clearFocus()
        }

        return super.onKeyPreIme(keyCode, event)
    }

    fun insertStringAtCursorPosition(string: String) {
        this.text?.replace(this.selectionStart, this.selectionEnd, string)
    }

    /**
     * Refuses an edit that changes part of a table, or inserts inside one, because the change could be out of
     * sight in a cut-off row. Replacing a whole table is allowed, and so is starting a new line after it.
     */
    private object TableGuard : InputFilter {
        override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
            val changesPart = dest.getSpans(dstart, dend, TableSpan::class.java).any { table ->
                val tableStart = dest.getSpanStart(table)
                val tableEnd = dest.getSpanEnd(table)

                if (dstart == dend) {
                    // A table without a closing newline ends the text, so text added there lands on its last row.
                    val onLastRow = dstart == tableEnd && dest[tableEnd - 1] != '\n' &&
                        source.subSequence(start, end).firstOrNull() != '\n'

                    dstart in tableStart + 1 until tableEnd || onLastRow
                } else {
                    dstart < tableEnd && dend > tableStart && (dstart > tableStart || dend < tableEnd)
                }
            }

            return if (changesPart) dest.subSequence(dstart, dend).toString() else null
        }
    }

    companion object {
        val TAG: String = RichTextEdit::class.java.name
    }
}
