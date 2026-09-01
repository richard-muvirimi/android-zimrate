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

/**
 * Localised country name for a currency, shown in place of the code because a code means
 * nothing to most people.
 *
 * Inherits the approximation in [OVERRIDES]: currencies shared across countries resolve to one
 * representative country, so XAF reads as Cameroon. Falls back to the code where no country
 * name resolves, which includes EUR.
 */
fun countryName(currencyCode: String): String {
    val region = countryCode(currencyCode)
    return runCatching { Locale.Builder().setRegion(region).build().displayCountry }
        .getOrNull()
        ?.takeIf { it.isNotEmpty() && !it.equals(region, ignoreCase = true) }
        ?: currencyCode.uppercase()
}
