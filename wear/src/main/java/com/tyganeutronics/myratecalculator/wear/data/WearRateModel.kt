package com.tyganeutronics.myratecalculator.wear.data

import android.content.Context
import com.tyganeutronics.myratecalculator.wear.util.codeWithName
import com.tyganeutronics.myratecalculator.wear.util.countryName
import org.json.JSONArray
import org.json.JSONObject

data class WearRateModel(
    val currency: String,
    val name: String,
    val rate: String,
    val lastChecked: Long,
    /** Added by the user on the phone, so its code is not ISO and resolves to no country. */
    val custom: Boolean = false,
    /** What [rate] is compared against for the movement mark. Empty from an older phone build. */
    val lastRate: String = "",
) {
    companion object {
        fun fromJson(obj: JSONObject) = WearRateModel(
            currency = obj.getString("currency"),
            name = obj.optString("name", ""),
            rate = obj.getString("rate"),
            lastChecked = obj.optLong("lastChecked", 0L),
            custom = obj.optBoolean("custom", false),
            lastRate = obj.optString("lastRate", ""),
        )

        fun listFromJson(json: String): List<WearRateModel> = runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("currency", currency)
        put("name", name)
        put("rate", rate)
        put("lastChecked", lastChecked)
        put("custom", custom)
        put("lastRate", lastRate)
    }
}

fun List<WearRateModel>.toJson(): String =
    JSONArray().also { arr -> forEach { arr.put(it.toJson()) } }.toString()

/**
 * What the rate is shown as: "ZAR · South Africa", or the user's own name where they added it
 * themselves — a code they invented resolves to an unrelated country otherwise.
 */
fun WearRateModel.label(context: Context, shortenedName: String? = null): String =
    codeWithName(context, currency, shortenedName ?: displayName())

/** The name half of [label], before the code is attached. */
fun WearRateModel.displayName(): String =
    if (custom) name.ifEmpty { currency } else countryName(currency)
