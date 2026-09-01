package com.tyganeutronics.myratecalculator.ui.recyclerview.viewholders

import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.murgupluoglu.flagkit.FlagKit
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import com.tyganeutronics.myratecalculator.database.viewmodels.RatesViewModel
import com.tyganeutronics.myratecalculator.ui.recyclerview.adapters.CalcField
import com.tyganeutronics.myratecalculator.utils.BrowserUtils
import com.tyganeutronics.myratecalculator.utils.CurrencyFlagUtil
import com.tyganeutronics.myratecalculator.utils.resolveAttr
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

class RateViewHolder(
    parent: ViewGroup,
    private val viewModel: RatesViewModel,
    private val onPinClick: (RateEntity) -> Unit,
    private val onRefreshClick: (RateEntity) -> Unit,
    private val onCalcClick: (entity: RateEntity, field: CalcField, currentValue: BigDecimal) -> Unit,
    private val onAmountsChanged: (exceptCurrency: String?) -> Unit,
) : RecyclerView.ViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.item_rate, parent, false)
) {
    private val imgFlag: ImageView = itemView.findViewById(R.id.img_flag)
    private val txtName: TextView = itemView.findViewById(R.id.txt_currency_name)
    private val tilRate: TextInputLayout = itemView.findViewById(R.id.til_rate)
    private val etRate: TextInputEditText = itemView.findViewById(R.id.et_rate)
    private val tilAmount: TextInputLayout = itemView.findViewById(R.id.til_amount)
    private val etAmount: TextInputEditText = itemView.findViewById(R.id.et_amount)
    private val btnPin: ImageButton = itemView.findViewById(R.id.btn_pin)
    private val btnRefresh: ImageButton = itemView.findViewById(R.id.btn_refresh)
    private val txtDate: TextView = itemView.findViewById(R.id.txt_date)

    var entity: RateEntity? = null
    var isUpdating = false
    var rateWatcher: TextWatcher? = null
    var amountWatcher: TextWatcher? = null

    fun bind(entity: RateEntity) {
        this.entity = entity
        val countryCode = CurrencyFlagUtil.countryCode(entity.currency)
        val flagRes = if (countryCode.isNotEmpty()) FlagKit.getResId(itemView.context, countryCode) else 0
        imgFlag.setImageResource(if (flagRes != 0) flagRes else R.mipmap.ic_launcher)

        val isBase = entity.currency == "USD" && entity.url.isEmpty()
        etRate.isEnabled = !isBase
        etRate.alpha = if (isBase) 0.5f else 1.0f
        tilRate.isEndIconVisible = !isBase

        isUpdating = true
        etRate.setText(viewModel.effectiveRate(entity).setScale(2, RoundingMode.HALF_UP).toPlainString())
        etAmount.setText(viewModel.calculateAmount(entity).toPlainString())
        isUpdating = false

        btnPin.setImageResource(
            if (entity.pinned || entity.currency == "USD") itemView.context.resolveAttr(R.attr.ic_star_filled)
            else itemView.context.resolveAttr(R.attr.ic_star_outline)
        )

        updateFootnote(entity)

        btnPin.setOnClickListener { onPinClick(entity) }
        btnRefresh.setOnClickListener { onRefreshClick(entity) }

        tilRate.setEndIconOnClickListener {
            val v = etRate.text?.toString()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
            onCalcClick(entity, CalcField.RATE, v)
        }
        tilAmount.setEndIconOnClickListener {
            val v = etAmount.text?.toString()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
            onCalcClick(entity, CalcField.AMOUNT, v)
        }

        rateWatcher?.let { etRate.removeTextChangedListener(it) }
        amountWatcher?.let { etAmount.removeTextChangedListener(it) }

        rateWatcher = etRate.addTextChangedListener {
            if (!isUpdating) {
                val newRate = it?.toString()?.toBigDecimalOrNull() ?: return@addTextChangedListener
                if (newRate > BigDecimal.ZERO) {
                    viewModel.setRateOverride(entity.currency, newRate)
                    onAmountsChanged(null)
                }
            }
        }

        amountWatcher = etAmount.addTextChangedListener {
            if (!isUpdating) {
                val newAmount = it?.toString()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                viewModel.setActiveAmount(entity.currency, newAmount)
                onAmountsChanged(entity.currency)
            }
        }
    }

    fun updateAmount(entity: RateEntity) {
        isUpdating = true
        etAmount.setText(viewModel.calculateAmount(entity).toPlainString())
        isUpdating = false
    }

    fun updateRate(entity: RateEntity) {
        isUpdating = true
        etRate.setText(viewModel.effectiveRate(entity).setScale(2, RoundingMode.HALF_UP).toPlainString())
        isUpdating = false
        updateFootnote(entity)
    }

    fun updatePin(entity: RateEntity) {
        btnPin.setImageResource(
            if (entity.pinned || entity.currency == "USD") itemView.context.resolveAttr(R.attr.ic_star_filled)
            else itemView.context.resolveAttr(R.attr.ic_star_outline)
        )
    }

    private fun updateFootnote(entity: RateEntity) {
        txtName.text = CurrencyFlagUtil.countryName(entity.currency)
        txtName.isSelected = true

        val dateStr = formatSyncDate(entity.lastChecked)
        if (dateStr.isNotEmpty()) {
            txtDate.visibility = View.VISIBLE
            txtDate.text = dateStr
        } else {
            txtDate.visibility = View.GONE
        }

        if (entity.url.isNotBlank()) {
            itemView.findViewById<View>(R.id.ll_footnote).setOnClickListener {
                BrowserUtils.openUrl(it.context, entity.url)
            }
        } else {
            itemView.findViewById<View>(R.id.ll_footnote).setOnClickListener(null)
        }
    }

    private fun formatSyncDate(lastChecked: Instant): String {
        if (lastChecked == Instant.MIN) return ""
        return DateUtils.getRelativeTimeSpanString(
            lastChecked.toEpochMilli(),
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }
}
