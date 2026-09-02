package com.tyganeutronics.myratecalculator.wear.data

import android.content.Context
import androidx.core.content.edit

object WearRateStore {

    private const val PREFS = "wear_rates"
    private const val KEY_RATES = "rates_json"
    // Keyed per complication instance: the same data source can occupy several slots on one
    // watch face, and each slot needs its own currency.
    private const val KEY_SELECTED_PREFIX = "selected_currency_"

    fun save(context: Context, rates: List<WearRateModel>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_RATES, rates.toJson())
        }
    }

    fun load(context: Context): List<WearRateModel> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RATES, null) ?: return emptyList()
        return WearRateModel.listFromJson(json)
    }

    fun selectedCurrency(context: Context, instanceId: Int): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_PREFIX + instanceId, null)
    }

    fun setSelectedCurrency(context: Context, instanceId: Int, currency: String) {
        // Committed synchronously: the complication update is requested immediately after
        // this returns, so the new value has to be readable before that fires.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) {
            putString(KEY_SELECTED_PREFIX + instanceId, currency)
        }
    }

    fun resolvedCurrency(context: Context, instanceId: Int): WearRateModel? {
        val rates = load(context)
        if (rates.isEmpty()) return null

        // Never picked a currency for this slot — show the first pinned rate.
        val selected = selectedCurrency(context, instanceId) ?: return rates.first()

        // Picked one that is no longer pinned. Show nothing rather than quietly putting a
        // different currency's number in a slot the user labelled as something else — a wrong
        // exchange rate reads as correct, a blank slot does not.
        return rates.firstOrNull { it.currency == selected }
    }
}
