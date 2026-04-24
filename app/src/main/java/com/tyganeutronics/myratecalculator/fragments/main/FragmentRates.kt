package com.tyganeutronics.myratecalculator.fragments.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.apollographql.apollo3.api.Optional
import com.google.android.material.snackbar.Snackbar
import com.maltaisn.calcdialog.CalcDialog
import com.tyganeutronics.myratecalculator.AppZimRate
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.contract.PurchasesContract
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import com.tyganeutronics.myratecalculator.database.models.SpendModel
import com.tyganeutronics.myratecalculator.database.viewmodels.RatesViewModel
import com.tyganeutronics.myratecalculator.database.viewmodels.RewardViewModel
import com.tyganeutronics.myratecalculator.fragments.FragmentCalculator
import com.tyganeutronics.myratecalculator.graphql.FetchRatesQuery
import com.tyganeutronics.myratecalculator.graphql.type.Prefer
import com.tyganeutronics.myratecalculator.interfaces.RewardModelInterface
import com.tyganeutronics.myratecalculator.interfaces.RewardsActivity
import com.tyganeutronics.myratecalculator.ui.base.BaseFragment
import com.tyganeutronics.myratecalculator.ui.recyclerview.adapters.CalcField
import com.tyganeutronics.myratecalculator.ui.recyclerview.adapters.RatesAdapter
import com.tyganeutronics.myratecalculator.utils.resolveAttr
import com.tyganeutronics.myratecalculator.utils.traits.getStringPref
import com.tyganeutronics.myratecalculator.utils.traits.invalidateOptionsMenu
import com.tyganeutronics.myratecalculator.utils.traits.requireViewById
import com.tyganeutronics.myratecalculator.utils.traits.setTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant

class FragmentRates : BaseFragment(), CalcDialog.CalcDialogCallback {

    private lateinit var ratesViewModel: RatesViewModel
    private lateinit var rewardViewModel: RewardViewModel
    private lateinit var adapter: RatesAdapter

    private var didCalculate = false
    private var autoFetchDone = false

    private var calcTargetCurrency: String? = null
    private var calcTargetField: CalcField = CalcField.AMOUNT

    companion object {
        const val TAG = "FragmentRates"
        private const val CALC_REQUEST_RATE = 1
        private const val CALC_REQUEST_AMOUNT = 2
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_rates, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun bindViews() {
        super.bindViews()

        ratesViewModel = ViewModelProvider(requireActivity())[RatesViewModel::class.java]
        rewardViewModel = (requireActivity() as RewardModelInterface).rewardViewModel

        adapter = RatesAdapter(
            viewModel = ratesViewModel,
            onPinClick = { entity ->
                firebaseAnalytics.logEvent("toggle_pin_currency", Bundle())
                ratesViewModel.togglePin(entity)
            },
            onRefreshClick = { entity ->
                firebaseAnalytics.logEvent("refresh_single_currency", Bundle())
                fetchRates(singleCurrency = entity.currency)
            },
            onCalcClick = { entity, field, currentValue ->
                calcTargetCurrency = entity.currency
                calcTargetField = field
                val requestCode =
                    if (field == CalcField.RATE) CALC_REQUEST_RATE else CALC_REQUEST_AMOUNT
                FragmentCalculator().apply {
                    settings.apply {
                        this.requestCode = requestCode
                        initialValue = currentValue
                        isSignBtnShown = false
                        minValue = java.math.BigDecimal.ZERO
                    }
                }.show(childFragmentManager, "calc")
            }
        )

        requireViewById<RecyclerView>(R.id.rv_rates).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@FragmentRates.adapter
        }

        requireViewById<SwipeRefreshLayout>(R.id.sr_layout).apply {
            setColorSchemeResources(R.color.colorPrimaryLight)
            setOnRefreshListener { fetchRates() }
        }

        requireViewById<View>(R.id.btn_empty_refresh).setOnClickListener {
            fetchRates()
        }

        attachSwipeToHide()
    }

