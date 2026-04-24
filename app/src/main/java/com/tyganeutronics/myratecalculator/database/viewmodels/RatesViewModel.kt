package com.tyganeutronics.myratecalculator.database.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.tyganeutronics.myratecalculator.AppZimRate
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant

class RatesViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppZimRate.database.rates()


    init {
        viewModelScope.launch(Dispatchers.IO) {
            AppZimRate.database.runInTransaction {
                dao.pinUsd()
                normalizeVisibleSortOrder()
            }
        }
    }

    /** Visible rates from Room, ordered by USD first, then pinned, then the rest. */
    val rates: LiveData<List<RateEntity>> = dao.getAllSorted()

    /** Rates the user has hidden. */
    val hiddenRates: LiveData<List<RateEntity>> = dao.getAllHidden()

    /** In-memory rate overrides entered by the user (not persisted to DB). */
    private val _rateOverrides = MutableStateFlow<Map<String, BigDecimal>>(emptyMap())
    val rateOverrides: StateFlow<Map<String, BigDecimal>> = _rateOverrides.asStateFlow()

    /** The currency whose amount field the user last edited. */
    private val _activeCurrency = MutableStateFlow<String?>(null)
    val activeCurrency: StateFlow<String?> = _activeCurrency.asStateFlow()

    /** The amount the user typed in the active currency's field. */
    private val _activeAmount = MutableStateFlow(BigDecimal.ONE)
    val activeAmount: StateFlow<BigDecimal> = _activeAmount.asStateFlow()

    /** Update when the user edits an amount input field. */
    fun setActiveAmount(currency: String, amount: BigDecimal) {
        _activeCurrency.value = currency
        _activeAmount.value = amount
    }

    /** Update when the user manually edits a rate input field. */
    fun setRateOverride(currency: String, rate: BigDecimal) {
        _rateOverrides.value = _rateOverrides.value + (currency to rate)
    }

    /** Returns the effective rate for a currency (override takes precedence over DB value). */
    fun effectiveRate(entity: RateEntity): BigDecimal =
        _rateOverrides.value[entity.currency] ?: entity.rate

    /**
     * Calculates what [entity]'s amount should be given the currently active currency/amount.
     * Formula: target_amount = active_amount × target_rate / source_rate
     */
    fun calculateAmount(entity: RateEntity): BigDecimal {
        val sourceCurrency = _activeCurrency.value ?: "USD"
        if (entity.currency == sourceCurrency) return _activeAmount.value

        val sourceRate = _rateOverrides.value[sourceCurrency]
            ?: rates.value?.find { it.currency == sourceCurrency }?.rate
            ?: BigDecimal.ONE

        val targetRate = effectiveRate(entity)

        if (sourceRate.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO

        return _activeAmount.value
            .multiply(targetRate)
            .divide(sourceRate, MathContext(10, RoundingMode.HALF_UP))
            .setScale(2, RoundingMode.HALF_UP)
    }

    /** Hide a rate from the main list. USD cannot be hidden. */
    fun hideRate(entity: RateEntity) {
        if (entity.currency == "USD") return
        viewModelScope.launch(Dispatchers.IO) {
            dao.setHidden(entity.currency, true)
        }
    }

    /** Restore a previously hidden rate back to the main list. */
    fun restoreRate(entity: RateEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.setHidden(entity.currency, false)
        }
    }

    /** Toggle the pinned state for a rate and persist it to Room. USD stays pinned always. */
    fun togglePin(entity: RateEntity) {
        if (entity.currency == "USD") return
        viewModelScope.launch(Dispatchers.IO) {
            AppZimRate.database.runInTransaction {
                val fresh = dao.findByCurrency(entity.currency) ?: return@runInTransaction
                fresh.pinned = !fresh.pinned
                fresh.sortOrder = Int.MAX_VALUE
                fresh.updatedAt = Instant.now()
                dao.update(fresh)
                normalizeVisibleSortOrder()
            }
        }
    }

    fun persistOrder(entities: List<RateEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            AppZimRate.database.runInTransaction {
                applyTieredSortOrder(entities)
            }
        }
    }

    /**
     * Upsert a list of fresh rates from the API into Room.
     * Preserves the `pinned` and `hidden` flags of existing records.
     * All saves happen inside a single SQLite transaction so Room fires LiveData exactly once.
     */
    fun saveApiRates(apiRates: List<RateEntity>) {
        _rateOverrides.value = emptyMap()
        _activeCurrency.value = null
        _activeAmount.value = BigDecimal.ONE
        viewModelScope.launch(Dispatchers.IO) {
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
                ))
                sorted.forEachIndexed { index, entity -> entity.sortOrder = index }
                dao.insertAll(sorted)
            }
        }
    }

    private fun normalizeVisibleSortOrder() {
        val normalized = dao.getAll()
            .sortedWith(
                compareBy<RateEntity>(
                    { it.currency != "USD" },
                    { !it.pinned },
                    { it.sortOrder },
                    { it.currency }
                )
            )
        applyTieredSortOrder(normalized)
    }

    private fun applyTieredSortOrder(entities: List<RateEntity>) {
        entities.forEachIndexed { index, entity ->
            dao.setSortOrder(entity.currency, index)
            dao.setPinned(entity.currency, entity.currency == "USD" || entity.pinned)
        }
    }
}
