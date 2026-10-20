package com.orgzly.android.ui.views.style

import android.text.Spannable
import android.text.Spanned
import android.text.style.TypefaceSpan
import java.util.regex.Pattern

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
