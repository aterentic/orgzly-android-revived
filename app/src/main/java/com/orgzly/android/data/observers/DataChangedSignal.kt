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
import java.util.concurrent.atomic.AtomicBoolean
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

    private val _syncAttempts = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Emits when a sync attempt ends, whether or not it changed anything.
     * Only a consumer that renders sync state has reason to collect this.
     */
    val syncAttempts: SharedFlow<Unit> = _syncAttempts.asSharedFlow()

    private val coalescing = AtomicBoolean(false)
    private val missedWhileCoalescing = AtomicBoolean(false)

    /**
     * Emit for a change no DB write will announce, such as a settings edit.
     * Skips the debounce, which exists to coalesce bulk writes.
     */
    fun notifyChanged() {
        emitChanged()
    }

    fun notifySyncAttemptFinished() {
        _syncAttempts.tryEmit(Unit)
    }

    /**
     * Hold back change events until [block] returns, then emit one covering
     * everything it wrote. A sync writes each book's status separately with a
     * network round-trip in between, so without this each book costs every
     * consumer a full refresh. Not reentrant.
     */
    suspend fun <T> coalescingWrites(block: suspend () -> T): T {
        coalescing.set(true)
        try {
            return block()
        } finally {
            coalescing.set(false)
            if (missedWhileCoalescing.getAndSet(false)) {
                emitChanged()
            }
        }
    }

    private fun emitChanged() {
        if (coalescing.get()) {
            missedWhileCoalescing.set(true)
            return
        }
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
