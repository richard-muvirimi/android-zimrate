package com.tyganeutronics.myratecalculator.database.models

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.contract.RewardContract
import com.tyganeutronics.myratecalculator.database.rtdb.Reward
import com.tyganeutronics.myratecalculator.database.rtdb.WalletRepository
import com.tyganeutronics.myratecalculator.utils.DateUtils
import com.tyganeutronics.myratecalculator.utils.contracts.RemoteConfigContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

object RewardModel {

    /** A grant must land even if the screen or advert that earned it has gone. */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun dayClockInReward(streak: Int): Long {
        val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

        return JSONArray(remoteConfig.getString(RemoteConfigContract.REWARD_CLOCK_IN))
            .optLong(streak, 0)
    }

    fun maybeRewardClockIn(context: Context, days: Int = 7) {
        scope.launch {
            WalletRepository.awaitLoaded()

            if (WalletRepository.rewardsOnDay(0).isNotEmpty()) return@launch

            val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

            var streak = 0
            for (i in 1..days) {
                if (WalletRepository.rewardsOnDay(i).isEmpty()) break
                streak++
            }

            val latestDate = WalletRepository.latestActivity()

            val amount: Long
            val expiresAt: Instant

            if (latestDate.isBefore(DateUtils.systemDateTime().toInstant())) {
                amount = dayClockInReward(streak.coerceAtMost(days - 1))
                expiresAt = expiryFrom(
                    LocalDateTime.now(),
                    remoteConfig.getLong(RemoteConfigContract.REWARD_CLOCK_IN_DAYS),
                )
            } else {
                // If date tempered with, lock clock-in for that day for 6 hours
                amount = 0
                expiresAt = LocalDateTime.ofInstant(latestDate, ZoneOffset.UTC)
                    .plusDays(1)
                    .truncatedTo(ChronoUnit.DAYS)
                    .minusSeconds(1)
                    .toInstant(ZoneOffset.UTC)
            }

            val description = context.getString(R.string.rewards_award_daily_clock_in, amount)

            award(amount, RewardContract.TYPES.CLOCK_IN, description, expiresAt)

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, description, Toast.LENGTH_LONG).show()
            }

            val bundle = Bundle()
            bundle.putInt("day", streak)

