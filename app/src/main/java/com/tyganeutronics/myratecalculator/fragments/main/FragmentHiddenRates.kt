package com.tyganeutronics.myratecalculator.fragments.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.murgupluoglu.flagkit.FlagKit
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import com.tyganeutronics.myratecalculator.database.viewmodels.RatesViewModel
import com.tyganeutronics.myratecalculator.utils.CurrencyFlagUtil

class FragmentHiddenRates : BottomSheetDialogFragment() {

    private lateinit var ratesViewModel: RatesViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_hidden_rates, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ratesViewModel = ViewModelProvider(requireActivity())[RatesViewModel::class.java]

        val rv = view.findViewById<RecyclerView>(R.id.rv_hidden)
        val txtEmpty = view.findViewById<TextView>(R.id.txt_no_hidden)

        val adapter = HiddenRatesAdapter { entity ->
            ratesViewModel.restoreRate(entity)
        }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        ratesViewModel.hiddenRates.observe(viewLifecycleOwner) { hidden ->
            adapter.submitList(hidden)
            val isEmpty = hidden.isEmpty()
            txtEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
            rv.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }
    }

    private inner class HiddenRatesAdapter(
        private val onRestore: (RateEntity) -> Unit
    ) : RecyclerView.Adapter<HiddenRatesAdapter.ViewHolder>() {

        private var items: List<RateEntity> = emptyList()

        fun submitList(list: List<RateEntity>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_hidden_rate, parent, false)
        )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) =
            holder.bind(items[position])

        override fun getItemCount() = items.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val imgFlag: ImageView = view.findViewById(R.id.img_hidden_flag)
            private val txtCode: TextView = view.findViewById(R.id.txt_hidden_code)
            private val txtName: TextView = view.findViewById(R.id.txt_hidden_name)
            private val btnRestore: Button = view.findViewById(R.id.btn_restore)

            fun bind(entity: RateEntity) {
                val countryCode = CurrencyFlagUtil.countryCode(entity.currency)
                val flagRes = if (countryCode.isNotEmpty()) FlagKit.getResId(itemView.context, countryCode) else 0
                imgFlag.setImageResource(if (flagRes != 0) flagRes else R.mipmap.ic_launcher)

                txtCode.text = entity.currency
                txtName.text = entity.name.ifEmpty { entity.currency }
                btnRestore.setOnClickListener { onRestore(entity) }
            }
        }
    }

    companion object {
        const val TAG = "FragmentHiddenRates"
    }
}
