package com.tyganeutronics.myratecalculator

import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager
import com.apollographql.apollo.ApolloClient
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.database.database
import com.google.firebase.initialize
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.tyganeutronics.myratecalculator.auth.AuthManager
import com.tyganeutronics.myratecalculator.database.rtdb.CurrencyRepository
import com.tyganeutronics.myratecalculator.database.rtdb.WalletRepository
import com.tyganeutronics.myratecalculator.migration.SetupState
import com.tyganeutronics.myratecalculator.utils.PreferenceMigrations
import com.tyganeutronics.myratecalculator.utils.contracts.ApiContract
import com.tyganeutronics.myratecalculator.work.RatesRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppZimRate : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()

        this.setUpApollo()
        PreferenceManager.setDefaultValues(this, R.xml.settings, false)

        // Must run before the schedule is built — it can change update_interval.
        PreferenceMigrations.run(this)
        RatesRefreshScheduler.sync(this)

        initializeAppCheck()
        setUpRealtimeDatabase()
        signIn()
        setUpRemoteConfig()
    }

    /**
     * Turns on the offline cache, and has to happen before anything asks the database for a
     * reference — calling it afterwards throws at runtime rather than failing to compile. That
     * is the only reason it sits this high in onCreate.
     */
    private fun setUpRealtimeDatabase() {
        Firebase.database.setPersistenceEnabled(true)
    }

    /**
     * Starts first-launch setup, and keeps the wallet pointed at whichever account is current.
     *
     * [SetupState] owns the sign in, because a failure there is not something to swallow — it
     * is the gate the user has to be told about.
     */
    private fun signIn() {
        SetupState.begin(this)

        CoroutineScope(Dispatchers.IO).launch {

            // Follows the account rather than being attached once, so signing out drops the
            // cached wallet and signing back in picks up the right one.
            AuthManager.state.collect {
                val uid = AuthManager.uid
                if (uid == null) {
                    WalletRepository.detach()
                    CurrencyRepository.detach()
                } else {
                    WalletRepository.attach(uid)
                    CurrencyRepository.attach(uid)
                }
            }
        }
    }

    private fun initializeAppCheck() {
        Firebase.initialize(context = this)
        Firebase.appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance(),
        )
    }

    private fun setUpRemoteConfig() {
        val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600
        }

        remoteConfig.setConfigSettingsAsync(configSettings)

        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
    }

    private fun setUpApollo() {
        apolloClient = ApolloClient.Builder()
            .serverUrl(ApiContract.getRatesUrl(this))
            .build()
    }

    companion object {
        lateinit var apolloClient: ApolloClient
    }
}