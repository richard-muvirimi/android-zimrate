package com.tyganeutronics.myratecalculator.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.core.graphics.drawable.toBitmap
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.murgupluoglu.flagkit.FlagKit
import com.tyganeutronics.myratecalculator.wear.data.WearRateModel
import com.tyganeutronics.myratecalculator.wear.data.WearRateStore
import com.tyganeutronics.myratecalculator.wear.data.append
import com.tyganeutronics.myratecalculator.wear.data.displayName
import com.tyganeutronics.myratecalculator.wear.data.move
import com.tyganeutronics.myratecalculator.wear.presentation.MainActivity
import com.tyganeutronics.myratecalculator.wear.util.countryCode
import java.util.Locale

class RateComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        // The watch face editor renders this while a slot is being configured, which is why a
        // freshly picked currency does not appear until you leave the editor. No instance id
        // is supplied here, so this cannot reflect a per-slot choice — showing the first
        // pinned rate is the closest honest stand-in for real data.
        val sample = WearRateStore.load(applicationContext).firstOrNull()
            ?: WearRateModel(SAMPLE_CURRENCY, "", SAMPLE_RATE, 0L)

        return when (type) {
            ComplicationType.SHORT_TEXT -> shortText(sample)
            ComplicationType.LONG_TEXT -> longText(sample)
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val rate = WearRateStore.resolvedCurrency(
            applicationContext,
            request.complicationInstanceId,
        ) ?: return null

        val tap = tapAction(request.complicationInstanceId, rate.currency)

        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> shortText(rate, tap)
            ComplicationType.LONG_TEXT -> longText(rate, tap)
            else -> null
        }
    }

    /**
     * Opens the app scrolled to this slot's currency.
     *
     * Extras are ignored when PendingIntents are compared, so two ZimRate complications would
     * share one intent and both open the first slot's currency. The instance id is used as the
     * request code to keep them distinct.
     */
    private fun tapAction(instanceId: Int, currency: String): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_CURRENCY, currency)

        return PendingIntent.getActivity(
            applicationContext,
            instanceId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * A small slot, so the code goes in the title and the number in the text and the watch face
     * stacks the two. That stacking is the face's to decide, not ours — and on a slot this narrow
     * it is also the only way both halves fit at a readable size.
     *
     * The flag is still attached for the faces that draw small images; most do not, so the title
     * is what actually identifies the currency in practice.
     */
    private fun shortText(rate: WearRateModel, tap: PendingIntent? = null) =
        ShortTextComplicationData.Builder(
            // Only the mark, no colour: a complication hands the watch face data, and the face
            // decides how to paint it. The arrow is the one channel a data source controls.
            // No separator either — a short text slot renders very few characters.
            text = PlainComplicationText.Builder(
                rate.move().append(compactRate(rate.rate), "")
            ).build(),
            // Spoken in full: the compaction above is a space constraint, not a precision choice.
            contentDescription =
                PlainComplicationText.Builder("${rate.currency} ${rate.rate}").build(),
        )
            .setTitle(PlainComplicationText.Builder(rate.currency).build())
            .setTapAction(tap)
            .apply { flagImage(rate)?.let { setSmallImage(it) } }
            .build()

    /**
     * The wide slot, and the one to pick for a rate that reads across rather than down: the code
     * and the number share the text line, so the face has a single horizontal string to place.
     *
     * The code is on that line rather than only in the title because a long-text slot that is
     * short on height renders the title away entirely, and a number with no currency next to it
     * says nothing. The title repeats no part of it — it adds the country.
     */
    private fun longText(rate: WearRateModel, tap: PendingIntent? = null) =
        LongTextComplicationData.Builder(
            text = PlainComplicationText.Builder(
                rate.move().append("${rate.currency} ${rate.rate}")
            ).build(),
            contentDescription =
                PlainComplicationText.Builder("${rate.currency} ${rate.rate}").build(),
        )
            .setTitle(PlainComplicationText.Builder(shortName(rate)).build())
            .setTapAction(tap)
            .apply { flagImage(rate)?.let { setSmallImage(it) } }
            .build()

    /**
     * The rate, narrowed to what a short text slot can actually draw.
     *
     * Such a slot renders about seven characters; past that the watch face shrinks or clips what
     * it was given, which is why a four figure rate came out mangled while a two figure one beside
     * it looked right. A rate already inside the budget is passed through untouched, so the common
     * two figure case is exactly the string the phone sent.
     *
     * Each step gives up the least it can — a decimal place before an order of magnitude, since
     * the cents on a rate in the thousands are below what anyone reads off a watch but the
     * magnitude never is — and the first result that fits wins. Choosing by measured width rather
     * than by value brackets is what keeps a rate that rounds up across a bracket (999.95, or
     * 99999.5) from spilling the one extra character that bracket arithmetic let through.
     */
    private fun compactRate(rate: String): String {
        if (rate.length <= MAX_RATE_CHARS) return rate
        val value = rate.toDoubleOrNull() ?: return rate.take(MAX_RATE_CHARS)

        return NARROWINGS.asSequence()
            .map { it(value) }
            .firstOrNull { it.length <= MAX_RATE_CHARS }
            ?: rate.take(MAX_RATE_CHARS)
    }

    /**
     * A currency code means nothing to most people, so the title names where the money is from.
     * The platform localises the country name, so this follows the ten locales the app ships
     * without needing any new strings.
     *
     * Complication titles are only a few characters wide, hence the trim back to a word
     * boundary. The code is no longer appended here — it sits on the text line, where it cannot
     * be trimmed away.
     */
    private fun shortName(rate: WearRateModel): String {
        val name = rate.displayName()
        if (name.length <= MAX_TITLE_CHARS) return name

        val clipped = name.take(MAX_TITLE_CHARS)
        val boundary = clipped.lastIndexOf(' ')
        return if (boundary >= MIN_WORD_CHARS) clipped.take(boundary) else clipped.trimEnd() + "…"
    }

    /**
     * Rendered only by watch faces that support small images, so it supplements the title
     * rather than replacing it. PHOTO keeps the flag's own colours; ICON would tint it flat.
     *
     * Nothing for a rate the user added: its code is not ISO, so a lookup would return an
     * unrelated country's flag.
     */
    private fun flagImage(rate: WearRateModel): SmallImage? {
        if (rate.custom) return null

        val code = countryCode(rate.currency).takeIf { it.isNotEmpty() } ?: return null
        val resId = runCatching { FlagKit.getResId(applicationContext, code) }.getOrNull() ?: return null
        if (resId == 0) return null

        // FlagKit resolves to vector drawables on API 24+, which cannot cross into the watch
        // face process by resource id, so the icon carries a rasterised bitmap.
        val drawable = runCatching { applicationContext.getDrawable(resId) }.getOrNull() ?: return null
        val icon = Icon.createWithBitmap(drawable.toBitmap(FLAG_PX_WIDTH, FLAG_PX_HEIGHT))

        return SmallImage.Builder(icon, SmallImageType.PHOTO).build()
    }

    companion object {

        /** Shown in the editor before any rates have synced. */
        private const val SAMPLE_CURRENCY = "ZWG"
        private const val SAMPLE_RATE = "37.50"

        private const val MAX_TITLE_CHARS = 10
        private const val MIN_WORD_CHARS = 4

        /**
         * Width the rate has to fit in a short text slot. One under the seven such a slot draws,
         * because the movement mark is appended to it and has to land inside the same budget.
         */
        private const val MAX_RATE_CHARS = 6

        /**
         * Ways to spend fewer characters on a rate, least lossy first. Formatted against
         * [Locale.US] like the string the phone sends, so a rate that passes through untouched
         * and one that gets narrowed do not disagree about the decimal separator.
         */
        private val NARROWINGS: List<(Double) -> String> = listOf(
            { v -> "%.1f".format(Locale.US, v) },
            { v -> "%.0f".format(Locale.US, v) },
            { v -> "%.1fk".format(Locale.US, v / 1_000) },
            { v -> "%.0fk".format(Locale.US, v / 1_000) },
            { v -> "%.1fm".format(Locale.US, v / 1_000_000) },
            { v -> "%.0fm".format(Locale.US, v / 1_000_000) },
        )
        private const val FLAG_PX_WIDTH = 96
        private const val FLAG_PX_HEIGHT = 64

        /**
         * Refreshes the named slots, or every slot when none are given. Targeting the slot
         * that just changed gives the watch face editor a specific instance to re-request,
         * where requestUpdateAll leaves it to decide when to refetch.
         */
        fun notifyUpdate(context: Context, vararg complicationInstanceIds: Int) {
            val requester = androidx.wear.watchface.complications.datasource
                .ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, RateComplicationService::class.java))

            if (complicationInstanceIds.isEmpty()) {
                requester.requestUpdateAll()
            } else {
                requester.requestUpdate(*complicationInstanceIds)
            }
        }
    }
}
