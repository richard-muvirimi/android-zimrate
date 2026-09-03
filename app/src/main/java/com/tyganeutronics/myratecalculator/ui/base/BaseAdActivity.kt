package com.tyganeutronics.myratecalculator.ui.base

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.appodeal.ads.Appodeal
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.utils.BaseUtils
import com.tyganeutronics.myratecalculator.utils.TokenUtils
import com.tyganeutronics.myratecalculator.utils.ads.banner.AppoBannerAdListener
import com.tyganeutronics.myratecalculator.utils.ads.interstitial.AppoInterstitialListener
import com.tyganeutronics.myratecalculator.utils.contracts.PreferenceContract
import com.tyganeutronics.myratecalculator.utils.traits.getBooleanPref
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

abstract class BaseAdActivity : BaseActivity() {

    val interstitialRunnable = Runnable { showInterstitialAd() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CoroutineScope(Dispatchers.Main).launch {

            if (TokenUtils.canLoadAds(baseContext)) {
                Appodeal.initialize(
                    this@BaseAdActivity,
                    getString(R.string.ads_appodeal_app_id),
                    Appodeal.BANNER or Appodeal.INTERSTITIAL or Appodeal.REWARDED_VIDEO
                ) {
                    // Initialization lands well after the first layout pass, so this is the only
                    // point at which the banner can actually be placed.
                    findViewById<ViewGroup>(R.id.adView)?.let { showBanner(it) }
                }
            }

            Appodeal.setTesting(!BaseUtils.isProductionBuild)
            Appodeal.muteVideosIfCallsMuted(true)

            if (TokenUtils.canShowAds(baseContext)) {
                Appodeal.cache(
                    this@BaseAdActivity,
                    Appodeal.REWARDED_VIDEO or Appodeal.INTERSTITIAL,
                    2
                )
            }

        }
    }

    override fun onViewCreated() {
        super.onViewCreated()
        setupAd()
    }

    override fun bindViews() {
        val analytics = getBooleanPref(PreferenceContract.FIREBASE_ANALYTICS, true)
        firebaseAnalytics.setAnalyticsCollectionEnabled(analytics)

    }

    private fun setupAd() {
        findViewById<ViewGroup>(R.id.adView)?.let { adView ->

            adView.post {

                showBanner(adView)

                setupInterstitial()
            }
        }
    }

    /**
     * Runs both at layout time and again once Appodeal reports itself initialized: on a cold start
     * the SDK is never ready for the first pass, and without the second run the slot stayed hidden
     * for the whole session. Guarded against filling the slot twice on the way through.
     */
    private fun showBanner(adView: ViewGroup) {
        if (!Appodeal.isInitialized(Appodeal.BANNER) || !TokenUtils.canShowAds(baseContext)) {
            adView.isGone = true
            return
        }

        if (adView.childCount > 0) {
            return
        }

        AppoBannerAdListener.apply {
            contextRef = WeakReference(baseContext)
        }

        Appodeal.setBannerViewId(R.id.adView)
        Appodeal.setBannerCallbacks(AppoBannerAdListener)

        val banner: View = Appodeal.getBannerView(this)

        Appodeal.show(this, Appodeal.BANNER_VIEW)

        adView.addView(banner)
        adView.isVisible = true
    }

    private fun setupInterstitial() {
        AppoInterstitialListener.apply {
            contextRef = WeakReference(baseContext)
        }

        Appodeal.setInterstitialCallbacks(AppoInterstitialListener)

        showInterstitialAd()
    }

    private fun showInterstitialAd() {
        if (TokenUtils.hasLowTokenBalance()) {

            Toast.makeText(
                this,
                R.string.rewards_earn_advert_loading,
                Toast.LENGTH_SHORT
            ).show()

            findViewById<CoordinatorLayout>(R.id.layout_container).let {
                if (Appodeal.isLoaded(Appodeal.INTERSTITIAL)) {
                    Appodeal.show(this, Appodeal.INTERSTITIAL)
                } else {
                    it.postDelayed(interstitialRunnable, 3000)
                }
            }
        }
    }

    override fun onDestroy() {
        findViewById<CoordinatorLayout>(R.id.layout_container)?.removeCallbacks(interstitialRunnable)

        super.onDestroy()
    }
}