package com.tyganeutronics.myratecalculator.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import com.google.firebase.analytics.FirebaseAnalytics
import com.tyganeutronics.myratecalculator.AppZimRate
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.activities.MainActivity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class MultipleRateProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetIds: IntArray?
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds?.forEach { id ->
            if (context != null && appWidgetManager != null) {
                updateWidget(context, appWidgetManager, id)
            }
        }
    }

    override fun onEnabled(context: Context?) {
        super.onEnabled(context)
        context?.let { FirebaseAnalytics.getInstance(it).logEvent("add_multiple_widget", Bundle()) }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        if (context != null) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MultipleRateProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            ids?.forEach { id -> updateWidget(context, appWidgetManager, id) }
            // Notify the list adapter that data may have changed
            appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.lv_rates)
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_multiple)

        // Date stamp — use the most recently checked pinned rate
        val rates = try { AppZimRate.database.rates().getAllPinned() } catch (e: Exception) { emptyList() }
        // Skip the Instant.MIN "never checked" sentinel — it is out of range for LocalDateTime.
        val lastChecked = rates.map { it.lastChecked }
            .filter { it > Instant.EPOCH }
            .maxOrNull() ?: Instant.now()
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        val dateStr = LocalDateTime.ofInstant(lastChecked, ZoneId.systemDefault()).format(formatter)
        views.setTextViewText(R.id.txt_date_checked, dateStr)

        // Wire the scrollable list to the RemoteViewsService
        val serviceIntent = Intent(context, MultipleRateRemoteViewsService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            // Make the Intent unique per widget instance so each gets its own factory
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.lv_rates, serviceIntent)
        views.setEmptyView(R.id.lv_rates, R.id.txt_no_pinned)

        // Tap → open app
        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_main, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
