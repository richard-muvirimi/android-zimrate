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

    /**
     * Observers belong here rather than in syncViews, and to the view's lifecycle rather than the
     * fragment's.
     *
     * syncViews runs from onStart, so registering there added another observer every time the
     * screen was returned to — and with the fragment as owner none of them were ever removed
     * before onDestroy, so the callbacks piled up and each one fired. viewLifecycleOwner drops
     * them at onDestroyView instead, which is also what stops a late emission reaching the dead
     * views these callbacks write to. bindViews runs once per view, so there is exactly one.
     */
    override fun bindViews() {
        super.bindViews()

        calculatorViewModel = ViewModelProvider(this)[RewardViewModel::class.java]

        requireViewById<LinearLayoutCompat>(R.id.btn_show_spends_history).setOnClickListener(this)

        val observer = Observer { balance: Long? ->
            requireViewById<AppCompatTextView>(R.id.txt_rewards_balance).apply {
                // Null is "not read yet", which is not the same as nothing — a dash says so
                // without claiming a number the wallet has not actually reported.
                text = balance?.let { String.format(Locale.getDefault(), "%d", it) } ?: "—"
            }
        }

        calculatorViewModel.coins.observe(viewLifecycleOwner, observer)
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