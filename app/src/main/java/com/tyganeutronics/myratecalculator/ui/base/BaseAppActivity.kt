package com.tyganeutronics.myratecalculator.ui.base

import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.models.SpendModel
import com.tyganeutronics.myratecalculator.interfaces.ReviewableActivity
import com.tyganeutronics.myratecalculator.utils.BaseUtils
import com.tyganeutronics.myratecalculator.utils.traits.containsPref
import com.tyganeutronics.myratecalculator.utils.traits.getLongPref
import com.tyganeutronics.myratecalculator.utils.traits.putLongPref
import java.time.Instant
import java.time.ZonedDateTime

abstract class BaseAppActivity : BaseAdActivity(), ReviewableActivity {

    companion object {
        /** When the rating sheet was last put in front of the user, as an epoch second. */
        private const val LAST_RATING_REQUESTED = "last_rating_requested"

        /** When the user last turned down an update, as an epoch second. */
        private const val LAST_UPDATE_DECLINED = "last_update_declined"
    }

    private lateinit var reviewManager: ReviewManager
    private lateinit var reviewInfo: ReviewInfo

    private lateinit var appUpdateManager: AppUpdateManager

    private val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            offerToInstallUpdate()
        }
    }

    /**
     * Offers the restart that finishes a flexible update. Indefinite on purpose: the download is
     * already on disk and installing it is one tap, so there is no reason to let the offer time
     * out and strand it.
     *
     * Both callers arrive from a task that can land after the activity is on its way out, and
     * there is no view left to hang a snackbar on by then.
     */
    private fun offerToInstallUpdate() {
        if (isFinishing || isDestroyed) return

        val container = findViewById<View>(R.id.layout_container) ?: return

        Snackbar.make(
            container,
            getString(R.string.app_update_downloaded),
            Snackbar.LENGTH_INDEFINITE
        ).apply {
            setAction(R.string.app_update_update) { appUpdateManager.completeUpdate() }
        }.show()
    }

    override fun syncViews() {
        if (BaseUtils.isPlayBuild || BaseUtils.isOtherBuild) {
            seedRatingClock()

            reviewManager = ReviewManagerFactory.create(this)

            reviewManager.requestReviewFlow()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // We got the ReviewInfo object
                        reviewInfo = task.result
                    } else {
                        // There was some problem, log or handle the error code.
                    }
                }
        }
    }

    /**
     * Starts the two month rating clock at first launch rather than at zero.
     *
     * Left unset, the throttle in [requestReview] reads "never asked" as "asked long ago", so the
     * very first conversion a new user made would put a review sheet in front of them — before
     * they have formed the opinion the sheet is there to collect.
     */
    private fun seedRatingClock() {
        if (containsPref(LAST_RATING_REQUESTED)) return
        putLongPref(LAST_RATING_REQUESTED, Instant.now().epochSecond)
    }

    override fun requestReview() {
        if (this::reviewManager.isInitialized && this::reviewInfo.isInitialized) {
            // ZonedDateTime, not LocalDateTime: the value written below is a true epoch second,
            // and LocalDateTime.toEpochSecond(UTC) labels local wall clock time as UTC. The two
            // halves of the comparison were out by the zone offset.
            if (getLongPref(LAST_RATING_REQUESTED, 0) < ZonedDateTime.now()
                    .minusMonths(2)
                    .toEpochSecond()
            ) {

                val flow = reviewManager.launchReviewFlow(this, reviewInfo)
                flow.addOnCompleteListener { _ ->
                    // The flow has finished. The API does not indicate whether the user
                    // reviewed or not, or even whether the review dialog was shown. Thus, no
                    // matter the result, we continue our app flow.

                    putLongPref(LAST_RATING_REQUESTED, Instant.now().epochSecond)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BaseUtils.isPlayBuild || BaseUtils.isOtherBuild) {
            checkAppHasUpdate()
        }
    }

    override fun onStart() {
        super.onStart()

        if (this::appUpdateManager.isInitialized) {
            appUpdateManager.registerListener(listener)
        }

        SpendModel.normalizeOverdrawnRewards()
    }

    /**
     * [listener] only hears state changes, and only while the activity is in the foreground —
     * it is unregistered again in [onStop]. A download that finished while the app was away, or
     * one that outlived the process it was started in, would otherwise sit on disk with nothing
     * ever offering to install it. So the status is asked for outright on the way back in.
     */
    override fun onResume() {
        super.onResume()

        if (!this::appUpdateManager.isInitialized) return

        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                offerToInstallUpdate()
            }
        }.addOnFailureListener(::recordUpdateCheckFailure)
    }

    override fun onStop() {
        super.onStop()

        if (this::appUpdateManager.isInitialized) {
            appUpdateManager.unregisterListener(listener)
        }
    }

    private fun checkAppHasUpdate() {
        appUpdateManager = AppUpdateManagerFactory.create(this)

        val activityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result: ActivityResult ->
            // Anything short of an accepted offer stands for a week. Left unrecorded, the next
            // rotation recreates the activity and puts the same dialog up again.
            if (result.resultCode != RESULT_OK) {
                putLongPref(LAST_UPDATE_DECLINED, Instant.now().epochSecond)
            }
        }

        appUpdateManager
            .appUpdateInfo
            .addOnSuccessListener { appUpdateInfo ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                    && (appUpdateInfo.clientVersionStalenessDays() ?: -1) >= 7
                    && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                    && refusalHasAgedOut()
                ) {
                    // Request the update.
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        activityResultLauncher,
                        AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                    )

                }
            }
            .addOnFailureListener(::recordUpdateCheckFailure)
    }

    /**
     * Whether a refused offer has aged out. Same epoch second convention as [requestReview]: the
     * value written above is a true epoch second, so the other half of the comparison has to be
     * built from [ZonedDateTime] rather than local wall clock time.
     */
    private fun refusalHasAgedOut(): Boolean =
        getLongPref(LAST_UPDATE_DECLINED, 0) < ZonedDateTime.now().minusDays(7).toEpochSecond()

    /**
     * A failed check is routine away from Play — there is no store there to answer it — so only a
     * play build has anything to learn from one. Recorded rather than logged: the point is to hear
     * about a check that has quietly stopped working for real users, which logcat cannot tell you.
     */
    private fun recordUpdateCheckFailure(error: Exception) {
        if (!BaseUtils.isPlayBuild) return

        FirebaseCrashlytics.getInstance().recordException(error)
    }

}