package com.tyganeutronics.myratecalculator.fragments.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.isGone
import com.appodeal.ads.Appodeal
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.contract.RewardContract
import com.tyganeutronics.myratecalculator.database.models.RewardModel
import com.tyganeutronics.myratecalculator.database.models.SpendModel
import com.tyganeutronics.myratecalculator.fragments.rewards.FragmentPurchase
import com.tyganeutronics.myratecalculator.interfaces.AdFragmentSubscriberInterface
import com.tyganeutronics.myratecalculator.interfaces.RewardsActivity
import com.tyganeutronics.myratecalculator.ui.base.BaseFragment
import com.tyganeutronics.myratecalculator.utils.BaseUtils
import com.tyganeutronics.myratecalculator.utils.ads.rewarded.AppoRewardedAdListener
import com.tyganeutronics.myratecalculator.utils.contracts.RemoteConfigContract
import com.tyganeutronics.myratecalculator.utils.traits.requireViewById
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference


class FragmentSectionRewards : BaseFragment(), OnClickListener, AdFragmentSubscriberInterface {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppoRewardedAdListener.apply {
            adSubscriberRef = WeakReference(this@FragmentSectionRewards)
        }

        Appodeal.setRewardedVideoCallbacks(AppoRewardedAdListener)
    }

    override fun requireShowAdButton(): Button {
        return requireViewById<AppCompatButton>(R.id.btn_trigger_earn_award)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile_rewards, container, false)
    }

    override fun bindViews() {
        super.bindViews()

        requireViewById<LinearLayoutCompat>(R.id.btn_show_award_history).setOnClickListener(this)
        requireViewById<AppCompatButton>(R.id.btn_trigger_earn_award).setOnClickListener(this)
        requireViewById<LinearLayoutCompat>(R.id.btn_show_purchases_history).setOnClickListener(this)

        bindPurchase()
    }

    /**
     * Coins can only be bought where Play Billing can complete a purchase, which is the Play
     * build and nowhere else.
     *
     * Samsung and the other-store builds have no billing library that will serve them — Galaxy
     * Store wants Samsung IAP and Amazon wants Amazon IAP, and Play Billing refuses an app it did
     * not distribute in any case. The button was still shown on all three, so tapping it reached a
     * client that could not connect and a sheet that closed itself again. Offering something that
     * cannot work is worse than not offering it.
     *
     * The divider goes with it: "OR" separating the purchase row from the advert row reads as
     * nonsense once there is nothing on the other side of it.
     *
     * Purchase history stays. A signed-in user can hold coins bought on a Play device and still
     * be entitled to see where they came from, and the row expands to fill the width the button
     * has given up.
     */
    private fun bindPurchase() {
        val purchase = requireViewById<AppCompatButton>(R.id.btn_trigger_purchase_award)

        if (!BaseUtils.isPlayBuild) {
            purchase.isGone = true
            requireViewById<AppCompatTextView>(R.id.txt_purchase_alt).isGone = true
            return
        }

        purchase.setOnClickListener(this)
    }

    override fun syncViews() {
        super.syncViews()

        // View scoped, so the streak arriving after the tab is gone is simply dropped. It was
        // an unscoped CoroutineScope, which nothing cancels, and every line below it reaches for
        // requireViewById after a suspending wallet read.
        viewLifecycleOwner.lifecycleScope.launch {
            val streak = SpendModel.daysStreak()

            listOf(
                R.id.rewards_amount_day_1,
                R.id.rewards_amount_day_2,
                R.id.rewards_amount_day_3,
                R.id.rewards_amount_day_4,
                R.id.rewards_amount_day_5,
                R.id.rewards_amount_day_6,
                R.id.rewards_amount_day_7,
            ).forEachIndexed { index, id ->
                requireViewById<AppCompatTextView>(id).apply {
                    text = RewardModel.dayClockInReward(index).toString()

                    val drawable =
                        if (streak[index]) R.drawable.ic_rewarded else R.drawable.ic_reward
                    setCompoundDrawablesWithIntrinsicBounds(0, 0, drawable, 0)
                }
            }
        }

        resetShowAdButtonText()
    }

    override fun resetShowAdButtonText() {
        val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

        requireShowAdButton().apply {
            text = getString(
                R.string.rewards_earn_advert,
                remoteConfig.getLong(RemoteConfigContract.REWARD_WATCH_ADVERT)
            )
        }
    }

    override fun onClick(v: View?) {
        if (v != null) {
            when (v.id) {
                R.id.btn_show_award_history -> {
                    val bundle = Bundle()
                    bundle.putString(RewardContract.COLUMN_NAME_TYPE, "")

                    (requireActivity() as RewardsActivity).showRewardHistory(bundle)
                }

                R.id.btn_trigger_earn_award -> {
                    AppoRewardedAdListener.apply {
                        showAd()
                    }
                }

                R.id.btn_show_purchases_history -> {
                    val bundle = Bundle()
                    bundle.putString(RewardContract.COLUMN_NAME_TYPE, RewardContract.TYPES.PURCHASE)

                    (requireActivity() as RewardsActivity).showRewardHistory(bundle)
                }

                R.id.btn_trigger_purchase_award -> {
                    val fragment = FragmentPurchase()
                    fragment.show(childFragmentManager, FragmentPurchase.TAG)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        Appodeal.setRewardedVideoCallbacks(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        AppoRewardedAdListener.apply {
            adSubscriberRef.clear()
        }
    }
}