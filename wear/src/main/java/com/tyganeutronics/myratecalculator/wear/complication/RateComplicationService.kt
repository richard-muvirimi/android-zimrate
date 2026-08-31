package com.tyganeutronics.myratecalculator.wear.complication

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.tyganeutronics.myratecalculator.wear.data.WearRateStore

class RateComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> shortText("ZWG", "37.50")
            ComplicationType.LONG_TEXT -> longText("ZWG", "37.50")
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val rate = WearRateStore.resolvedCurrency(applicationContext) ?: return null
        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> shortText(rate.currency, rate.rate)
            ComplicationType.LONG_TEXT -> longText(rate.currency, rate.rate)
            else -> null
        }
    }

    private fun shortText(currency: String, rate: String) =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(rate).build(),
            contentDescription = PlainComplicationText.Builder("$currency $rate").build(),
        )
            .setTitle(PlainComplicationText.Builder(currency).build())
            .build()

    private fun longText(currency: String, rate: String) =
        LongTextComplicationData.Builder(
            text = PlainComplicationText.Builder(rate).build(),
            contentDescription = PlainComplicationText.Builder("$currency $rate").build(),
        )
            .setTitle(PlainComplicationText.Builder(currency).build())
            .build()

    companion object {
        fun notifyUpdate(context: Context) {
            androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, RateComplicationService::class.java))
                .requestUpdateAll()
        }
    }
}
