package com.orgzly.android.sync

import androidx.work.WorkInfo.State.BLOCKED
import androidx.work.WorkInfo.State.CANCELLED
import androidx.work.WorkInfo.State.ENQUEUED
import androidx.work.WorkInfo.State.FAILED
import androidx.work.WorkInfo.State.RUNNING
import androidx.work.WorkInfo.State.SUCCEEDED
import androidx.work.WorkInfo
import com.orgzly.android.sync.SyncRunner.QueuedSync
import com.orgzly.android.sync.SyncRunner.SyncOrigin.AUTO
import com.orgzly.android.sync.SyncRunner.SyncOrigin.MANUAL
import com.orgzly.android.sync.SyncRunner.SyncRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncRequestTest {
    @Test
    fun startsWhenNothingIsQueued() {
        assertEquals(SyncRequest.START_NOW, SyncRequest.forExisting(emptyList(), MANUAL))
    }

    @Test
    fun startsWhenEarlierSyncsHaveEnded() {
        for (state in listOf(SUCCEEDED, FAILED, CANCELLED)) {
            assertEquals(SyncRequest.START_NOW, SyncRequest.forExisting(listOf(manual(state)), MANUAL))
        }
    }

    @Test
    fun replacesASyncThatIsOnlyWaiting() {
        assertEquals(SyncRequest.START_NOW, SyncRequest.forExisting(listOf(manual(ENQUEUED)), MANUAL))
    }

    @Test
    fun replacesAWaitingSyncWithAnotherQueuedBehindIt() {
        assertEquals(SyncRequest.START_NOW, SyncRequest.forExisting(listOf(manual(ENQUEUED), manual(BLOCKED)), MANUAL))
    }

    @Test
    fun queuesOneSyncBehindARunningOne() {
        assertEquals(SyncRequest.AFTER_RUNNING, SyncRequest.forExisting(listOf(manual(RUNNING)), AUTO))
    }

    @Test
    fun addsNothingWhenASyncIsAlreadyQueuedBehindTheRunningOne() {
        assertEquals(SyncRequest.ALREADY_QUEUED, SyncRequest.forExisting(listOf(manual(RUNNING), manual(BLOCKED)), AUTO))
    }

    @Test
    fun queuedAutoSyncDoesNotCoverAManualRequest() {
        val existing = listOf(manual(RUNNING), QueuedSync(BLOCKED, AUTO))

        assertEquals(SyncRequest.AFTER_RUNNING, SyncRequest.forExisting(existing, MANUAL))
    }

    @Test
    fun queuedAutoSyncCoversAnotherAutoRequest() {
        val existing = listOf(manual(RUNNING), QueuedSync(BLOCKED, AUTO))

        assertEquals(SyncRequest.ALREADY_QUEUED, SyncRequest.forExisting(existing, AUTO))
    }

    private fun manual(state: WorkInfo.State) = QueuedSync(state, MANUAL)
}
