package com.orgzly.android.util

import java.util.regex.Pattern

// The newline ending the last row belongs to the table, because it sets that line's height.
private val TABLE_PATTERN = Pattern.compile("""^[ \t]*\|.*(?:\n[ \t]*\|.*)*\n?""", Pattern.MULTILINE)

/** The runs of org table lines in [text], in order. */
fun tableRanges(text: CharSequence): List<IntRange> {
    val m = TABLE_PATTERN.matcher(text)

    return generateSequence { if (m.find()) m.start() until m.end() else null }.toList()
}
