package com.tyganeutronics.myratecalculator.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tyganeutronics.myratecalculator.utils.traits.getBooleanPref
import com.tyganeutronics.myratecalculator.utils.traits.getStringPref
import java.util.concurrent.TimeUnit

/**
 * Owns the background refresh schedule. `check_update` turns it on or off and
 * `update_interval` sets the period, so the settings screen drives it directly.
 */
object RatesRefreshScheduler {

    private const val WORK_NAME = "rates-refresh"

    /** Brings the schedule in line with the current settings. Safe to call repeatedly. */
    fun sync(context: Context) {
        if (!context.getBooleanPref("check_update", true)) {
            cancel(context)
            return
        }

        val hours = context.getStringPref("update_interval", "24").toLongOrNull() ?: 24L

        val request = PeriodicWorkRequestBuilder<RatesRefreshWorker>(hours, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        // UPDATE keeps the existing schedule running when only the period changed.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
