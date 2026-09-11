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
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.activities.MainActivity
import com.tyganeutronics.myratecalculator.database.rtdb.CurrencyRepository
import com.tyganeutronics.myratecalculator.utils.WidgetUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

class MultipleRateProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetIds: IntArray?
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        if (context == null || appWidgetManager == null || appWidgetIds == null) return
        renderAsync(context, appWidgetManager, appWidgetIds)
    }

    override fun onEnabled(context: Context?) {
        super.onEnabled(context)
        context?.let { FirebaseAnalytics.getInstance(it).logEvent("add_multiple_widget", Bundle()) }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        if (context == null) return

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, MultipleRateProvider::class.java)
        val ids = appWidgetManager.getAppWidgetIds(componentName) ?: return

        renderAsync(context, appWidgetManager, ids)
        // Notify the list adapter that data may have changed
        appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.lv_rates)
    }

    /**
     * Only the sync stamp is read here — the list itself is filled by
     * [MultipleRateRemoteViewsService], which is handed a binder thread and is allowed to block
     * on it. So this is the sole part of the multiple-rate widget that needs moving off the
     * broadcast thread ahead of the store swap.
     *
     * The adapter is wired up front rather than after the read, so the list starts loading while
     * the stamp is still being worked out.
     */
    private fun renderAsync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()

        appWidgetIds.forEach { id ->
            val message = if (WidgetUtils.hasRendered(context, id)) "" else {
                context.getString(R.string.widget_loading)
            }
            appWidgetManager.updateAppWidget(id, buildViews(context, id, message))
        }

        WidgetUtils.scope.launch {
            try {
                appWidgetIds.forEach { id ->
                    val stamp = withTimeoutOrNull(WidgetUtils.READ_TIMEOUT_MS) { lastChecked() }

                    if (stamp == null) {
                        if (!WidgetUtils.hasRendered(context, id)) {
                            val message = context.getString(R.string.widget_unavailable)
                            appWidgetManager.updateAppWidget(id, buildViews(context, id, message))
                        }
                        return@forEach
                    }

                    val message = WidgetUtils.formatChecked(stamp)
                    appWidgetManager.updateAppWidget(id, buildViews(context, id, message))
                    WidgetUtils.markRendered(context, id)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Date stamp — the most recently checked pinned rate. */
    private suspend fun lastChecked(): Instant {
        CurrencyRepository.awaitLoaded()
        val rates = CurrencyRepository.allPinned()

        // Skip the Instant.MIN "never checked" sentinel — it is not representable in millis.
        return rates.map { it.lastChecked }
            .filter { it > Instant.EPOCH }
            .maxOrNull() ?: Instant.now()
    }

    private fun buildViews(context: Context, appWidgetId: Int, checked: String): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_multiple)

        views.setTextViewText(R.id.txt_date_checked, checked)

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

        return views
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds?.forEach { id ->
            context?.let { WidgetUtils.clearRendered(it, id) }
        }
    }
}
