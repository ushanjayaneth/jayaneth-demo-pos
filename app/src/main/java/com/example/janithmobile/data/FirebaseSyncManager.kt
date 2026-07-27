package com.example.janithmobile.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class FirebaseSyncManager(private val context: Context, private val repository: DataRepository) {

    private val tag = "FirebaseSyncManager"
    private var firestore: FirebaseFirestore? = null
    private var storeId = "janith-store-1"
    private var isInitialized = false

    init {
        CoroutineScope(Dispatchers.IO).launch {
            tryInit()
        }
    }

    suspend fun tryInit() {
        if (isInitialized) return
        val configStr = repository.getFirebaseConfig() ?: return
        try {
            val json = JSONObject(configStr)
            val apiKey = json.optString("apiKey")
            val projectId = json.optString("projectId")
            val appId = json.optString("appId")
            storeId = json.optString("storeId", "janith-store-1")

            if (apiKey.isEmpty() || projectId.isEmpty() || appId.isEmpty()) {
                Log.w(tag, "Firebase options are incomplete")
                return
            }

            val options = FirebaseOptions.Builder()
                .setApiKey(apiKey)
                .setProjectId(projectId)
                .setApplicationId(appId)
                .build()

            // Initialize on Main thread because FirebaseApp demands it
            withContextMain {
                val app = if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context, options)
                } else {
                    FirebaseApp.getInstance()
                }
                firestore = FirebaseFirestore.getInstance(app)
                isInitialized = true
                Log.d(tag, "Firebase initialized successfully")
            }
            // Trigger an initial sync
            syncNow()
        } catch (e: Exception) {
            Log.e(tag, "Error initializing Firebase: ${e.message}", e)
        }
    }

    private suspend fun withContextMain(block: suspend () -> Unit) {
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            block()
        }
    }

    fun isReady(): Boolean = isInitialized && firestore != null

    suspend fun syncNow() {
        if (!isReady()) return
        val db = firestore ?: return
        
        Log.d(tag, "Starting sync...")

        // 0. Check & Sync Device License Status
        try {
            val device = repository.getDeviceInfo()
            if (device != null) {
                val devDocRef = db.collection("stores").document(storeId)
                    .collection("devices").document(device.id)

                devDocRef.get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val status = snapshot.getString("licenseStatus") ?: "ACTIVE"
                        CoroutineScope(Dispatchers.IO).launch {
                            repository.saveSetting("licenseStatus", status)
                            repository.saveSetting("lastOnlineCheckTime", System.currentTimeMillis().toString())
                        }
                    } else {
                        val devMap = hashMapOf(
                            "id" to device.id,
                            "store" to device.store,
                            "devName" to device.devName,
                            "cashier" to device.cashier,
                            "licenseStatus" to "ACTIVE",
                            "lastSeen" to System.currentTimeMillis()
                        )
                        devDocRef.set(devMap)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error checking device license: ${e.message}")
        }

        // 1. Sync Products
        try {
            val unsyncedProds = repository.getUnsyncedProducts()
            if (unsyncedProds.isNotEmpty()) {
                val batch = db.batch()
                val syncedIds = mutableListOf<String>()
                unsyncedProds.forEach { prod ->
                    val docRef = db.collection("stores").document(storeId)
                        .collection("products").document(prod.syncId)
                    
                    val map = hashMapOf(
                        "name" to prod.name,
                        "barcode" to prod.barcode,
                        "categoryId" to prod.categoryId,
                        "retailPrice" to prod.retailPrice,
                        "wsalePrice" to prod.wsalePrice,
                        "stock" to prod.stock,
                        "description" to prod.description,
                        "syncId" to prod.syncId
                    )
                    batch.set(docRef, map)
                    syncedIds.add(prod.syncId)
                }
                batch.commit().addOnSuccessListener {
                    CoroutineScope(Dispatchers.IO).launch {
                        repository.markProductsAsSynced(syncedIds)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error syncing products: ${e.message}")
        }

        // 2. Sync Customers
        try {
            val unsyncedCusts = repository.getUnsyncedCustomers()
            if (unsyncedCusts.isNotEmpty()) {
                val batch = db.batch()
                val syncedIds = mutableListOf<String>()
                unsyncedCusts.forEach { cust ->
                    val docRef = db.collection("stores").document(storeId)
                        .collection("customers").document(cust.syncId)
                    
                    val map = hashMapOf(
                        "name" to cust.name,
                        "phone" to cust.phone,
                        "address" to cust.address,
                        "totalDue" to cust.totalDue,
                        "syncId" to cust.syncId
                    )
                    batch.set(docRef, map)
                    syncedIds.add(cust.syncId)
                }
                batch.commit().addOnSuccessListener {
                    CoroutineScope(Dispatchers.IO).launch {
                        repository.markCustomersAsSynced(syncedIds)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error syncing customers: ${e.message}")
        }

        // 3. Sync Sales
        try {
            val unsyncedSales = repository.getUnsyncedSales()
            if (unsyncedSales.isNotEmpty()) {
                val batch = db.batch()
                val syncedIds = mutableListOf<String>()
                unsyncedSales.forEach { sale ->
                    val docRef = db.collection("stores").document(storeId)
                        .collection("sales").document(sale.syncId)
                    
                    val itemsList = sale.items.map {
                        hashMapOf(
                            "productId" to it.productId,
                            "name" to it.name,
                            "price" to it.price,
                            "qty" to it.qty,
                            "subtotal" to it.subtotal,
                            "mode" to it.mode
                        )
                    }

                    val map = hashMapOf(
                        "billNo" to sale.billNo,
                        "saleType" to sale.saleType,
                        "subtotal" to sale.subtotal,
                        "discount" to sale.discount,
                        "total" to sale.total,
                        "paymentMethod" to sale.paymentMethod,
                        "paidAmount" to sale.paidAmount,
                        "changeAmount" to sale.changeAmount,
                        "items" to itemsList,
                        "customerId" to sale.customerId,
                        "customerName" to sale.customerName,
                        "cashierName" to sale.cashierName,
                        "deviceId" to sale.deviceId,
                        "createdAt" to sale.createdAt,
                        "syncId" to sale.syncId
                    )
                    batch.set(docRef, map)
                    syncedIds.add(sale.syncId)
                }
                batch.commit().addOnSuccessListener {
                    CoroutineScope(Dispatchers.IO).launch {
                        repository.markSalesAsSynced(syncedIds)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error syncing sales: ${e.message}")
        }
    }
}
