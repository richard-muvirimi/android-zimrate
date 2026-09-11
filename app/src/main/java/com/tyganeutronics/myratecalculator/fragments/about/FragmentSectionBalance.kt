package com.tyganeutronics.myratecalculator.fragments.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.viewmodels.RewardViewModel
import com.tyganeutronics.myratecalculator.interfaces.RewardsActivity
import com.tyganeutronics.myratecalculator.ui.base.BaseFragment
import com.tyganeutronics.myratecalculator.utils.traits.requireViewById
import java.util.Locale

class FragmentSectionBalance : BaseFragment(), OnClickListener {

    private lateinit var calculatorViewModel: RewardViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile_balance, container, false)
    }

    override fun bindViews() {
        super.bindViews()

        calculatorViewModel = ViewModelProvider(this)[RewardViewModel::class.java]

        requireViewById<LinearLayoutCompat>(R.id.btn_show_spends_history).setOnClickListener(this)
    }

    override fun syncViews() {
        super.syncViews()

        val observer = Observer { balance: Long? ->
            requireViewById<AppCompatTextView>(R.id.txt_rewards_balance).apply {
                // Null is "not read yet", which is not the same as nothing — a dash says so
                // without claiming a number the wallet has not actually reported.
                text = balance?.let { String.format(Locale.getDefault(), "%d", it) } ?: "—"
            }
        }

        // Observe the LiveData, passing in this activity as the LifecycleOwner and the observer.
        calculatorViewModel.coins.observe(this, observer)
    }

    override fun onClick(v: View?) {
        if (v != null) {
            when (v.id) {

                R.id.btn_show_spends_history -> {
                    (requireActivity() as RewardsActivity).showPurchasesHistory(Bundle())
                }
            }
        }
    }
}