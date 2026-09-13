package com.tyganeutronics.myratecalculator.fragments.rewards

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.viewmodels.RewardViewModel
import com.tyganeutronics.myratecalculator.ui.base.BaseExpandedDialogFragment
import com.tyganeutronics.myratecalculator.utils.traits.requireViewById

class FragmentCoinsBalance : BaseExpandedDialogFragment() {

    private lateinit var rewardViewModel: RewardViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_coins_balance, container, false)
    }

    override fun bindViews() {
        super.bindViews()

        rewardViewModel = ViewModelProvider(this)[RewardViewModel::class.java]

        toolBar.apply {
            title = getString(R.string.rewards_coins_title)
            setNavigationIcon(R.drawable.ic_close)
            setNavigationOnClickListener {
                if (isVisible) {
                    dismiss()
                }
            }
        }

        observeBalance()
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
    private fun observeBalance() {
        // Stays with the observer rather than in syncViews, and that pairing matters: a LiveData
        // only replays to a new observer, so leaving this on its own in onStart would reset the
        // line to "checking" on every return and nothing would ever correct it.
        requireViewById<AppCompatTextView>(R.id.txt_rewards_status).text =
            getString(R.string.rewards_coins_loading)

        val observer = Observer { balance: Long? ->

            requireViewById<AppCompatTextView>(R.id.txt_rewards_status).text = when {
                // Still waiting on the wallet — keep saying so rather than declaring it empty.
                balance == null -> getString(R.string.rewards_coins_loading)
                balance > 0 -> getString(R.string.rewards_coins_available)
                else -> getString(R.string.rewards_coins_exhausted)
            }
        }

        rewardViewModel.coins.observe(viewLifecycleOwner, observer)
    }

    private val toolBar: Toolbar
        get() = requireViewById(R.id.toolbar)

    companion object {
        const val TAG = "CoinsBalanceFragment"
    }
}