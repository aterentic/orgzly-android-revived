package com.orgzly.android.data

import android.content.Context
import com.orgzly.android.db.dao.BookDao
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.prefs.StateWorkflows
import com.orgzly.org.OrgFileSettings

/**
 * The state keywords in force for a notebook.
 *
 * A notebook declaring its own workflow **replaces** the app-configured states for its notes,
 * as it does in org, rather than narrowing them: a file is free to use states the app has never
 * heard of, and free to omit ones it has.
 */
object BookWorkflow {

    /** Org treats all three as sources of keywords, and a file may use more than one. */
    private val KEYWORDS = listOf("TODO", "SEQ_TODO", "TYP_TODO")

    /** Null when the preface declares no workflow, so the caller falls back to the setting. */
    fun fromPreface(preface: String?): StateWorkflows? {
        val settings = preface?.let { OrgFileSettings.fromPreface(it) } ?: return null

        val lines = KEYWORDS.flatMap { settings.getKeywordValues(it).orEmpty() }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return null
        }

        // Each line is one workflow, which is how StateWorkflows already reads the setting.
        return StateWorkflows(lines.joinToString("\n"))
    }

    fun of(context: Context, preface: String?): StateWorkflows =
        fromPreface(preface) ?: StateWorkflows(AppPreferences.states(context))

    /**
     * The workflow of each notebook that declares one, by notebook id.
     *
     * Absent means "uses the app's states", which is the common case, so this stays small —
     * a query inlines one branch per entry and none at all when it is empty.
     */
    fun declaredBy(prefaces: List<BookDao.BookPreface>): Map<Long, StateWorkflows> =
        prefaces.mapNotNull { book -> fromPreface(book.preface)?.let { book.id to it } }.toMap()

    fun todoKeywords(context: Context, preface: String?): Set<String> =
        of(context, preface).flatMapTo(LinkedHashSet()) { it.todoKeywords }

    fun doneKeywords(context: Context, preface: String?): Set<String> =
        of(context, preface).flatMapTo(LinkedHashSet()) { it.doneKeywords }

    /** The notebook's own `#+TODO:` as written, or null when it declares none. */
    fun todoLineValue(preface: String?): String? =
        preface?.let { OrgFileSettings.fromPreface(it) }
            ?.getKeywordValues("TODO")
            ?.firstOrNull { it.isNotBlank() }

    /**
     * Rewrites a preface so its `#+TODO:` reads [workflow], or declares none when it is null.
     *
     * Every `#+TODO:` line collapses into one, because the dialog edits a single value. Lines
     * for the other two spellings are left alone: nothing in the app writes them, so a file
     * carrying one had it written elsewhere and should keep it.
     */
    fun withWorkflowInPreface(preface: String?, workflow: String?): String {
        val line = workflow?.trim()?.takeIf { it.isNotEmpty() }?.let { "#+TODO: $it" }

        val original = preface ?: ""
        if (original.isEmpty()) {
            return line ?: ""
        }

        val lines = original.lines()
        val isTodo = { l: String -> l.trimStart().startsWith("#+TODO:", true) }
        val first = lines.indexOfFirst(isTodo)

        val out = ArrayList<String>(lines.size + 1)
        lines.forEachIndexed { i, l ->
            when {
                !isTodo(l) -> out.add(l)
                i == first && line != null -> out.add(line)
                else -> Unit // a later #+TODO: line collapses into the first
            }
        }

        if (line != null && first < 0) {
            out.add(0, line)
        }

        return out.joinToString("\n")
    }
}
