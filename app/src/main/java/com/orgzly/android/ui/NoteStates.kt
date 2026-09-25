package com.orgzly.android.ui

import android.content.Context

import com.orgzly.android.data.BookWorkflow

import java.util.ArrayList

class NoteStates {
    private val values = ArrayList<String>()

    val array: Array<String>
        get() = values.toTypedArray()

    fun indexOf(keyword: String) = values.indexOf(keyword)

    operator fun get(i: Int) = values[i]

    companion object {
        const val NO_STATE_KEYWORD = "NOTE"

        @JvmStatic
        fun fromPreferences(context: Context): NoteStates = fromBook(context, null)

        /**
         * The states a notebook offers: its own workflow when its preface declares one, the
         * app-configured states otherwise. A null preface means no notebook is in scope — a
         * selection spanning several, for instance — and falls back to the setting.
         */
        @JvmStatic
        fun fromBook(context: Context, preface: String?): NoteStates {
            val noteStates = NoteStates()

            noteStates.values.addAll(BookWorkflow.todoKeywords(context, preface))
            noteStates.values.addAll(BookWorkflow.doneKeywords(context, preface))

            return noteStates
        }

        @JvmStatic
        fun isKeyword(keyword: String?): Boolean {
            return keyword != null && keyword != NO_STATE_KEYWORD
        }
    }
}