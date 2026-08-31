package com.tyganeutronics.myratecalculator.utils

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import com.tyganeutronics.myratecalculator.widget.MultipleRateProvider
import com.tyganeutronics.myratecalculator.widget.SingleRateProvider
import java.time.Instant

object WidgetUtils {

    /**
     * Relative "3 hours ago" stamp, matching the footnote on the rates screen. Empty for the
     * Instant.MIN "never checked" sentinel, which is not representable in millis.
     */
    fun formatChecked(lastChecked: Instant): String {
        if (lastChecked <= Instant.EPOCH) return ""

        return DateUtils.getRelativeTimeSpanString(
            lastChecked.toEpochMilli(),
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }

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
