package com.tyganeutronics.myratecalculator.activities

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.models.RewardModel
import com.tyganeutronics.myratecalculator.database.viewmodels.RewardViewModel
import com.tyganeutronics.myratecalculator.fragments.main.FragmentAbout
import com.tyganeutronics.myratecalculator.fragments.main.FragmentRates
import com.tyganeutronics.myratecalculator.fragments.rewards.FragmentCoinsBalance
import com.tyganeutronics.myratecalculator.fragments.rewards.FragmentRewards
import com.tyganeutronics.myratecalculator.fragments.rewards.FragmentSpends
import com.tyganeutronics.myratecalculator.interfaces.RewardModelInterface
import com.tyganeutronics.myratecalculator.interfaces.RewardsActivity
import com.tyganeutronics.myratecalculator.ui.base.BaseAppActivity
import com.tyganeutronics.myratecalculator.widget.MultipleRateProvider
import com.tyganeutronics.myratecalculator.widget.SingleRateProvider

class MainActivity : BaseAppActivity(), NavigationBarView.OnItemSelectedListener, RewardsActivity,
    RewardModelInterface {

    override lateinit var rewardViewModel: RewardViewModel

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
    }

    private fun fetchRemoteConfigs() {
        val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

        remoteConfig.fetchAndActivate()
    }

    private fun selectHomeFragment() {
        getBottomNavigationView().selectedItemId = R.id.navigation_calculator
    }

    private fun getBottomNavigationView(): BottomNavigationView {
        return findViewById(R.id.bottomNavView)
    }

    override fun bindViews() {
        super.bindViews()
        setSupportActionBar(findViewById(R.id.toolbar))
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        when (item.itemId) {
            R.id.navigation_calculator -> {
                transaction.replace(R.id.nav_host_fragment, FragmentRates(), FragmentRates.TAG)
            }

            R.id.navigation_about -> {
                transaction.replace(R.id.nav_host_fragment, FragmentAbout(), FragmentAbout.TAG)
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