package com.orgzly.android.sync

import android.content.Intent
import androidx.core.content.ContextCompat.startActivity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.orgzly.BuildConfig
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.ui.repos.ReposActivity
import com.orgzly.android.ui.showSnackbar
import com.orgzly.android.util.LogUtils
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object SyncRunner {
    const val IS_AUTO_SYNC = "auto-sync"

    private val TAG: String = SyncRunner::class.java.name

    internal const val UNIQUE_WORK_NAME = "sync"

    private const val AUTO_SYNC_TAG = "auto-sync"

    // Serialized so that two triggers cannot both see a running sync and both append,
    // and so that a stop cannot be overtaken by a start requested before it.
    private val enqueueExecutor = Executors.newSingleThreadExecutor()

    @JvmStatic
    fun startAuto() {
        startSync(true)
    }

    @JvmStatic
    @JvmOverloads
    fun startSync(autoSync: Boolean = false) {
        val workManager = WorkManager.getInstance(App.getAppContext())


        // There is a bug in WorkManager, documented here https://issuetracker.google.com/issues/115575872,
        // due to which an ACTION_UPDATE intent is sent to AppWidgetProviders when the last worker finishes.
        // This leads to an ugly flickering and resetting of the scroll position when using the Orgzly widget,
        // especially in th case of marking a task done, while having auto update activated.
        // The provided solution is to schedule a worker in the "infinite" future, thus preventing the case
        // of the last worker finishing.
        val infiniteScheduledWorkerToPreventWidgetUpdate = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(10 * 365, TimeUnit.DAYS)
            .build()

        workManager.beginUniqueWork(
            "infiniteScheduledWorkerToPreventWidgetUpdate",
            ExistingWorkPolicy.REPLACE,
            infiniteScheduledWorkerToPreventWidgetUpdate
        ).enqueue()

        // Enqueue the sync worker
        val syncWorker = OneTimeWorkRequestBuilder<SyncWorker>()
            // On Android >= 12 notification from overridden getForegroundInfo might not be shown
            // Sync-in-progress notification cannot be canceled if app is killed by the system,
            // when handling notification manually from the worker.
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(IS_AUTO_SYNC to autoSync))
            .apply { if (autoSync) addTag(AUTO_SYNC_TAG) }
            .build()

        val origin = if (autoSync) SyncOrigin.AUTO else SyncOrigin.MANUAL

        enqueueExecutor.execute {
            val existing = workManager.getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).get().map {
                QueuedSync(it.state, if (AUTO_SYNC_TAG in it.tags) SyncOrigin.AUTO else SyncOrigin.MANUAL)
            }
            val request = SyncRequest.forExisting(existing, origin)

            if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, existing, origin, request)

            request.policy?.let { policy ->
                workManager.beginUniqueWork(UNIQUE_WORK_NAME, policy, syncWorker).enqueue()
            }
        }
    }

    internal enum class SyncOrigin {
        MANUAL,
        AUTO;

        // An auto sync stops early for repositories without auto-sync support, so it cannot stand in for a manual one.
        fun covers(request: SyncOrigin) = this == MANUAL || request == AUTO
    }

    internal data class QueuedSync(val state: WorkInfo.State, val origin: SyncOrigin)

    /**
     * How a new sync request joins the syncs already queued.
     */
    internal enum class SyncRequest(val policy: ExistingWorkPolicy?) {
        /** Nothing runs. A waiting sync is usually a killed one sitting out its retry backoff. */
        START_NOW(ExistingWorkPolicy.REPLACE),

        /** A sync runs, and changes made since it started need one more. Skipped if the running one fails. */
        AFTER_RUNNING(ExistingWorkPolicy.APPEND_OR_REPLACE),

        /** A sync runs, and one that covers this request is already queued behind it. */
        ALREADY_QUEUED(null);

        companion object {
            fun forExisting(existing: Collection<QueuedSync>, origin: SyncOrigin): SyncRequest = when {
                existing.none { it.state == WorkInfo.State.RUNNING } -> START_NOW
                existing.any { it.state == WorkInfo.State.BLOCKED && it.origin.covers(origin) } -> ALREADY_QUEUED
                else -> AFTER_RUNNING
            }
        }
    }

    @JvmStatic
    fun showSyncFailedSnackBar(activity: FragmentActivity, state: SyncState) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, activity, state)

        val msg = state.getDescription(activity)

        activity.showSnackbar(msg, R.string.repositories) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setClass(activity, ReposActivity::class.java)
            }
            startActivity(activity, intent, null)
        }
    }

    @JvmStatic
    fun stopSync() {
        val workManager = WorkManager.getInstance(App.getAppContext())
        enqueueExecutor.execute {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }

    @JvmStatic
    fun onStateChange(tag: String): LiveData<SyncState?> {
        return onAllWorkInfo().map { workInfoList ->
            syncStateFromWorkInfoList(workInfoList).also { state ->
                logStateChange(tag, state, workInfoList)
            }
        }

//        return MediatorLiveData<SyncState>().apply {
//            addSource(onInitWorkInfo()) {
//                value = state
//            }
//
//            addSource(onMainWorkInfo()) {
//                value = state
//            }
//        }
    }

    private fun logStateChange(tag: String, state: SyncState?, workInfoList: List<WorkInfo>?) {
        if (BuildConfig.LOG_DEBUG) {
            // LogUtils.d(TAG, "-> ($tag) Workers changed state to $state <- $workInfoList")
            LogUtils.d(
                TAG,
                "-> ($tag) Workers changed state to $state <- ${workInfoList?.map { it.state }}"
            )
        }
    }

    private fun onAllWorkInfo(): LiveData<List<WorkInfo>> {
        return WorkManager.getInstance(App.getAppContext())
            .getWorkInfosForUniqueWorkLiveData(UNIQUE_WORK_NAME)
    }

    internal fun syncStateFromWorkInfoList(workInfoList: List<WorkInfo>): SyncState? {
        // A sync queued behind a running one stays BLOCKED until that one ends.
        val active = workInfoList.firstOrNull { it.state == WorkInfo.State.RUNNING }
            ?: workInfoList.firstOrNull { it.state == WorkInfo.State.ENQUEUED }

        if (active != null) {
            return SyncState.fromData(active.progress)
                ?: SyncState.getInstance(SyncState.Type.STARTING)
        }

        if (workInfoList.any { it.state == WorkInfo.State.CANCELLED }) {
            return SyncState.getInstance(SyncState.Type.CANCELED)
        }

        // A follow-up fails with no output when the sync before it fails.
        val finished = workInfoList.mapNotNull { SyncState.fromData(it.outputData) }

        return finished.firstOrNull { it.isFailure() } ?: finished.firstOrNull()
    }
}