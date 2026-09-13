package com.tyganeutronics.myratecalculator.work

import android.content.Context
import android.util.Log
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

    private const val TAG = "RatesRefreshScheduler"

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

        guarded {
            // UPDATE keeps the existing schedule running when only the period changed.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }

    fun cancel(context: Context) {
        guarded { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }
    }

    /**
     * Where WorkManager is allowed to be unavailable.
     *
     * This is the first call that touches it now that the startup initializer is gone, so this is
     * where its initialisation actually happens — and on a device whose framework does not match
     * the SDK_INT it claims, that initialisation throws [LinkageError] rather than an exception.
     * Caught on purpose and by that name: background refresh is a convenience, and losing it is
     * not worth refusing to run.
     */
    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: LinkageError) {
            Log.w(TAG, "WorkManager is unusable here; background refresh is off", e)
        } catch (e: Exception) {
            Log.w(TAG, "Could not update the refresh schedule", e)
        }
    }
}
