package com.orgzly.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class OrgTableTest {

    // Expected outputs are Emacs's org-table-align on the same input.

    @Test
    fun testPadsCellsToTheirColumnAndRebuildsSeparators() {
        assertEquals(
            "| a  | 1 |\n|----+---|\n| bb | 2 |",
            alignOrgTable("| a | 1 |\n|-\n| bb | 2 |"))
    }

    @Test
    fun testRightAlignsAColumnWhereAtLeastHalfTheFilledCellsAreNumbers() {
        assertEquals("| 10 |\n|  x |\n|    |", alignOrgTable("| 10 |\n| x |\n|  |"))
        assertEquals("| 10 |\n| x  |\n| y  |", alignOrgTable("| 10 |\n| x |\n| y |"))
        assertEquals(
            "|         10 |\n|        1/2 |\n| 2026-10-21 |",
            alignOrgTable("| 10 |\n| 1/2 |\n| 2026-10-21 |"))
    }

    @Test
    fun testFillsMissingCellsAndClosesRows() {
        assertEquals(
            "| 1  | a |   |\n| x  |   |   |\n|----+---+---|\n| yy | b | c |",
            alignOrgTable("| 1 | a |\n| x |\n|---|\n| yy | b | c |"))
        assertEquals("| a | b |\n| x |   |", alignOrgTable("| a  | b |\n|  x"))
    }

    @Test
    fun testKeepsTheFirstRowsIndentation() {
        assertEquals("  | a | b |\n  |---+---|\n  | c |   |", alignOrgTable("  | a|b\n  |--\n  |c"))
    }

    @Test
    fun testMeasuresALinkByItsDescription() {
        assertEquals(
            "| a    |\n| [[http://x.org][link]] |",
            alignOrgTable("| a |\n| [[http://x.org][link]] |"))
    }

    @Test
    fun testCountsWideCharactersAsTwoColumns() {
        assertEquals("| 10 | é    |\n|  x | 日本 |", alignOrgTable("| 10 | é |\n| x | 日本 |"))
        assertEquals(
            "| 한국     | ab |\n| Ｆｕｌｌ | ｶﾀ |\n| x        | y  |",
            alignOrgTable("| 한국 | ab |\n| Ｆｕｌｌ | ｶﾀ |\n| x | y |"))
    }

    @Test
    fun testGivesATableOfOnlySeparatorsOneColumn() {
        assertEquals("|---|\n|---|", alignOrgTable("|--\n|-"))
    }

    @Test
    fun testKeepsLinesThatAreNotRows() {
        assertEquals("| a |\ntext\n| b |\n", alignOrgTable("|a|\ntext\n|b|\n"))
    }

    @Test
    fun testAligningAgainChangesNothing() {
        randomTables().forEach { table ->
            val aligned = alignOrgTable(table)

            assertEquals(table, aligned, alignOrgTable(aligned))
        }
    }

    @Test
    fun testAlignedRowsAreEquallyWideAndKeepTheirCells() {
        randomTables().forEach { table ->
            val aligned = alignOrgTable(table).lines()

            assertEquals(table, 1, aligned.map { it.replace("[[u][link]]", "link").length }.distinct().size)
            assertEquals(table, cells(table), cells(aligned.joinToString("\n")))
        }
    }

    @Test
    fun testReplacingKeepsTheTablesNewline() {
        val source = "a\n|x|\nb"
        val table = SourceTable(2..5, "|x|\n")

        assertEquals("a\n| y |\nb", replaceOrgTable(source, table, "|y|"))
        assertEquals("a\n| y |\nb", replaceOrgTable(source, table, "|y|\n\n"))
    }

    @Test
    fun testReplacingWithBlankTextRemovesTheTable() {
        assertEquals("a\nb", replaceOrgTable("a\n|x|\nb", SourceTable(2..5, "|x|\n"), " \n"))
    }

    @Test
    fun testReplacingRefusesASourceThatChanged() {
        assertNull(replaceOrgTable("a\n|z|\nb", SourceTable(2..5, "|x|\n"), "|y|"))
        assertNull(replaceOrgTable("a\n", SourceTable(2..5, "|x|\n"), "|y|"))
    }

    @Test
    fun testFindsTheSourceTableByItsPlaceWhenAllTablesAreShown() {
        val source = "|[[u][d]]|\ntext\n|b|"
        val shown = listOf("|d|\n", "|b|")
        val render = { text: String -> text.replace("[[u][d]]", "d") }

        assertEquals(SourceTable(0..10, "|[[u][d]]|\n"), findSourceTable(source, shown, 0, render))
        assertEquals(SourceTable(16..18, "|b|"), findSourceTable(source, shown, 1, render))
    }

    @Test
    fun testFindsAHiddenTablesNeighbourOnlyByAUniqueRendering() {
        assertEquals(SourceTable(9..11, "|y|"), findSourceTable("|x|\ntext\n|y|", listOf("|y|"), 0) { it })
        assertNull(findSourceTable("|x|\ntext\n|x|\n", listOf("|x|\n"), 0) { it })
    }

    @Test
    fun testRefusesASourceTableThatRendersDifferently() {
        assertNull(findSourceTable("|a|\n\n|b|", listOf("|b|", "|a|"), 0) { it })
        assertNull(findSourceTable("|a|", listOf("|a|"), 1) { it })
    }

    private fun cells(table: String): List<List<String>> =
        table.lines().filterNot { it.trimStart().startsWith("|-") }.map { line ->
            line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }.dropLastWhile { it.isEmpty() }
        }

    // A cell starts with a space, because a row starting with "|-" is a separator in Emacs too.
    private fun randomTables(): List<String> {
        val random = Random(427)
        val words = listOf("a", "bb", "10", "-3.5", "x y", "", "1e3", "[[u][link]]", "ccc", "2026-10-21")

        return List(200) {
            val columns = random.nextInt(1, 5)

            List(random.nextInt(1, 6)) {
                if (random.nextInt(5) == 0) {
                    "|-"
                } else {
                    List(columns) { " ".repeat(random.nextInt(1, 3)) + words.random(random) }.joinToString("|", "|", "|")
                }
            }.joinToString("\n")
        }
    }
}
