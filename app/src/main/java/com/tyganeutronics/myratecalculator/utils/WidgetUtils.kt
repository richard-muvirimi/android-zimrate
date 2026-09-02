package com.tyganeutronics.myratecalculator.utils

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.text.format.DateUtils
import com.murgupluoglu.flagkit.FlagKit
import com.tyganeutronics.myratecalculator.R
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

    /**
     * Flag for a rate, or the generic currency mark for one the user added — an invented code is
     * not ISO, so resolving it to a country would dress OMIR in Oman's flag.
     *
     * RemoteViews cannot resolve theme attributes, so the night variant is picked from the
     * configuration, the way values-night picks the widget text colours.
     */
    fun flagRes(context: Context, currency: String, custom: Boolean): Int {
        if (custom) {
            return if (isNight(context)) R.drawable.ic_dollar_dark else R.drawable.ic_dollar
        }

        val country = CurrencyFlagUtil.countryCode(currency)
        val flag = if (country.isNotEmpty()) FlagKit.getResId(context, country) else 0
        return if (flag != 0) flag else R.mipmap.ic_launcher
    }

    /** Matches the rates screen: the user's own label for a custom rate, the country otherwise. */
    fun label(context: Context, currency: String, custom: Boolean, name: String): String =
        if (custom) {
            CurrencyFlagUtil.codeWithName(context, currency, name.ifEmpty { currency })
        } else {
            CurrencyFlagUtil.codeWithName(context, currency)
        }

    private fun isNight(context: Context) =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

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
