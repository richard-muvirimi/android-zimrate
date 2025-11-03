package com.tyganeutronics.myratecalculator.utils.ads.rewarded

import android.widget.Toast
import com.appodeal.ads.Appodeal
import com.appodeal.ads.RewardedVideoCallbacks
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.models.RewardModel
import com.tyganeutronics.myratecalculator.interfaces.AdFragmentSubscriberInterface
import java.lang.ref.WeakReference

object AppoRewardedAdListener : RewardedVideoCallbacks {

    lateinit var adSubscriberRef: WeakReference<AdFragmentSubscriberInterface>

    fun showAd() {
        adSubscriberRef.get()?.let {
            it.requireShowAdButton().text = it.requireContext()
                .getString(R.string.rewards_earn_advert_loading)

            if (Appodeal.isLoaded(Appodeal.REWARDED_VIDEO)) {
                Appodeal.show(it.requireActivity(), Appodeal.REWARDED_VIDEO)
            }
        }
    }

    override fun onRewardedVideoLoaded(isPrecache: Boolean) {
        // Called when rewarded video is loaded

        adSubscriberRef.get()?.let {
            it.requireShowAdButton().isEnabled = Appodeal.isInitialized(Appodeal.REWARDED_VIDEO)
        }
    }

    override fun onRewardedVideoFailedToLoad() {
        // Called when rewarded video failed to load

        adSubscriberRef.get()?.let {
            it.requireShowAdButton().isEnabled = false

            Toast.makeText(
                it.requireContext(),
                R.string.rewards_toast_advert_failed,
                Toast.LENGTH_LONG
            ).show()

            it.resetShowAdButtonText()
        }
    }

    override fun onRewardedVideoShown() {
        // Called when rewarded video is shown

        adSubscriberRef.get()?.let {
            it.requireShowAdButton().isEnabled = false
        }
    }

    override fun onRewardedVideoShowFailed() {
        // Called when rewarded video show failed

        adSubscriberRef.get()?.let {
            it.requireShowAdButton().isEnabled = true
            it.resetShowAdButtonText()
        }
    }

    override fun onRewardedVideoClicked() {
        // Called when rewarded video is clicked

        adSubscriberRef.get()?.let {
            RewardModel.rewardWatchAdvertClick(it.requireContext(), 0.02)
        }
    }

    override fun onRewardedVideoFinished(amount: Double, currency: String) {
        // Called when rewarded video is viewed until the end

        adSubscriberRef.get()?.let {

            // Reward the user for watching the ad to completion
            Toast.makeText(
                it.requireContext(),
                R.string.rewards_toast_watched_advert,
                Toast.LENGTH_LONG
            ).show()

            RewardModel.rewardWatchVideoAdvert(it.requireContext(), amount)
        }
    }

    override fun onRewardedVideoClosed(finished: Boolean) {
        // Called when rewarded video is closed

        adSubscriberRef.get()?.let {
            it.requireShowAdButton().isEnabled = true
        }
    }

    override fun onRewardedVideoExpired() {
        // Called when rewarded video is expired

    }
}