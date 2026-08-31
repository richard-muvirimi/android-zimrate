package com.tyganeutronics.myratecalculator.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tyganeutronics.myratecalculator.AppZimRate
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.contract.PurchasesContract
import com.tyganeutronics.myratecalculator.database.models.RatesModel
import com.tyganeutronics.myratecalculator.database.models.SpendModel
import com.tyganeutronics.myratecalculator.utils.RatesNotifier
import com.tyganeutronics.myratecalculator.utils.contracts.CurrencyContract
import com.tyganeutronics.myratecalculator.utils.traits.getBooleanPref
import com.tyganeutronics.myratecalculator.utils.traits.putLongPref

/**
 * Periodic refresh that keeps the home screen widgets and the paired watch current between
 * app opens. Each run costs one coin, the same as a manual refresh — when the balance is
 * empty the user is told and the schedule stops until they top up.
 *
 * Writes go through [RatesModel] rather than the view model, so a rate the user typed on the
 * rates screen is never replaced underneath them.
 */
class RatesRefreshWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        if (!context.getBooleanPref("check_update", true)) {
            RatesRefreshScheduler.cancel(context)
            return Result.success()
        }

        if (AppZimRate.database.rewards().tokenBalance() <= 0) {
            RatesNotifier.notifyCoinsExhausted(context)
            RatesRefreshScheduler.cancel(context)
            return Result.success()
        }

        return try {
            val rates = RatesModel.fetch(RatesModel.preferred(context))

            // Nothing came back — do not charge a coin for it, just try again next period.
            if (rates.isEmpty()) return Result.success()

            RatesModel.save(context, rates)
            context.putLongPref(CurrencyContract.LAST_CHECK, System.currentTimeMillis())

            SpendModel.consume(
                context,
                1,
                PurchasesContract.TYPES.DATA_FETCH,
                context.getString(R.string.rewards_spend_data_fetch),
            )

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
