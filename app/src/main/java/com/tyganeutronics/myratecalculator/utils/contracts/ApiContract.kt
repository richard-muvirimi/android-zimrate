package com.tyganeutronics.myratecalculator.utils.contracts

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import com.tyganeutronics.myratecalculator.utils.BaseUtils

object ApiContract {

    fun getRatesUrl(context: Context): String {
        return "https://zimrate.tyganeutronics.com/api/graphql"
    }

    /**
     * Where a user asks for their own account to be erased.
     *
     * Server side because the database rules deny a client the write: `users/$uid` has no
     * top-level write rule, and the rewards and spends rules require `newData.exists()` so that
     * an overdrawn grant cannot simply be deleted. The website calls the same endpoint.
     */
    fun getAccountUrl(): String {
        return "https://zimrate.tyganeutronics.com/api/account"
    }

    /**
     * Where a paid-for coin purchase is turned into a grant.
     *
     * Server side because the handset must not be the thing that decides how many coins it gets.
     * The old code wrote the grant itself, naming its own amount, which meant anyone able to
     * reach the database with their own ID token could mint coins without paying. The server
     * checks the purchase token with Google and writes the row with the Admin SDK, so the
     * database rules can forbid clients writing purchase rows at all.
     */
    fun getPurchaseUrl(): String {
        return "https://zimrate.tyganeutronics.com/api/wallet/purchase"
    }

    private fun getBaseApiUrl(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).isCharging
            && !BaseUtils.isProductionBuild
        ) {
            "http://192.168.81.155/wordpress/api/v1"
        } else {
            "https://zimrate.tyganeutronics.com/api/v1"
            //"http://afrorate.tyganeutronics.com/wp-json/afrorate/v3/rate"
        }
    }

}