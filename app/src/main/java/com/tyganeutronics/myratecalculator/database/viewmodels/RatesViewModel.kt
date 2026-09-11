package com.tyganeutronics.myratecalculator.database.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import com.tyganeutronics.myratecalculator.database.models.RatesModel
import com.tyganeutronics.myratecalculator.database.rtdb.CurrencyRepository
import com.tyganeutronics.myratecalculator.database.rtdb.WalletContract
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

    init {
        viewModelScope.launch(Dispatchers.IO) {
            CurrencyRepository.awaitLoaded()
            normalizeVisibleSortOrder()
        }
    }

    /** Visible rates, ordered by USD first, then pinned, then the rest. */
    val rates: LiveData<List<RateEntity>> = CurrencyRepository.visible.asLiveData()

    /** Rates the user has hidden. */
    val hiddenRates: LiveData<List<RateEntity>> = CurrencyRepository.hidden.asLiveData()

    /** In-memory rate overrides entered by the user (not persisted). */
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

    /** Returns the effective rate for a currency (override takes precedence over stored value). */
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
            val now = Instant.now()

            CurrencyRepository.put(RateEntity().apply {
                this.currency = code
                this.name = name.trim().ifEmpty { code }
                this.rate = rate
                this.custom = true
                this.sortOrder = Int.MAX_VALUE
                this.lastChecked = now
                this.createdAt = now
                this.updatedAt = now
            })

            normalizeVisibleSortOrder()
            syncWatch()
            WidgetUtils.refreshAll(getApplication())
        }
    }

    /**
     * Writes an edited custom rate back. Typed rates are otherwise held in memory only and
     * cleared by the next refresh, which would quietly undo an edit to a rate the user owns.
     */
    fun updateCustomRate(entity: RateEntity, rate: BigDecimal) {
        if (!entity.custom || rate <= BigDecimal.ZERO) return

        viewModelScope.launch(Dispatchers.IO) {
            val fresh = CurrencyRepository.findByCurrency(entity.currency) ?: return@launch
            if (fresh.rate.compareTo(rate) == 0) return@launch

            CurrencyRepository.put(fresh.apply {
                this.lastRate = this.rate
                this.rate = rate
                this.lastChecked = Instant.now()
                this.updatedAt = Instant.now()
            })

            syncWatch()
            WidgetUtils.refreshAll(getApplication())
        }
    }

    /** Removes a custom rate outright. API rates are hidden rather than deleted. */
    fun deleteCustomRate(entity: RateEntity) {
        if (!entity.custom) return

        viewModelScope.launch(Dispatchers.IO) {
            CurrencyRepository.delete(entity.currency)
            normalizeVisibleSortOrder()
            syncWatch()
            WidgetUtils.refreshAll(getApplication())
        }
    }

    /** True when [currency] is already in the list, so the add dialog can reject a duplicate. */
    fun currencyExists(currency: String): Boolean =
        CurrencyRepository.findByCurrency(currency.trim().uppercase()) != null

    /** Hide a rate from the main list. USD cannot be hidden. */
    fun hideRate(entity: RateEntity) {
        if (entity.currency == "USD") return
        viewModelScope.launch(Dispatchers.IO) {
            CurrencyRepository.setField(entity.currency, WalletContract.HIDDEN, true)
            syncWatch()
        }
    }

    /** Restore a previously hidden rate back to the main list. */
    fun restoreRate(entity: RateEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            CurrencyRepository.setField(entity.currency, WalletContract.HIDDEN, false)
            syncWatch()
        }
    }

    /** Toggle the pinned state for a rate. USD stays pinned always. */
    fun togglePin(entity: RateEntity) {
        if (entity.currency == "USD") return
        viewModelScope.launch(Dispatchers.IO) {
            val fresh = CurrencyRepository.findByCurrency(entity.currency) ?: return@launch

            CurrencyRepository.put(fresh.apply {
                this.pinned = !this.pinned
                this.sortOrder = Int.MAX_VALUE
                this.updatedAt = Instant.now()
            })

            normalizeVisibleSortOrder()
            syncWatch()
        }
    }

    fun persistOrder(entities: List<RateEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            CurrencyRepository.applyOrder(entities)
            syncWatch()
        }
    }

    /**
     * Upsert a list of fresh rates from the API, discarding any rates or amounts the user typed.
     * Only call this for a user-initiated refresh — background refreshes go straight through
     * [RatesModel.save] so they never clobber typed input.
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
     * Mirrors the pinned set to the watch. Pin, hide and order changes all alter what is
     * pinned, so each of them has to re-push — a refresh is not the only thing the watch
     * needs to hear about.
     */
    private fun syncWatch() {
        WearSyncHelper.pushPinnedRates(getApplication(), CurrencyRepository.allPinned())
    }

    private suspend fun normalizeVisibleSortOrder() {
        CurrencyRepository.applyOrder(CurrencyRepository.visibleSorted())
    }
}
