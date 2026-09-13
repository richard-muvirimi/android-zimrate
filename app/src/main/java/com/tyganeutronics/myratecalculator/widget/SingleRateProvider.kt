package com.tyganeutronics.myratecalculator.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.google.firebase.analytics.FirebaseAnalytics
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.activities.MainActivity
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import com.tyganeutronics.myratecalculator.database.rtdb.CurrencyRepository
import com.tyganeutronics.myratecalculator.utils.WidgetUtils
import com.tyganeutronics.myratecalculator.utils.traits.getStringPref
import com.tyganeutronics.myratecalculator.utils.traits.removePref
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.math.RoundingMode

class SingleRateProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetIds: IntArray?
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        if (context == null || appWidgetIds == null) return
        renderAsync(context, appWidgetIds)
    }

    override fun onEnabled(context: Context?) {
        super.onEnabled(context)
        context?.let { FirebaseAnalytics.getInstance(it).logEvent("add_single_widget", Bundle()) }
    }

    /**
     * Reads the rate off the broadcast thread rather than on it.
     *
     * Room answers instantly and would have been fine here, but the store that replaces it has
     * no synchronous read at all — so the threading is changed now, against a database whose
     * output is known, rather than in the same commit that swaps the store underneath it.
     *
     * [goAsync] keeps the broadcast alive for the read. The loading view is only drawn when this
     * widget has never shown anything: the launcher retains the last RemoteViews it was given,
     * so an existing widget keeps its values on screen until real ones replace them.
     *
     * Reached only through [onUpdate], and there must be no onReceive override calling it as well.
     * [goAsync] hands out the pending result once and nulls its own reference, so a second call
     * inside one broadcast returns null — and since [AppWidgetProvider.onReceive] already routes
     * an APPWIDGET_UPDATE here, an override that rendered again would take that null and crash on
     * finish. That is exactly what used to happen on every rate refresh.
     */
    private fun renderAsync(context: Context, appWidgetIds: IntArray) {
        val pendingResult = goAsync()

        appWidgetIds.forEach { id ->
            if (!WidgetUtils.hasRendered(context, id)) {
                drawPlaceholder(context, id, context.getString(R.string.widget_loading))
            }
        }

        WidgetUtils.scope.launch {
            try {
                appWidgetIds.forEach { id ->
                    val drawn = withTimeoutOrNull(WidgetUtils.READ_TIMEOUT_MS) {
                        drawRate(context, id)
                    }

                    // A read that never came back leaves a first-time widget spinning forever,
                    // so it gets an instruction instead. One that has drawn before keeps what
                    // it already shows, which is stale but true.
                    if (drawn == null && !WidgetUtils.hasRendered(context, id)) {
                        drawPlaceholder(context, id, context.getString(R.string.widget_unavailable))
                    }
                }
            } finally {
                // Nullable by contract — see the note above. Not crashing is the only sane
                // response: there is no broadcast left to release.
                pendingResult?.finish()
            }
        }
    }

    private suspend fun drawRate(context: Context, appWidgetId: Int) {
        val views = baseViews(context)

        // Retrieve saved currency preference for this widget instance
        val currency = context.getStringPref("widget-$appWidgetId", "USD")


        // Waits for the listener rather than reading a half-warm cache — the timeout in
        // renderAsync is what stops that becoming an endless spinner.
        CurrencyRepository.awaitLoaded()
        val entity: RateEntity? = CurrencyRepository.findByCurrency(currency)

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
        val checked = entity?.lastChecked?.let { WidgetUtils.formatChecked(context, it) }.orEmpty()
        views.setTextViewText(R.id.txt_single_date, checked)
        views.setViewVisibility(
            R.id.txt_single_date,
            if (checked.isEmpty()) View.GONE else View.VISIBLE,
        )

        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        WidgetUtils.markRendered(context, appWidgetId)
    }

    /** The widget's frame with a message where the rate would be, and no stale numbers in it. */
    private fun drawPlaceholder(context: Context, appWidgetId: Int, message: String) {
        val views = baseViews(context)

        views.setImageViewResource(R.id.img_single_flag, R.mipmap.ic_launcher)
        views.setTextViewText(R.id.txt_single_name, message)
        views.setTextViewText(R.id.txt_single_rate, "—")
        views.setViewVisibility(R.id.txt_single_date, View.GONE)

        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
    }

    /** Everything that does not depend on a rate being read, including the tap target. */
    private fun baseViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_single)

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_main, pendingIntent)

        return views
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds?.forEach { id ->
            context?.removePref("widget-$id")
            context?.let { WidgetUtils.clearRendered(it, id) }
        }
    }
}
