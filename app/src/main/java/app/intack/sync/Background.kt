package app.intack.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.intack.IntackApp
import app.intack.data.Diagnostics
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/** Best-effort catch-up every 15 minutes while online: joins every list's swarm for a short window. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Diagnostics.count("Background: 15-minute check-ins")
        val sync = (applicationContext as IntackApp).sync
        sync.acquire()
        try {
            delay(SYNC_WINDOW_MS)
        } finally {
            sync.release()
        }
        return Result.success()
    }

    companion object {
        private const val SYNC_WINDOW_MS = 60_000L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
