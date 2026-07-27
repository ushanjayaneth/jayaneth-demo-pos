package com.example.janithmobile.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// ===== DATA MODELS =====
data class Product(
    val id: Int = 0,
    val barcode: String?,
    val name: String,
    val categoryId: Int?,
    val retailPrice: Double,
    val wsalePrice: Double?,
    val costPrice: Double? = 0.0,
    val stock: Int?,
    val description: String?,
    val syncId: String = UUID.randomUUID().toString(),
    val synced: Int = 0
)

data class Category(
    val id: Int = 0,
    val name: String,
    val icon: String?,
    val color: String?,
    val sortOrder: Int,
    val syncId: String = UUID.randomUUID().toString(),
    val synced: Int = 0
)

data class CartItem(
    val productId: Int,
    val name: String,
    val price: Double,
    val costPrice: Double? = 0.0,
    var qty: Int,
    var subtotal: Double,
    val mode: String
)

data class Sale(
    val id: Int = 0,
    val billNo: String,
    val saleType: String,
    val subtotal: Double,
    val discount: Double,
    val total: Double,
    val paymentMethod: String,
    val paidAmount: Double?,
    val changeAmount: Double?,
    val items: List<CartItem>,
    val customerId: Int?,
    val customerName: String?,
    val cashierName: String?,
    val deviceId: String?,
    val createdAt: Long,
    val syncId: String = UUID.randomUUID().toString(),
    val synced: Int = 0
)

data class HeldBill(
    val id: Int = 0,
    val billNo: String,
    val saleType: String,
    val subtotal: Double,
    val discount: Double,
    val total: Double,
    val items: List<CartItem>,
    val customerId: Int?,
    val customerName: String?,
    val cashierName: String?,
    val deviceId: String?,
    val createdAt: Long
)

data class Customer(
    val id: Int = 0,
    val name: String,
    val phone: String?,
    val address: String?,
    val totalDue: Double = 0.0,
    val syncId: String = UUID.randomUUID().toString(),
    val synced: Int = 0
)

data class DeviceInfo(
    val id: String,
    val cashier: String,
    val devName: String,
    val store: String
)

data class Expense(
    val id: Int = 0,
    val amount: Double,
    val description: String?,
    val type: String,
    val createdAt: Long
)

data class RepairJob(
    val id: Int = 0,
    val customerName: String,
    val customerPhone: String?,
    val deviceModel: String,
    val issueDescription: String,
    val estimatedCost: Double,
    val status: String,
    val createdAt: Long
)

data class ReturnBill(
    val id: Int = 0,
    val saleBillNo: String,
    val returnedItems: List<CartItem>,
    val refundAmount: Double,
    val createdAt: Long
)

interface DataRepository {
    // Flows for reactive UI updates
    val products: Flow<List<Product>>
    val categories: Flow<List<Category>>
    val customers: Flow<List<Customer>>
    val heldBills: Flow<List<HeldBill>>
    val sales: Flow<List<Sale>>
    val expenses: Flow<List<Expense>>
    val repairs: Flow<List<RepairJob>>
    val returns: Flow<List<ReturnBill>>

    suspend fun refreshAll()
    
    // Product Actions
    suspend fun saveProduct(product: Product): Boolean
    suspend fun deleteProduct(id: Int): Boolean

    // Category Actions
    suspend fun saveCategory(category: Category): Boolean
    suspend fun deleteCategory(id: Int): Boolean

    // Customer Actions
    suspend fun saveCustomer(customer: Customer): Boolean
    suspend fun deleteCustomer(id: Int): Boolean
    suspend fun recordCustomerPayment(customerId: Int, amount: Double): Boolean

    // Sale Actions
    suspend fun completeSale(sale: Sale): Int
    suspend fun getSalesForPeriod(fromTimestamp: Long): List<Sale>

    // Held Bill Actions
    suspend fun holdBill(heldBill: HeldBill): Boolean
    suspend fun deleteHeldBill(id: Int): Boolean

    // Expense Actions
    suspend fun addExpense(expense: Expense): Boolean
    suspend fun getExpensesForPeriod(fromTimestamp: Long): List<Expense>
    
    // Repair Actions
    suspend fun saveRepairJob(job: RepairJob): Boolean
    suspend fun updateRepairStatus(jobId: Int, status: String): Boolean
    
