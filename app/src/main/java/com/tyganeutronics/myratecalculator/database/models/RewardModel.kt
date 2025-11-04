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
import com.tyganeutronics.myratecalculator.AppZimRate
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.contract.RewardContract
import com.tyganeutronics.myratecalculator.database.entities.RewardEntity
import com.tyganeutronics.myratecalculator.utils.DateUtils
import com.tyganeutronics.myratecalculator.utils.contracts.RemoteConfigContract
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

object RewardModel {

    fun dayClockInReward(streak: Int): Long {
        val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

        return JSONArray(remoteConfig.getString(RemoteConfigContract.REWARD_CLOCK_IN))
            .optLong(streak, 0)
    }

    fun maybeRewardClockIn(context: Context, days: Int = 7) {
        AppZimRate.database.let {
            it.transactionExecutor.execute {

                if (it.rewards().getDayRewards().isEmpty()) {

                    val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

                    var streak = 0
                    for (i in 1..days) {
                        if (it.rewards().getDayRewards(i).isEmpty()) {
                            break
                        }
                        streak++
                    }

                    val latestDate = Instant.ofEpochSecond(it.rewards().getLatestDate())

                    val reward = RewardEntity()
                    if (latestDate.isBefore(DateUtils.systemDateTime().toInstant())) {

                        reward.amount = dayClockInReward(streak.coerceAtMost(days - 1))
                        reward.expiresAt = LocalDateTime.now()
                            .plusDays(remoteConfig.getLong(RemoteConfigContract.REWARD_CLOCK_IN_DAYS))
                            .plusDays(1)
                            .truncatedTo(ChronoUnit.DAYS)
                            .minusSeconds(1)
                            .toInstant(ZoneOffset.UTC)
                    } else {
                        // If date tempered with, lock clock-in for that day for 6 hours
                        reward.amount = 0
                        reward.expiresAt = LocalDateTime.ofInstant(
                            latestDate,
                            ZoneOffset.UTC
                        )
                            .plusDays(1)
                            .truncatedTo(ChronoUnit.DAYS)
                            .minusSeconds(1)
                            .toInstant(ZoneOffset.UTC)
                    }
                    reward.balance = reward.amount
                    reward.type = RewardContract.TYPES.CLOCK_IN
                    reward.description = context
                        .getString(R.string.rewards_award_daily_clock_in, reward.amount)

                    reward.save()

                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            context,
                            context.getString(R.string.rewards_award_daily_clock_in, reward.amount),
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    val bundle = Bundle()
                    bundle.putInt("day", streak)

                    FirebaseAnalytics.getInstance(context).logEvent("reward_clock_in", bundle)

                }
            }
        }
    }

    fun rewardStarterPack(context: Context? = null) {
        AppZimRate.database.let {
            it.transactionExecutor.execute {

                val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

                val reward = RewardEntity()
                reward.amount = remoteConfig.getLong(RemoteConfigContract.REWARD_STARTER_PACK)
                reward.balance = reward.amount
                reward.type = RewardContract.TYPES.STARTER_PACK

                reward.description = if (context !== null) {
                    context.getString(R.string.rewards_awarded_starter_pack, reward.amount)
                } else {
                    "Starter pack reward. Awarded ${reward.amount} Coins."
                }

                reward.expiresAt = LocalDateTime.now()
                    .plusDays(remoteConfig.getLong(RemoteConfigContract.REWARD_STARTER_PACK_DAYS))
                    .plusDays(1)
                    .truncatedTo(ChronoUnit.DAYS)
                    .minusSeconds(1)
                    .toInstant(ZoneOffset.UTC)
                reward.save()

            }
        }

        if (context !== null) {
            FirebaseAnalytics.getInstance(context).logEvent("reward_starter_pack", null)
        }
    }

    fun rewardBannerClick(context: Context, amount: Double) {
        AppZimRate.database.let {
            it.transactionExecutor.execute {

                val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

                val reward = RewardEntity()
                reward.amount = remoteConfig
                    .getLong(RemoteConfigContract.REWARD_BANNER_CLICK)
                    .plus(amount.times(100).toLong())

                reward.balance = reward.amount
                reward.type = RewardContract.TYPES.BANNER_CLICK
                reward.description = context
                    .getString(R.string.rewards_award_advert_click, reward.amount)
                reward.expiresAt = LocalDateTime.now()
                    .plusDays(remoteConfig.getLong(RemoteConfigContract.REWARD_BANNER_DAYS))
                    .plusDays(1)
                    .truncatedTo(ChronoUnit.DAYS)
                    .minusSeconds(1)
                    .toInstant(ZoneOffset.UTC)
                reward.save()

            }
        }

        FirebaseAnalytics.getInstance(context).logEvent("reward_banner_click", null)
    }

