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
import com.tyganeutronics.myratecalculator.wear.data.displayName
import com.tyganeutronics.myratecalculator.wear.data.label
import com.tyganeutronics.myratecalculator.wear.presentation.MainActivity
import com.tyganeutronics.myratecalculator.wear.util.countryCode

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
     * The title carries the currency code rather than a country name because a short-text slot
     * renders only a handful of characters, and a code always fits where "South Africa" would
     * be cut. The flag is still attached for the faces that draw small images; most do not, so
     * the title is what actually identifies the currency in practice.
     */
    private fun shortText(rate: WearRateModel, tap: PendingIntent? = null) =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(rate.rate).build(),
            contentDescription =
                PlainComplicationText.Builder("${rate.currency} ${rate.rate}").build(),
        )
            .setTitle(PlainComplicationText.Builder(rate.currency).build())
            .setTapAction(tap)
            .apply { flagImage(rate)?.let { setSmallImage(it) } }
            .build()

    private fun longText(rate: WearRateModel, tap: PendingIntent? = null) =
        LongTextComplicationData.Builder(
            text = PlainComplicationText.Builder(rate.rate).build(),
            contentDescription =
                PlainComplicationText.Builder("${rate.currency} ${rate.rate}").build(),
        )
            .setTitle(
                PlainComplicationText.Builder(rate.label(applicationContext, shortName(rate)))
                    .build()
            )
            .setTapAction(tap)
            .apply { flagImage(rate)?.let { setSmallImage(it) } }
            .build()

    /**
     * A currency code means nothing to most people, so the title names where the money is from
     * as well. The platform localises the country name, so this follows the ten locales the app
     * ships without needing any new strings.
     *
     * Complication titles are only a few characters wide, hence the trim back to a word
     * boundary. Trimmed before the code is attached so the code survives — it is the half that
     * identifies the rate unambiguously.
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
