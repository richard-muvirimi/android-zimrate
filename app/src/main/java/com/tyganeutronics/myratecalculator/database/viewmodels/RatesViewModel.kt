package com.tyganeutronics.myratecalculator.database.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.tyganeutronics.myratecalculator.AppZimRate
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import com.tyganeutronics.myratecalculator.database.models.RatesModel
import com.tyganeutronics.myratecalculator.utils.WidgetUtils
import com.tyganeutronics.myratecalculator.wear.WearSyncHelper
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

    /**
     * Stores a rate the user typed in themselves, for a currency the API does not carry. It
     * enters the list like any other rate — pinnable, hideable, and visible to the watch and
     * the widgets — and [RatesModel.save] leaves it alone on every refresh.
     */
    fun addCustomRate(currency: String, name: String, rate: BigDecimal) {
        val code = currency.trim().uppercase()
        if (code.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            AppZimRate.database.runInTransaction {
                val now = Instant.now()
                dao.insert(RateEntity().apply {
                    this.currency = code
                    this.name = name.trim().ifEmpty { code }
                    this.rate = rate
                    this.custom = true
                    this.lastChecked = now
                    this.createdAt = now
                    this.updatedAt = now
                })
                normalizeVisibleSortOrder()
            }
            syncWatch()
            WidgetUtils.refreshAll(getApplication())
        }
    }

    /**
     * Writes an edited custom rate back to Room. Typed rates are otherwise held in memory only
     * and cleared by the next refresh, which would quietly undo an edit to a rate the user owns.
     */
    fun updateCustomRate(entity: RateEntity, rate: BigDecimal) {
        if (!entity.custom || rate <= BigDecimal.ZERO) return

        viewModelScope.launch(Dispatchers.IO) {
            val fresh = dao.findByCurrency(entity.currency) ?: return@launch
            if (fresh.rate.compareTo(rate) == 0) return@launch

            fresh.lastRate = fresh.rate
            fresh.rate = rate
            fresh.lastChecked = Instant.now()
            fresh.updatedAt = Instant.now()
            dao.update(fresh)

            syncWatch()
            WidgetUtils.refreshAll(getApplication())
        }
    }

    /** Removes a custom rate outright. API rates are hidden rather than deleted. */
    fun deleteCustomRate(entity: RateEntity) {
        if (!entity.custom) return

        viewModelScope.launch(Dispatchers.IO) {
            AppZimRate.database.runInTransaction {
                dao.deleteByCurrency(entity.currency)
                normalizeVisibleSortOrder()
            }
            syncWatch()
            WidgetUtils.refreshAll(getApplication())
        }
    }

    /** True when [currency] is already in the list, so the add dialog can reject a duplicate. */
    fun currencyExists(currency: String): Boolean =
        dao.findByCurrency(currency.trim().uppercase()) != null

    /** Hide a rate from the main list. USD cannot be hidden. */
    fun hideRate(entity: RateEntity) {
        if (entity.currency == "USD") return
        viewModelScope.launch(Dispatchers.IO) {
            dao.setHidden(entity.currency, true)
            syncWatch()
        }
    }

    /** Restore a previously hidden rate back to the main list. */
    fun restoreRate(entity: RateEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.setHidden(entity.currency, false)
            syncWatch()
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
            syncWatch()
        }
    }

    fun persistOrder(entities: List<RateEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            AppZimRate.database.runInTransaction {
                applyTieredSortOrder(entities)
            }
            syncWatch()
        }
    }

    /**
     * Upsert a list of fresh rates from the API into Room, discarding any rates or amounts
     * the user typed. Only call this for a user-initiated refresh — background refreshes go
     * straight through [RatesModel.save] so they never clobber typed input.
     */
    fun saveApiRates(apiRates: List<RateEntity>) {
        _rateOverrides.value = emptyMap()
        _activeCurrency.value = null
        _activeAmount.value = BigDecimal.ONE
        viewModelScope.launch(Dispatchers.IO) {
            RatesModel.save(getApplication(), apiRates)
        }
    }

    /**
     * Mirrors the pinned set to the watch. Pin, hide and order changes all alter what
     * [dao] returns for pinned rows, so each of them has to re-push — a refresh is not the
     * only thing the watch needs to hear about.
     */
    private fun syncWatch() {
        WearSyncHelper.pushPinnedRates(getApplication(), dao.getAllPinned())
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
