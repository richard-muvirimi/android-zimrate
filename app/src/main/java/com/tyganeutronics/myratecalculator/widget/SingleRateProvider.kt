package com.tyganeutronics.myratecalculator.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.google.firebase.analytics.FirebaseAnalytics
import com.tyganeutronics.myratecalculator.AppZimRate
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.activities.MainActivity
import com.tyganeutronics.myratecalculator.utils.WidgetUtils
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

        val custom = entity?.custom == true
        views.setImageViewResource(
            R.id.img_single_flag,
            WidgetUtils.flagRes(context, currency, custom),
        )

        views.setTextViewText(
            R.id.txt_single_name,
            WidgetUtils.label(context, currency, custom, entity?.name.orEmpty()),
        )
        views.setTextViewText(R.id.txt_single_rate, rateText)

        // Hidden until there is a real sync stamp, so the row does not show an empty line.
        val checked = entity?.lastChecked?.let { WidgetUtils.formatChecked(it) }.orEmpty()
        views.setTextViewText(R.id.txt_single_date, checked)
        views.setViewVisibility(
            R.id.txt_single_date,
            if (checked.isEmpty()) View.GONE else View.VISIBLE,
        )

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
