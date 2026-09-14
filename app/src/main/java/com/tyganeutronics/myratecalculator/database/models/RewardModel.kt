package com.tyganeutronics.myratecalculator.database.models

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.appcheck.appCheck
import com.google.firebase.auth.auth
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.contract.RewardContract
import com.tyganeutronics.myratecalculator.database.rtdb.Reward
import com.tyganeutronics.myratecalculator.database.rtdb.WalletRepository
import com.tyganeutronics.myratecalculator.utils.DateUtils
import com.tyganeutronics.myratecalculator.utils.contracts.ApiContract
import com.tyganeutronics.myratecalculator.utils.contracts.RemoteConfigContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
     * What the server made of a purchase, and with it whether the purchase may be consumed.
     *
     * Consuming destroys the entitlement permanently, so it is only ever right once the coins
     * are known to be in the wallet — hence the split between an outright refusal and a failure
     * that may yet succeed.
     */
    sealed interface PurchaseOutcome {
        /** Coins are in the wallet. Safe to consume. */
        data class Credited(val coins: Long) : PurchaseOutcome

        /** A previous attempt already credited this token. Safe to consume. */
        data object AlreadyCredited : PurchaseOutcome

        /** Nothing was credited. Do not consume — Play hands the purchase back to try again. */
        data class Failed(val reason: String?) : PurchaseOutcome
    }

    /**
     * Asks the server to credit a coin purchase.
     *
     * The grant used to be written here, by this handset, naming its own amount — which meant
     * anyone able to reach the database with their own ID token could mint coins without paying
     * for them. The purchase token now goes to the server, which checks it with Google and writes
     * the row with the Admin SDK. The amount comes from the server's catalogue, not from
     * [BillingContract]; the copy here only labels the buttons.
     *
     * [com.tyganeutronics.myratecalculator.fragments.rewards.FragmentPurchase] can reach this
     * more than once for one purchase — onResume re-queries, and a consume that fails is handed
     * back on the next query. The server keys the grant on the purchase token, so a repeat is a
     * no-op there rather than a second grant.
     *
     * Requires connectivity, which costs nothing: Play Billing needed it to reach this point
     * anyway. Daily and advert grants are still written straight to the wallet and still work
     * with no network at all.
     */
    suspend fun rewardPurchaseCoins(
        context: Context,
        amount: Long,
        purchaseToken: String,
        productId: String,
    ): PurchaseOutcome {
        val user = Firebase.auth.currentUser
            ?: return PurchaseOutcome.Failed("Not signed in")

        val outcome = try {
            val idToken = user.getIdToken(false).await().token
                ?: error("No ID token to authorise the purchase with")

            // Best effort, matching the deletion endpoint: the server verifies App Check in soft
            // mode, and refusing to credit a paid-for purchase because an integrity token could
            // not be minted would be the wrong trade.
            val appCheckToken = runCatching {
                Firebase.appCheck.getAppCheckToken(false).await().token
            }.getOrNull()

            withContext(Dispatchers.IO) {
                // The description is sent so the history row reads in the user's language; the
                // server clamps it and computes everything that carries value itself.
                requestCredit(
                    idToken = idToken,
                    appCheckToken = appCheckToken,
                    productId = productId,
                    purchaseToken = purchaseToken,
                    description = context.getString(R.string.rewards_award_coins_purchased, amount),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Purchase credit failed", e)
            PurchaseOutcome.Failed(e.message)
        }

        if (outcome is PurchaseOutcome.Credited) {
            FirebaseAnalytics.getInstance(context).logEvent("reward_purchase_coins", null)
        }

        return outcome
    }

    /**
     * Posts the purchase to the server and reads back what it decided.
     *
     * Anything other than a 2xx is a [PurchaseOutcome.Failed], deliberately without trying to
     * sort permanent refusals from transient ones: the cost of treating a permanent refusal as
     * retryable is a request repeated on the next Play query, while the cost of the reverse is
     * consuming a purchase that was never credited, which cannot be undone.
     */
    private fun requestCredit(
        idToken: String,
        appCheckToken: String?,
        productId: String,
        purchaseToken: String,
        description: String,
    ): PurchaseOutcome {
        val payload = JSONObject()
            .put("productId", productId)
            .put("purchaseToken", purchaseToken)
            .put("description", description)
            .toString()

        val connection = (URL(ApiContract.getPurchaseUrl()).openConnection() as HttpURLConnection)
            .apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $idToken")
                appCheckToken?.let { setRequestProperty("X-Firebase-AppCheck", it) }
                connectTimeout = PURCHASE_TIMEOUT_MS
                readTimeout = PURCHASE_TIMEOUT_MS
            }

        return try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.w(TAG, "Purchase refused with $code $detail")
                return PurchaseOutcome.Failed(detail)
            }

            val body = JSONObject(
                connection.inputStream.bufferedReader().use { it.readText() }
            )

            if (body.optBoolean("credited")) {
                PurchaseOutcome.Credited(body.optLong("coins"))
            } else {
                PurchaseOutcome.AlreadyCredited
            }
        } finally {
            connection.disconnect()
        }
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

    private const val TAG = "RewardModel"

    /** Matches the deletion endpoint's budget; a purchase is one small round trip either way. */
    private const val PURCHASE_TIMEOUT_MS = 15_000
}
