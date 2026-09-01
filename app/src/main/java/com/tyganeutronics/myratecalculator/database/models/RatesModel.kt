package com.tyganeutronics.myratecalculator.database.models

import android.content.Context
import com.apollographql.apollo.api.Optional
import com.tyganeutronics.myratecalculator.AppZimRate
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import com.tyganeutronics.myratecalculator.graphql.FetchRatesQuery
import com.tyganeutronics.myratecalculator.graphql.type.Prefer
import com.tyganeutronics.myratecalculator.utils.WidgetUtils
import com.tyganeutronics.myratecalculator.utils.traits.getStringPref
import com.tyganeutronics.myratecalculator.wear.WearSyncHelper
import java.math.BigDecimal
import java.time.Instant

/**
 * Fetching and persisting of server rates, shared by the rates screen and the background
 * refresh worker.
 *
 * Note this deliberately knows nothing about the in-memory rate overrides the user types
 * on the rates screen — only a user-initiated refresh clears those, in [RatesViewModel].
 */
object RatesModel {

    /** The aggregation strategy picked in settings, defaulting to the median. */
    fun preferred(context: Context): Prefer {
        val option = context.getStringPref("preferred_currency", "median").uppercase()
        return try {
            Prefer.valueOf(option)
        } catch (_: IllegalArgumentException) {
            Prefer.MEDIAN
        }
    }

    /** Queries the server. [singleCurrency] narrows the result to one currency. */
    suspend fun fetch(prefer: Prefer, singleCurrency: String? = null): List<RateEntity> {
        val response = AppZimRate
            .apolloClient
            .query(FetchRatesQuery(prefer = Optional.present(prefer)))
            .execute()

        // Apollo 4+ surfaces fetch errors on the response instead of throwing. Callers tell
        // "no rates" apart from "request failed", so keep failures propagating as before.
        response.exception?.let { throw it }

        return response.data?.rates?.mapNotNull { r ->
            if (singleCurrency != null && r.currency != singleCurrency) return@mapNotNull null

            RateEntity().apply {
                currency = r.currency ?: return@mapNotNull null
                name = r.name ?: currency
                url = r.url ?: ""
                rate = r.rate?.toString()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                lastChecked = r.last_updated?.toLong()
                    ?.let { Instant.ofEpochSecond(it) } ?: Instant.now()
            }
        } ?: emptyList()
    }

    /**
     * Persists [apiRates] in a single transaction so Room fires LiveData exactly once, then
     * pushes the pinned rows to the watch. Existing pin/hide/order state is carried over.
     */
    fun save(context: Context, apiRates: List<RateEntity>) {
        val dao = AppZimRate.database.rates()

        AppZimRate.database.runInTransaction {
            // Only inject a synthetic USD base for full refreshes (more than one currency).
            // Single-currency per-row refreshes should not touch USD.
            val ratesToSave = when {
                apiRates.any { it.currency == "USD" } -> apiRates
                apiRates.size > 1 -> apiRates + RateEntity().apply {
                    currency = "USD"
                    name = "US Dollar"
                    rate = BigDecimal.ONE
                    url = ""
                }

                else -> apiRates
            }

            val now = Instant.now()
            ratesToSave.forEach { incoming ->
                val existing = dao.findByCurrency(incoming.currency)
                if (existing != null) {
                    incoming.id = existing.id
                    incoming.pinned = existing.pinned
                    incoming.hidden = existing.hidden
                    incoming.createdAt = existing.createdAt
                    incoming.sortOrder = existing.sortOrder
                } else {
                    incoming.createdAt = now
                    incoming.sortOrder = Int.MAX_VALUE
                }
                if (incoming.currency == "USD") {
                    incoming.pinned = true
                }
                incoming.updatedAt = now
            }

            val sorted = ratesToSave.sortedWith(
                compareBy(
                    { it.currency != "USD" },
                    { !it.pinned },
                    { it.sortOrder },
                    { it.currency }
                )
            )
            sorted.forEachIndexed { index, entity -> entity.sortOrder = index }
            dao.insertAll(sorted)
        }

        WearSyncHelper.pushPinnedRates(context, dao.getAllPinned())
        WidgetUtils.refreshAll(context)
    }
}
