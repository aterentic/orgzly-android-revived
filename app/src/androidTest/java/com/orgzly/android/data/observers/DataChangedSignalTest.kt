package com.orgzly.android.data.observers

import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.db.entity.Repo
import com.orgzly.android.repos.RepoType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DataChangedSignalTest : OrgzlyTest() {

    @get:Rule
    val retry = RetryTestRule()

    private lateinit var scope: CoroutineScope
    private lateinit var signal: DataChangedSignal

    @Before
    fun setUpSignal() {
        scope = CoroutineScope(Dispatchers.Default + Job())
        signal = DataChangedSignal(database, scope)
        Thread.sleep(INIT_GRACE_MS)
    }

    @After
    fun tearDownSignal() {
        scope.cancel()
    }

    @Test
    fun notifyChanged_emits() {
        val latch = CountDownLatch(1)
        subscribed { latch.countDown() }
        signal.notifyChanged()
        assertTrue(
            "Expected emission from notifyChanged()",
            latch.await(EMIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun observedTableInsert_emits() {
        val latch = CountDownLatch(1)
        subscribed { latch.countDown() }
        testUtils.setupBook("book", "* Note")
        assertTrue(
            "Expected emission from observed-table insert",
            latch.await(EMIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun bulkInserts_emitFewerEventsThanWrites() {
        val counter = AtomicInteger(0)
        subscribed { counter.incrementAndGet() }
        for (i in 1..BULK_INSERTS) {
            testUtils.setupBook("book-$i", "* Note $i")
        }
        Thread.sleep(DEBOUNCE_FLUSH_MS)
        // How many windows the writes span depends on emulator speed, so the
        // guarantee under test is coalescing, not an exact count.
        assertTrue(
            "Expected fewer emissions than writes, got ${counter.get()}",
            counter.get() in 1 until BULK_INSERTS
        )
    }

    @Test
    fun coalescingWrites_emitsOnceForEverythingInside() {
        val counter = AtomicInteger(0)
        subscribed { counter.incrementAndGet() }
        runBlocking {
            signal.coalescingWrites {
                for (i in 1..BULK_INSERTS) {
                    testUtils.setupBook("coalesced-$i", "* Note $i")
                    Thread.sleep(DEBOUNCE_FLUSH_MS)
                }
            }
        }
        Thread.sleep(DEBOUNCE_FLUSH_MS)
        assertEquals(
            "Writes spanning several debounce windows must still emit once",
            1,
            counter.get()
        )
    }

    @Test
    fun unobservedTableInsert_doesNotEmit() {
        val latch = CountDownLatch(1)
        subscribed { latch.countDown() }
        database.repo().insert(Repo(0, RepoType.MOCK, "mock://unobserved"))
        assertFalse(
            "Insert into unobserved table must not emit",
            latch.await(NEGATIVE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun syncAttempt_reachesOnlySyncAttemptCollectors() {
        val changes = AtomicInteger(0)
        val attempts = CountDownLatch(1)
        subscribed { changes.incrementAndGet() }
        val ready = CountDownLatch(1)
        scope.launch {
            signal.syncAttempts.onSubscription { ready.countDown() }
                .collect { attempts.countDown() }
        }
        ready.await(READY_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        signal.notifySyncAttemptFinished()

        assertTrue(
            "Expected the sync-attempt collector to see the emission",
            attempts.await(EMIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        )
        assertEquals("A sync attempt is not a data change", 0, changes.get())
    }

    /** Returns once [onEvent] is subscribed, so an emission cannot be missed. */
    private fun subscribed(onEvent: () -> Unit) {
        val ready = CountDownLatch(1)
        scope.launch {
            signal.events.onSubscription { ready.countDown() }.collect { onEvent() }
        }
        assertTrue(
            "Collector did not subscribe",
            ready.await(READY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        )
    }

    companion object {
        // Lets DataChangedSignal.init's coroutine subscribe to Room's
        // InvalidationTracker before any write fires.
        private const val INIT_GRACE_MS = 500L

        private const val BULK_INSERTS = 10
        private const val READY_TIMEOUT_MS = 5_000L
        private const val EMIT_TIMEOUT_MS = 2_000L
        private const val NEGATIVE_TIMEOUT_MS = 1_000L
        private const val DEBOUNCE_FLUSH_MS = 1_000L
    }
}
