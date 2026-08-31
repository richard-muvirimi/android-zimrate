package com.tyganeutronics.myratecalculator.wear.data

import android.content.Context
import androidx.core.content.edit

object WearRateStore {

    private const val PREFS = "wear_rates"
    private const val KEY_RATES = "rates_json"
    private const val KEY_SELECTED = "selected_currency"

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

    fun selectedCurrency(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED, null)
    }

    fun setSelectedCurrency(context: Context, currency: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_SELECTED, currency)
        }
    }

    fun resolvedCurrency(context: Context): WearRateModel? {
        val rates = load(context)
        if (rates.isEmpty()) return null
        val selected = selectedCurrency(context)
        return rates.firstOrNull { it.currency == selected } ?: rates.first()
    }
}
