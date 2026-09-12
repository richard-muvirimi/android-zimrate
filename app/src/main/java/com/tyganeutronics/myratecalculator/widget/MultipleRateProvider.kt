package com.tyganeutronics.myratecalculator.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
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

        // The stamp is redrawn above, but the rows come from MultipleRateRemoteViewsService and
        // only reload when told to.
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.lv_rates)
    }

    override fun onEnabled(context: Context?) {
        super.onEnabled(context)
        context?.let { FirebaseAnalytics.getInstance(it).logEvent("add_multiple_widget", Bundle()) }
    }

    /**
     * Only the sync stamp is read here — the list itself is filled by
     * [MultipleRateRemoteViewsService], which is handed a binder thread and is allowed to block
     * on it. So this is the sole part of the multiple-rate widget that needs moving off the
     * broadcast thread ahead of the store swap.
     *
     * The adapter is wired up front rather than after the read, so the list starts loading while
     * the stamp is still being worked out.
     *
     * Reached only through [onUpdate], and there must be no onReceive override calling it as well.
     * [goAsync] hands out the pending result once and nulls its own reference, so a second call
     * inside one broadcast returns null — and since [AppWidgetProvider.onReceive] already routes
     * an APPWIDGET_UPDATE here, an override that rendered again would take that null and crash on
     * finish. That is exactly what used to happen on every rate refresh.
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
                // Nullable by contract — see the note above. Not crashing is the only sane
                // response: there is no broadcast left to release.
                pendingResult?.finish()
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
