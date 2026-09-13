package com.tyganeutronics.myratecalculator.fragments.rewards

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.rtdb.Spend
import com.tyganeutronics.myratecalculator.database.rtdb.WalletRepository
import com.tyganeutronics.myratecalculator.interfaces.SpendItemInterface
import com.tyganeutronics.myratecalculator.ui.base.BaseListFragment
import com.tyganeutronics.myratecalculator.ui.recyclerview.adapters.SpendsAdapter
import com.tyganeutronics.myratecalculator.utils.traits.displayBackButton
import com.tyganeutronics.myratecalculator.utils.traits.hideBackButton
import com.tyganeutronics.myratecalculator.utils.traits.setTitle
import kotlinx.coroutines.launch

/** Spend history, live off the wallet listener. See [FragmentRewards] on the loader's removal. */
class FragmentSpends : BaseListFragment(), SpendItemInterface {

    override var items: List<Spend> = emptyList()

    override fun hasItems(): Boolean {
        return items.isNotEmpty()
    }

    override fun syncViews() {
        super.syncViews()

        displayBackButton()

        setTitle(R.string.rewards_coin_spends_history_title)
    }

    override fun search(): String? {
        return null
    }

    override fun onStart() {
        super.onStart()

        contentLoading()

        viewLifecycleOwner.lifecycleScope.launch {
            WalletRepository.spends.collect { loaded ->
                if (loaded == null) return@collect

                items = WalletRepository.spendHistory()
                deliver()
            }
        }
    }

    /**
     * Posted rather than called straight through, and that is load bearing.
     *
     * [BaseListFragment] sizes its list from the fragment's own view — `setMeasuredDimension` on
     * `requireView().width, requireView().height`. The loader this replaced always arrived after
     * the first layout pass, so those were real numbers. A StateFlow hands over its current value
     * the instant it is collected, which here is inside onStart, before any layout has happened
     * and while both are still zero — so the list measured to nothing and drew nothing, while
     * every log along the way insisted it had items.
     */
    private fun deliver() {
        // Checked again inside the runnable, and on `view` rather than isAdded: the view was
        // alive when this was posted, which is not the same as being alive when it runs, and
        // contentReady goes straight at requireViewById.
        view?.post { if (view != null) contentReady() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseAnalytics.logEvent("view_Purchases_History", null)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_list, container, false)
    }

    override fun bindViews() {
        super.bindViews()

        recyclerView.adapter = SpendsAdapter(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        hideBackButton()
    }

    companion object {
        const val TAG = "FragmentSpends"
    }
}
