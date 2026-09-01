package com.tyganeutronics.myratecalculator.fragments.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.viewmodels.RatesViewModel
import java.math.BigDecimal

/** Collects a currency the API does not carry, so the user can track a rate of their own. */
class FragmentCustomRate : BottomSheetDialogFragment() {

    private lateinit var ratesViewModel: RatesViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_custom_rate, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ratesViewModel = ViewModelProvider(requireActivity())[RatesViewModel::class.java]

        view.findViewById<Button>(R.id.btn_custom_cancel).setOnClickListener { dismiss() }
        view.findViewById<Button>(R.id.btn_custom_save).setOnClickListener { save(view) }
    }

    private fun save(view: View) {
        val tilCode = view.findViewById<TextInputLayout>(R.id.til_custom_code)
        val tilRate = view.findViewById<TextInputLayout>(R.id.til_custom_rate)

        val code = view.findViewById<TextInputEditText>(R.id.et_custom_code)
            .text?.toString()?.trim()?.uppercase().orEmpty()
        val name = view.findViewById<TextInputEditText>(R.id.et_custom_name)
            .text?.toString()?.trim().orEmpty()
        val rate = view.findViewById<TextInputEditText>(R.id.et_custom_rate)
            .text?.toString()?.trim()?.toBigDecimalOrNull()

        tilCode.error = null
        tilRate.error = null

        if (code.isEmpty()) {
            tilCode.error = getString(R.string.custom_rate_error_code)
            return
        }
        if (ratesViewModel.currencyExists(code)) {
            tilCode.error = getString(R.string.custom_rate_error_duplicate, code)
            return
        }
        if (rate == null || rate <= BigDecimal.ZERO) {
            tilRate.error = getString(R.string.custom_rate_error_rate)
            return
        }

        ratesViewModel.addCustomRate(code, name, rate)

        Toast.makeText(
            requireContext(),
            getString(R.string.custom_rate_added, code),
            Toast.LENGTH_SHORT
        ).show()

        dismiss()
    }

    companion object {
        const val TAG = "FragmentCustomRate"
    }
}
