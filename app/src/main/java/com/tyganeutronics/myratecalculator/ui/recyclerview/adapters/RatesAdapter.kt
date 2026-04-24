package com.tyganeutronics.myratecalculator.ui.recyclerview.adapters

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import com.tyganeutronics.myratecalculator.database.viewmodels.RatesViewModel
import com.tyganeutronics.myratecalculator.ui.recyclerview.viewholders.RateViewHolder
import java.math.BigDecimal

enum class CalcField { RATE, AMOUNT }

class RatesAdapter(
    private val viewModel: RatesViewModel,
    private val onPinClick: (RateEntity) -> Unit,
    private val onRefreshClick: (RateEntity) -> Unit,
    private val onCalcClick: (entity: RateEntity, field: CalcField, currentValue: BigDecimal) -> Unit,
) : ListAdapter<RateEntity, RateViewHolder>(DIFF_CALLBACK) {

    init {
        setHasStableIds(true)
    }

    companion object {
        const val PAYLOAD_AMOUNT = "payload_amount"
        const val PAYLOAD_RATE = "payload_rate"
        const val PAYLOAD_PIN = "payload_pin"

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RateEntity>() {
            override fun areItemsTheSame(oldItem: RateEntity, newItem: RateEntity) =
                oldItem.currency == newItem.currency

            override fun areContentsTheSame(oldItem: RateEntity, newItem: RateEntity) =
                oldItem.currency == newItem.currency &&
                        oldItem.rate == newItem.rate &&
                        oldItem.pinned == newItem.pinned &&
                        oldItem.updatedAt == newItem.updatedAt
        }
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id
    }

    fun onItemMove(from: Int, to: Int): Boolean {
        if (from == 0 || to == 0) return false
        val fromEntity = currentList.getOrNull(from) ?: return false
        val toEntity = currentList.getOrNull(to) ?: return false
        if (fromEntity.pinned != toEntity.pinned) return false
        notifyItemMoved(from, to)

        val reordered = currentList.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        viewModel.persistOrder(reordered)

        return true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = RateViewHolder(
        parent,
        viewModel,
        onPinClick,
        onRefreshClick,
        onCalcClick,
        ::notifyAmountsChanged,
    )

    override fun onBindViewHolder(holder: RateViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: RateViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        val entity = getItem(position)
        var needsFullBind = false
        payloads.forEach { payload ->
            when (payload) {
                PAYLOAD_AMOUNT -> holder.updateAmount(entity)
                PAYLOAD_RATE -> holder.updateRate(entity)
                PAYLOAD_PIN -> holder.updatePin(entity)
                else -> needsFullBind = true
            }
        }
        if (needsFullBind) onBindViewHolder(holder, position)
    }

    fun refreshAllRates() {
        for (i in 0 until itemCount) {
            notifyItemChanged(i, PAYLOAD_RATE)
        }
    }

    fun applyCalcResult(currency: String, field: CalcField, value: BigDecimal) {
        when (field) {
            CalcField.AMOUNT -> {
                viewModel.setActiveAmount(currency, value)
                for (i in 0 until itemCount) {
                    notifyItemChanged(i, PAYLOAD_AMOUNT)
                }
            }

            CalcField.RATE -> {
                viewModel.setRateOverride(currency, value)
                for (i in 0 until itemCount) {
                    if (getItem(i).currency == currency) {
                        notifyItemChanged(i, PAYLOAD_RATE)
                    }
                    notifyItemChanged(i, PAYLOAD_AMOUNT)
                }
            }
        }
    }

    private fun notifyAmountsChanged(exceptCurrency: String?) {
        for (i in 0 until itemCount) {
            if (getItem(i).currency != exceptCurrency) {
                notifyItemChanged(i, PAYLOAD_AMOUNT)
            }
        }
    }
}
