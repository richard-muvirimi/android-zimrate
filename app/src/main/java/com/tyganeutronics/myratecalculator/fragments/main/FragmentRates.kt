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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.maltaisn.calcdialog.CalcDialog
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.contract.PurchasesContract
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import com.tyganeutronics.myratecalculator.database.models.RatesModel
import com.tyganeutronics.myratecalculator.database.models.SpendModel
import com.tyganeutronics.myratecalculator.database.viewmodels.RatesViewModel
import com.tyganeutronics.myratecalculator.database.viewmodels.RewardViewModel
import com.tyganeutronics.myratecalculator.fragments.FragmentCalculator
import com.tyganeutronics.myratecalculator.interfaces.RewardModelInterface
import com.tyganeutronics.myratecalculator.interfaces.RewardsActivity
import com.tyganeutronics.myratecalculator.ui.base.BaseFragment
import com.tyganeutronics.myratecalculator.ui.recyclerview.adapters.CalcField
import com.tyganeutronics.myratecalculator.ui.recyclerview.adapters.RatesAdapter
import com.tyganeutronics.myratecalculator.ui.recyclerview.adapters.Section
import com.tyganeutronics.myratecalculator.utils.contracts.CurrencyContract
import com.tyganeutronics.myratecalculator.utils.resolveAttr
import com.tyganeutronics.myratecalculator.utils.traits.getBooleanPref
import com.tyganeutronics.myratecalculator.utils.traits.getLongPref
import com.tyganeutronics.myratecalculator.utils.traits.getStringPref
import com.tyganeutronics.myratecalculator.utils.traits.helpPrompt
import com.tyganeutronics.myratecalculator.utils.traits.putLongPref
import com.tyganeutronics.myratecalculator.utils.traits.invalidateOptionsMenu
import com.tyganeutronics.myratecalculator.utils.traits.requireViewById
import com.tyganeutronics.myratecalculator.utils.traits.setTitle
import com.tyganeutronics.myratecalculator.utils.traits.showHelpOnce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetSequence
import uk.co.samuelwall.materialtaptargetprompt.extras.backgrounds.RectanglePromptBackground
import uk.co.samuelwall.materialtaptargetprompt.extras.focals.RectanglePromptFocal
import java.util.concurrent.TimeUnit

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
        private const val HELP_KEY = "showRatesHelp"
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
            onDeleteClick = { entity -> confirmDeleteCustomRate(entity) },
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
            layoutManager = ratesLayoutManager()
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

    /** Deleting is irreversible — there is no server copy to fetch a custom rate back from. */
    private fun confirmDeleteCustomRate(entity: RateEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.custom_rate_delete_title, entity.currency))
            .setMessage(R.string.custom_rate_delete_message)
            .setNegativeButton(R.string.calculator_dialog_cancel, null)
            .setPositiveButton(R.string.custom_rate_delete_confirm) { _, _ ->
                firebaseAnalytics.logEvent("delete_custom_rate", Bundle())
                ratesViewModel.deleteCustomRate(entity)
                showSnackbar(getString(R.string.custom_rate_deleted, entity.currency))
            }
            .show()
    }

    /**
     * One column on a phone held upright, more once the window is wide enough that a card would
     * otherwise stretch. Section headings span the full row so the grouping still reads.
     */
    private fun ratesLayoutManager(): GridLayoutManager {
        val columns = resources.getInteger(R.integer.rates_columns)

        return GridLayoutManager(context, columns).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                // Headings are the rows entityAt cannot resolve to a rate.
                override fun getSpanSize(position: Int) =
                    if (adapter.entityAt(position) == null) columns else 1
            }
        }
    }

    override fun syncViews() {
        super.syncViews()

        setTitle(R.string.menu_calculator)

        rewardViewModel.coins.observe(this) {
            invalidateOptionsMenu()
            maybeAutoFetch()
        }

        ratesViewModel.rates.observe(this) { rates ->
            adapter.submitRates(rates)
            val empty = rates.isEmpty()
            requireViewById<View>(R.id.layout_empty).visibility =
                if (empty) View.VISIBLE else View.GONE
            requireViewById<View>(R.id.rv_rates).visibility =
                if (empty) View.GONE else View.VISIBLE

            // Posted so the first cards are laid out and can be pointed at.
            if (!empty) requireViewById<RecyclerView>(R.id.rv_rates).post { maybeShowHelp() }

            maybeAutoFetch()
        }
    }

    /**
     * The flag is only spent once Other rates has something in it, so a first load that arrives
     * without it does not burn the single chance to show the sequence unprompted.
     */
    private fun maybeShowHelp() {
        if (adapter.firstPositionIn(Section.OTHERS) == null) return
        showHelpOnce(HELP_KEY) { showHelp() }
    }

    /**
     * Refreshes once per screen, as soon as both the rates and the coin balance are known.
     * Both are loaded asynchronously and either can arrive first, so this is driven from both
     * observers — checking too early would read a null balance and skip the refresh entirely.
     */
    private fun maybeAutoFetch() {
        if (autoFetchDone) return

        val rates = ratesViewModel.rates.value ?: return
        rewardViewModel.coins.value ?: return

        autoFetchDone = true
        when {
            // With nothing on screen the app is unusable, so a top up prompt is fair.
            rates.isEmpty() -> fetchRates()
            // Merely stale — refresh quietly if they can afford it, never nag.
            shouldUpdate() -> fetchRates(promptForCoins = false)
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

            R.id.menu_help -> {
                firebaseAnalytics.logEvent("show_help_sequence", null)
                showHelp()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * The three things this screen cannot show it does: the star, the swipe, and what a refresh
     * costs.
     *
     * Anchored to Other rates rather than to whatever card happens to be first. Both gestures
     * only make sense against a currency the user has not dealt with yet — telling someone to
     * star a row that is already starred, or to swipe away USD, which is pinned and cannot be
     * hidden, explains nothing.
     */
    private fun showHelp() {
        val rv = requireViewById<RecyclerView>(R.id.rv_rates)
        val position = adapter.firstPositionIn(Section.OTHERS) ?: return

        // Other rates sit below the favourites, so the card being explained is usually off
        // screen — there would be nothing under the spotlight without this.
        rv.scrollToPosition(position)
        rv.post {
            val card = rv.findViewHolderForAdapterPosition(position)?.itemView ?: return@post

            MaterialTapTargetSequence()
                .addPrompt(
                    helpPrompt(R.string.prompt_pin, R.string.prompt_pin_description)
                        .setTarget(card.findViewById<View>(R.id.btn_pin))
                )
                .addPrompt(
                    helpPrompt(R.string.prompt_hide, R.string.prompt_hide_description)
                        .setTarget(card)
                        .setPromptFocal(RectanglePromptFocal())
                        .setPromptBackground(RectanglePromptBackground())
                )
                .addPrompt(
                    helpPrompt(R.string.prompt_coins, R.string.prompt_coins_description)
                        .setTarget(R.id.menu_coins)
                )
                .show()
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
                val entity = adapter.entityAt(pos) ?: return 0
                return if (entity.currency == "USD") 0
                else ItemTouchHelper.UP or ItemTouchHelper.DOWN
            }

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val pos = viewHolder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return 0
                val entity = adapter.entityAt(pos) ?: return 0
                return if (entity.currency == "USD") 0
                else ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                val entity = adapter.entityAt(pos) ?: return
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

    /**
     * True when the update_interval setting has elapsed since the last successful check, so opening the
     * app pulls fresh rates. The `check_update` setting turns this off entirely.
     */
    private fun shouldUpdate(): Boolean {
        if (!requireContext().getBooleanPref("check_update", true)) return false

        val last = requireContext().getLongPref(CurrencyContract.LAST_CHECK, 0L)
        val hours = requireContext().getStringPref("update_interval", "24").toLongOrNull() ?: 24L

        return System.currentTimeMillis() > last + TimeUnit.HOURS.toMillis(hours)
    }

    /**
     * [promptForCoins] shows the top up dialog when the balance is empty. Refreshes the user
     * did not ask for pass false so an empty balance never nags them on open.
     */
    private fun fetchRates(singleCurrency: String? = null, promptForCoins: Boolean = true) {
        if (!hasCoins()) {
            if (promptForCoins) (requireActivity() as RewardsActivity).showTopUpDialog()
            return
        }

        setRefreshing(true)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val apiRates = RatesModel.fetch(
                    RatesModel.preferred(requireContext()),
                    singleCurrency,
                )

                if (apiRates.isEmpty()) {
                    showSnackbar(getString(R.string.update_none))
                } else {
                    requireContext().putLongPref(
                        CurrencyContract.LAST_CHECK,
                        System.currentTimeMillis(),
                    )
                    applyOrOfferRates(apiRates)

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

    /**
     * Applies fresh rates immediately, or — when auto update is off — offers them behind a
     * snackbar, so a rate the user typed is never replaced without them agreeing to it.
     */
    private fun applyOrOfferRates(apiRates: List<RateEntity>) {
        if (requireContext().getBooleanPref("auto_update", true)) {
            applyRates(apiRates)
            return
        }

        view?.let { root ->
            Snackbar.make(root, R.string.update_available, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.update_apply) { applyRates(apiRates) }
                .show()
        }
    }

    private fun applyRates(apiRates: List<RateEntity>) {
        ratesViewModel.saveApiRates(apiRates)
        // Overrides cleared synchronously above — refresh rate fields for currencies
        // not returned by the API (e.g. ZWG when status=false) so stale typed
        // values don't persist on screen.
        adapter.refreshAllRates()
    }

    private fun hasCoins(): Boolean = (rewardViewModel.coins.value ?: 0) > 0

    private fun canConsumeCoins(): Boolean {
        val hasCoins = hasCoins()
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
