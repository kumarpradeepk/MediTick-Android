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

/**
 * Untrusted purchase evidence sent only over TLS to MediTick's backend. The
 * backend verifies it with Google Play before deriving a quota identity.
 */
class AIScanPurchaseIdentity(
    val purchaseToken: String,
    val productId: String,
    val productType: String,
    private val debugId: String? = null,
) {
    fun putInto(target: org.json.JSONObject): org.json.JSONObject {
        if (debugId != null) {
            return target.put("provider", "debug").put("debug_id", debugId)
        }
        return target
            .put("provider", "google_play")
            .put("purchase_token", purchaseToken)
            .put("product_id", productId)
            .put("product_type", productType)
    }

    override fun toString(): String = if (debugId != null) {
        "AIScanPurchaseIdentity(debug=<redacted>)"
    } else {
        "AIScanPurchaseIdentity(productId=$productId, token=<redacted>)"
    }

    companion object {
        fun debug(id: String) = AIScanPurchaseIdentity("", "", "", debugId = id)
    }
}

class BillingManager private constructor(private val context: Context) : PurchasesUpdatedListener {
    private val prefs = context.getSharedPreferences("meditick-billing", Context.MODE_PRIVATE)
    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    var isPro by mutableStateOf(prefs.getBoolean("is_pro", false)); private set
    var scanIdentity by mutableStateOf<AIScanPurchaseIdentity?>(null); private set
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
            val subscriptions: List<ProductDetails> = if (result.responseCode == BillingClient.BillingResponseCode.OK) details.productDetailsList else emptyList()
            val inAppParams = QueryProductDetailsParams.newBuilder().setProductList(
                listOf(QueryProductDetailsParams.Product.newBuilder().setProductId("lifetime").setProductType(BillingClient.ProductType.INAPP).build()),
            ).build()
            client.queryProductDetailsAsync(inAppParams) { inAppResult, inAppDetails ->
                val lifetime: List<ProductDetails> = if (inAppResult.responseCode == BillingClient.BillingResponseCode.OK) inAppDetails.productDetailsList else emptyList()
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
        scanIdentity = active
            .sortedWith(compareBy<Purchase> { if ("lifetime" in it.products) 0 else 1 }
                .thenBy { it.products.joinToString(",") })
            .firstOrNull()
            ?.let { purchase ->
                purchase.products.firstOrNull(PRODUCT_IDS::contains)?.let { productId ->
                    AIScanPurchaseIdentity(
                        purchaseToken = purchase.purchaseToken,
                        productId = productId,
                        productType = if (productId == "lifetime") "inapp" else "subs",
                    )
                }
            }
        prefs.edit().putBoolean("is_pro", isPro).remove("scan_account_id").apply()
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
