package com.bookbotwatch.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bookbotwatch.core.PriceChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Periodicka kontrola na pozadi. Prezije aj restart telefonu. */
class CheckWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val report = PriceChecker.run(applicationContext)
        val foundSomething = report.drops.isNotEmpty() ||
            report.stockAlerts.isNotEmpty() ||
            report.restorioAlerts.isNotEmpty()
        if (report.error != null && !foundSomething) Result.retry() else Result.success()
    }

    companion object {
        private const val NAME = "bookbot_periodic_check"

        fun schedule(context: Context, intervalHours: Int) {
            val request = PeriodicWorkRequestBuilder<CheckWorker>(
                intervalHours.toLong().coerceAtLeast(1L), TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
