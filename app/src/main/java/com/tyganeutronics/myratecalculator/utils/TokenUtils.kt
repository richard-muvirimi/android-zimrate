package com.tyganeutronics.myratecalculator.utils

import android.content.Context
import android.os.Build
import com.tyganeutronics.myratecalculator.AppZimRate
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

object TokenUtils {

    fun canLoadAds(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    }

    fun canShowAds(context: Context): Boolean {
        return hasNoPaidTokens() && installOlderThan(context)
    }

    fun hasNoPaidTokens(): Boolean {
        return AppZimRate.database.rewards().tokenTypeBalance() <= 0
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

    fun hasLowTokenBalance(): Boolean {
        return AppZimRate.database.rewards().tokenBalance() < 5
    }

}