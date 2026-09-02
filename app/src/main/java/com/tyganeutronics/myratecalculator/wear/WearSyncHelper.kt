package com.tyganeutronics.myratecalculator.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object WearSyncHelper {

    private const val PATH = "/zimrate/rates"

    fun pushPinnedRates(context: Context, rates: List<RateEntity>) {
        val json = JSONArray().apply {
            rates.filter { it.pinned }.forEach { entity ->
                // Instant.MIN is the "never checked" sentinel and overflows toEpochMilli().
                // The watch already reads 0 as unknown (WearRateModel.fromJson, MainActivity).
                val lastChecked = entity.lastChecked
                    .takeIf { it > Instant.EPOCH }
                    ?.toEpochMilli() ?: 0L

                put(JSONObject().apply {
                    put("currency", entity.currency)
                    put("name", entity.name)
                    // The watch cannot tell an invented code from an ISO one, and would name and
                    // flag OMIR after Oman without being told.
                    put("custom", entity.custom)
                    put("rate", entity.rate.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
                    // Rounded the same way as the rate above, so the watch only marks a move it
                    // can actually show — a change too small to alter the displayed number is not
                    // one the wearer can see.
                    put(
                        "lastRate",
                        entity.lastRate.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                    )
                    put("lastChecked", lastChecked)
                })
            }
        }.toString()

        val request = PutDataMapRequest.create(PATH).apply {
            dataMap.putString("rates", json)
            dataMap.putLong("updated", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(request)
    }
}