    // Return Actions
    suspend fun recordReturn(returnBill: ReturnBill): Boolean

    // Settings
    suspend fun getDeviceInfo(): DeviceInfo?
    suspend fun saveDeviceInfo(info: DeviceInfo): Boolean
    suspend fun getFirebaseConfig(): String?
    suspend fun saveFirebaseConfig(json: String): Boolean
    suspend fun getPrinterSettings(): String?
    suspend fun savePrinterSettings(json: String): Boolean
    suspend fun getSetting(key: String, defaultValue: String): String
    suspend fun saveSetting(key: String, value: String): Boolean

    // Sync helpers
    suspend fun getUnsyncedSales(): List<Sale>
    suspend fun markSalesAsSynced(syncIds: List<String>)
    suspend fun getUnsyncedProducts(): List<Product>
    suspend fun markProductsAsSynced(syncIds: List<String>)
    suspend fun getUnsyncedCustomers(): List<Customer>
    suspend fun markCustomersAsSynced(syncIds: List<String>)
}

class DefaultDataRepository(context: Context) : DataRepository {
    private val dbHelper = DatabaseHelper(context)

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    override val products = _products.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    override val categories = _categories.asStateFlow()

    private val _customers = MutableStateFlow<List<Customer>>(emptyList())
    override val customers = _customers.asStateFlow()

    private val _heldBills = MutableStateFlow<List<HeldBill>>(emptyList())
    override val heldBills = _heldBills.asStateFlow()

    private val _sales = MutableStateFlow<List<Sale>>(emptyList())
    override val sales = _sales.asStateFlow()

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    override val expenses = _expenses.asStateFlow()

    private val _repairs = MutableStateFlow<List<RepairJob>>(emptyList())
    override val repairs = _repairs.asStateFlow()

    private val _returns = MutableStateFlow<List<ReturnBill>>(emptyList())
    override val returns = _returns.asStateFlow()

    override suspend fun refreshAll() = withContext(Dispatchers.IO) {
        loadProducts()
        loadCategories()
        loadCustomers()
        loadHeldBills()
        loadSales()
        loadExpenses()
        loadRepairs()
        loadReturns()
    }

