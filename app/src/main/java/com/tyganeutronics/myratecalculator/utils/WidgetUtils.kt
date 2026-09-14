package com.tyganeutronics.myratecalculator.utils

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.text.format.DateUtils
import android.util.Log
import com.murgupluoglu.flagkit.FlagKit
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.utils.traits.getBooleanPref
import com.tyganeutronics.myratecalculator.utils.traits.putBooleanPref
import com.tyganeutronics.myratecalculator.utils.traits.removePref
import com.tyganeutronics.myratecalculator.widget.MultipleRateProvider
import com.tyganeutronics.myratecalculator.widget.SingleRateProvider
import kotlinx.coroutines.CoroutineExceptionHandler
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

    private const val TAG = "WidgetUtils"

    /**
     * Outlives any one broadcast on purpose — [android.content.BroadcastReceiver.PendingResult]
     * keeps the process alive for the work, and the receiver instance itself is discarded as
     * soon as onReceive returns.
     *
     * The handler is not decoration. A [SupervisorJob] stops a failing child cancelling its
     * siblings; it does nothing about an exception nobody catches, which goes to the thread's
     * default handler and takes the process down. That is how a null pending result turned a
     * widget refresh into a crash on every update, and the next mistake in here would do the
     * same. A widget that cannot draw should keep showing yesterday's rate, not kill the app.
     */
    val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, error ->
            Log.w(TAG, "Widget render failed", error)
        }
    )

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
     * When the rate was last checked, as a clock time today and a date before that. Empty for the
     * Instant.MIN "never checked" sentinel, which is not representable in millis.
     *
     * Absolute rather than the relative "3 hours ago" the rates screen shows, because a widget is
     * not redrawn on a timer — `updatePeriodMillis` is 0 on both providers, so the only redraws
     * come from a rate being saved. A relative label is computed once and then frozen, which is
     * how a widget ends up insisting a rate was checked five hours ago two days later. A clock
     * time stays true however long it sits there.
     *
     * Formatted through [DateUtils.formatDateTime] rather than `java.text.DateFormat`, because
     * only the context-aware one follows the user's 12- versus 24-hour setting; the bare
     * java.text formatters follow the locale's default and ignore the toggle.
     */
    fun formatChecked(context: Context, lastChecked: Instant): String {
        if (lastChecked <= Instant.EPOCH) return ""

        val millis = lastChecked.toEpochMilli()

        val flags = if (DateUtils.isToday(millis)) {
            DateUtils.FORMAT_SHOW_TIME
        } else {
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NUMERIC_DATE
        }

        return DateUtils.formatDateTime(context, millis, flags)
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
        // Nullable, and Kotlin does not say so because it is a platform type. There is no
        // AppWidget service at all on a device or profile without the widget host — Android TV,
        // some managed profiles — and this runs on every rate save, whether or not a widget was
        // ever placed. Nothing to refresh there, so nothing to do.
        val manager = AppWidgetManager.getInstance(context) ?: return

        val ids = manager.getAppWidgetIds(ComponentName(context, provider))
        if (ids.isEmpty()) return

        context.sendBroadcast(Intent(context, provider).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        })
    }
}
