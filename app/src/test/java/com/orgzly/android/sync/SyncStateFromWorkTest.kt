package com.orgzly.android.sync

import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkInfo.State.BLOCKED
import androidx.work.WorkInfo.State.ENQUEUED
import androidx.work.WorkInfo.State.FAILED
import androidx.work.WorkInfo.State.RUNNING
import androidx.work.WorkInfo.State.SUCCEEDED
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class SyncStateFromWorkTest {
    @Test
    fun runningSyncIsShownOverTheOneQueuedBehindIt() {
        val running = work(BLOCKED) + work(RUNNING, progress = SyncState.getInstance(SyncState.Type.COLLECTING_BOOKS))

        assertEquals(SyncState.Type.COLLECTING_BOOKS, stateOf(running))
    }

    @Test
    fun waitingSyncShowsAsStarting() {
        assertEquals(SyncState.Type.STARTING, stateOf(work(ENQUEUED)))
    }

    @Test
    fun failureIsShownWhenTheFollowUpFailsWithoutOutput() {
        val failed = work(FAILED) + work(FAILED, output = SyncState.getInstance(SyncState.Type.FAILED_NO_CONNECTION))

        assertEquals(SyncState.Type.FAILED_NO_CONNECTION, stateOf(failed))
    }

    @Test
    fun failureIsShownOverASuccessInTheSameChain() {
        val chain = work(SUCCEEDED, output = SyncState.getInstance(SyncState.Type.FINISHED)) +
            work(FAILED, output = SyncState.getInstance(SyncState.Type.FAILED_EXCEPTION))

        assertEquals(SyncState.Type.FAILED_EXCEPTION, stateOf(chain))
    }

    @Test
    fun finishedChainIsShownAsFinished() {
        val chain = work(SUCCEEDED, output = SyncState.getInstance(SyncState.Type.FINISHED)) +
            work(SUCCEEDED, output = SyncState.getInstance(SyncState.Type.FINISHED))

        assertEquals(SyncState.Type.FINISHED, stateOf(chain))
    }

    private fun stateOf(work: List<WorkInfo>) = SyncRunner.syncStateFromWorkInfoList(work)?.type

    private fun work(state: WorkInfo.State, output: SyncState? = null, progress: SyncState? = null) =
        listOf(WorkInfo(UUID.randomUUID(), state, emptySet(), output?.toData() ?: Data.EMPTY, progress?.toData() ?: Data.EMPTY))
}
