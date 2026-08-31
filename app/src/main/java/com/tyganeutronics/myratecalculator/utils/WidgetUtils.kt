package com.tyganeutronics.myratecalculator.utils

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.tyganeutronics.myratecalculator.widget.MultipleRateProvider
import com.tyganeutronics.myratecalculator.widget.SingleRateProvider

object WidgetUtils {

    /** Redraws every placed widget so they pick up freshly saved rates. */
    fun refreshAll(context: Context) {
        refresh(context, MultipleRateProvider::class.java)
        refresh(context, SingleRateProvider::class.java)
    }

    private fun refresh(context: Context, provider: Class<*>) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, provider))
        if (ids.isEmpty()) return

        context.sendBroadcast(Intent(context, provider).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        })
    }
}
