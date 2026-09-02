package com.tyganeutronics.myratecalculator.activities

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationBarView
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.models.RewardModel
import com.tyganeutronics.myratecalculator.database.viewmodels.RewardViewModel
import com.tyganeutronics.myratecalculator.fragments.main.FragmentAbout
import com.tyganeutronics.myratecalculator.fragments.main.FragmentCustomRate
import com.tyganeutronics.myratecalculator.fragments.main.FragmentGlance
import com.tyganeutronics.myratecalculator.fragments.main.FragmentRates
import com.tyganeutronics.myratecalculator.fragments.rewards.FragmentCoinsBalance
import com.tyganeutronics.myratecalculator.fragments.rewards.FragmentRewards
import com.tyganeutronics.myratecalculator.fragments.rewards.FragmentSpends
import com.tyganeutronics.myratecalculator.interfaces.RewardModelInterface
import com.tyganeutronics.myratecalculator.interfaces.RewardsActivity
import com.tyganeutronics.myratecalculator.ui.base.BaseAppActivity
import com.tyganeutronics.myratecalculator.utils.RatesNotifier
import com.tyganeutronics.myratecalculator.utils.traits.getBooleanPref
import com.tyganeutronics.myratecalculator.utils.traits.putBooleanPref
import com.tyganeutronics.myratecalculator.widget.MultipleRateProvider
import com.tyganeutronics.myratecalculator.widget.SingleRateProvider
import com.tyganeutronics.myratecalculator.work.RatesRefreshScheduler

class MainActivity : BaseAppActivity(), NavigationBarView.OnItemSelectedListener, RewardsActivity,
    RewardModelInterface {

    override lateinit var rewardViewModel: RewardViewModel

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    companion object {
        private const val PREF_ASKED_NOTIFICATIONS = "asked_notifications"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        //Do not remove before create activity, crushes on rotate device
        rewardViewModel = ViewModelProvider(this)[RewardViewModel::class.java]

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getBottomNavigationView().setOnItemSelectedListener(this)

        if (savedInstanceState == null) {
            selectHomeFragment()
        }

        fetchRemoteConfigs()

        RewardModel.maybeRewardClockIn(this)

        // Background refreshing stops itself when coins run out; bring it back on a top up.
        rewardViewModel.coins.observe(this) { coins ->
            if ((coins ?: 0) > 0) RatesRefreshScheduler.sync(this)
        }

        requestNotificationPermission()
    }

    /**
     * Asked once, so the coins-exhausted notification can actually reach the user. Declining
     * costs them nothing beyond that notification — refreshing still stops at a zero balance.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (RatesNotifier.canNotify(this)) return
        if (getBooleanPref(PREF_ASKED_NOTIFICATIONS, false)) return

        putBooleanPref(PREF_ASKED_NOTIFICATIONS, true)
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun fetchRemoteConfigs() {
        val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

        remoteConfig.fetchAndActivate()
    }

    /**
     * Opening the app lands on the glance screen. Most openings are someone checking a rate, and
     * that read costs nothing — the calculator is a tap away for anyone who came to work with it.
     */
    private fun selectHomeFragment() {
        getBottomNavigationView().selectedItemId = R.id.navigation_home
    }

    private fun getBottomNavigationView(): BottomNavigationView {
        return findViewById(R.id.bottomNavView)
    }

    override fun bindViews() {
        super.bindViews()
        setSupportActionBar(findViewById(R.id.toolbar))

        getAddRateFab().setOnClickListener {
            if (supportFragmentManager.findFragmentByTag(FragmentCustomRate.TAG) === null) {
                FragmentCustomRate().show(supportFragmentManager, FragmentCustomRate.TAG)
            }
        }

        // Rewards and purchase history are pushed onto the back stack rather than through the
        // navigation bar, so the FAB has to follow those transitions too.
        supportFragmentManager.addOnBackStackChangedListener { syncAddRateFab() }
    }

    private fun getAddRateFab(): FloatingActionButton = findViewById(R.id.fab_add_rate)

    /** Custom rates belong to the rates list, so the FAB only rides along with that screen. */
    private fun syncAddRateFab() {
        val onRates = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) is FragmentRates
        if (onRates) getAddRateFab().show() else getAddRateFab().hide()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        when (item.itemId) {
            R.id.navigation_calculator -> {
                transaction.replace(R.id.nav_host_fragment, FragmentRates(), FragmentRates.TAG)
                getAddRateFab().show()
            }

            R.id.navigation_home -> {
                transaction.replace(R.id.nav_host_fragment, FragmentGlance(), FragmentGlance.TAG)
                // Nothing on the glance screen adds a rate, so the FAB would land on a bubble.
                getAddRateFab().hide()
            }

            R.id.navigation_about -> {
                transaction.replace(R.id.nav_host_fragment, FragmentAbout(), FragmentAbout.TAG)
                getAddRateFab().hide()
            }
        }

        transaction.commit()
        return true
    }

    override fun showRewardHistory(bundle: Bundle) {
        val fragment = FragmentRewards()
        fragment.arguments = bundle

        navigateToFragment(fragment, FragmentRewards.TAG)
    }

    override fun showPurchasesHistory(bundle: Bundle) {
        val fragment = FragmentSpends()
        fragment.arguments = bundle

        navigateToFragment(fragment, FragmentSpends.TAG)
    }

    override fun showTopUpDialog() {
        if (!isFinishing) {
            if (supportFragmentManager.findFragmentByTag(FragmentCoinsBalance.TAG) === null) {
                val fragment = FragmentCoinsBalance()
                fragment.show(supportFragmentManager, FragmentCoinsBalance.TAG)
            }
        }
    }

    override fun onStop() {
        super.onStop()

        updateMultipleWidget()
        updateSingleWidget()
    }

    private fun updateMultipleWidget() {

        val componentName = ComponentName(this, MultipleRateProvider::class.java)

        val appWidgetManager = AppWidgetManager.getInstance(this)

        val intent = Intent(this, MultipleRateProvider::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE

        val multipleIds = appWidgetManager.getAppWidgetIds(componentName)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, multipleIds)
        sendBroadcast(intent)
    }

    private fun updateSingleWidget() {

        val componentName = ComponentName(this, SingleRateProvider::class.java)

        val appWidgetManager = AppWidgetManager.getInstance(this)

        val intent = Intent(this, SingleRateProvider::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE

        val multipleIds = appWidgetManager.getAppWidgetIds(componentName)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, multipleIds)
        sendBroadcast(intent)
    }
}