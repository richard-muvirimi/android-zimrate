package com.tyganeutronics.myratecalculator

import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.apollographql.apollo3.ApolloClient
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.tyganeutronics.myratecalculator.database.Database
import com.tyganeutronics.myratecalculator.database.contract.DatabaseContract
import com.tyganeutronics.myratecalculator.database.models.RewardModel
import com.tyganeutronics.myratecalculator.utils.TokenUtils
import com.tyganeutronics.myratecalculator.utils.PreferenceMigrations
import com.tyganeutronics.myratecalculator.utils.contracts.ApiContract
import com.tyganeutronics.myratecalculator.work.RatesRefreshScheduler

class AppZimRate : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()

        this.setUpApollo()
        PreferenceManager.setDefaultValues(this, R.xml.settings, false)

        // Must run before the schedule is built — it can change update_interval.
        PreferenceMigrations.run(this)
        RatesRefreshScheduler.sync(this)

        initializeAppCheck()
        setUpDataBase()
        setUpRemoteConfig()
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

    private fun setUpDataBase() {
        val database = Room.databaseBuilder(
            applicationContext,
            Database::class.java,
            DatabaseContract.DATABASE_NAME
        )
        database.allowMainThreadQueries()
        database.addMigrations(Database.MIGRATION_1_2, Database.MIGRATION_2_3, Database.MIGRATION_3_4)
        database.fallbackToDestructiveMigrationOnDowngrade()
        database.enableMultiInstanceInvalidation()
        database.addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)

                if (!TokenUtils.installOlderThan(
                        applicationContext,
                        1
                    )
                ) {
                    RewardModel.rewardStarterPack(applicationContext)
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)

                //clear old history
                AppZimRate.database.apply {
                    transactionExecutor.execute {
                        rewards().cleanExpired()
                        spends().cleanExpired()
                    }
                }
            }
        })

        Companion.database = database.build()
    }

    private fun setUpApollo() {
        apolloClient = ApolloClient.Builder()
            .serverUrl(ApiContract.getRatesUrl(this))
            .build()
    }

    companion object {
        lateinit var apolloClient: ApolloClient
        lateinit var database: Database
    }
}