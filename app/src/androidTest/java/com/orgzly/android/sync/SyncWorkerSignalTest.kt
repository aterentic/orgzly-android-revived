package com.orgzly.android.sync

import androidx.work.testing.TestListenableWorkerBuilder
import com.orgzly.android.App
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SyncWorkerSignalTest : OrgzlyTest() {

    @get:Rule
    val retry = RetryTestRule()

    @Test
    fun doWork_reportsSyncAttempt_regardlessOfOutcome() {
        val signal = App.appComponent.dataChangedSignal()
        val latch = CountDownLatch(1)
        val ready = CountDownLatch(1)
        val scope = CoroutineScope(Dispatchers.Default + Job())
        scope.launch {
            signal.syncAttempts.onSubscription { ready.countDown() }
                .collect { latch.countDown() }
        }
        try {
            assertTrue(
                "Collector did not subscribe",
                ready.await(READY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            )

            val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
            runBlocking { worker.doWork() }

            assertTrue(
                "Expected a sync-attempt emission after SyncWorker.doWork()",
                latch.await(EMIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            )
        } finally {
            scope.cancel()
        }
    }

    companion object {
        private const val READY_TIMEOUT_MS = 5_000L
        private const val EMIT_TIMEOUT_MS = 5_000L
    }
}
