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