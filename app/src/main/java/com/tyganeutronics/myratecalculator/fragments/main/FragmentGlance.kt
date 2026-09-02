package com.tyganeutronics.myratecalculator.fragments.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.maltaisn.calcdialog.CalcDialog
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import com.tyganeutronics.myratecalculator.database.viewmodels.RatesViewModel
import com.tyganeutronics.myratecalculator.fragments.FragmentCalculator
import com.tyganeutronics.myratecalculator.ui.BubbleFlowLayout
import com.tyganeutronics.myratecalculator.ui.RateBubbleBinder
import com.tyganeutronics.myratecalculator.ui.base.BaseFragment
import com.tyganeutronics.myratecalculator.utils.traits.requireViewById
import com.tyganeutronics.myratecalculator.utils.traits.setTitle
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * The favourites as bubbles. Reading at a glance is the point, and nothing here fetches, so it
 * costs no coins — but tapping through converts between them without leaving the screen.
 *
 * What is tapped in here stays in here. The base and the amount are the screen's own, not the
 * shared view model's, so a sum worked out at a glance never turns up later on the calculator
 * screen or outlives leaving this one. It also means the rates shown are always the real ones —
 * a rate typed over by hand on the calculator screen is scratch work and does not reach here.
 */
class FragmentGlance : BaseFragment(), CalcDialog.CalcDialogCallback {

    private lateinit var ratesViewModel: RatesViewModel

    private var favourites: List<RateEntity> = emptyList()

    /** What everything is converted from, and how much of it. Deliberately not persisted. */
    private var baseCurrency: String = DEFAULT_BASE
    private var amount: BigDecimal = BigDecimal.ONE

    companion object {
        const val TAG = "FragmentGlance"
        private const val CALC_REQUEST_AMOUNT = 1
        private const val DEFAULT_BASE = "USD"
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

        // Bound to the view's lifecycle and registered once here rather than in syncViews, which
        // runs on every start and would stack up a fresh observer each time.
        ratesViewModel.rates.observe(viewLifecycleOwner) { rates ->
            favourites = rates.filter { it.pinned }
            render()
        }
    }

    override fun syncViews() {
        super.syncViews()
        setTitle(R.string.menu_rates)
    }

    /**
     * Rebuilt outright on every change. There are only ever a handful of favourites, and each
     * bubble's size depends on the rest of the set, so a partial update would have to resize
     * everything anyway.
     */
    private fun render() {
        val flow = requireViewById<BubbleFlowLayout>(R.id.flow_bubbles)
        flow.removeAllViews()

        // Only favourites are drawn, so a base that is not one of them has no bubble to mark.
        val base = favourites.firstOrNull { it.currency == baseCurrency }
            ?: favourites.firstOrNull { it.currency == DEFAULT_BASE }

        val diameters = RateBubbleBinder.diameters(requireContext(), favourites)
        favourites.forEachIndexed { index, entity ->
            RateBubbleBinder.addBubble(
                parent = flow,
                entity = entity,
                diameterPx = diameters[index],
                value = convert(entity, base),
                isBase = entity.currency == base?.currency,
                onClick = ::onBubbleTap,
            )
        }

        val empty = favourites.isEmpty()
        requireViewById<View>(R.id.layout_empty).visibility =
            if (empty) View.VISIBLE else View.GONE
        requireViewById<View>(R.id.sv_bubbles).visibility =
            if (empty) View.GONE else View.VISIBLE
    }

    /**
     * What [entity] is worth, given [amount] of [base]. Falls back to the rate as published when
     * there is no base to convert from, which is what the screen shows before anything is tapped.
     */
    private fun convert(entity: RateEntity, base: RateEntity?): BigDecimal {
        if (base == null || base.rate.signum() == 0) {
            return entity.rate.setScale(2, RoundingMode.HALF_UP)
        }

        return amount
            .multiply(entity.rate)
            .divide(base.rate, MathContext(10, RoundingMode.HALF_UP))
            .setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * One tap does the whole job: the bubble becomes what the sum is counted in, and the pad
     * opens straight away to take the amount. Ringing it before the pad appears means the
     * change has already landed behind the dialog rather than waiting on it.
     */
    private fun onBubbleTap(entity: RateEntity) {
        baseCurrency = entity.currency
        render()

        FragmentCalculator().apply {
            settings.apply {
                requestCode = CALC_REQUEST_AMOUNT
                initialValue = amount
                isSignBtnShown = false
                minValue = BigDecimal.ZERO
            }
        }.show(childFragmentManager, "calc")
    }

    override fun onValueEntered(requestCode: Int, value: BigDecimal?) {
        if (requestCode != CALC_REQUEST_AMOUNT) return
        amount = value ?: BigDecimal.ONE
        render()
    }
}
