package com.tyganeutronics.myratecalculator.wear.data

import org.json.JSONArray
import org.json.JSONObject

data class WearRateModel(
    val currency: String,
    val name: String,
    val rate: String,
    val lastChecked: Long,
) {
    companion object {
        fun fromJson(obj: JSONObject) = WearRateModel(
            currency = obj.getString("currency"),
            name = obj.optString("name", ""),
            rate = obj.getString("rate"),
            lastChecked = obj.optLong("lastChecked", 0L),
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
    }
}

fun List<WearRateModel>.toJson(): String =
    JSONArray().also { arr -> forEach { arr.put(it.toJson()) } }.toString()