    private fun loadProducts() {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<Product>()
        db.rawQuery("SELECT * FROM ${DatabaseHelper.TABLE_PRODUCTS}", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToProduct(cursor))
            }
        }
        _products.value = list
    }

    private fun loadCategories() {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<Category>()
        db.rawQuery("SELECT * FROM ${DatabaseHelper.TABLE_CATEGORIES} ORDER BY sortOrder ASC", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToCategory(cursor))
            }
        }
        _categories.value = list
    }

    private fun loadCustomers() {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<Customer>()
        db.rawQuery("SELECT * FROM ${DatabaseHelper.TABLE_CUSTOMERS}", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToCustomer(cursor))
            }
        }
        _customers.value = list
    }

    private fun loadHeldBills() {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<HeldBill>()
        db.rawQuery("SELECT * FROM ${DatabaseHelper.TABLE_HELD_BILLS} ORDER BY createdAt DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToHeldBill(cursor))
            }
        }
        _heldBills.value = list
    }

    private fun loadSales() {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<Sale>()
        db.rawQuery("SELECT * FROM ${DatabaseHelper.TABLE_SALES} ORDER BY createdAt DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToSale(cursor))
            }
        }
        _sales.value = list
    }

    private fun loadExpenses() {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<Expense>()
        db.rawQuery("SELECT * FROM ${DatabaseHelper.TABLE_EXPENSES} ORDER BY createdAt DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToExpense(cursor))
            }
        }
        _expenses.value = list
    }

    private fun loadRepairs() {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<RepairJob>()
        db.rawQuery("SELECT * FROM ${DatabaseHelper.TABLE_REPAIRS} ORDER BY createdAt DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToRepairJob(cursor))
            }
        }
        _repairs.value = list
    }

    private fun loadReturns() {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<ReturnBill>()
        db.rawQuery("SELECT * FROM ${DatabaseHelper.TABLE_RETURNS} ORDER BY createdAt DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToReturn(cursor))
            }
        }
        _returns.value = list
    }

    // ===== CRUD IMPLEMENTATION =====

    override suspend fun saveProduct(product: Product): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("barcode", product.barcode)
            put("name", product.name)
            put("categoryId", product.categoryId)
            put("retailPrice", product.retailPrice)
            put("wsalePrice", product.wsalePrice)
            put("costPrice", product.costPrice)
            put("stock", product.stock)
            put("description", product.description)
            put("syncId", product.syncId)
            put("synced", 0)
        }
        val result = if (product.id > 0) {
            db.update(DatabaseHelper.TABLE_PRODUCTS, values, "id = ?", arrayOf(product.id.toString())) > 0
        } else {
            db.insert(DatabaseHelper.TABLE_PRODUCTS, null, values) > -1
        }
        if (result) loadProducts()
        result
    }

    override suspend fun deleteProduct(id: Int): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val result = db.delete(DatabaseHelper.TABLE_PRODUCTS, "id = ?", arrayOf(id.toString())) > 0
        if (result) loadProducts()
        result
    }

    override suspend fun saveCategory(category: Category): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("name", category.name)
            put("icon", category.icon)
            put("color", category.color)
            put("sortOrder", category.sortOrder)
            put("syncId", category.syncId)
            put("synced", 0)
        }
        val result = if (category.id > 0) {
            db.update(DatabaseHelper.TABLE_CATEGORIES, values, "id = ?", arrayOf(category.id.toString())) > 0
        } else {
            db.insert(DatabaseHelper.TABLE_CATEGORIES, null, values) > -1
        }
        if (result) {
            loadCategories()
            loadProducts() // Category changes can affect products screen
        }
        result
    }

    override suspend fun deleteCategory(id: Int): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            // Nullify products under this category
            val pValues = ContentValues().apply { putNull("categoryId") }
            db.update(DatabaseHelper.TABLE_PRODUCTS, pValues, "categoryId = ?", arrayOf(id.toString()))
            val deleted = db.delete(DatabaseHelper.TABLE_CATEGORIES, "id = ?", arrayOf(id.toString())) > 0
            if (deleted) {
                db.setTransactionSuccessful()
            }
            db.endTransaction()
            loadCategories()
            loadProducts()
            deleted
        } catch (e: Exception) {
            db.endTransaction()
            false
        }
    }

    override suspend fun saveCustomer(customer: Customer): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("name", customer.name)
            put("phone", customer.phone)
            put("address", customer.address)
            put("totalDue", customer.totalDue)
            put("syncId", customer.syncId)
            put("synced", 0)
        }
        val result = if (customer.id > 0) {
            db.update(DatabaseHelper.TABLE_CUSTOMERS, values, "id = ?", arrayOf(customer.id.toString())) > 0
        } else {
            db.insert(DatabaseHelper.TABLE_CUSTOMERS, null, values) > -1
        }
        if (result) loadCustomers()
        result
    }

    override suspend fun deleteCustomer(id: Int): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val result = db.delete(DatabaseHelper.TABLE_CUSTOMERS, "id = ?", arrayOf(id.toString())) > 0
        if (result) loadCustomers()
        result
    }

    override suspend fun recordCustomerPayment(customerId: Int, amount: Double): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            var currentDue = 0.0
            db.rawQuery("SELECT totalDue FROM ${DatabaseHelper.TABLE_CUSTOMERS} WHERE id = ?", arrayOf(customerId.toString())).use { c ->
                if (c.moveToFirst()) currentDue = c.getDouble(0)
            }
            val newDue = (currentDue - amount).coerceAtLeast(0.0)
            val values = ContentValues().apply {
                put("totalDue", newDue)
                put("synced", 0)
            }
            val ok = db.update(DatabaseHelper.TABLE_CUSTOMERS, values, "id = ?", arrayOf(customerId.toString())) > 0
            if (ok) {
                db.setTransactionSuccessful()
            }
            db.endTransaction()
            loadCustomers()
            ok
        } catch (e: Exception) {
            db.endTransaction()
            false
        }
    }

    override suspend fun completeSale(sale: Sale): Int = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        var insertedId = -1
        try {
            val values = ContentValues().apply {
                put("billNo", sale.billNo)
                put("saleType", sale.saleType)
                put("subtotal", sale.subtotal)
                put("discount", sale.discount)
                put("total", sale.total)
                put("paymentMethod", sale.paymentMethod)
                put("paidAmount", sale.paidAmount)
                put("changeAmount", sale.changeAmount)
                put("itemsJson", serializeCart(sale.items))
                put("customerId", sale.customerId)
                put("customerName", sale.customerName)
                put("cashierName", sale.cashierName)
                put("deviceId", sale.deviceId)
                put("createdAt", sale.createdAt)
                put("syncId", sale.syncId)
                put("synced", 0)
            }
            insertedId = db.insert(DatabaseHelper.TABLE_SALES, null, values).toInt()

            // Update stock quantities
            sale.items.forEach { item ->
                db.execSQL("UPDATE ${DatabaseHelper.TABLE_PRODUCTS} SET stock = MAX(0, stock - ?) WHERE id = ? AND stock IS NOT NULL", arrayOf(item.qty, item.productId))
            }

            // If loan sale, update customer totalDue
            if (sale.paymentMethod == "loan" && sale.customerId != null) {
                db.execSQL("UPDATE ${DatabaseHelper.TABLE_CUSTOMERS} SET totalDue = totalDue + ? WHERE id = ?", arrayOf<Any>(sale.total, sale.customerId))
            }

            db.setTransactionSuccessful()
            db.endTransaction()
            loadSales()
            loadCustomers()
            loadProducts()
        } catch (e: Exception) {
            db.endTransaction()
            insertedId = -1
        }
        insertedId
    }

    override suspend fun getSalesForPeriod(fromTimestamp: Long): List<Sale> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<Sale>()
        db.rawQuery("SELECT * FROM ${DatabaseHelper.TABLE_SALES} WHERE createdAt >= ? ORDER BY createdAt DESC", arrayOf(fromTimestamp.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToSale(cursor))
            }
        }
        list
    }

    override suspend fun holdBill(heldBill: HeldBill): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("billNo", heldBill.billNo)
            put("saleType", heldBill.saleType)
            put("subtotal", heldBill.subtotal)
            put("discount", heldBill.discount)
            put("total", heldBill.total)
            put("itemsJson", serializeCart(heldBill.items))
            put("customerId", heldBill.customerId)
            put("customerName", heldBill.customerName)
            put("cashierName", heldBill.cashierName)
            put("deviceId", heldBill.deviceId)
            put("createdAt", heldBill.createdAt)
        }
        val result = db.insert(DatabaseHelper.TABLE_HELD_BILLS, null, values) > -1
        if (result) loadHeldBills()
        result
    }

    override suspend fun deleteHeldBill(id: Int): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val result = db.delete(DatabaseHelper.TABLE_HELD_BILLS, "id = ?", arrayOf(id.toString())) > 0
        if (result) loadHeldBills()
        result
    }

    // Expense Actions
    override suspend fun addExpense(expense: Expense): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("amount", expense.amount)
            put("description", expense.description)
            put("type", expense.type)
            put("createdAt", expense.createdAt)
        }
        val result = db.insert(DatabaseHelper.TABLE_EXPENSES, null, values) > -1
        if (result) loadExpenses()
        result
    }

    override suspend fun getExpensesForPeriod(fromTimestamp: Long): List<Expense> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<Expense>()
        db.rawQuery("SELECT * FROM ${DatabaseHelper.TABLE_EXPENSES} WHERE createdAt >= ? ORDER BY createdAt DESC", arrayOf(fromTimestamp.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToExpense(cursor))
            }
        }
        list
    }

    // Repair Actions
    override suspend fun saveRepairJob(job: RepairJob): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("customerName", job.customerName)
            put("customerPhone", job.customerPhone)
            put("deviceModel", job.deviceModel)
            put("issueDescription", job.issueDescription)
            put("estimatedCost", job.estimatedCost)
            put("status", job.status)
            put("createdAt", job.createdAt)
        }
        val result = if (job.id > 0) {
            db.update(DatabaseHelper.TABLE_REPAIRS, values, "id = ?", arrayOf(job.id.toString())) > 0
        } else {
            db.insert(DatabaseHelper.TABLE_REPAIRS, null, values) > -1
        }
        if (result) loadRepairs()
        result
    }

    override suspend fun updateRepairStatus(jobId: Int, status: String): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("status", status)
        }
        val result = db.update(DatabaseHelper.TABLE_REPAIRS, values, "id = ?", arrayOf(jobId.toString())) > 0
        if (result) loadRepairs()
        result
    }

    // Return Actions
    override suspend fun recordReturn(returnBill: ReturnBill): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("saleBillNo", returnBill.saleBillNo)
                put("returnedItemsJson", serializeCart(returnBill.returnedItems))
                put("refundAmount", returnBill.refundAmount)
                put("createdAt", returnBill.createdAt)
            }
            val result = db.insert(DatabaseHelper.TABLE_RETURNS, null, values) > -1
            if (result) {
                // Return stock quantities back to products
                returnBill.returnedItems.forEach { item ->
                    db.execSQL("UPDATE ${DatabaseHelper.TABLE_PRODUCTS} SET stock = stock + ? WHERE id = ? AND stock IS NOT NULL", arrayOf(item.qty, item.productId))
                }
                db.setTransactionSuccessful()
            }
            db.endTransaction()
            if (result) {
                loadReturns()
                loadProducts()
            }
            result
        } catch (e: Exception) {
            db.endTransaction()
            false
        }
    }

    // ===== SETTINGS MANAGEMENT =====

    override suspend fun getDeviceInfo(): DeviceInfo? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT value FROM ${DatabaseHelper.TABLE_SETTINGS} WHERE key = ?", arrayOf("device")).use { c ->
            if (c.moveToFirst()) {
                val json = JSONObject(c.getString(0))
                DeviceInfo(
                    id = json.getString("id"),
                    cashier = json.getString("cashier"),
                    devName = json.getString("devName"),
                    store = json.optString("store", "Janith Mobile")
                )
            } else null
        }
    }

    override suspend fun saveDeviceInfo(info: DeviceInfo): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val json = JSONObject().apply {
            put("id", info.id)
            put("cashier", info.cashier)
            put("devName", info.devName)
            put("store", info.store)
        }
        val values = ContentValues().apply {
            put("key", "device")
            put("value", json.toString())
        }
        db.replace(DatabaseHelper.TABLE_SETTINGS, null, values) > -1
    }

    override suspend fun getFirebaseConfig(): String? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT value FROM ${DatabaseHelper.TABLE_SETTINGS} WHERE key = ?", arrayOf("firebaseConfig")).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }

    override suspend fun saveFirebaseConfig(json: String): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("key", "firebaseConfig")
            put("value", json)
        }
        db.replace(DatabaseHelper.TABLE_SETTINGS, null, values) > -1
    }

    override suspend fun getPrinterSettings(): String? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT value FROM ${DatabaseHelper.TABLE_SETTINGS} WHERE key = ?", arrayOf("printerSettings")).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }

    override suspend fun savePrinterSettings(json: String): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("key", "printerSettings")
            put("value", json)
        }
        db.replace(DatabaseHelper.TABLE_SETTINGS, null, values) > -1
    }

    override suspend fun getSetting(key: String, defaultValue: String): String = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT value FROM ${DatabaseHelper.TABLE_SETTINGS} WHERE key = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) c.getString(0) else defaultValue
        }
    }

    override suspend fun saveSetting(key: String, value: String): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        db.replace(DatabaseHelper.TABLE_SETTINGS, null, values) > -1
    }

    // ===== SYNC HELPER QUERIES =====

    override suspend fun getUnsyncedSales(): List<Sale> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<Sale>()
        db.rawQuery("SELECT * FROM ${DatabaseHelper.TABLE_SALES} WHERE synced = 0", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToSale(cursor))
            }
        }
        list
    }

    override suspend fun markSalesAsSynced(syncIds: List<String>) = withContext(Dispatchers.IO) {
        if (syncIds.isEmpty()) return@withContext
        val db = dbHelper.writableDatabase
        val args = syncIds.joinToString(",") { "'$it'" }
        db.execSQL("UPDATE ${DatabaseHelper.TABLE_SALES} SET synced = 1 WHERE syncId IN ($args)")
        loadSales()
    }

    override suspend fun getUnsyncedProducts(): List<Product> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<Product>()
        db.rawQuery("SELECT * FROM ${DatabaseHelper.TABLE_PRODUCTS} WHERE synced = 0", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToProduct(cursor))
            }
        }
        list
    }

    override suspend fun markProductsAsSynced(syncIds: List<String>) = withContext(Dispatchers.IO) {
        if (syncIds.isEmpty()) return@withContext
        val db = dbHelper.writableDatabase
        val args = syncIds.joinToString(",") { "'$it'" }
        db.execSQL("UPDATE ${DatabaseHelper.TABLE_PRODUCTS} SET synced = 1 WHERE syncId IN ($args)")
        loadProducts()
    }

    override suspend fun getUnsyncedCustomers(): List<Customer> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<Customer>()
        db.rawQuery("SELECT * FROM ${DatabaseHelper.TABLE_CUSTOMERS} WHERE synced = 0", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToCustomer(cursor))
            }
        }
        list
    }

    override suspend fun markCustomersAsSynced(syncIds: List<String>) = withContext(Dispatchers.IO) {
        if (syncIds.isEmpty()) return@withContext
        val db = dbHelper.writableDatabase
        val args = syncIds.joinToString(",") { "'$it'" }
        db.execSQL("UPDATE ${DatabaseHelper.TABLE_CUSTOMERS} SET synced = 1 WHERE syncId IN ($args)")
        loadCustomers()
    }

    // ===== CURSOR TRANSLATORS =====

    private fun cursorToProduct(c: Cursor): Product {
        return Product(
            id = c.getInt(c.getColumnIndexOrThrow("id")),
            barcode = c.getString(c.getColumnIndexOrThrow("barcode")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            categoryId = if (c.isNull(c.getColumnIndexOrThrow("categoryId"))) null else c.getInt(c.getColumnIndexOrThrow("categoryId")),
            retailPrice = c.getDouble(c.getColumnIndexOrThrow("retailPrice")),
            wsalePrice = if (c.isNull(c.getColumnIndexOrThrow("wsalePrice"))) null else c.getDouble(c.getColumnIndexOrThrow("wsalePrice")),
            costPrice = if (c.isNull(c.getColumnIndexOrThrow("costPrice"))) null else c.getDouble(c.getColumnIndexOrThrow("costPrice")),
            stock = if (c.isNull(c.getColumnIndexOrThrow("stock"))) null else c.getInt(c.getColumnIndexOrThrow("stock")),
            description = c.getString(c.getColumnIndexOrThrow("description")),
            syncId = c.getString(c.getColumnIndexOrThrow("syncId")) ?: UUID.randomUUID().toString(),
            synced = c.getInt(c.getColumnIndexOrThrow("synced"))
        )
    }

    private fun cursorToCategory(c: Cursor): Category {
        return Category(
            id = c.getInt(c.getColumnIndexOrThrow("id")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            icon = c.getString(c.getColumnIndexOrThrow("icon")),
            color = c.getString(c.getColumnIndexOrThrow("color")),
            sortOrder = c.getInt(c.getColumnIndexOrThrow("sortOrder")),
            syncId = c.getString(c.getColumnIndexOrThrow("syncId")) ?: UUID.randomUUID().toString(),
            synced = c.getInt(c.getColumnIndexOrThrow("synced"))
        )
    }

    private fun cursorToCustomer(c: Cursor): Customer {
        return Customer(
            id = c.getInt(c.getColumnIndexOrThrow("id")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            phone = c.getString(c.getColumnIndexOrThrow("phone")),
            address = c.getString(c.getColumnIndexOrThrow("address")),
            totalDue = c.getDouble(c.getColumnIndexOrThrow("totalDue")),
            syncId = c.getString(c.getColumnIndexOrThrow("syncId")) ?: UUID.randomUUID().toString(),
            synced = c.getInt(c.getColumnIndexOrThrow("synced"))
        )
    }

    private fun cursorToHeldBill(c: Cursor): HeldBill {
        val itemsStr = c.getString(c.getColumnIndexOrThrow("itemsJson"))
        val items = deserializeCart(itemsStr)
        return HeldBill(
            id = c.getInt(c.getColumnIndexOrThrow("id")),
            billNo = c.getString(c.getColumnIndexOrThrow("billNo")),
            saleType = c.getString(c.getColumnIndexOrThrow("saleType")),
            subtotal = c.getDouble(c.getColumnIndexOrThrow("subtotal")),
            discount = c.getDouble(c.getColumnIndexOrThrow("discount")),
            total = c.getDouble(c.getColumnIndexOrThrow("total")),
            items = items,
            customerId = if (c.isNull(c.getColumnIndexOrThrow("customerId"))) null else c.getInt(c.getColumnIndexOrThrow("customerId")),
            customerName = c.getString(c.getColumnIndexOrThrow("customerName")),
            cashierName = c.getString(c.getColumnIndexOrThrow("cashierName")),
            deviceId = c.getString(c.getColumnIndexOrThrow("deviceId")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt"))
        )
    }

    private fun cursorToSale(c: Cursor): Sale {
        val itemsStr = c.getString(c.getColumnIndexOrThrow("itemsJson"))
        val items = deserializeCart(itemsStr)
        return Sale(
            id = c.getInt(c.getColumnIndexOrThrow("id")),
            billNo = c.getString(c.getColumnIndexOrThrow("billNo")),
            saleType = c.getString(c.getColumnIndexOrThrow("saleType")),
            subtotal = c.getDouble(c.getColumnIndexOrThrow("subtotal")),
            discount = c.getDouble(c.getColumnIndexOrThrow("discount")),
            total = c.getDouble(c.getColumnIndexOrThrow("total")),
            paymentMethod = c.getString(c.getColumnIndexOrThrow("paymentMethod")),
            paidAmount = if (c.isNull(c.getColumnIndexOrThrow("paidAmount"))) null else c.getDouble(c.getColumnIndexOrThrow("paidAmount")),
            changeAmount = if (c.isNull(c.getColumnIndexOrThrow("changeAmount"))) null else c.getDouble(c.getColumnIndexOrThrow("changeAmount")),
            items = items,
            customerId = if (c.isNull(c.getColumnIndexOrThrow("customerId"))) null else c.getInt(c.getColumnIndexOrThrow("customerId")),
            customerName = c.getString(c.getColumnIndexOrThrow("customerName")),
            cashierName = c.getString(c.getColumnIndexOrThrow("cashierName")),
            deviceId = c.getString(c.getColumnIndexOrThrow("deviceId")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt")),
            syncId = c.getString(c.getColumnIndexOrThrow("syncId")) ?: UUID.randomUUID().toString(),
            synced = c.getInt(c.getColumnIndexOrThrow("synced"))
        )
    }

    private fun cursorToExpense(c: Cursor): Expense {
        return Expense(
            id = c.getInt(c.getColumnIndexOrThrow("id")),
            amount = c.getDouble(c.getColumnIndexOrThrow("amount")),
            description = c.getString(c.getColumnIndexOrThrow("description")),
            type = c.getString(c.getColumnIndexOrThrow("type")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt"))
        )
    }

    private fun cursorToRepairJob(c: Cursor): RepairJob {
        return RepairJob(
            id = c.getInt(c.getColumnIndexOrThrow("id")),
            customerName = c.getString(c.getColumnIndexOrThrow("customerName")),
            customerPhone = c.getString(c.getColumnIndexOrThrow("customerPhone")),
            deviceModel = c.getString(c.getColumnIndexOrThrow("deviceModel")),
            issueDescription = c.getString(c.getColumnIndexOrThrow("issueDescription")),
            estimatedCost = c.getDouble(c.getColumnIndexOrThrow("estimatedCost")),
            status = c.getString(c.getColumnIndexOrThrow("status")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt"))
        )
    }

    private fun cursorToReturn(c: Cursor): ReturnBill {
        val returnedItemsJson = c.getString(c.getColumnIndexOrThrow("returnedItemsJson"))
        val returnedItems = deserializeCart(returnedItemsJson)
        return ReturnBill(
            id = c.getInt(c.getColumnIndexOrThrow("id")),
            saleBillNo = c.getString(c.getColumnIndexOrThrow("saleBillNo")),
            returnedItems = returnedItems,
            refundAmount = c.getDouble(c.getColumnIndexOrThrow("refundAmount")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt"))
        )
    }

    // ===== JSON SERIALIZATION =====

    private fun serializeCart(items: List<CartItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("productId", item.productId)
                put("name", item.name)
                put("price", item.price)
                put("costPrice", item.costPrice ?: 0.0)
                put("qty", item.qty)
                put("subtotal", item.subtotal)
                put("mode", item.mode)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun deserializeCart(jsonStr: String): List<CartItem> {
        val list = mutableListOf<CartItem>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    CartItem(
                        productId = obj.getInt("productId"),
                        name = obj.getString("name"),
                        price = obj.getDouble("price"),
                        costPrice = obj.optDouble("costPrice", 0.0),
                        qty = obj.getInt("qty"),
                        subtotal = obj.getDouble("subtotal"),
                        mode = obj.optString("mode", "retail")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
