package com.orgzly.android.ui.views.richtext

import android.util.TypedValue
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.orgzly.R

/** Shows [table] in a dialog that scrolls both ways. A link to a note, notebook or file closes the dialog first. */
fun showTableDialog(source: RichTextView, table: CharSequence) {
    val context = source.context
    val padding = context.resources.getDimensionPixelSize(R.dimen.screen_edge)

    lateinit var dialog: AlertDialog

    val view = RichTextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, source.textSize)
        setPadding(padding, padding, padding, padding)
        setText(table, TextView.BufferType.SPANNABLE)

        setOnActionListener(object : ActionableRichTextView by source {
            override fun followLinkToNoteOrBookWithProperty(name: String, value: String) {
                dialog.dismiss()
                source.followLinkToNoteOrBookWithProperty(name, value)
            }

            override fun followLinkToFile(path: String) {
                dialog.dismiss()
                source.followLinkToFile(path)
            }
        })
    }

    val scrolling = ScrollView(context).apply {
        addView(HorizontalScrollView(context).apply { addView(view) })
    }

    dialog = MaterialAlertDialogBuilder(context)
        .setView(scrolling)
        .setPositiveButton(R.string.ok, null)
        .show()
}