    fun rewardInterstitialClick(context: Context, amount: Double) {
        AppZimRate.database.let {
            it.transactionExecutor.execute {

                val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

                val reward = RewardEntity()
                reward.amount = remoteConfig
                    .getLong(RemoteConfigContract.REWARD_INTERSTITIAL_CLICK)
                    .plus(amount.times(100).toLong())

                reward.balance = reward.amount
                reward.type = RewardContract.TYPES.BANNER_CLICK
                reward.description = context
                    .getString(R.string.rewards_award_advert_click, reward.amount)
                reward.expiresAt = LocalDateTime.now()
                    .plusDays(remoteConfig.getLong(RemoteConfigContract.REWARD_INTERSTITIAL_DAYS))
                    .plusDays(1)
                    .truncatedTo(ChronoUnit.DAYS)
                    .minusSeconds(1)
                    .toInstant(ZoneOffset.UTC)
                reward.save()

            }
        }

        FirebaseAnalytics.getInstance(context).logEvent("reward_interstitial_click", null)
    }

    fun rewardWatchAdvertClick(context: Context, amount: Double) {
        AppZimRate.database.let {
            it.transactionExecutor.execute {

                val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

                val reward = RewardEntity()
                reward.amount = remoteConfig
                    .getLong(RemoteConfigContract.REWARD_WATCH_ADVERT_CLICK)
                    .plus(amount.times(100).toLong())

                reward.balance = reward.amount
                reward.type = RewardContract.TYPES.BANNER_CLICK
                reward.description = context
                    .getString(R.string.rewards_award_advert_click, reward.amount)
                reward.expiresAt = LocalDateTime.now()
                    .plusDays(remoteConfig.getLong(RemoteConfigContract.REWARD_WATCH_ADVERT_DAYS))
                    .plusDays(1)
                    .truncatedTo(ChronoUnit.DAYS)
                    .minusSeconds(1)
                    .toInstant(ZoneOffset.UTC)
                reward.save()

            }
        }

        FirebaseAnalytics.getInstance(context).logEvent("reward_video_click", null)
    }

    fun rewardWatchVideoAdvert(context: Context, amount: Double) {
        AppZimRate.database.let {
            it.transactionExecutor.execute {

                val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

                val reward = RewardEntity()
                reward.amount = remoteConfig
                    .getLong(RemoteConfigContract.REWARD_WATCH_ADVERT)
                    .plus(amount.times(100).toLong())

                reward.balance = reward.amount
                reward.type = RewardContract.TYPES.WATCH_ADVERT
                reward.description = context
                    .getString(R.string.rewards_award_watch_advert, reward.amount)
                reward.expiresAt = LocalDateTime.now()
                    .plusDays(remoteConfig.getLong(RemoteConfigContract.REWARD_WATCH_ADVERT_DAYS))
                    .plusDays(1)
                    .truncatedTo(ChronoUnit.DAYS)
                    .minusSeconds(1)
                    .toInstant(ZoneOffset.UTC)
                reward.save()

            }
        }

        FirebaseAnalytics.getInstance(context).logEvent("reward_watch_advert", null)
    }

    fun rewardWatchInterstitialAdvert(context: Context, amount: Double) {
        AppZimRate.database.let {
            it.transactionExecutor.execute {

                val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

                val reward = RewardEntity()
                reward.amount = remoteConfig
                    .getLong(RemoteConfigContract.REWARD_INTERSTITIAL)
                    .plus(amount.times(100).toLong())

                reward.balance = reward.amount
                reward.type = RewardContract.TYPES.WATCH_ADVERT
                reward.description = context
                    .getString(R.string.rewards_award_watch_advert, reward.amount)
                reward.expiresAt = LocalDateTime.now()
                    .plusDays(remoteConfig.getLong(RemoteConfigContract.REWARD_INTERSTITIAL_DAYS))
                    .plusDays(1)
                    .truncatedTo(ChronoUnit.DAYS)
                    .minusSeconds(1)
                    .toInstant(ZoneOffset.UTC)
                reward.save()

            }
        }

        FirebaseAnalytics.getInstance(context).logEvent("reward_watch_interstitial_advert", null)
    }


    fun rewardPurchaseCoins(context: Context, amount: Long) {
        AppZimRate.database.let {
            it.transactionExecutor.execute {

                val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

                val reward = RewardEntity()
                reward.amount = amount
                reward.balance = reward.amount
                reward.type = RewardContract.TYPES.PURCHASE
                reward.description = context
                    .getString(R.string.rewards_award_coins_purchased, reward.amount)
                reward.expiresAt = LocalDateTime.now()
                    .plusDays(remoteConfig.getLong(RemoteConfigContract.REWARD_PURCHASE_DAYS))
                    .plusDays(1)
                    .truncatedTo(ChronoUnit.DAYS)
                    .minusSeconds(1)
                    .toInstant(ZoneOffset.UTC)
                reward.save()

            }
        }

        FirebaseAnalytics.getInstance(context).logEvent("reward_purchase_coins", null)
    }

}