package com.orgzly.android.ui.views.richtext

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.orgzly.R

/** An org table's source text, and where its edited text goes. */
class TableEdit(val source: String, val save: (String) -> Unit)

/** What the table dialog opens with. */
sealed interface TableDialogContent {
    /** The table as drawn, with working links, and an Edit button when [edit] is present. */
    class Viewing(val display: CharSequence, val links: ActionableRichTextView, val edit: TableEdit?) : TableDialogContent

    /** The table's source text, on lines that don't wrap. */
    class Editing(val edit: TableEdit) : TableDialogContent
}

fun showTableDialog(context: Context, textSize: Float, content: TableDialogContent) {
    when (content) {
        is TableDialogContent.Viewing -> showViewing(context, textSize, content)
        is TableDialogContent.Editing -> showEditing(context, textSize, content.edit)
    }
}

/** A link to a note, notebook or file closes the dialog first. */
private fun showViewing(context: Context, textSize: Float, content: TableDialogContent.Viewing) {
    val padding = context.resources.getDimensionPixelSize(R.dimen.screen_edge)

    lateinit var dialog: AlertDialog

    val view = RichTextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        setPadding(padding, padding, padding, padding)
        setText(content.display, TextView.BufferType.SPANNABLE)

        setOnActionListener(object : ActionableRichTextView by content.links {
            override fun followLinkToNoteOrBookWithProperty(name: String, value: String) {
                dialog.dismiss()
                content.links.followLinkToNoteOrBookWithProperty(name, value)
            }

            override fun followLinkToFile(path: String) {
                dialog.dismiss()
                content.links.followLinkToFile(path)
            }
        })
    }

    val scrolling = ScrollView(context).apply {
        addView(HorizontalScrollView(context).apply { addView(view) })
    }

    dialog = MaterialAlertDialogBuilder(context)
        .setView(scrolling)
        .setPositiveButton(R.string.ok, null)
        .apply {
            content.edit?.let { edit ->
                setNeutralButton(R.string.edit) { _, _ -> showEditing(context, textSize, edit) }
            }
        }
        .show()
}

private fun showEditing(context: Context, textSize: Float, edit: TableEdit) {
    val padding = context.resources.getDimensionPixelSize(R.dimen.screen_edge)

    val editor = AppCompatEditText(context).apply {
        // A multi-line input type turns horizontal scrolling off, so it is set first.
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setHorizontallyScrolling(true)

        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        gravity = Gravity.TOP or Gravity.START
        setText(edit.source.trimEnd('\n'))
    }

    val container = FrameLayout(context).apply {
        setPadding(padding, padding, padding, 0)
        addView(editor)
    }

    val dialog = MaterialAlertDialogBuilder(context)
        .setView(container)
        .setPositiveButton(R.string.save) { _, _ -> edit.save(editor.text.toString()) }
        .setNegativeButton(R.string.cancel, null)
        .create()

    dialog.setCanceledOnTouchOutside(false)
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    dialog.show()

    // Taking focus moves the cursor to the end, so it goes back to the start, where the table's first columns are.
    editor.requestFocus()
    editor.setSelection(0)
}
