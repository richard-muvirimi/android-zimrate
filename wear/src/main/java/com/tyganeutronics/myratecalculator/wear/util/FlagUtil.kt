package com.tyganeutronics.myratecalculator.wear.util

import java.util.Currency
import java.util.Locale

private val OVERRIDES = mapOf(
    "EUR" to "eu",
    "XAF" to "cm",
    "XOF" to "sn",
    "XCD" to "ag",
    "XPF" to "pf",
    "ANG" to "cw",
)

private val localeMap: Map<String, String> by lazy {
    Locale.getAvailableLocales()
        .filter { it.country.isNotEmpty() }
        .mapNotNull { locale ->
            runCatching { Currency.getInstance(locale) }.getOrNull()
                ?.let { it.currencyCode to locale.country.lowercase() }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (currency, countries) ->
            val preferred = currency.take(2).lowercase()
            countries.firstOrNull { it == preferred } ?: countries.first()
        }
}

fun countryCode(currencyCode: String): String {
    val upper = currencyCode.uppercase()
    return OVERRIDES[upper] ?: localeMap[upper] ?: upper.take(2).lowercase()
}
