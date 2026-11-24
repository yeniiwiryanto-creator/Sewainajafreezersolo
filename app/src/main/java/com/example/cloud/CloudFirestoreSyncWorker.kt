package com.example.cloud

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

/**
 * WorkManager background worker that guarantees offline-to-online Firestore & Cloud Storage
 * synchronization with exponential backoff even after process death or unstable connectivity.
 */
class CloudFirestoreSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val appInitialized = runCatching { FirebaseApp.getApps(applicationContext).isNotEmpty() }
            .getOrDefault(false)
        if (!appInitialized) {
            return Result.success()
        }

        val currentUid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
        val targetUid = inputData.getString(KEY_UID)?.takeIf { it.isNotBlank() } ?: currentUid
        if (targetUid.isNullOrBlank() || (currentUid != null && currentUid != targetUid)) {
            return Result.success()
        }

        val dao = AppDatabase.getInstance(applicationContext).freezerDao()
        val cloudRepo = runCatching { CloudFirestoreRepository(applicationContext) }.getOrNull()
            ?: return Result.retry()
        val photoMgr = CloudStoragePhotoManager(applicationContext, dao)
        val syncManager = CloudSyncAndMigrationManager(
            context = applicationContext,
            dao = dao,
            cloudRepo = cloudRepo,
            photoManager = photoMgr
        )

        return try {
            val allDrained = syncManager.drainPendingQueue(targetUid, ignoreBackoff = false)
            if (allDrained) {
                Result.success()
            } else if (runAttemptCount < MAX_WORK_RETRIES) {
                Result.retry()
            } else {
                Log.w(TAG, "CloudFirestoreSyncWorker reached max attempts ($MAX_WORK_RETRIES); keeping items queued for next trigger.")
                Result.failure()
            }
        } catch (e: Exception) {
            Log.w(TAG, "CloudFirestoreSyncWorker transient failure (attempt $runAttemptCount): ${e.message}")
            if (runAttemptCount < MAX_WORK_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "CloudFirestoreSyncWkr"
        const val UNIQUE_WORK_NAME = "sewain_cloud_firestore_sync_work"
        const val KEY_UID = "target_uid"
        private const val MAX_WORK_RETRIES = 6

        fun enqueueSync(
            context: Context,
            uid: String,
            initialDelaySeconds: Long = 0L
        ) {
            if (uid.isBlank()) return
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val input = Data.Builder()
                    .putString(KEY_UID, uid)
                    .build()

                val builder = OneTimeWorkRequestBuilder<CloudFirestoreSyncWorker>()
                    .setConstraints(constraints)
                    .setInputData(input)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        15L,
                        TimeUnit.SECONDS
                    )
                if (initialDelaySeconds > 0L) {
                    builder.setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
                }

                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniqueWork(
                        UNIQUE_WORK_NAME,
                        ExistingWorkPolicy.REPLACE,
                        builder.build()
                    )
            }.onFailure { err ->
                Log.d(TAG, "WorkManager scheduling skipped (test or uninitialized environment): ${err.message}")
            }
        }
    }
}
