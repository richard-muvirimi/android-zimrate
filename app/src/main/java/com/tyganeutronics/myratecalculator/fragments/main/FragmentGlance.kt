package com.tyganeutronics.myratecalculator.fragments.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import com.tyganeutronics.myratecalculator.database.viewmodels.RatesViewModel
import com.tyganeutronics.myratecalculator.ui.BubbleFlowLayout
import com.tyganeutronics.myratecalculator.ui.RateBubbleBinder
import com.tyganeutronics.myratecalculator.ui.base.BaseFragment
import com.tyganeutronics.myratecalculator.utils.traits.requireViewById
import com.tyganeutronics.myratecalculator.utils.traits.setTitle
import java.math.BigDecimal

/**
 * The favourites as bubbles, for reading rather than working with. Nothing here fetches, so
 * opening it costs no coins — the calculator screen owns refreshing.
 */
class FragmentGlance : BaseFragment() {

    private lateinit var ratesViewModel: RatesViewModel

    companion object {
        const val TAG = "FragmentGlance"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_glance, container, false)
    }

    override fun bindViews() {
        super.bindViews()

        ratesViewModel = ViewModelProvider(requireActivity())[RatesViewModel::class.java]
    }

    override fun syncViews() {
        super.syncViews()

        setTitle(R.string.menu_home)

        ratesViewModel.rates.observe(this) { rates ->
            // Someone glancing wants the currencies they follow, not the whole list. USD is always
            // pinned, so this is never empty while there are rates at all.
            val favourites = rates.filter { it.pinned }
            renderBubbles(favourites)

            val empty = favourites.isEmpty()
            requireViewById<View>(R.id.layout_empty).visibility =
                if (empty) View.VISIBLE else View.GONE
            requireViewById<View>(R.id.sv_bubbles).visibility =
                if (empty) View.GONE else View.VISIBLE
        }
    }

    /**
     * Rebuilt outright on every change. There are only ever a handful of favourites, and each
     * bubble's size depends on the rest of the set, so a partial update would have to resize
     * everything anyway.
     */
    private fun renderBubbles(favourites: List<RateEntity>) {
        val flow = requireViewById<BubbleFlowLayout>(R.id.flow_bubbles)
        flow.removeAllViews()

        val diameters = RateBubbleBinder.diameters(requireContext(), favourites)
        favourites.forEachIndexed { index, entity ->
            RateBubbleBinder.addBubble(flow, entity, diameters[index], ::openInCalculator)
        }
    }

    /**
     * Hands the tapped currency to the calculator as the amount being converted from, so the rest
     * of the list reads as what one unit of it is worth. The view model is scoped to the activity,
     * so the value is already in place by the time the other screen binds.
     */
    private fun openInCalculator(entity: RateEntity) {
        ratesViewModel.setActiveAmount(entity.currency, BigDecimal.ONE)
        requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavView)
            .selectedItemId = R.id.navigation_calculator
    }
}
