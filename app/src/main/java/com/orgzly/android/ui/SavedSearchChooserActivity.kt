package com.orgzly.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.AppIntent
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.SavedSearch
import com.orgzly.android.ui.main.MainActivity
import com.orgzly.android.ui.savedsearches.SavedSearchesViewModel
import com.orgzly.android.ui.savedsearches.SavedSearchesViewModelFactory
import com.orgzly.android.widgets.ListWidgetSelectionAdapter
import javax.inject.Inject

/**
 * Activity for creating a saved search shortcut.
 */
class SavedSearchChooserActivity : AppCompatActivity(), OnViewHolderClickListener<SavedSearch> {

    @Inject
    lateinit var dataRepository: DataRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        App.appComponent.inject(this)

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_list_widget_selection)

        val viewFlipper = findViewById<ViewFlipper>(R.id.activity_list_widget_selection_view_flipper)

        val viewAdapter = ListWidgetSelectionAdapter(this)
        viewAdapter.setHasStableIds(true)

        findViewById<RecyclerView>(R.id.activity_list_widget_selection_recycler_view).let {
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = viewAdapter
        }

        val factory = SavedSearchesViewModelFactory.getInstance(dataRepository)
        val model = ViewModelProvider(this, factory)[SavedSearchesViewModel::class.java]

        model.viewState.observe(this) {
            viewFlipper.displayedChild = when (it) {
                SavedSearchesViewModel.ViewState.EMPTY -> 1
                else -> 0
            }
        }

        model.data.observe(this) { savedSearches ->
            viewAdapter.submitList(savedSearches)
        }
    }

    override fun onClick(view: View, position: Int, item: SavedSearch) {
        val shortcut = ShortcutInfoCompat.Builder(this, "saved-search-${item.id}")
            // Import accepts an empty name, and the builder rejects an empty label.
            .setShortLabel(item.name.ifEmpty { getString(R.string.saved_search_shortcut_label) })
            .setIcon(IconCompat.createWithResource(this, R.mipmap.cic_shortcut_search))
            .setIntent(createLaunchIntent(item.id))
            .build()

        setResult(RESULT_OK, ShortcutManagerCompat.createShortcutResultIntent(this, shortcut))

        finish()
    }

    override fun onLongClick(view: View, position: Int, item: SavedSearch) {
    }

    // The id rather than the query, so the shortcut follows later edits to the search.
    private fun createLaunchIntent(savedSearchId: Long): Intent {
        return Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(AppIntent.EXTRA_SAVED_SEARCH_ID, savedSearchId)
        }
    }
}
