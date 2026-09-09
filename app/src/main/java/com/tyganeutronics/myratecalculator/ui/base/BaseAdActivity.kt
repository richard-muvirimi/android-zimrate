package com.tyganeutronics.myratecalculator.ui.base

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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

    /** Separate from [interstitialRunnable]: this one shows, it does not go round again. */
    private val showInterstitialRunnable = Runnable {
        if (!isFinishing) Appodeal.show(this, Appodeal.INTERSTITIAL)
    }

    /** When the current wait began, so it can be given up on. */
    private var interstitialWaitStarted = 0L

    companion object {
        private const val INTERSTITIAL_RETRY_MS = 3000L

        /** A beat for the toast to be read before the ad lands on top of it. */
        private const val INTERSTITIAL_LEAD_MS = 2000L

        /** How long an ad stays plausible enough to keep saying it is coming. */
        private const val INTERSTITIAL_WAIT_LIMIT_MS = 30000L
    }

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

        interstitialWaitStarted = SystemClock.uptimeMillis()
        showInterstitialAd()
    }

    /**
     * Keeps the user told that an advert is on its way for as long as one genuinely is, so it
     * never arrives out of nowhere onto a tap meant for something else.
     *
     * The promise has to be true to be worth making, which is what the two guards are for: no ad
     * is requested at all during the install grace period or for a user holding paid tokens, and
     * one that has not filled within [INTERSTITIAL_WAIT_LIMIT_MS] is not coming.
     */
    private fun showInterstitialAd() {
        if (!TokenUtils.canShowAds(baseContext) || !TokenUtils.hasLowTokenBalance()) return

        val container = findViewById<View>(R.id.layout_container) ?: return
        val loaded = Appodeal.isLoaded(Appodeal.INTERSTITIAL)

        if (!loaded &&
            SystemClock.uptimeMillis() - interstitialWaitStarted >= INTERSTITIAL_WAIT_LIMIT_MS
        ) return

        Toast.makeText(
            this,
            R.string.rewards_earn_advert_loading,
            Toast.LENGTH_SHORT
        ).show()

        if (loaded) {
            // Delayed even though it could be shown right now: cached from a previous session,
            // the very first pass through here is already loaded, and showing on the same tick
            // as the first toast is the ambush this whole loop exists to prevent.
            container.postDelayed(showInterstitialRunnable, INTERSTITIAL_LEAD_MS)
        } else {
            container.postDelayed(interstitialRunnable, INTERSTITIAL_RETRY_MS)
        }
    }

    override fun onDestroy() {
        findViewById<View>(R.id.layout_container)?.apply {
            removeCallbacks(interstitialRunnable)
            removeCallbacks(showInterstitialRunnable)
        }

        super.onDestroy()
    }
}