package com.example.janithmobile.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "janith_mobile.db"
        const val DATABASE_VERSION = 3

        // Table Names
        const val TABLE_PRODUCTS = "products"
        const val TABLE_CATEGORIES = "categories"
        const val TABLE_SALES = "sales"
        const val TABLE_HELD_BILLS = "held_bills"
        const val TABLE_CUSTOMERS = "customers"
        const val TABLE_SETTINGS = "settings"
        const val TABLE_EXPENSES = "expenses"
        const val TABLE_REPAIRS = "repairs"
        const val TABLE_RETURNS = "returns"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Products
        db.execSQL("""
            CREATE TABLE $TABLE_PRODUCTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                barcode TEXT,
                name TEXT NOT NULL,
                categoryId INTEGER,
                retailPrice REAL NOT NULL,
                wsalePrice REAL,
                costPrice REAL,
                stock INTEGER,
                description TEXT,
                syncId TEXT,
                synced INTEGER DEFAULT 0
            )
        """)

        // Categories
        db.execSQL("""
            CREATE TABLE $TABLE_CATEGORIES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                icon TEXT,
                color TEXT,
                sortOrder INTEGER,
                syncId TEXT,
                synced INTEGER DEFAULT 0
            )
        """)

        // Sales
        db.execSQL("""
            CREATE TABLE $TABLE_SALES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                billNo TEXT NOT NULL,
                saleType TEXT NOT NULL,
                subtotal REAL NOT NULL,
                discount REAL NOT NULL,
                total REAL NOT NULL,
                paymentMethod TEXT NOT NULL,
                paidAmount REAL,
                changeAmount REAL,
                itemsJson TEXT NOT NULL,
                customerId INTEGER,
                customerName TEXT,
                cashierName TEXT,
                deviceId TEXT,
                createdAt INTEGER NOT NULL,
                syncId TEXT,
                synced INTEGER DEFAULT 0
            )
        """)

        // Held Bills
        db.execSQL("""
            CREATE TABLE $TABLE_HELD_BILLS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                billNo TEXT NOT NULL,
                saleType TEXT NOT NULL,
                subtotal REAL NOT NULL,
                discount REAL NOT NULL,
                total REAL NOT NULL,
                itemsJson TEXT NOT NULL,
                customerId INTEGER,
                customerName TEXT,
                cashierName TEXT,
                deviceId TEXT,
                createdAt INTEGER NOT NULL
            )
        """)

        // Customers
        db.execSQL("""
            CREATE TABLE $TABLE_CUSTOMERS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT,
                address TEXT,
                totalDue REAL DEFAULT 0.0,
                syncId TEXT,
                synced INTEGER DEFAULT 0
            )
        """)

         // Settings
        db.execSQL("""
            CREATE TABLE $TABLE_SETTINGS (
                key TEXT PRIMARY KEY,
                value TEXT
            )
        """)

        // Expenses
        db.execSQL("""
            CREATE TABLE $TABLE_EXPENSES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                amount REAL NOT NULL,
                description TEXT,
                type TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """)

        // Repairs
        db.execSQL("""
            CREATE TABLE $TABLE_REPAIRS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                customerName TEXT NOT NULL,
                customerPhone TEXT,
                deviceModel TEXT NOT NULL,
                issueDescription TEXT NOT NULL,
                estimatedCost REAL NOT NULL,
                status TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """)

        // Returns
        db.execSQL("""
            CREATE TABLE $TABLE_RETURNS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                saleBillNo TEXT NOT NULL,
                returnedItemsJson TEXT NOT NULL,
                refundAmount REAL NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        try {
            db.execSQL("ALTER TABLE $TABLE_PRODUCTS ADD COLUMN costPrice REAL")
        } catch (_: Exception) {
            // Column already exists
        }
        // Remove dummy default categories
        try {
            db.execSQL("DELETE FROM $TABLE_CATEGORIES WHERE name IN ('General', 'Beverages', 'Food', 'Electronics')")
        } catch (_: Exception) {}
    }

    private fun insertDefaultCategories(db: SQLiteDatabase) {
        // No default dummy categories inserted as requested by user
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PRODUCTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CATEGORIES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SALES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HELD_BILLS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CUSTOMERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SETTINGS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EXPENSES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_REPAIRS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_RETURNS")
        onCreate(db)
    }
}
