package com.orgzly.android.sync

import android.os.SystemClock
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.db.entity.Repo
import com.orgzly.android.repos.RepoType
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class SyncRunnerTest : OrgzlyTest() {
    @get:Rule
    val retryTestRule = RetryTestRule()

    private lateinit var repo: Repo

    @Before
    override fun setUp() {
        super.setUp()

        repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
    }

    @After
    override fun tearDown() {
        SyncRunner.stopSync()

        super.tearDown()
    }

    @Test
    fun testSyncStartsWhileAnotherIsWaitingToRetry() {
        setupRook("book-a")

        // Stands in for a sync killed with its process, waiting out its retry backoff.
        val waiting = OneTimeWorkRequestBuilder<SyncWorker>().setInitialDelay(1, TimeUnit.HOURS).build()
        workManager().enqueueUniqueWork(SyncRunner.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, waiting).result.get()

        SyncRunner.startSync()

        assertTrue(waitFor(10_000) { dataRepository.getBook("book-a") != null })
    }

    @Test
    fun testSyncRequestedDuringSyncRunsAfterIt() {
        val names = (1..15).map { "book-%02d".format(it) }
        names.forEach { setupRook(it) }

        SyncRunner.startSync()
        assertTrue(waitFor(10_000) { names.any { dataRepository.getBook(it) != null } })
        assertTrue(syncStates().contains(WorkInfo.State.RUNNING))

        setupRook("book-late")
        SyncRunner.startSync()

        assertTrue(waitFor(30_000) { dataRepository.getBook("book-late") != null })
    }

    private fun setupRook(name: String) {
        testUtils.setupRook(repo, "mock://repo-a/$name.org", "* Note in $name", "1520681916000", 1520681916000L)
    }

    private fun workManager() = WorkManager.getInstance(context)

    private fun syncStates() =
        workManager().getWorkInfosForUniqueWork(SyncRunner.UNIQUE_WORK_NAME).get().map { it.state }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) {
            if (condition()) return true
            SystemClock.sleep(100)
        }
        return condition()
    }
}
