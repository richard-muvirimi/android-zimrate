package com.tyganeutronics.myratecalculator.utils

import android.content.Context
import com.tyganeutronics.myratecalculator.utils.traits.getBooleanPref
import com.tyganeutronics.myratecalculator.utils.traits.getStringPref
import com.tyganeutronics.myratecalculator.utils.traits.putBooleanPref
import com.tyganeutronics.myratecalculator.utils.traits.putStringPref
import com.tyganeutronics.myratecalculator.utils.traits.removePref

/**
 * One time fix ups for stored preferences on upgrade. Each runs behind its own flag so it
 * applies exactly once per install.
 */
object PreferenceMigrations {

    private const val MIGRATED_UPDATE_INTERVAL = "migrated_update_interval_v1"
    private const val CLEARED_LEGACY_RATES = "cleared_legacy_rate_prefs_v1"

    /**
     * Rates used to be stored one per preference, keyed by currency code, before they moved
     * into the database. The keys came from translatable="false" resources so they are these
     * exact literals on every install.
     */
    private val LEGACY_RATE_KEYS = listOf("USD", "BOND", "RTGS", "OMIR", "RBZ", "ZAR")

    fun run(context: Context) {
        migrateUpdateInterval(context)
        clearLegacyRatePrefs(context)
    }

    /** Drops the old per currency rate preferences, which nothing reads any more. */
    private fun clearLegacyRatePrefs(context: Context) {
        if (context.getBooleanPref(CLEARED_LEGACY_RATES, false)) return
        context.putBooleanPref(CLEARED_LEGACY_RATES, true)

        LEGACY_RATE_KEYS.forEach { context.removePref(it) }
    }

    /**
     * Until background refreshing existed, update_interval was written by the settings
     * defaults but never read by anything, so a stored "1" records no decision the user
     * actually made. Now that it drives the refresh worker, leaving it would quietly refresh
     * — and spend a coin — every hour after upgrading, so installs still holding the old
     * inert value are moved to the current daily default.
     *
     * Anyone who picks an interval themselves keeps it: this runs once, before they have had
     * the chance. Fresh installs already default to "24", so it does nothing for them.
     */
    private fun migrateUpdateInterval(context: Context) {
        if (context.getBooleanPref(MIGRATED_UPDATE_INTERVAL, false)) return
        context.putBooleanPref(MIGRATED_UPDATE_INTERVAL, true)

        if (context.getStringPref("update_interval", "24") == "1") {
            context.putStringPref("update_interval", "24")
        }
    }
}
