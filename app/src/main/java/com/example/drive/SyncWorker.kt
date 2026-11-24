package com.example.drive

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import java.util.concurrent.TimeUnit

/**
 * 1. SyncWorker
 * Android WorkManager CoroutineWorker responsible for:
 * - Immediate & automatic background synchronization after SQLite commits
 * - Network connectivity constraint (NetworkType.CONNECTED)
 * - Exponential backoff retries
 * - Safe continuation of persistent outbox queue after process restart
 */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val UNIQUE_SYNC_WORK_NAME = GoogleDriveMirrorSyncEngine.UNIQUE_MIRROR_SYNC_WORK

        @Volatile
        var testRepositoryFactory: ((Context) -> SyncRepository)? = null

        fun enqueueImmediateSync(context: Context) {
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val request = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    UNIQUE_SYNC_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request
                )
            }
        }

        fun cancelScheduledSync(context: Context) {
            runCatching {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_SYNC_WORK_NAME)
            }
        }
    }

    override suspend fun doWork(): Result {
        val repository = testRepositoryFactory?.invoke(applicationContext)
            ?: DriveMirrorSyncWorker.testEngineFactory?.invoke(applicationContext)?.syncRepository
            ?: SyncRepository(
                context = applicationContext,
                dao = AppDatabase.getInstance(applicationContext).freezerDao()
            )
        val outcome = repository.processSyncQueue(forceRetryFailed = false, ignoreBackoffTimers = true)
        return outcome.fold(
            onSuccess = { batch ->
                when {
                    batch.requiresReAuth ||
                        batch.overallSyncStatus == GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_AUTH -> {
                        // Stop retrying until user re-authenticates Google Drive
                        Result.failure()
                    }
                    batch.overallSyncStatus == GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_NETWORK ||
                        batch.failedCount > 0 ||
                        batch.totalFailedInDb > 0 ||
                        batch.remainingPendingCount > 0 ||
                        batch.totalPendingInDb > 0 -> {
                        if (runAttemptCount < 5) Result.retry() else Result.failure()
                    }
                    else -> Result.success()
                }
            },
            onFailure = { err ->
                if (DriveApiException.isAuthFailure(err)) {
                    Result.failure()
                } else if (runAttemptCount < 5) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        )
    }
}
