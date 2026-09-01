package com.tyganeutronics.myratecalculator.utils

import android.content.Context
import com.tyganeutronics.myratecalculator.R
import java.util.Currency
import java.util.Locale

object CurrencyFlagUtil {

    // Only for currencies where the JDK has no locale and take(2) gives the wrong country:
    // supranational/multi-country currencies.
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
                // ISO 4217 currency codes are prefixed with their country code (USD→us, ZAR→za).
                // When multiple countries share a currency, prefer the matching one.
                val preferred = currency.take(2).lowercase()
                countries.firstOrNull { it == preferred } ?: countries.first()
            }
    }

    fun countryCode(currencyCode: String): String {
        val upper = currencyCode.uppercase()
        return OVERRIDES[upper]
            ?: localeMap[upper]
            ?: upper.take(2).lowercase()
    }

    /**
     * Localised country name for a currency, shown in place of the code because a code means
     * nothing to most people.
     *
     * Inherits the approximation in [OVERRIDES]: currencies shared across countries resolve to
     * one representative country, so XAF reads as Cameroon. Falls back to the code where no
     * country name resolves, which includes EUR.
     */
    fun countryName(currencyCode: String): String {
        val region = countryCode(currencyCode)
        return runCatching { Locale.Builder().setRegion(region).build().displayCountry }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() && !it.equals(region, ignoreCase = true) }
            ?: currencyCode.uppercase()
    }

    /**
     * The label a rate is shown under: "ZAR · South Africa".
     *
     * Pass [name] to override the country name — a custom rate carries the user's own label, and
     * a code they invented would otherwise resolve to an unrelated country. The code is returned
     * on its own where the two would repeat, which [countryName] causes for EUR and for a custom
     * rate saved without a name.
     */
    fun codeWithName(
        context: Context,
        currencyCode: String,
        name: String = countryName(currencyCode),
    ): String = if (name.equals(currencyCode, ignoreCase = true)) {
        currencyCode.uppercase()
    } else {
        context.getString(R.string.currency_code_with_name, currencyCode.uppercase(), name)
    }
}
