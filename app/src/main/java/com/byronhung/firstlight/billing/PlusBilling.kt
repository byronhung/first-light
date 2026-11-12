package com.byronhung.firstlight.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.byronhung.firstlight.BuildConfig
import com.byronhung.firstlight.data.AlarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * First Light Plus through Google Play Billing: one non-consumable in-app product, [PRODUCT_ID].
 *
 * Google Play is the source of truth. Every time the app comes to the front we ask Play what this
 * Google account owns and copy the answer into settings: that restores Plus after a reinstall or on
 * a new phone, unlocks a cash payment that cleared while the app was closed, and takes Plus away
 * after a refund. A failed query changes nothing, so being offline never locks anyone out.
 *
 * Purchases must be acknowledged within 3 days or Google refunds them automatically.
 */
class PlusBilling(
    context: Context,
    private val repository: AlarmRepository,
    private val scope: CoroutineScope,
) {
    /** What the offer sheet shows. */
    data class Offer(
        /** Play's localised price, e.g. "₱249.00". Null until loaded or when Play can't sell. */
        val price: String? = null,
        /** False once we know Plus can't be bought here (no Play, product missing). Null = still finding out. */
        val available: Boolean? = null,
        /** A purchase is waiting on payment (cash at a store, slow cards). */
        val pending: Boolean = false,
        /** Play says this account owns Plus. */
        val owned: Boolean = false,
    )

    private val _offer = MutableStateFlow(Offer())
    val offer: StateFlow<Offer> = _offer.asStateFlow()

    /**
     * Fires once per new purchase (the thank-you screen). New = not yet acknowledged: a purchase made
     * just now, or a cash payment that cleared while the app was closed. A restore after a reinstall
     * finds an already-acknowledged purchase and stays quiet.
     */
    private val _newPurchase = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val newPurchase: SharedFlow<Unit> = _newPurchase

    private val _synced = MutableStateFlow(false)

    /**
     * True once Play has answered "what does this account own?" this run and settings match it.
     * Until then Plus is unknown, so nothing should try to sell it (a reinstall showed the
     * "three good mornings" popup to someone who'd already bought Plus).
     */
    val synced: StateFlow<Boolean> = _synced.asStateFlow()

    private var product: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        // No enableAutoServiceReconnection(): it raced our own startConnection ("already in the
        // process of connecting", result 5), the purchase query failed, and a reinstall didn't
        // restore Plus (0.17). One connection path: refresh() below.
        .build()

    /** A startConnection is in flight; a second one would fail with DEVELOPER_ERROR. */
    @Volatile private var connecting = false

    /** Failed queries retried so far this run (a fresh install can catch Play still waking up). */
    @Volatile private var retries = 0

    /** Connect, load the price, and sync what's owned. Safe to call repeatedly (each app resume). */
    fun refresh() {
        if (client.isReady) {
            loadProduct()
            syncPurchases()
            return
        }
        if (connecting) return // The connection in flight will load and sync when it lands.
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    loadProduct()
                    syncPurchases()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.responseCode} ${result.debugMessage}")
                    _offer.update { it.copy(available = false) }
                    retryLater()
                }
            }

            override fun onBillingServiceDisconnected() {
                // The next refresh() (app resume, opening the offer) connects again.
                connecting = false
            }
        })
    }

    /** Opens Google Play's purchase sheet. Returns false if Plus can't be bought right now. */
    fun buy(activity: Activity): Boolean {
        val details = product ?: run { refresh(); return false }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()),
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) syncPurchases()
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { result, details ->
            val found = details.productDetailsList.firstOrNull { it.productId == PRODUCT_ID }
            if (result.responseCode == BillingClient.BillingResponseCode.OK && found != null) {
                product = found
                _offer.update { it.copy(price = found.oneTimePurchaseOfferDetails?.formattedPrice, available = true) }
            } else {
                Log.w(TAG, "No product '$PRODUCT_ID': ${result.responseCode} ${result.debugMessage}")
                _offer.update { it.copy(available = false) }
            }
        }
    }

    private fun syncPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                // Unknown is not "not owned": change nothing, try again shortly.
                Log.w(TAG, "Purchase query failed: ${result.responseCode} ${result.debugMessage}")
                retryLater()
                return@queryPurchasesAsync
            }
            val plus = purchases.filter { PRODUCT_ID in it.products }
            Log.i(TAG, "Play says: ${plus.map { it.purchaseState to it.isAcknowledged }.ifEmpty { "no Plus purchase" }}")
            plus.forEach { handle(it) }
            val owned = plus.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            _offer.update { it.copy(owned = owned, pending = !owned && plus.any { p -> p.purchaseState == Purchase.PurchaseState.PENDING }) }
            scope.launch {
                // Debug builds keep the Settings debug switch working; release builds follow Play.
                if (owned) writePlus(true) else if (!BuildConfig.DEBUG) writePlus(false)
                _synced.value = true
            }
        }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().filter { PRODUCT_ID in it.products }.forEach { handle(it) }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> syncPurchases()
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> Log.w(TAG, "Purchase failed: ${result.responseCode} ${result.debugMessage}")
        }
    }

    private fun handle(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                _offer.update { it.copy(pending = false, owned = true) }
                setPlus(true) // Unlock first: the thank-you screen shouldn't wait on the network.
                if (!purchase.isAcknowledged) {
                    _newPurchase.tryEmit(Unit)
                    val ack = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                    client.acknowledgePurchase(ack) { r ->
                        // Not fatal: the next sync acknowledges it again, well inside the 3 days.
                        if (r.responseCode != BillingClient.BillingResponseCode.OK) Log.w(TAG, "Acknowledge failed: ${r.debugMessage}")
                    }
                }
            }
            Purchase.PurchaseState.PENDING -> _offer.update { it.copy(pending = true) }
            else -> Unit
        }
    }

    /** Up to 3 retries per run, a few seconds apart. */
    private fun retryLater() {
        if (retries >= 3) return
        retries++
        scope.launch {
            delay(2_000L * retries)
            refresh()
        }
    }

    private fun setPlus(on: Boolean) {
        scope.launch { writePlus(on) }
    }

    /** Writes only on a change, so a sync never creates the settings row on a fresh install. */
    private suspend fun writePlus(on: Boolean) {
        if (repository.currentSettings().isPlus != on) repository.setPlus(on)
    }

    companion object {
        /** Must match the one-time product ID created in Play Console → Monetize → Products. */
        const val PRODUCT_ID = "plus"
        private const val TAG = "PlusBilling"
    }
}
