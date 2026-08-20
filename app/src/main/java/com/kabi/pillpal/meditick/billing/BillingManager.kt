package com.kabi.pillpal.meditick.billing

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.*
import com.kabi.pillpal.meditick.R
import com.kabi.pillpal.meditick.notifications.NotificationScheduler

data class BillingPlan(
    val id: String, val title: String, val subtitle: String, val price: String,
    val productDetails: ProductDetails, val offerToken: String? = null,
)

class BillingManager private constructor(private val context: Context) : PurchasesUpdatedListener {
    private val prefs = context.getSharedPreferences("meditick-billing", Context.MODE_PRIVATE)
    private val client = BillingClient.newBuilder(context)
        .setListener(this).enablePendingPurchases().build()

    var isPro by mutableStateOf(prefs.getBoolean("is_pro", false)); private set
    var plans by mutableStateOf<List<BillingPlan>>(emptyList()); private set
    var isLoading by mutableStateOf(false); private set
    var lastMessage by mutableStateOf<String?>(null); private set

    init { connect() }

    fun connect() {
        if (client.isReady) { refresh(); return }
        isLoading = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
                else { isLoading = false; lastMessage = result.debugMessage }
            }
            override fun onBillingServiceDisconnected() { isLoading = false }
        })
    }

    fun refresh() {
        if (!client.isReady) { connect(); return }
        isLoading = true
        queryProducts()
        queryPurchases()
    }

    private fun queryProducts() {
        val subParams = QueryProductDetailsParams.newBuilder().setProductList(
            listOf("monthly", "yearly").map {
                QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build()
            },
        ).build()
        client.queryProductDetailsAsync(subParams) { result, details ->
            val subscriptions: List<ProductDetails> = if (result.responseCode == BillingClient.BillingResponseCode.OK) details else emptyList()
            val inAppParams = QueryProductDetailsParams.newBuilder().setProductList(
                listOf(QueryProductDetailsParams.Product.newBuilder().setProductId("lifetime").setProductType(BillingClient.ProductType.INAPP).build()),
            ).build()
            client.queryProductDetailsAsync(inAppParams) { inAppResult, inAppDetails ->
                val lifetime: List<ProductDetails> = if (inAppResult.responseCode == BillingClient.BillingResponseCode.OK) inAppDetails else emptyList()
                plans = (subscriptions + lifetime).mapNotNull(::toPlan).sortedBy { listOf("monthly", "yearly", "lifetime").indexOf(it.id) }
                isLoading = false
                if (plans.isEmpty()) lastMessage = context.getString(R.string.billing_products_missing)
            }
        }
    }

    private fun toPlan(details: ProductDetails): BillingPlan? {
        if (details.productType == BillingClient.ProductType.INAPP) {
            val price = details.oneTimePurchaseOfferDetails?.formattedPrice ?: return null
            return BillingPlan(details.productId, context.getString(R.string.billing_plan_lifetime), context.getString(R.string.billing_tag_one_time), price, details)
        }
        val offer = details.subscriptionOfferDetails?.firstOrNull() ?: return null
        val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return null
        val title = context.getString(if (details.productId == "yearly") R.string.billing_plan_yearly else R.string.billing_plan_monthly)
        val subtitle = context.getString(if (details.productId == "yearly") R.string.billing_tag_best_value else R.string.billing_tag_flexible)
        return BillingPlan(details.productId, title, subtitle, phase.formattedPrice, details, offer.offerToken)
    }

    fun purchase(activity: Activity, plan: BillingPlan) {
        val details = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(plan.productDetails).apply {
            plan.offerToken?.let(::setOfferToken)
        }.build()
        val result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(details)).build())
        if (result.responseCode != BillingClient.BillingResponseCode.OK) lastMessage = result.debugMessage
    }

    fun restore() {
        lastMessage = context.getString(R.string.billing_checking_purchases)
        queryPurchases(onComplete = { lastMessage = context.getString(if (isPro) R.string.billing_restored else R.string.billing_none_found) })
    }

    private fun queryPurchases(onComplete: (() -> Unit)? = null) {
        if (!client.isReady) { connect(); return }
        val all = mutableListOf<Purchase>()
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { _, purchases ->
            all += purchases
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { _, inApps ->
                all += inApps
                applyPurchases(all)
                onComplete?.invoke()
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> applyPurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> lastMessage = result.debugMessage
        }
    }

    private fun applyPurchases(purchases: List<Purchase>) {
        val active = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.any(PRODUCT_IDS::contains) }
        active.filterNot { it.isAcknowledged }.forEach { purchase ->
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { }
        }
        isPro = active.isNotEmpty()
        prefs.edit().putBoolean("is_pro", isPro).apply()
        NotificationScheduler.scheduleAll(context)
        if (isPro) lastMessage = context.getString(R.string.billing_pro_unlocked)
    }

    fun clearMessage() { lastMessage = null }

    companion object {
        private val PRODUCT_IDS = setOf("monthly", "yearly", "lifetime")
        @Volatile private var instance: BillingManager? = null
        fun get(context: Context): BillingManager = instance ?: synchronized(this) {
            instance ?: BillingManager(context.applicationContext).also { instance = it }
        }
    }
}
