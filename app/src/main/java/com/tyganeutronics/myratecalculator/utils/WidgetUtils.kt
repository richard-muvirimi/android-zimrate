package com.tyganeutronics.myratecalculator.utils

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.text.format.DateUtils
import com.murgupluoglu.flagkit.FlagKit
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.utils.traits.getBooleanPref
import com.tyganeutronics.myratecalculator.utils.traits.putBooleanPref
import com.tyganeutronics.myratecalculator.utils.traits.removePref
import com.tyganeutronics.myratecalculator.widget.MultipleRateProvider
import com.tyganeutronics.myratecalculator.widget.SingleRateProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Instant

object WidgetUtils {

    /**
     * How long a widget may spend reading before it gives up and says so. A broadcast has
     * roughly ten seconds before the system loses patience, and drawing the fallback has to
     * happen inside that, so the read gets rather less than all of it.
     */
    const val READ_TIMEOUT_MS = 6_000L

    /**
     * Outlives any one broadcast on purpose — [android.content.BroadcastReceiver.PendingResult]
     * keeps the process alive for the work, and the receiver instance itself is discarded as
     * soon as onReceive returns.
     */
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Whether this widget has ever drawn real data.
     *
     * The launcher keeps the last RemoteViews it was handed, so after a reboot a placed widget
     * still shows its previous values until something replaces them. Pushing a loading view on
     * every update would throw that away and make the widget blink on a cadence, so the loading
     * state is only ever drawn when there is genuinely nothing there yet.
     */
    fun hasRendered(context: Context, appWidgetId: Int): Boolean =
        context.getBooleanPref(renderedKey(appWidgetId), false)

    fun markRendered(context: Context, appWidgetId: Int) {
        context.putBooleanPref(renderedKey(appWidgetId), true)
    }

    fun clearRendered(context: Context, appWidgetId: Int) {
        context.removePref(renderedKey(appWidgetId))
    }

    private fun renderedKey(appWidgetId: Int) = "widget-rendered-$appWidgetId"

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
