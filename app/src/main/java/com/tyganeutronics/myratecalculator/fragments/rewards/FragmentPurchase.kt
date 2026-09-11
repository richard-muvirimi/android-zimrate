package com.tyganeutronics.myratecalculator.fragments.rewards

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.TooltipCompat
import androidx.core.widget.ContentLoadingProgressBar
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsParams.Product
import com.android.billingclient.api.QueryPurchasesParams
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.auth.AuthManager
import com.tyganeutronics.myratecalculator.database.models.RewardModel
import com.tyganeutronics.myratecalculator.ui.base.BaseFragment
import com.tyganeutronics.myratecalculator.utils.contracts.BillingContract
import com.tyganeutronics.myratecalculator.utils.traits.findViewById
import com.tyganeutronics.myratecalculator.utils.traits.hideBackButton
import com.tyganeutronics.myratecalculator.utils.traits.requireViewById
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FragmentPurchase : BaseFragment(), View.OnClickListener, PurchasesUpdatedListener,
    PurchasesResponseListener {

    private lateinit var billingClient: BillingClient

    /** Held so a purchase arriving after the sheet closes still has somewhere to credit from. */
    private var appContext: Context? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_purchase, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appContext = requireContext().applicationContext

        // Create and initialize BillingManager which talks to BillingLibrary
        billingClient = BillingClient.newBuilder(requireContext())
            .setListener(this)
            // The no-argument enablePendingPurchases() was removed in Play Billing 8.0.0.
            // This is its documented equivalent — coins are one time products.
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

        startBillingConnection()
    }

    override fun bindViews() {
        super.bindViews()

        loadingProgressBar.show()

        //inputs
        for (sku in BillingContract.ids) {
            requireViewById<View>(BillingContract.mapSkuToViewId(sku)!!).setOnClickListener(this)
        }
    }

    override fun syncViews() {
        super.syncViews()

        toolBar.apply {
            title = getString(R.string.rewards_earn_purchase)
            setNavigationIcon(R.drawable.ic_close)
            setNavigationOnClickListener {
                if (isVisible) {
                    dismiss()
                }
            }
            inflateMenu(R.menu.fragment_purchase)
            setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.menu_restore -> {
                        Toast.makeText(
                            requireContext(),
                            R.string.billing_restoring_purchases,
                            Toast.LENGTH_LONG
                        ).show()

                        queryPurchases()
                        true
                    }

                    else -> false
                }
            }
        }
    }

    private fun startBillingConnection() {

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        Log.i(TAG, "Billing client successfully set up")
                        queryOneTimeProducts()
                    }

                    BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
                    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                        Toast.makeText(
                            requireContext(),
                            R.string.billing_coin_purchase_not_supported,
                            Toast.LENGTH_LONG
                        ).show()

                        dismiss()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.i(TAG, "Billing service disconnected")

                Toast.makeText(
                    requireContext(),
                    R.string.billing_coin_purchase_failed,
                    Toast.LENGTH_LONG
                ).show()

                dismiss()
            }
        })
    }

    private fun queryOneTimeProducts() {

        val products = BillingContract.ids.map {
            Product.newBuilder()
                .setProductId(it)
                .setProductType(ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        // Since Play Billing 8.0.0 the listener receives a QueryProductDetailsResult rather
        // than a bare list, so products that could not be fetched are reported instead of
        // silently missing.
        billingClient.queryProductDetailsAsync(params) { billingResult, queryResult ->
            Log.i(TAG, "onProductDetailsResponse ${billingResult.responseCode}")

            val productDetailsList = queryResult.productDetailsList
            queryResult.unfetchedProductList.forEach { unfetched ->
                Log.w(TAG, "Unfetched product $unfetched")
            }

            Handler(Looper.getMainLooper()).post {

                if (productDetailsList.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        R.string.billing_coin_purchase_failed,
                        Toast.LENGTH_LONG
                    ).show()

                    dismiss()
                }

                if (activity !== null) {

                    loadingProgressBar.hide()

                    for (productDetails in productDetailsList) {

                        Log.i(TAG, productDetails.toString())

                        BillingContract.mapSkuToViewId(productDetails.productId)?.let {
                            findViewById<AppCompatButton>(it)?.let { button ->
                                TooltipCompat.setTooltipText(button, productDetails.description)

                                button.text = getString(
                                    R.string.billing_purchase_coins,
                                    BillingContract.coins[productDetails.productId]!!.first,
                                    BillingContract.coins[productDetails.productId]!!.second
                                )
                                button.tag = productDetails

                                button.isEnabled = true
                            }
                        }
                    }
                }
            }
        }
    }

    private val loadingProgressBar: ContentLoadingProgressBar
        get() = requireViewById(R.id.donations_loading)

    private val toolBar: Toolbar
        get() = requireViewById(R.id.toolbar)

    override fun onClick(v: View) {

        queryPurchases()

        // Money must never land in an anonymous account: it cannot be recovered on another
        // handset, and clearing storage would take it with no way back. Free coins are earned
        // without any of this — the ask only appears where its point is obvious.
        if (!AuthManager.hasAccount) {
            Toast.makeText(
                requireContext(),
                R.string.account_required_to_buy,
                Toast.LENGTH_LONG
            ).show()

            FragmentSignIn().show(parentFragmentManager, FragmentSignIn.TAG)
            return
        }

        if (BillingContract.ids.contains(BillingContract.mapViewIdToSku(v.id))) {
            val productDetails = v.tag as ProductDetails

            val params = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build()
            )

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(params)
                .build()

            billingClient.launchBillingFlow(requireActivity(), flowParams)
        }
    }

    override fun onResume() {
        super.onResume()
        // Note: We query purchases in onResume() to handle purchases completed while the activity
        // is inactive. For example, this can happen if the activity is destroyed during the
        // purchase flow. This ensures that when the activity is resumed it reflects the user's
        // current purchases.
        queryPurchases()
    }

    private fun queryPurchases() {

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params, this)

    }

    /**
     * Credits the coins BEFORE consuming the purchase, which is the opposite of the order this
     * used to run in and the whole point of it.
     *
     * Play forgets a purchase the moment it is consumed and will never hand it back, so
     * consuming first leaves a window where a crash — or the activity being destroyed mid flow —
     * destroys paid coins with nothing left to recover them from. Recording first means the
     * worst case is an unconsumed purchase, which Play returns on the next query so the consume
     * simply retries. [RewardModel.rewardPurchaseCoins] is keyed on the purchase token, so being
     * handed the same purchase again credits nothing further.
     */
    private fun creditThenConsume(purchase: Purchase) {
        // A PENDING purchase is not paid for yet. enablePendingPurchases() is on, so these do
        // arrive here, and the old code would have consumed and credited one.
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        val productId = purchase.products.firstOrNull() ?: return
        val pack = BillingContract.coins[productId] ?: return

        // One grant for the whole order rather than one per unit — quantity is part of the
        // amount, not a reason to mint several rows.
        val coins = (pack.first + pack.second) * purchase.quantity

        // Not requireContext() — a purchase can land after the sheet is gone, which is the very
        // case this method exists to survive.
        val context = appContext ?: return
        val orderId = purchase.orderId.orEmpty()

        // Deliberately not tied to this fragment's lifecycle: money has changed hands, so the
        // grant has to land even if the sheet is dismissed while it is being written.
        billingScope.launch {
            val credited = RewardModel.rewardPurchaseCoins(context, coins, purchase.purchaseToken)

            billingClient.consumeAsync(
                ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            ) { result, _ ->
                // A failed consume is self healing — Play returns the purchase on the next
                // query and this runs again, crediting nothing and retrying the consume.
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "Consume failed (${result.responseCode}), will retry on next query")
                }
            }

            if (credited) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.billing_coins_credited, coins, orderId),
                        Toast.LENGTH_LONG
                    ).show()

                    if (isAdded) dismiss()
                }
            }
        }
    }

    companion object {
        const val TAG = "FragmentPurchase"

        /** Outlives the fragment on purpose — see [creditThenConsume]. */
        private val billingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { creditThenConsume(it) }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                // Handle an error caused by a user cancelling the purchase flow.

                Toast.makeText(
                    requireContext().applicationContext,
                    R.string.billing_coin_purchase_cancelled,
                    Toast.LENGTH_LONG
                ).show()

                dismiss()
            }

            else -> {
                // Handle any other error codes.
            }
        }
    }

    override fun onQueryPurchasesResponse(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>
    ) {
        onPurchasesUpdated(billingResult, purchases)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        hideBackButton()
    }
}