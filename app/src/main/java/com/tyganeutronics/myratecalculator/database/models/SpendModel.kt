package com.tyganeutronics.myratecalculator.database.models

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.tyganeutronics.myratecalculator.database.rtdb.WalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object SpendModel {

    /** Spending must land even if the screen that triggered it has gone. */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Takes [credits] out of the wallet.
     *
     * The per-coin loop this used to run is gone: it wrote one row per coin, which was merely
     * wasteful against SQLite and would be a write amplifier against a network store. One call
     * now decrements each grant it draws on by the whole amount taken from it.
     */
    fun consume(context: Context, credits: Long, type: String, description: String) {
        scope.launch {
            WalletRepository.consume(credits, type, description.format(credits))
        }

        val bundle = Bundle()
        bundle.putLong("amount", credits)

        FirebaseAnalytics.getInstance(context).logEvent("spend_coins", bundle)
    }

    /**
     * How many of the last [days] days have a clock-in, counting back from today and stopping at
     * the first gap.
     */
    fun daysStreak(days: Int = 7): List<Boolean> {
        val streak = arrayOfNulls<Boolean>(days)
        streak.fill(false)

        for (i in 0 until days) {
            if (WalletRepository.rewardsOnDay(i).isEmpty()) break
            streak[i] = true
        }

        return streak.toList().filterNotNull()
    }

    /**
     * Shuffles an overdraft off a grant that has gone negative and onto one with room.
     *
     * Repeats because one pass moves what a single donor can cover. Each pass is an atomic pair
     * of transforms, so this settles rather than fighting a second handset doing the same.
     */
    fun normalizeOverdrawnRewards() {
        scope.launch {
            while (WalletRepository.normalizeOverdrawn()) {
                // keep going while there is still an overdraft a donor can absorb
            }
        }
    }
}