            FirebaseAnalytics.getInstance(context).logEvent("reward_clock_in", bundle)
        }
    }

    fun rewardStarterPack(context: Context? = null) {
        scope.launch {
            val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig
            val amount = remoteConfig.getLong(RemoteConfigContract.REWARD_STARTER_PACK)

            val description = if (context !== null) {
                context.getString(R.string.rewards_awarded_starter_pack, amount)
            } else {
                "Starter pack reward. Awarded $amount Coins."
            }

            award(
                amount,
                RewardContract.TYPES.STARTER_PACK,
                description,
                expiry(RemoteConfigContract.REWARD_STARTER_PACK_DAYS),
            )
        }

        if (context !== null) {
            FirebaseAnalytics.getInstance(context).logEvent("reward_starter_pack", null)
        }
    }

    fun rewardBannerClick(context: Context, amount: Double) {
        advertReward(
            context,
            RemoteConfigContract.REWARD_BANNER_CLICK,
            RemoteConfigContract.REWARD_BANNER_DAYS,
            amount,
            RewardContract.TYPES.BANNER_CLICK,
            R.string.rewards_award_advert_click,
            "reward_banner_click",
        )
    }

    fun rewardInterstitialClick(context: Context, amount: Double) {
        advertReward(
            context,
            RemoteConfigContract.REWARD_INTERSTITIAL_CLICK,
            RemoteConfigContract.REWARD_INTERSTITIAL_DAYS,
            amount,
            RewardContract.TYPES.BANNER_CLICK,
            R.string.rewards_award_advert_click,
            "reward_interstitial_click",
        )
    }

    fun rewardWatchAdvertClick(context: Context, amount: Double) {
        advertReward(
            context,
            RemoteConfigContract.REWARD_WATCH_ADVERT_CLICK,
            RemoteConfigContract.REWARD_WATCH_ADVERT_DAYS,
            amount,
            RewardContract.TYPES.BANNER_CLICK,
            R.string.rewards_award_advert_click,
            "reward_video_click",
        )
    }

    fun rewardWatchVideoAdvert(context: Context, amount: Double) {
        advertReward(
            context,
            RemoteConfigContract.REWARD_WATCH_ADVERT,
            RemoteConfigContract.REWARD_WATCH_ADVERT_DAYS,
            amount,
            RewardContract.TYPES.WATCH_ADVERT,
            R.string.rewards_award_watch_advert,
            "reward_watch_advert",
        )
    }

    fun rewardWatchInterstitialAdvert(context: Context, amount: Double) {
        advertReward(
            context,
            RemoteConfigContract.REWARD_INTERSTITIAL,
            RemoteConfigContract.REWARD_INTERSTITIAL_DAYS,
            amount,
            RewardContract.TYPES.WATCH_ADVERT,
            R.string.rewards_award_watch_advert,
            "reward_watch_interstitial_advert",
        )
    }

    /**
     * Every advert grant has the same shape — a configured base plus a share of the revenue the
     * impression earned — so they share one body rather than six copies of it.
     */
    private fun advertReward(
        context: Context,
        amountKey: String,
        daysKey: String,
        revenue: Double,
        type: String,
        descriptionRes: Int,
        event: String,
    ) {
        scope.launch {
            val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

            val amount = remoteConfig.getLong(amountKey)
                .plus(revenue.times(100).toLong())

            award(
                amount,
                type,
                context.getString(descriptionRes, amount),
                expiry(daysKey),
            )
        }

        FirebaseAnalytics.getInstance(context).logEvent(event, null)
    }

    /**
     * Credits a coin purchase, keyed on the Play purchase token so the same purchase can only
     * ever grant once. [com.tyganeutronics.myratecalculator.fragments.rewards.FragmentPurchase]
     * can reach this more than once for one purchase — onResume re-queries, and a consume that
     * fails is handed back on the next query — so the key is what makes retrying safe rather
     * than expensive.
     *
     * Returns true when this call created the grant.
     */
    suspend fun rewardPurchaseCoins(
        context: Context,
        amount: Long,
        purchaseToken: String,
    ): Boolean {
        val reward = Reward(
            key = "",
            amount = amount,
            balance = amount,
            type = RewardContract.TYPES.PURCHASE,
            description = context.getString(R.string.rewards_award_coins_purchased, amount),
            expiresAt = expiry(RemoteConfigContract.REWARD_PURCHASE_DAYS),
            createdAt = Instant.now(),
        )

        val credited = WalletRepository.grantOnce(purchaseKey(purchaseToken), reward)

        if (credited) {
            FirebaseAnalytics.getInstance(context).logEvent("reward_purchase_coins", null)
        }

        return credited
    }

    private suspend fun award(
        amount: Long,
        type: String,
        description: String,
        expiresAt: Instant,
    ) {
        WalletRepository.grant(
            Reward(
                key = "",
                amount = amount,
                balance = amount,
                type = type,
                description = description,
                expiresAt = expiresAt,
                createdAt = Instant.now(),
            )
        )
    }

    private fun expiry(daysKey: String): Instant =
        expiryFrom(LocalDateTime.now(), Firebase.remoteConfig.getLong(daysKey))

    /** End of the last day the grant is good for, matching what the Room rows carried. */
    private fun expiryFrom(from: LocalDateTime, days: Long): Instant =
        from.plusDays(days)
            .plusDays(1)
            .truncatedTo(ChronoUnit.DAYS)
            .minusSeconds(1)
            .toInstant(ZoneOffset.UTC)

    /**
     * A node key derived from the purchase token. Hex, because a Realtime Database key cannot
     * contain `.` `$` `#` `[` `]` or `/` and a raw Play token can.
     */
    private fun purchaseKey(purchaseToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(purchaseToken.toByteArray(Charsets.UTF_8))

        return "p_" + digest.take(16).joinToString("") { "%02x".format(it) }
    }
}
