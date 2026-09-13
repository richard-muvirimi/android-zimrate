package com.tyganeutronics.myratecalculator.fragments.rewards

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.contract.RewardContract
import com.tyganeutronics.myratecalculator.database.rtdb.Reward
import com.tyganeutronics.myratecalculator.database.rtdb.WalletRepository
import com.tyganeutronics.myratecalculator.interfaces.RewardItemInterface
import com.tyganeutronics.myratecalculator.ui.base.BaseListFragment
import com.tyganeutronics.myratecalculator.ui.recyclerview.adapters.RewardsAdapter
import com.tyganeutronics.myratecalculator.utils.traits.displayBackButton
import com.tyganeutronics.myratecalculator.utils.traits.hideBackButton
import com.tyganeutronics.myratecalculator.utils.traits.setTitle
import kotlinx.coroutines.launch

/**
 * Grant history, live off the wallet listener.
 *
 * The AsyncTaskLoader this used to run on went with Room. It existed to get a blocking query
 * off the main thread; there is no blocking query any more, only a list that is already in
 * memory, and collecting it means the screen also redraws when a grant lands while it is open.
 */
class FragmentRewards : BaseListFragment(), RewardItemInterface {

    override var items: List<Reward> = emptyList()

    override fun hasItems(): Boolean {
        return items.isNotEmpty()
    }

    override fun syncViews() {
        super.syncViews()

        displayBackButton()

        if (type().isNotEmpty()) {
            setTitle(R.string.rewards_purchases_history_title)
        } else {
            setTitle(R.string.rewards_awarded_history_title)
        }

        // Both variants share the expiry-first order, so both get the explanation.
        setCaption(R.string.rewards_sort_note)
    }

    override fun search(): String? {
        return null
    }

    private fun type(): String =
        arguments?.getString(RewardContract.COLUMN_NAME_TYPE, "") ?: ""

    override fun onStart() {
        super.onStart()

        contentLoading()

        viewLifecycleOwner.lifecycleScope.launch {
            WalletRepository.rewards.collect { loaded ->
                // Null means the wallet has not been read yet — keep the loading state rather
                // than reporting an empty history.
                if (loaded == null) return@collect

                val type = type()
                items = if (type.isNotEmpty()) {
                    WalletRepository.activeRewardsOfType(type)
                } else {
                    WalletRepository.activeRewards()
                }

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
        firebaseAnalytics.logEvent("view_Rewards_History", null)
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

        recyclerView.adapter = RewardsAdapter(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        hideBackButton()
    }

    companion object {
        const val TAG = "FragmentRewards"
    }
}
