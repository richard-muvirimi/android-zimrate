package com.tyganeutronics.myratecalculator.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.google.firebase.analytics.FirebaseAnalytics
import com.murgupluoglu.flagkit.FlagKit
import com.tyganeutronics.myratecalculator.AppZimRate
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.activities.MainActivity
import com.tyganeutronics.myratecalculator.utils.CurrencyFlagUtil
import com.tyganeutronics.myratecalculator.utils.traits.getStringPref
import com.tyganeutronics.myratecalculator.utils.traits.removePref
import java.math.RoundingMode

class SingleRateProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetIds: IntArray?
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds?.forEach { id ->
            if (context != null) updateWidget(context, id)
        }
    }

    override fun onEnabled(context: Context?) {
        super.onEnabled(context)
        context?.let { FirebaseAnalytics.getInstance(it).logEvent("add_single_widget", Bundle()) }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        if (context != null) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, SingleRateProvider::class.java)
            appWidgetManager.getAppWidgetIds(componentName)?.forEach { id ->
                updateWidget(context, id)
            }
        }
    }

    private fun updateWidget(context: Context, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_single)

        // Retrieve saved currency preference for this widget instance
        val currency = context.getStringPref("widget-$appWidgetId", "USD")

        // Look up the rate from Room DB; fall back to "—" if not found
        val entity = try {
            AppZimRate.database.rates().findByCurrency(currency)
        } catch (e: Exception) {
            null
        }

        val rateText = entity?.rate
            ?.setScale(2, RoundingMode.HALF_UP)
            ?.toPlainString() ?: "—"

        val countryCode = CurrencyFlagUtil.countryCode(currency)
        val flagRes = if (countryCode.isNotEmpty()) FlagKit.getResId(context, countryCode) else 0
        views.setImageViewResource(R.id.img_single_flag, if (flagRes != 0) flagRes else R.mipmap.ic_launcher)

        views.setTextViewText(R.id.tv_rate, rateText)
        views.setTextViewText(R.id.tv_rate_title, currency)

        // Tap → open app
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_main, pendingIntent)

        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds?.forEach { id ->
            context?.removePref("widget-$id")
        }
    }
}
