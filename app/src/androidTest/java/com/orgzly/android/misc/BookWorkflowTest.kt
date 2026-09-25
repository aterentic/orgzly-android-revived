package com.orgzly.android.misc

import com.orgzly.android.OrgzlyTest
import com.orgzly.android.data.BookWorkflow
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.query.user.DottedQueryParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The whole path: a notebook declaring a workflow, its notes parsed with it, and a search
 * resolving done-ness against it. Each piece was unit-tested in isolation and the combination
 * still did not work, so the combination is what this asserts.
 */
class BookWorkflowTest : OrgzlyTest() {

    @Before
    override fun setUp() {
        super.setUp()
        AppPreferences.states(context, "TODO NEXT | DONE")
    }

    private fun search(query: String): List<String> =
        dataRepository.selectNotesFromQuery(DottedQueryParser().parse(query))
            .map { it.note.title }

    @Test
    fun testStateDeclaredByTheNotebookIsParsed() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL\n\n* CNCL Abandoned\n")

        val note = dataRepository.getLastNote("Abandoned")!!
        assertEquals("CNCL", note.state)
        assertEquals("Abandoned", note.title)
    }

    @Test
    fun testSearchFindsAStateDeclaredByTheNotebook() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL\n\n* CNCL Abandoned\n* NEXT Ongoing\n")

        assertEquals(listOf("Abandoned"), search("it.done"))
        assertEquals(listOf("Ongoing"), search("it.todo"))
    }

    /** Replacement: the app's DONE is not a done state inside a notebook declaring its own. */
    @Test
    fun testSearchDoesNotUseConfiguredStatesForADeclaringNotebook() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL\n\n* DONE Not a state here\n")

        assertTrue(search("it.done").isEmpty())
    }

    @Test
    fun testSearchStillUsesConfiguredStatesElsewhere() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL\n\n* CNCL Abandoned\n")
        testUtils.setupBook("book-b", "* DONE Finished\n")

        assertEquals(setOf("Abandoned", "Finished"), search("it.done").toSet())
    }

    /** Setting a workflow has to re-read the notebook, or its notes keep their old parse. */
    @Test
    fun testSettingAWorkflowRereadsTheNotebook() {
        testUtils.setupBook("book-a", "* CNCL Abandoned\n")

        // Parsed with the app's states, so the keyword is part of the title.
        assertEquals("CNCL Abandoned", dataRepository.getLastNote("CNCL Abandoned")!!.title)

        val book = dataRepository.getBook("book-a")!!
        dataRepository.setBookPreface(
            book.id, BookWorkflow.withWorkflowInPreface(book.preface, "NEXT | CNCL"))

        val note = dataRepository.getLastNote("Abandoned")!!
        assertEquals("CNCL", note.state)
        assertEquals(listOf("Abandoned"), search("it.done"))
    }

    /** Clearing it must put the keyword back, not leave a state nothing declares. */
    @Test
    fun testClearingAWorkflowRereadsTheNotebook() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL\n\n* CNCL Abandoned\n")

        val book = dataRepository.getBook("book-a")!!
        dataRepository.setBookPreface(
            book.id, BookWorkflow.withWorkflowInPreface(book.preface, null))

        assertEquals("CNCL Abandoned", dataRepository.getLastNote("CNCL Abandoned")!!.title)
        assertTrue(search("it.done").isEmpty())
    }

    /**
     * The global reparse runs when the states setting changes and from background work. It
     * must not flatten a notebook that declares its own workflow.
     */
    @Test
    fun testGlobalReparseKeepsStatesDeclaredByANotebook() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL\n\n* CNCL Abandoned\n")

        dataRepository.reParseNotesStateAndTitles()

        val note = dataRepository.getLastNote("Abandoned")!!
        assertEquals("CNCL", note.state)
        assertEquals("Abandoned", note.title)
    }

    // --- which notebook is in scope for a selection ---

    private fun noteIds(vararg titles: String): Set<Long> =
        titles.map { dataRepository.getLastNote(it)!!.id }.toSet()

    @Test
    fun testOneNoteResolvesToItsOwnNotebook() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL\n\n* CNCL Abandoned\n")

        val preface = dataRepository.getSharedBookPreface(noteIds("Abandoned"))

        assertEquals(listOf("CNCL"), BookWorkflow.doneKeywords(context, preface).toList())
    }

    @Test
    fun testSeveralNotesFromOneNotebookResolveToIt() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL\n\n* CNCL One\n* NEXT Two\n")

        val preface = dataRepository.getSharedBookPreface(noteIds("One", "Two"))

        assertEquals(listOf("CNCL"), BookWorkflow.doneKeywords(context, preface).toList())
    }

    /** A mixed selection has no single workflow, so the app's states are the only answer. */
    @Test
    fun testNotesFromSeveralNotebooksResolveToNothing() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL\n\n* CNCL One\n")
        testUtils.setupBook("book-b", "* DONE Two\n")

        assertNull(dataRepository.getSharedBookPreface(noteIds("One", "Two")))
    }

    @Test
    fun testANotebookDeclaringNothingResolvesToTheConfiguredStates() {
        testUtils.setupBook("book-b", "* DONE Finished\n")

        val preface = dataRepository.getSharedBookPreface(noteIds("Finished"))

        assertEquals(listOf("DONE"), BookWorkflow.doneKeywords(context, preface).toList())
    }
}
