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
    }

    private val toolBar: Toolbar
        get() = requireViewById(R.id.toolbar)

    override fun syncViews() {
        super.syncViews()

        // Starts as "checking" rather than "exhausted" — the balance is not known until the
        // observer first fires, and an unread wallet must not be reported as an empty one.
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

        // Observe the LiveData, passing in this activity as the LifecycleOwner and the observer.
        rewardViewModel.coins.observe(this, observer)

    }

    companion object {
        const val TAG = "CoinsBalanceFragment"
    }
}