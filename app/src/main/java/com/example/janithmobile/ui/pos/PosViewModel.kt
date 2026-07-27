package com.example.janithmobile.ui.pos

import android.app.Application
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.janithmobile.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

class PosViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DefaultDataRepository(application)
    val syncManager = FirebaseSyncManager(application, repository)
    val printerManager = PrinterManager(application, repository)

    // Reactive State lists
    val categories: StateFlow<List<Category>> = repository.categories
    val customers: StateFlow<List<Customer>> = repository.customers
    val heldBills: StateFlow<List<HeldBill>> = repository.heldBills
    val sales: StateFlow<List<Sale>> = repository.sales
    val expenses: StateFlow<List<Expense>> = repository.expenses
    val repairs: StateFlow<List<RepairJob>> = repository.repairs
    val returns: StateFlow<List<ReturnBill>> = repository.returns

    // POS Screen state
    var selectedSaleMode = mutableStateOf("retail")
    var selectedCategoryId = mutableStateOf<Int?>(null)
    var searchQuery = mutableStateOf("")

    // Cart details
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems = _cartItems.asStateFlow()

    var discountValue = mutableStateOf(0.0)
    var discountType = mutableStateOf("a") // "a" = amount (Rs), "p" = percentage (%)

    var selectedCustomer = mutableStateOf<Customer?>(null)
    var currentDeviceInfo = mutableStateOf<DeviceInfo?>(null)
    var lowStockThreshold = mutableStateOf(5)
    var isFirebaseOnline = mutableStateOf(true)
    var isLicenseBlocked = mutableStateOf(false)
    var lockReason = mutableStateOf("NONE") // "NONE", "BLOCKED", "OFFLINE_EXPIRED"

    // Products Flow with search & category filters
    val filteredProducts: StateFlow<List<Product>> = combine(
        repository.products,
        MutableStateFlow(selectedCategoryId),
        MutableStateFlow(searchQuery)
    ) { prods, catIdState, queryState ->
        val catId = catIdState.value
        val query = queryState.value.lowercase()
        prods.filter { p ->
            val matchCat = catId == null || p.categoryId == catId
            val matchQuery = query.isEmpty() || 
                    p.name.lowercase().contains(query) || 
                    (p.barcode != null && p.barcode.contains(query))
            matchCat && matchQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.refreshAll()
            loadDeviceInfo()
            loadSettings()
        }
    }

    private suspend fun loadSettings() {
        val thresholdStr = repository.getSetting("lowStockThreshold", "5")
        lowStockThreshold.value = thresholdStr.toIntOrNull() ?: 5

        val status = repository.getSetting("licenseStatus", "ACTIVE")
        val lastCheckStr = repository.getSetting("lastOnlineCheckTime", System.currentTimeMillis().toString())
        val lastCheck = lastCheckStr.toLongOrNull() ?: System.currentTimeMillis()
        val daysOffline = (System.currentTimeMillis() - lastCheck) / (1000 * 60 * 60 * 24)

        if (status == "BLOCKED") {
            isLicenseBlocked.value = true
            lockReason.value = "BLOCKED"
        } else if (daysOffline > 7) {
            isLicenseBlocked.value = true
            lockReason.value = "OFFLINE_EXPIRED"
        } else {
            isLicenseBlocked.value = false
            lockReason.value = "NONE"
        }
    }

    fun setLicenseStatus(status: String) {
        viewModelScope.launch {
            repository.saveSetting("licenseStatus", status)
            repository.saveSetting("lastOnlineCheckTime", System.currentTimeMillis().toString())
            if (status == "BLOCKED") {
                isLicenseBlocked.value = true
                lockReason.value = "BLOCKED"
            } else {
                isLicenseBlocked.value = false
                lockReason.value = "NONE"
            }
        }
    }

    fun checkLicenseStatus() {
        viewModelScope.launch {
            loadSettings()
        }
    }

    fun updateLowStockThreshold(limit: Int) {
        lowStockThreshold.value = limit
        viewModelScope.launch {
            repository.saveSetting("lowStockThreshold", limit.toString())
        }
    }

    private suspend fun loadDeviceInfo() {
        val info = repository.getDeviceInfo()
        if (info != null) {
            currentDeviceInfo.value = info
        } else {
            // Default setup values
            val newInfo = DeviceInfo(
                id = java.util.UUID.randomUUID().toString().substring(0, 8),
                cashier = "Cashier 1",
                devName = "Counter A",
                store = "Janith Mobile"
            )
            repository.saveDeviceInfo(newInfo)
            currentDeviceInfo.value = newInfo
        }
    }

    fun updateDeviceInfo(info: DeviceInfo) {
        viewModelScope.launch {
            repository.saveDeviceInfo(info)
            currentDeviceInfo.value = info
        }
    }

    // ===== CART CONTROL =====

    fun addToCart(product: Product) {
        val list = _cartItems.value.toMutableList()
        val price = getProductPriceForMode(product, selectedSaleMode.value)
        val existingIndex = list.indexOfFirst { it.productId == product.id && it.mode == selectedSaleMode.value }
        
        if (existingIndex > -1) {
            val item = list[existingIndex]
            item.qty += 1
            item.subtotal = item.qty * item.price
        } else {
            list.add(
                CartItem(
                    productId = product.id,
                    name = product.name,
                    price = price,
                    costPrice = product.costPrice,
                    qty = 1,
                    subtotal = price,
                    mode = selectedSaleMode.value
                )
            )
        }
        _cartItems.value = list
    }

    private fun getProductPriceForMode(product: Product, mode: String): Double {
        return if (mode.startsWith("wsale")) {
            product.wsalePrice ?: product.retailPrice
        } else {
            product.retailPrice
        }
    }

    fun updateCartQty(productId: Int, delta: Int) {
        val list = _cartItems.value.toMutableList()
        val index = list.indexOfFirst { it.productId == productId && it.mode == selectedSaleMode.value }
        if (index > -1) {
            val item = list[index]
            val newQty = item.qty + delta
            if (newQty <= 0) {
                list.removeAt(index)
            } else {
                item.qty = newQty
                item.subtotal = newQty * item.price
            }
            _cartItems.value = list
        }
    }

    fun removeCartItem(productId: Int) {
        val list = _cartItems.value.toMutableList()
        list.removeAll { it.productId == productId && it.mode == selectedSaleMode.value }
        _cartItems.value = list
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        selectedCustomer.value = null
        discountValue.value = 0.0
    }

    // Cart calculations
    fun getCartSubtotal(): Double {
        return _cartItems.value.sumOf { it.subtotal }
    }

    fun getCartDiscount(): Double {
        val sub = getCartSubtotal()
        val div = discountValue.value
        val disc = if (discountType.value == "p") {
            sub * (div / 100.0)
        } else {
            div
        }
        return disc.coerceIn(0.0, sub)
    }

    fun getCartTotal(): Double {
        return (getCartSubtotal() - getCartDiscount()).coerceAtLeast(0.0)
    }

    // ===== SALE FINALIZE =====

    fun completeSale(paidAmount: Double, paymentMethod: String, onDone: (Sale) -> Unit) {
        val device = currentDeviceInfo.value ?: return
        val total = getCartTotal()
        val change = if (paymentMethod == "cash") {
            (paidAmount - total).coerceAtLeast(0.0)
        } else 0.0

        val sale = Sale(
            billNo = "B" + System.currentTimeMillis().toString().takeLast(8),
            saleType = selectedSaleMode.value,
            subtotal = getCartSubtotal(),
            discount = getCartDiscount(),
            total = total,
            paymentMethod = paymentMethod,
            paidAmount = paidAmount,
            changeAmount = change,
            items = _cartItems.value,
            customerId = selectedCustomer.value?.id,
            customerName = selectedCustomer.value?.name,
            cashierName = device.cashier,
            deviceId = device.id,
            createdAt = System.currentTimeMillis()
        )

        viewModelScope.launch {
            val insertedId = repository.completeSale(sale)
            if (insertedId > 0) {
                val completeSaleObj = sale.copy(id = insertedId)
                if (printerManager.isConnected() && printerManager.autoPrint) {
                    printerManager.printSale(completeSaleObj, device.store)
                }
                onDone(completeSaleObj)
                clearCart()
                // Sync in background to cloud
                launch(kotlinx.coroutines.Dispatchers.IO) {
                    syncManager.syncNow()
                }
            }
        }
    }

    // ===== HELD BILL ACTION =====

    fun holdCurrentBill() {
        if (_cartItems.value.isEmpty()) return
        val device = currentDeviceInfo.value ?: return
        val heldBill = HeldBill(
            billNo = "H" + System.currentTimeMillis().toString().takeLast(8),
            saleType = selectedSaleMode.value,
            subtotal = getCartSubtotal(),
            discount = getCartDiscount(),
            total = getCartTotal(),
            items = _cartItems.value,
            customerId = selectedCustomer.value?.id,
            customerName = selectedCustomer.value?.name,
            cashierName = device.cashier,
            deviceId = device.id,
            createdAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.holdBill(heldBill)
            clearCart()
        }
    }

    fun resumeHeldBill(bill: HeldBill) {
        clearCart()
        selectedSaleMode.value = bill.saleType
        _cartItems.value = bill.items
        discountValue.value = bill.discount
        discountType.value = "a" // Standardise back to amount representation
        viewModelScope.launch {
            repository.deleteHeldBill(bill.id)
            if (bill.customerId != null) {
                selectedCustomer.value = customers.value.find { it.id == bill.customerId }
            }
        }
    }

    fun deleteHeldBill(id: Int) {
        viewModelScope.launch {
            repository.deleteHeldBill(id)
        }
    }

    // ===== PRODUCT CRUD =====

    fun saveProduct(p: Product, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveProduct(p)
            repository.refreshAll()
            onDone()
            launch(kotlinx.coroutines.Dispatchers.IO) { syncManager.syncNow() }
        }
    }

    fun deleteProduct(id: Int, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteProduct(id)
            repository.refreshAll()
            onDone()
        }
    }

    // ===== CATEGORY CRUD =====

    fun saveCategory(c: Category, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveCategory(c)
            repository.refreshAll()
            onDone()
        }
    }

    fun deleteCategory(id: Int, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteCategory(id)
            repository.refreshAll()
            onDone()
        }
    }

    // ===== CUSTOMER CRUD =====

    fun saveCustomer(c: Customer, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveCustomer(c)
            repository.refreshAll()
            onDone()
            launch(kotlinx.coroutines.Dispatchers.IO) { syncManager.syncNow() }
        }
    }

    fun deleteCustomer(id: Int, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteCustomer(id)
            repository.refreshAll()
            onDone()
        }
    }

    fun recordCustomerDuePayment(customerId: Int, amount: Double) {
        viewModelScope.launch {
            repository.recordCustomerPayment(customerId, amount)
            repository.refreshAll()
            launch(kotlinx.coroutines.Dispatchers.IO) { syncManager.syncNow() }
        }
    }

    // ===== EXPENSE ACTIONS =====
    fun addExpense(amount: Double, description: String?, type: String) {
        viewModelScope.launch {
            repository.addExpense(Expense(amount = amount, description = description, type = type, createdAt = System.currentTimeMillis()))
            repository.refreshAll()
        }
    }

    // ===== REPAIR ACTIONS =====
    fun saveRepairJob(job: RepairJob, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveRepairJob(job)
            repository.refreshAll()
            onDone()
        }
    }

    fun updateRepairStatus(jobId: Int, status: String) {
        viewModelScope.launch {
            repository.updateRepairStatus(jobId, status)
            repository.refreshAll()
        }
    }

    // ===== RETURN ACTIONS =====
    fun recordReturn(saleBillNo: String, returnedItems: List<CartItem>, refundAmount: Double, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.recordReturn(ReturnBill(saleBillNo = saleBillNo, returnedItems = returnedItems, refundAmount = refundAmount, createdAt = System.currentTimeMillis()))
            repository.refreshAll()
            onDone()
        }
    }

    // ===== BARCODE ACTION =====

    fun handleBarcodeScan(barcode: String): Boolean {
        val prod = repository.products.value.find { it.barcode == barcode }
        return if (prod != null) {
            addToCart(prod)
            true
        } else {
            false
        }
    }

    fun saveFirebaseConfig(configJson: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.saveFirebaseConfig(configJson)
            syncManager.tryInit()
        }
    }

    suspend fun getFirebaseConfig(): String? {
        return repository.getFirebaseConfig()
    }
}