    override fun syncViews() {
        super.syncViews()

        setTitle(R.string.menu_calculator)

        rewardViewModel.coins.observe(this) {
            invalidateOptionsMenu()
        }

        ratesViewModel.rates.observe(this) { rates ->
            adapter.submitList(rates)
            val empty = rates.isEmpty()
            requireViewById<View>(R.id.layout_empty).visibility =
                if (empty) View.VISIBLE else View.GONE
            requireViewById<View>(R.id.rv_rates).visibility =
                if (empty) View.GONE else View.VISIBLE

            if (empty && !autoFetchDone && canConsumeCoins()) {
                autoFetchDone = true
                fetchRates()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        didCalculate = false
    }

    override fun onStop() {
        super.onStop()

        if (didCalculate && canConsumeCoins()) {
            SpendModel.consume(
                requireContext(),
                1,
                PurchasesContract.TYPES.CALCULATION,
                getString(R.string.rewards_spend_calculation)
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.fragment_main, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val coins = rewardViewModel.coins.value
        menu.findItem(R.id.menu_coins)?.title =
            getString(R.string.menu_coins_balance, coins ?: 0)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_refresh_all -> {
                firebaseAnalytics.logEvent("refresh_all_rates", Bundle())
                fetchRates()
                true
            }

            R.id.menu_hidden_rates -> {
                FragmentHiddenRates().show(childFragmentManager, FragmentHiddenRates.TAG)
                true
            }

            R.id.menu_info -> {
                firebaseAnalytics.logEvent("view_info_dialog", null)
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.menu_info)
                    .setIcon(
                        ContextCompat.getDrawable(requireContext(), requireContext().resolveAttr(R.attr.ic_refresh))
                    )
                    .setMessage(R.string.info_message)
                    .setPositiveButton(R.string.info_dismiss, null)
                    .show()
                true
            }

            R.id.menu_coins -> {
                (requireActivity() as RewardsActivity).showTopUpDialog()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun attachSwipeToHide() {
        val rv = requireViewById<RecyclerView>(R.id.rv_rates)
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                return adapter.onItemMove(from, to)
            }

            override fun getDragDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val pos = viewHolder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return 0
                val entity = adapter.currentList.getOrNull(pos) ?: return 0
                return if (entity.currency == "USD") 0
                else ItemTouchHelper.UP or ItemTouchHelper.DOWN
            }

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val pos = viewHolder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return 0
                val entity = adapter.currentList.getOrNull(pos) ?: return 0
                return if (entity.currency == "USD") 0
                else ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                val entity = adapter.currentList.getOrNull(pos) ?: return
                ratesViewModel.hideRate(entity)

                view?.let { root ->
                    Snackbar.make(
                        root,
                        getString(R.string.rate_hidden, entity.currency),
                        Snackbar.LENGTH_LONG
                    ).setAction(R.string.restore) {
                        ratesViewModel.restoreRate(entity)
                    }.show()
                }
            }
        }).attachToRecyclerView(rv)
    }

    override fun onValueEntered(requestCode: Int, value: java.math.BigDecimal?) {
        val currency = calcTargetCurrency ?: return
        val amount = value ?: return
        when (requestCode) {
            CALC_REQUEST_AMOUNT -> {
                adapter.applyCalcResult(currency, CalcField.AMOUNT, amount)
                didCalculate = true
            }

            CALC_REQUEST_RATE -> {
                adapter.applyCalcResult(currency, CalcField.RATE, amount)
            }
        }
    }

    private fun fetchRates(singleCurrency: String? = null) {
        if (!canConsumeCoins()) return

        setRefreshing(true)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val preferStr = requireContext()
                    .getStringPref("preferred_currency", "mean")
                    .uppercase()
                val prefer = try {
                    Prefer.valueOf(preferStr)
                } catch (_: IllegalArgumentException) {
                    Prefer.MEAN
                }

                val response = AppZimRate
                    .apolloClient
                    .query(FetchRatesQuery(prefer = Optional.present(prefer)))
                    .execute()

                val apiRates = response.data?.rates?.mapNotNull { r ->
                    if (singleCurrency != null && r.currency != singleCurrency) return@mapNotNull null

                    RateEntity().apply {
                        currency = r.currency ?: return@mapNotNull null
                        name = r.name ?: currency
                        url = r.url ?: ""
                        rate = r.rate?.toString()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                        lastChecked = r.last_updated?.toLong()
                            ?.let { Instant.ofEpochSecond(it) } ?: Instant.now()
                    }
                } ?: emptyList()

                if (apiRates.isEmpty() && singleCurrency != null) {
                    showSnackbar(getString(R.string.update_available))
                } else {
                    ratesViewModel.saveApiRates(apiRates)
                    // Overrides cleared synchronously above — refresh rate fields for currencies
                    // not returned by the API (e.g. ZWG when status=false) so stale typed
                    // values don't persist on screen.
                    adapter.refreshAllRates()

                    SpendModel.consume(
                        requireContext(),
                        1,
                        PurchasesContract.TYPES.DATA_FETCH,
                        getString(R.string.rewards_spend_data_fetch)
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                showSnackbar(e.localizedMessage ?: "Fetch failed")
            } finally {
                setRefreshing(false)
            }
        }
    }

    private fun canConsumeCoins(): Boolean {
        val hasCoins = (rewardViewModel.coins.value ?: 0) > 0
        if (!hasCoins) {
            (requireActivity() as RewardsActivity).showTopUpDialog()
        }
        return hasCoins
    }

    private fun setRefreshing(value: Boolean) {
        view?.findViewById<SwipeRefreshLayout>(R.id.sr_layout)?.isRefreshing = value
    }

    private fun showSnackbar(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show()
        }
    }

}
