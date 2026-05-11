package com.orgzly.android.data.observers

import com.orgzly.BuildConfig
import com.orgzly.android.db.OrgzlyDatabase
import com.orgzly.android.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataChangedSignal @Inject constructor(
    database: OrgzlyDatabase,
    scope: CoroutineScope
) {

    private val _events = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emits when data a consumer renders has changed. */
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    /**
     * Emit for a change no DB write will announce, such as a settings edit.
     * Skips the debounce, which exists to coalesce bulk writes.
     */
    fun notifyChanged() {
        emitChanged()
    }

    private fun emitChanged() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, "emit")
        _events.tryEmit(Unit)
    }

    init {
        scope.launch {
            database.invalidationTracker
                .createFlow(*TABLES, emitInitialState = false)
                .debounce(DEBOUNCE_MS)
                .collect { emitChanged() }
        }
    }

    companion object {
        private val TAG: String = DataChangedSignal::class.java.name

        private const val DEBOUNCE_MS = 250L

        private val TABLES = arrayOf(
            "notes",
            "books",
            "searches",
            "note_events",
            "org_ranges",
            "org_timestamps",
            "book_syncs",
            "book_properties"
        )
    }
}
