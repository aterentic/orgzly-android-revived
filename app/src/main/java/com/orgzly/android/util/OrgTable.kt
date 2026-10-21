package com.orgzly.android.util

import java.util.regex.Pattern

// The newline ending the last row belongs to the table, because it sets that line's height.
private val TABLE_PATTERN = Pattern.compile("""^[ \t]*\|.*(?:\n[ \t]*\|.*)*\n?""", Pattern.MULTILINE)

/** The runs of org table lines in [text], in order. */
fun tableRanges(text: CharSequence): List<IntRange> {
    val m = TABLE_PATTERN.matcher(text)

    return generateSequence { if (m.find()) m.start() until m.end() else null }.toList()
}

/** A table's place in a note's source, and the text it had there. */
data class SourceTable(val range: IntRange, val text: String) {

    /** Whether [source] still holds this table at its place. */
    fun isIn(source: CharSequence): Boolean =
        range.last < source.length && source.subSequence(range.first, range.last + 1).toString() == text

    /**
     * The text that replaces this table with [edited]: aligned, keeping the table's trailing newline.
     * A blank [edited] removes the table.
     */
    fun replacement(edited: String): String {
        val body = edited.trimEnd('\n')

        return when {
            body.isBlank() -> ""
            text.endsWith('\n') -> alignOrgTable(body) + "\n"
            else -> alignOrgTable(body)
        }
    }
}

/**
 * The source table drawn as [shown]`[index]`, where [shown] holds the tables' texts on screen in order. When every
 * source table is on screen, order maps them. A folded drawer can hide some, and then only the single source table
 * that renders as the shown one is taken, so an edit never lands on another table.
 */
fun findSourceTable(source: String, shown: List<String>, index: Int, render: (String) -> String): SourceTable? {
    val tables = tableRanges(source).map { SourceTable(it, source.substring(it)) }
    val target = shown.getOrNull(index) ?: return null

    return if (tables.size == shown.size) {
        tables[index].takeIf { render(it.text) == target }
    } else {
        tables.filter { render(it.text) == target }.singleOrNull()
    }
}

/** [source] with [table] replaced by [edited], or null when [source] no longer holds [table] at its place. */
fun replaceOrgTable(source: String, table: SourceTable, edited: String): String? =
    if (table.isIn(source)) source.replaceRange(table.range, table.replacement(edited)) else null

/**
 * Lines up [table]'s columns as Emacs's `org-table-align` does: every row takes the first row's indentation,
 * each cell is padded to its column's width, a column of mostly numbers is right-aligned, and separator rows
 * are rebuilt. Lines that are not table rows stay as they are.
 */
fun alignOrgTable(table: String): String {
    val lines = table.split('\n').map(::tableLine)
    val rows = lines.filterIsInstance<TableLine.Row>()
    val columnCount = when {
        rows.isNotEmpty() -> rows.maxOf { it.cells.size }
        lines.any { it is TableLine.Separator } -> 1
        else -> return table
    }
    val indent = lines.firstNotNullOfOrNull { (it as? TableLine.Row)?.indent ?: (it as? TableLine.Separator)?.indent }.orEmpty()

    val columns = (0 until columnCount).map { c -> rows.map { it.cells.getOrElse(c) { "" } } }
    val widths = columns.map { cells -> maxOf(1, cells.maxOfOrNull(::displayWidth) ?: 0) }
    val rightAligned = columns.map(::isNumberColumn)

    return lines.joinToString("\n") { line ->
        when (line) {
            is TableLine.Row ->
                (0 until columnCount).joinToString(" | ", "$indent| ", " |") { c ->
                    pad(line.cells.getOrElse(c) { "" }, widths[c], rightAligned[c])
                }

            is TableLine.Separator ->
                widths.joinToString("+", "$indent|", "|") { "-".repeat(it + 2) }

            is TableLine.Other ->
                line.text
        }
    }
}

private sealed interface TableLine {
    class Row(val indent: String, val cells: List<String>) : TableLine
    class Separator(val indent: String) : TableLine
    class Other(val text: String) : TableLine
}

private val ROW = Regex("""([ \t]*)\|(.*)""")

private fun tableLine(line: String): TableLine {
    val match = ROW.matchEntire(line) ?: return TableLine.Other(line)
    val (indent, body) = match.destructured

    if (body.startsWith('-')) {
        return TableLine.Separator(indent)
    }

    return TableLine.Row(indent, body.trimEnd().removeSuffix("|").split('|').map { it.trim() })
}

private fun pad(cell: String, width: Int, rightAligned: Boolean): String {
    val gap = " ".repeat(width - displayWidth(cell))

    return if (rightAligned) gap + cell else cell + gap
}

// Emacs's org-table-number-regexp, matched as Emacs does by default, ignoring case.
private val NUMBER = Regex(
    """[<>]?[-+^.0-9]*[0-9][-+^.0-9eEdDx()%:]*|[<>]?[-+]?0[xX][0-9a-fA-F.]+|[<>]?[-+]?[0-9]+#[0-9a-zA-Z.]+|nan|[-+u]?inf""",
    RegexOption.IGNORE_CASE)

// Emacs's org-table-number-fraction.
private const val NUMBER_FRACTION = 0.5

