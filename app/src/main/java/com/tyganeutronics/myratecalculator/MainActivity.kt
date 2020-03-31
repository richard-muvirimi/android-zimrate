package com.tyganeutronics.myratecalculator

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.LinearLayoutCompat
import com.tyganeutronics.base.BaseUtils
import com.tyganeutronics.myratecalculator.models.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.result_layout.view.*

class MainActivity : AppCompatActivity(), TextWatcher, AdapterView.OnItemSelectedListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        calc_bond.text?.append(
            BaseUtils.getPrefs(this).getString(
                getString(R.string.currency_bond),
                "1"
            )
        )
        calc_rtgs.text?.append(
            BaseUtils.getPrefs(this).getString(
                getString(R.string.currency_rtgs),
                "1"
            )
        )
        calc_rbz.text?.append(
            BaseUtils.getPrefs(this).getString(
                getString(R.string.currency_rbz),
                "1"
            )
        )

        calc_bond.addTextChangedListener(this)
        calc_rtgs.addTextChangedListener(this)
        calc_rbz.addTextChangedListener(this)
        calc_amount.addTextChangedListener(this)

        calc_currency.onItemSelectedListener = this

        calc_currency.setSelection(BaseUtils.getPrefs(this).getInt("currency", 0))

        calc_amount.text?.append(
            BaseUtils.getPrefs(this).getString(
                "amount",
                "1"
            )
        )
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {

    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        calculate()
    }

    override fun afterTextChanged(s: Editable?) {
        calculate()
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

    }

    private fun getCalculator(): Calculator {

        var bondText = calc_bond.text?.toString()
        var rtgsText = calc_rtgs.text?.toString()
        var rbzText = calc_rbz.text?.toString()

        if (bondText == null || bondText.isEmpty()) {
            bondText = "1"
        }
        if (rtgsText == null || rtgsText.isEmpty()) {
            rtgsText = "1"
        }
        if (rbzText == null || rbzText.isEmpty()) {
            rbzText = "1"
        }

        BaseUtils.getPrefs(this).edit().putString(getString(R.string.currency_bond), bondText)
            .apply()
        BaseUtils.getPrefs(this).edit().putString(getString(R.string.currency_rtgs), rtgsText)
            .apply()
        BaseUtils.getPrefs(this).edit().putString(getString(R.string.currency_rbz), rbzText)
            .apply()

        var usd = USD(1.0)
        var bond = BOND(bondText.toDouble())
        var rtgs = RTGS(rtgsText.toDouble())
        var rbz = RBZ(rbzText.toDouble())

        var currency: Currency = usd
        when (calc_currency.selectedItem) {
            getString(R.string.currency_usd) -> {
                currency = usd
            }
            getString(R.string.currency_bond) -> {
                currency = bond
            }
            getString(R.string.currency_rtgs) -> {
                currency = rtgs
            }
            getString(R.string.currency_rbz) -> {
                currency = rbz
            }
        }

        return Calculator(usd, bond, rtgs, rbz, currency)
    }

    private fun calculate() {

        var calculator = getCalculator()

        var amountText = calc_amount.text?.toString() ?: "1"

        if (amountText.isNotEmpty()) {

            //save amount entered
            BaseUtils.getPrefs(this).edit().putString("amount", amountText)
                .apply()
            BaseUtils.getPrefs(this).edit().putInt("currency", calc_currency.selectedItemPosition)
                .apply()

            calc_result_layout.removeAllViews()

            add_result(
                getString(
                    R.string.result,
                    amountText.toDouble(),
                    calc_currency.selectedItem,
                    calculator.toUSD(amountText.toDouble()),
                    getString(R.string.currency_usd)
                )
            )

            add_result(
                getString(
                    R.string.result,
                    amountText.toDouble(),
                    calc_currency.selectedItem,
                    calculator.toBOND(amountText.toDouble()),
                    getString(R.string.currency_bond)
                )
            )

            add_result(
                getString(
                    R.string.result,
                    amountText.toDouble(),
                    calc_currency.selectedItem,
                    calculator.toRTGS(amountText.toDouble()),
                    getString(R.string.currency_rtgs)
                )
            )

            add_result(
                getString(
                    R.string.result,
                    amountText.toDouble(),
                    calc_currency.selectedItem,
                    calculator.toRBZ(amountText.toDouble()),
                    getString(R.string.currency_rbz)
                )
            )
        }

    }

    private fun add_result(result: String) {

        var resultLayout: LinearLayoutCompat =
            layoutInflater.inflate(
                R.layout.result_layout,
                calc_result_layout,
                false
            ) as LinearLayoutCompat

        resultLayout.result_text.text = result

        calc_result_layout.addView(resultLayout)
    }
}
