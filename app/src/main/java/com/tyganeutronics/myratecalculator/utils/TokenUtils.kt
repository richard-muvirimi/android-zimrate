package com.tyganeutronics.myratecalculator.utils

import android.content.Context
import android.os.Build
import com.tyganeutronics.myratecalculator.database.contract.RewardContract
import com.tyganeutronics.myratecalculator.database.rtdb.WalletRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

object TokenUtils {

    fun canLoadAds(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    }

    /**
     * Total unexpired balance, or null while it is not yet known.
     *
     * Room answers straight away so this never returns null today. It is nullable because the
     * listener that replaces it will return null until its first callback, and every caller
     * needs to already have an answer for that — a balance read too early is indistinguishable
     * from an empty wallet, and silently treating one as the other is how a paying user gets
     * shown adverts.
     */
    fun balance(): Long? = WalletRepository.balance()

    /** Unexpired balance from purchases alone, or null while it is not yet known. */
    fun paidBalance(): Long? = WalletRepository.balanceOfType(RewardContract.TYPES.PURCHASE)

    fun canShowAds(context: Context): Boolean {
        return hasNoPaidTokens() && installOlderThan(context)
    }

    /**
     * Unknown balance counts as "has paid" — withholding adverts from someone who turns out to
     * be on the free tier costs an impression, showing them to someone who paid costs trust.
     */
    fun hasNoPaidTokens(): Boolean {
        return (paidBalance() ?: 1L) <= 0
    }

    fun installOlderThan(context: Context, days: Long = 3): Boolean {
        val installDate = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(
                context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
            ), ZoneOffset.UTC
        )

        return LocalDateTime.now()
            .minusDays(days)
            .isAfter(installDate)
    }

    /** Unknown balance is not low — nobody should be nagged to top up on a guess. */
    fun hasLowTokenBalance(): Boolean {
        val balance = balance() ?: return false
        return balance < 5
    }

}