private fun isNumberColumn(cells: List<String>): Boolean {
    val filled = cells.filter { it.isNotEmpty() }

    return filled.isNotEmpty() && filled.count { NUMBER.matches(it) } >= NUMBER_FRACTION * filled.size
}

private val LINK = Regex("""\[\[([^\]]+)](?:\[([^\]]+)])?]""")

/** The columns [cell] takes in Emacs, which shows a link as its description. */
private fun displayWidth(cell: String): Int {
    val shown = LINK.replace(cell) { it.groupValues[2].ifEmpty { it.groupValues[1] } }

    return shown.codePoints().map(::columns).sum()
}

private fun columns(codePoint: Int): Int = when {
    Character.getType(codePoint).toByte() in ZERO_WIDTH_TYPES -> 0
    WIDE.any { codePoint in it } -> 2
    else -> 1
}

private val ZERO_WIDTH_TYPES = setOf(Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.FORMAT)

// The code points from U+00A0 that Emacs 31.1 draws two columns wide, from its char-width.
private val WIDE = listOf(
    0x1100..0x115F, 0x231A..0x231B, 0x2329..0x232A, 0x23E9..0x23EC, 0x23F0..0x23F0, 0x23F3..0x23F3, 0x25FD..0x25FE,
    0x2614..0x2615, 0x2630..0x2637, 0x2648..0x2653, 0x267F..0x267F, 0x268A..0x2693, 0x26A1..0x26A1, 0x26AA..0x26AB,
    0x26BD..0x26BE, 0x26C4..0x26C5, 0x26CE..0x26CE, 0x26D4..0x26D4, 0x26EA..0x26EA, 0x26F2..0x26F3, 0x26F5..0x26F5,
    0x26FA..0x26FA, 0x26FD..0x26FD, 0x2705..0x2705, 0x270A..0x270B, 0x2728..0x2728, 0x274C..0x274C, 0x274E..0x274E,
    0x2753..0x2755, 0x2757..0x2757, 0x2795..0x2797, 0x27B0..0x27B0, 0x27BF..0x27BF, 0x2B1B..0x2B1C, 0x2B50..0x2B50,
    0x2B55..0x2B55, 0x2E80..0x2E99, 0x2E9B..0x2EF3, 0x2F00..0x2FD5, 0x2FF0..0x303E, 0x3041..0x3096, 0x3099..0x30FF,
    0x3105..0x312F, 0x3131..0x31E5, 0x31EF..0x3247, 0x3250..0xA48C, 0xA490..0xA4C6, 0xA960..0xA97C, 0xAC00..0xD7A3,
    0xF900..0xFAFF, 0xFE10..0xFE19, 0xFE30..0xFE6B, 0xFF01..0xFF60, 0xFFE0..0xFFE6, 0x16FE0..0x16FE4,
    0x16FF0..0x16FF6, 0x17000..0x187F7, 0x18800..0x18CD5, 0x18CFF..0x18D1E, 0x18D80..0x18DF2, 0x1AFF0..0x1AFF3,
    0x1AFF5..0x1AFFB, 0x1AFFD..0x1AFFE, 0x1B000..0x1B122, 0x1B132..0x1B132, 0x1B150..0x1B152, 0x1B155..0x1B155,
    0x1B164..0x1B167, 0x1B170..0x1B2FB, 0x1D300..0x1D356, 0x1D360..0x1D376, 0x1F004..0x1F004, 0x1F0CF..0x1F0CF,
    0x1F18E..0x1F18E, 0x1F191..0x1F19A, 0x1F1AD..0x1F1AD, 0x1F200..0x1F202, 0x1F210..0x1F23B, 0x1F240..0x1F248,
    0x1F250..0x1F251, 0x1F260..0x1F265, 0x1F300..0x1F320, 0x1F32D..0x1F335, 0x1F337..0x1F37C, 0x1F37E..0x1F393,
    0x1F3A0..0x1F3CA, 0x1F3CF..0x1F3D3, 0x1F3E0..0x1F3F0, 0x1F3F4..0x1F3F4, 0x1F3F8..0x1F43E, 0x1F440..0x1F440,
    0x1F442..0x1F4FC, 0x1F4FF..0x1F53D, 0x1F54B..0x1F54E, 0x1F550..0x1F567, 0x1F57A..0x1F57A, 0x1F595..0x1F596,
    0x1F5A4..0x1F5A4, 0x1F5FB..0x1F64F, 0x1F680..0x1F6C5, 0x1F6CC..0x1F6CC, 0x1F6D0..0x1F6D2, 0x1F6D5..0x1F6D8,
    0x1F6DC..0x1F6DF, 0x1F6EB..0x1F6EC, 0x1F6F4..0x1F6FC, 0x1F7E0..0x1F7EB, 0x1F7F0..0x1F7F0, 0x1F90C..0x1F93A,
    0x1F93C..0x1F945, 0x1F947..0x1FA53, 0x1FA60..0x1FA6D, 0x1FA70..0x1FA7C, 0x1FA80..0x1FA8A, 0x1FA8E..0x1FAC6,
    0x1FAC8..0x1FAC8, 0x1FACD..0x1FADC, 0x1FADF..0x1FAEA, 0x1FAEF..0x1FAF8, 0x1FB00..0x1FB92, 0x20000..0x3FFFF)
