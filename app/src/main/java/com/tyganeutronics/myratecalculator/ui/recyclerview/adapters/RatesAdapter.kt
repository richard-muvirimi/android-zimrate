package com.tyganeutronics.myratecalculator.ui.recyclerview.adapters

import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import com.tyganeutronics.myratecalculator.database.viewmodels.RatesViewModel
import com.tyganeutronics.myratecalculator.ui.recyclerview.viewholders.RateViewHolder
import com.tyganeutronics.myratecalculator.ui.recyclerview.viewholders.SectionHeaderViewHolder
import com.tyganeutronics.myratecalculator.utils.CurrencyFlagUtil
import java.math.BigDecimal

enum class CalcField { RATE, AMOUNT }

/** A row in the rates list: either a section heading or a rate. */
sealed class RateListItem {
    data class Header(@StringRes val titleRes: Int) : RateListItem()
    data class Rate(val entity: RateEntity) : RateListItem()
}

/** The groups the rates list is split into, in the order they are shown. */
enum class Section(@StringRes val titleRes: Int) {
    FAVOURITES(R.string.section_favourites),
    SUGGESTED(R.string.section_suggested),
    CUSTOM(R.string.section_custom_rates),
    OTHERS(R.string.section_other_rates),
}

class RatesAdapter(
    private val viewModel: RatesViewModel,
    private val onPinClick: (RateEntity) -> Unit,
    private val onRefreshClick: (RateEntity) -> Unit,
    private val onDeleteClick: (RateEntity) -> Unit,
    private val onCalcClick: (entity: RateEntity, field: CalcField, currentValue: BigDecimal) -> Unit,
) : ListAdapter<RateListItem, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    init {
        setHasStableIds(true)
    }

    companion object {
        const val PAYLOAD_AMOUNT = "payload_amount"
        const val PAYLOAD_RATE = "payload_rate"
        const val PAYLOAD_PIN = "payload_pin"

        private const val TYPE_HEADER = 0
        private const val TYPE_RATE = 1

        /** Currencies of this country are suggested before the rest of the list. */
        private const val LOCAL_COUNTRY_CODE = "zw"

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RateListItem>() {
            override fun areItemsTheSame(oldItem: RateListItem, newItem: RateListItem) = when {
                oldItem is RateListItem.Header && newItem is RateListItem.Header ->
                    oldItem.titleRes == newItem.titleRes

                oldItem is RateListItem.Rate && newItem is RateListItem.Rate ->
                    oldItem.entity.currency == newItem.entity.currency

                else -> false
            }

            override fun areContentsTheSame(oldItem: RateListItem, newItem: RateListItem) = when {
                oldItem is RateListItem.Header && newItem is RateListItem.Header ->
                    oldItem.titleRes == newItem.titleRes

                oldItem is RateListItem.Rate && newItem is RateListItem.Rate ->
                    oldItem.entity.currency == newItem.entity.currency &&
                            oldItem.entity.rate == newItem.entity.rate &&
                            oldItem.entity.pinned == newItem.entity.pinned &&
                            oldItem.entity.updatedAt == newItem.entity.updatedAt

                else -> false
            }
        }
    }

    /**
     * Groups [rates] under their section headings. The incoming list is already ordered USD
     * first, then pinned, then the rest, and grouping keeps that order within each section.
     * A heading is only emitted when its section has rows.
     */
    fun submitRates(rates: List<RateEntity>) {
        val sections = rates.groupBy(::sectionOf)
        val items = mutableListOf<RateListItem>()

        Section.entries.forEach { section ->
            val rows = sections[section].orEmpty()
            if (rows.isEmpty()) return@forEach

            items += RateListItem.Header(section.titleRes)
            rows.mapTo(items) { RateListItem.Rate(it) }
        }

        submitList(items)
    }

    /**
     * Which section [entity] belongs to. Local currencies the user has not favourited yet are
     * surfaced as suggestions rather than being buried at the bottom of the full list.
     */
    private fun sectionOf(entity: RateEntity): Section = when {
        entity.pinned || entity.currency == "USD" -> Section.FAVOURITES
        // Checked before the local-currency guess, whose country lookup is meaningless for a
        // code the user invented and would scatter custom rates into Suggested.
        entity.custom -> Section.CUSTOM
        CurrencyFlagUtil.countryCode(entity.currency) == LOCAL_COUNTRY_CODE -> Section.SUGGESTED
        else -> Section.OTHERS
    }

    /** The rate at [position], or null when that row is a section heading. */
    fun entityAt(position: Int): RateEntity? =
        (currentList.getOrNull(position) as? RateListItem.Rate)?.entity

    /** The first rate under [section], or null when that section has no rows. */
    fun firstPositionIn(section: Section): Int? =
        currentList.indexOfFirst { it is RateListItem.Rate && sectionOf(it.entity) == section }
            .takeIf { it >= 0 }

    override fun getItemId(position: Int): Long = when (val item = getItem(position)) {
        // Heading ids are negative so they cannot collide with row ids from the database.
        is RateListItem.Header -> -item.titleRes.toLong()
        is RateListItem.Rate -> item.entity.id
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is RateListItem.Header -> TYPE_HEADER
        is RateListItem.Rate -> TYPE_RATE
    }

    fun onItemMove(from: Int, to: Int): Boolean {
        // Headings are not rows, so dragging onto one is rejected here.
        val fromEntity = entityAt(from) ?: return false
        val toEntity = entityAt(to) ?: return false
        if (fromEntity.currency == "USD" || toEntity.currency == "USD") return false
        // Reordering stays inside a section — a row cannot be dragged across a heading.
        if (sectionOf(fromEntity) != sectionOf(toEntity)) return false
        notifyItemMoved(from, to)

        val reordered = currentList.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        viewModel.persistOrder(reordered.mapNotNull { (it as? RateListItem.Rate)?.entity })

        return true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_HEADER) {
            SectionHeaderViewHolder(parent)
        } else {
            RateViewHolder(
                parent,
                viewModel,
                onPinClick,
                onRefreshClick,
                onDeleteClick,
                onCalcClick,
                ::notifyAmountsChanged,
            )
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is RateListItem.Header -> (holder as SectionHeaderViewHolder).bind(item.titleRes)
            is RateListItem.Rate -> (holder as RateViewHolder).bind(item.entity)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        val entity = entityAt(position)
        if (payloads.isEmpty() || entity == null || holder !is RateViewHolder) {
            onBindViewHolder(holder, position)
            return
        }
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
        forEachRate { index, _ -> notifyItemChanged(index, PAYLOAD_RATE) }
    }

    fun applyCalcResult(currency: String, field: CalcField, value: BigDecimal) {
        when (field) {
            CalcField.AMOUNT -> {
                viewModel.setActiveAmount(currency, value)
                forEachRate { index, _ -> notifyItemChanged(index, PAYLOAD_AMOUNT) }
            }

            CalcField.RATE -> {
                viewModel.setRateOverride(currency, value)
                forEachRate { index, entity ->
                    if (entity.currency == currency) {
                        notifyItemChanged(index, PAYLOAD_RATE)
                    }
                    notifyItemChanged(index, PAYLOAD_AMOUNT)
                }
            }
        }
    }

    private fun notifyAmountsChanged(exceptCurrency: String?) {
        forEachRate { index, entity ->
            if (entity.currency != exceptCurrency) {
                notifyItemChanged(index, PAYLOAD_AMOUNT)
            }
        }
    }

    /** Runs [action] for every rate row, skipping section headings. */
    private fun forEachRate(action: (index: Int, entity: RateEntity) -> Unit) {
        for (index in 0 until itemCount) {
            val entity = (getItem(index) as? RateListItem.Rate)?.entity ?: continue
            action(index, entity)
        }
    }
}
