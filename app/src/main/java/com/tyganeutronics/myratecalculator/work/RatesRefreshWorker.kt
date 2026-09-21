package com.tyganeutronics.myratecalculator.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.contract.PurchasesContract
import com.tyganeutronics.myratecalculator.database.models.RatesModel
import com.tyganeutronics.myratecalculator.database.models.SpendModel
import com.tyganeutronics.myratecalculator.database.rtdb.CurrencyRepository
import com.tyganeutronics.myratecalculator.database.rtdb.WalletRepository
import com.tyganeutronics.myratecalculator.utils.RatesNotifier
import com.tyganeutronics.myratecalculator.utils.TokenUtils
import com.tyganeutronics.myratecalculator.utils.contracts.CurrencyContract
import com.tyganeutronics.myratecalculator.utils.traits.getBooleanPref
import com.tyganeutronics.myratecalculator.utils.traits.putLongPref
import kotlinx.coroutines.withTimeoutOrNull

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

        // Almost always this process was started by WorkManager for this run alone — the whole
        // point of the schedule is to refresh while the app is closed. AppZimRate.onCreate has
        // therefore only just kicked sign in off, and neither listener has reported yet, so
        // every read below would come back unknown and the run would end having done nothing.
        //
        // The wait is what moving the store to RTDB made necessary: the balance used to be a
        // synchronous Room read, so there was never anything to wait on. Awaiting the wallet
        // also settles the currency side, since both are attached together once there is a uid.
        val loaded = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
            WalletRepository.awaitLoaded()
            CurrencyRepository.awaitLoaded()
        }

        // Retry rather than success. A run that never saw the wallet decided nothing, and
        // calling it done would spend the whole period on it.
        if (loaded == null) return Result.retry()

        // An unknown balance is not an empty one. Cancelling the schedule on a read that simply
        // had not arrived would silently stop refreshing for someone with coins to spend, and
        // they would only find out by noticing stale rates. Loaded above, so this is only still
        // null if the account went away underneath the run.
        val balance = TokenUtils.balance() ?: return Result.retry()

        if (balance <= 0) {
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

    companion object {

        /**
         * How long to wait for sign in and the first callback from each listener.
         *
         * Generous against the ten minutes a worker is given, because nothing this worker does
         * is worth anything without them, and still short enough to leave the fetch room.
         */
        private const val LOAD_TIMEOUT_MS = 60_000L
    }
}
