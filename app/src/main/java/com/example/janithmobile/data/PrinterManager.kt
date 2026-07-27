package com.example.janithmobile.data

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.UUID

/**
 * PrinterManager — Universal USB + Bluetooth thermal printer driver.
 *
 * Paper width mapping (characters per line at 12cpi):
 *   58 mm → 32 chars  (standard)
 *   72 mm → 40 chars
 *   80 mm → 48 chars
 *   Custom → user-defined
 */
class PrinterManager(private val context: Context, private val repository: DataRepository) {

    private val tag = "PrinterManager"
    private val actionUsbPermission = "com.janith.mobile.USB_PERMISSION"

    // Standard Serial Port Profile UUID used by all Bluetooth printers
    private val BT_SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // ──── Connection State ────────────────────────────────────────────────────
    enum class ConnectionType { NONE, USB, BLUETOOTH }

    private val _connectionState = MutableStateFlow(ConnectionType.NONE)
    val connectionState: StateFlow<ConnectionType> = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    // USB
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var usbEndpoint: UsbEndpoint? = null
    private var usbDevice: UsbDevice? = null

    // Bluetooth
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }
    private var btSocket: BluetoothSocket? = null
    private var btOutputStream: OutputStream? = null

    // ──── Printer Settings ────────────────────────────────────────────────────
    /**
     * Paper width in mm. Supported: 58, 72, 80. Anything else is treated as custom.
     * Stored in shared settings so it persists across restarts.
     */
    var paperWidthMm: Int = 80
        private set

    /** Characters per line derived from paperWidthMm */
    val charsPerLine: Int get() = when (paperWidthMm) {
        58   -> 32
        72   -> 40
        80   -> 48
        else -> (paperWidthMm * 0.6).toInt().coerceAtLeast(24) // proportional fallback
    }

    var autoPrint = true
    var footerMessage = "Thank you! Come again!"

    // Keep old field name for backward compatibility
    @Deprecated("Use charsPerLine", ReplaceWith("charsPerLine"))
    val paperWidth: Int get() = charsPerLine

    // ──── Initialization ──────────────────────────────────────────────────────
    init {
        loadSettings()
        registerUsbPermissionReceiver()
    }

    private fun loadSettings() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.getPrinterSettings()?.let { json ->
                    val obj = JSONObject(json)
                    paperWidthMm = obj.optInt("widthMm", 80)
                    autoPrint = obj.optBoolean("auto", true)
                    footerMessage = obj.optString("footer", "Thank you! Come again!")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error loading printer settings: ${e.message}")
            }
        }
    }

    fun saveSettings(widthMm: Int, auto: Boolean, footer: String) {
        paperWidthMm = widthMm
        autoPrint = auto
        footerMessage = footer
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("widthMm", widthMm)
                    put("auto", auto)
                    put("footer", footer)
                }.toString()
                repository.savePrinterSettings(json)
            } catch (e: Exception) {
                Log.e(tag, "Error saving printer settings: ${e.message}")
            }
        }
    }

    // ──── USB ─────────────────────────────────────────────────────────────────

    private fun registerUsbPermissionReceiver() {
        val filter = IntentFilter(actionUsbPermission).also {
            it.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            it.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbPermissionReceiver, filter)
        }
    }

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                actionUsbPermission -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let { connectToUsbDevice(it) }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    device?.let { autoConnectUsbPrinter(it) }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    if (_connectionState.value == ConnectionType.USB) {
                        disconnectUsb()
                    }
                }
            }
        }
    }

    /** Called when a USB device is plugged in — requests permission then auto-connects */
    private fun autoConnectUsbPrinter(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            connectToUsbDevice(device)
        } else {
            requestUsbPermission(device)
        }
    }

    fun requestUsbPermission(device: UsbDevice) {
        val intent = Intent(actionUsbPermission)
        val pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_MUTABLE)
        usbManager.requestPermission(device, pi)
    }

    fun getUsbDeviceList(): List<UsbDevice> = usbManager.deviceList.values.toList()

    /**
     * Connect to a specific USB device.
     * Returns true if connection succeeded.
     */
    fun connectToUsbDevice(device: UsbDevice): Boolean {
        if (!usbManager.hasPermission(device)) {
            requestUsbPermission(device)
            return false
        }

        // Disconnect any existing connection first
        disconnectAll()

        try {
            var printerIface: UsbInterface? = null
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                    printerIface = intf
                    break
                }
            }
            // Fallback: use first interface (many cheap printers report class 0 or 255)
            if (printerIface == null && device.interfaceCount > 0) {
                printerIface = device.getInterface(0)
            }

            val intf = printerIface ?: return false

            var outEndpoint: UsbEndpoint? = null
            for (i in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(i)
                if (ep.direction == UsbConstants.USB_DIR_OUT) {
                    outEndpoint = ep
                    break
                }
            }

            val ep = outEndpoint ?: return false
            val conn = usbManager.openDevice(device) ?: return false

            if (conn.claimInterface(intf, true)) {
                usbDevice = device
                usbInterface = intf
                usbEndpoint = ep
                usbConnection = conn
                _connectionState.value = ConnectionType.USB
                _connectedDeviceName.value = device.productName ?: device.deviceName
                Log.d(tag, "USB printer connected: ${device.productName}")
                return true
            }
        } catch (e: Exception) {
            Log.e(tag, "USB connect error: ${e.message}", e)
        }
        return false
    }

    private fun disconnectUsb() {
        try {
            usbInterface?.let { usbConnection?.releaseInterface(it) }
            usbConnection?.close()
        } catch (_: Exception) {}
        usbConnection = null
        usbInterface = null
        usbEndpoint = null
        usbDevice = null
        if (_connectionState.value == ConnectionType.USB) {
            _connectionState.value = ConnectionType.NONE
            _connectedDeviceName.value = null
        }
    }

    // ──── Bluetooth ───────────────────────────────────────────────────────────

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    /** Returns list of already-paired Bluetooth devices */
    fun getPairedBluetoothDevices(): List<BluetoothDevice> {
        if (!hasBluetoothPermission()) return emptyList()
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(tag, "BT permission denied: ${e.message}")
            emptyList()
        }
    }

    /**
     * Connect to a Bluetooth printer.
     * Runs in background coroutine since BT socket connection can block.
     * Calls [onResult] with true/false when done.
     */
    fun connectToBluetoothDevice(device: BluetoothDevice, onResult: (Boolean) -> Unit) {
        if (!hasBluetoothPermission()) {
            onResult(false)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            disconnectAll()
            try {
                val socket = device.createRfcommSocketToServiceRecord(BT_SPP_UUID)
                bluetoothAdapter?.cancelDiscovery()
                socket.connect()
                btSocket = socket
                btOutputStream = socket.outputStream
                _connectionState.value = ConnectionType.BLUETOOTH
                try {
                    _connectedDeviceName.value = device.name ?: device.address
                } catch (_: SecurityException) {
                    _connectedDeviceName.value = device.address
                }
                Log.d(tag, "BT printer connected: ${device.address}")
                onResult(true)
            } catch (e: Exception) {
                Log.e(tag, "BT connect error: ${e.message}", e)
                btSocket = null
                btOutputStream = null
                onResult(false)
            }
        }
    }

    private fun disconnectBluetooth() {
        try {
            btOutputStream?.close()
            btSocket?.close()
        } catch (_: Exception) {}
        btSocket = null
        btOutputStream = null
        if (_connectionState.value == ConnectionType.BLUETOOTH) {
            _connectionState.value = ConnectionType.NONE
            _connectedDeviceName.value = null
        }
    }

    // ──── Shared ──────────────────────────────────────────────────────────────

    fun isConnected(): Boolean = _connectionState.value != ConnectionType.NONE

    fun disconnectAll() {
        disconnectUsb()
        disconnectBluetooth()
        _connectionState.value = ConnectionType.NONE
        _connectedDeviceName.value = null
    }

    // ──── Low-level Write ─────────────────────────────────────────────────────

    private fun writeBytes(data: ByteArray): Boolean {
        return when (_connectionState.value) {
            ConnectionType.USB -> writeUsb(data)
            ConnectionType.BLUETOOTH -> writeBluetooth(data)
            ConnectionType.NONE -> false
        }
    }

    private fun writeUsb(data: ByteArray): Boolean {
        val conn = usbConnection ?: return false
        val ep = usbEndpoint ?: return false
        return try {
            var offset = 0
            while (offset < data.size) {
                val chunk = (data.size - offset).coerceAtMost(512)
                conn.bulkTransfer(ep, data, offset, chunk, 5000)
                offset += chunk
            }
            true
        } catch (e: Exception) {
            Log.e(tag, "USB write error: ${e.message}")
            false
        }
    }

    private fun writeBluetooth(data: ByteArray): Boolean {
        return try {
            btOutputStream?.write(data)
            btOutputStream?.flush()
            true
        } catch (e: Exception) {
            Log.e(tag, "BT write error: ${e.message}")
            disconnectBluetooth()
            false
        }
    }

    // ──── ESC/POS Helpers ─────────────────────────────────────────────────────

    private fun buildEscPos(block: EscPosBuilder.() -> Unit): ByteArray {
        return EscPosBuilder(charsPerLine).apply(block).build()
    }

    // ──── Public Print Functions ──────────────────────────────────────────────

    fun printSale(sale: Sale, storeName: String): Boolean {
        if (!isConnected()) return false
        val bytes = buildEscPos {
            init()
            align(CENTER)
            doubleSize(); text(storeName.uppercase() + "\n")
            normalSize(); text("POS BILL RECEIPT\n")
            align(LEFT)
            divider('=')
            text("Bill No : ${sale.billNo}\n")
            text("Date    : ${formatDate(sale.createdAt)}\n")
            text("Cashier : ${sale.cashierName ?: "N/A"}\n")
            text("Type    : ${sale.saleType.uppercase()}\n")
            if (sale.customerName != null) text("Customer: ${sale.customerName}\n")
            divider('=')

            // Table header
            val col = charsPerLine - 13
            text("ITEM".padEnd(col) + "QTY".padStart(4) + "AMOUNT".padStart(9) + "\n")
            divider('-')
            sale.items.forEach { item ->
                val name = item.name.take(col)
                text(name.padEnd(col) + item.qty.toString().padStart(4) +
                        String.format("%.2f", item.subtotal).padStart(9) + "\n")
            }
            divider('=')

            val labelW = charsPerLine - 10
            if (sale.discount > 0) {
                text("Subtotal:".padEnd(labelW) + String.format("%10.2f", sale.subtotal) + "\n")
                text("Discount:".padEnd(labelW) + String.format("%10.2f", -sale.discount) + "\n")
            }
            bold(true)
            text("TOTAL:".padEnd(labelW) + String.format("%10.2f", sale.total) + "\n")
            bold(false)
            if (sale.paymentMethod != "loan" && (sale.paidAmount ?: 0.0) > 0.0) {
                text("Paid:".padEnd(labelW) + String.format("%10.2f", sale.paidAmount ?: 0.0) + "\n")
                val change = sale.changeAmount ?: 0.0
                if (change > 0.0) text("Change:".padEnd(labelW) + String.format("%10.2f", change) + "\n")
            } else if (sale.paymentMethod == "loan") {
                bold(true); text("** CREDIT / LOAN BILL **\n"); bold(false)
            }
            divider('=')
            align(CENTER)
            text("\n$footerMessage\n\n\n\n")
            cut()
        }
        return writeBytes(bytes)
    }

    fun printBarcode(productName: String, barcode: String): Boolean {
        if (!isConnected()) return false
        val bytes = buildEscPos {
            init()
            align(CENTER)
            doubleHeight(); text(productName.take(charsPerLine) + "\n")
            normalSize(); text("\n* $barcode *\n")
            text("Code: $barcode\n\n\n\n")
            cut()
        }
        return writeBytes(bytes)
    }

    fun printDayEndReport(sales: List<Sale>, expenses: List<Expense>, dateStr: String, storeName: String): Boolean {
        if (!isConnected()) return false
        val totalSales = sales.sumOf { it.total }
        val cashSales = sales.filter { it.paymentMethod == "cash" }.sumOf { it.total }
        val cardSales = sales.filter { it.paymentMethod == "card" }.sumOf { it.total }
        val loanSales = sales.filter { it.paymentMethod == "loan" }.sumOf { it.total }
        val totalExpenses = expenses.sumOf { it.amount }
        val netProfit = totalSales - totalExpenses

        val bytes = buildEscPos {
            init()
            align(CENTER)
            doubleSize(); text(storeName.uppercase() + "\n")
            doubleHeight(); text("DAY END REPORT\n")
            normalSize(); align(LEFT)
            divider('=')
            text("Date: $dateStr\n")
            divider('=')
            val cw = charsPerLine - 10
            text("Cash Sales:".padEnd(cw) + String.format("%10.2f", cashSales) + "\n")
            text("Card Sales:".padEnd(cw) + String.format("%10.2f", cardSales) + "\n")
            text("Loan Sales:".padEnd(cw) + String.format("%10.2f", loanSales) + "\n")
            divider('-')
            text("TOTAL SALES:".padEnd(cw) + String.format("%10.2f", totalSales) + "\n")
            text("EXPENSES:".padEnd(cw) + String.format("%10.2f", -totalExpenses) + "\n")
            divider('=')
            bold(true)
            text("NET PROFIT:".padEnd(cw) + String.format("%10.2f", netProfit) + "\n")
            bold(false)
            divider('=')
            if (expenses.isNotEmpty()) {
                text("EXPENSES BREAKDOWN:\n")
                expenses.forEach { exp ->
                    val desc = (exp.description ?: exp.type).take(cw)
                    text(desc.padEnd(cw) + String.format("%10.2f", exp.amount) + "\n")
                }
                divider('=')
            }
            align(CENTER)
            text("\nGenerated by Jayaneth POS\n\n\n\n")
            cut()
        }
        return writeBytes(bytes)
    }

    // ──── Utility ─────────────────────────────────────────────────────────────

    private fun formatDate(ts: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(ts))

    fun cleanup() {
        try { context.unregisterReceiver(usbPermissionReceiver) } catch (_: Exception) {}
        disconnectAll()
    }

    // ──── ESC/POS Builder ────────────────────────────────────────────────────

    companion object {
        const val CENTER: Byte = 0x01
        const val LEFT: Byte   = 0x00
        const val RIGHT: Byte  = 0x02
    }

    inner class EscPosBuilder(private val width: Int) {
        private val list = mutableListOf<Byte>()
        private val charset = Charset.forName("US-ASCII")

        fun text(s: String) = apply { list.addAll(s.toByteArray(charset).toList()) }
        fun cmd(vararg b: Byte) = apply { list.addAll(b.toList()) }

        fun init() = cmd(0x1B, 0x40)
        fun align(a: Byte) = cmd(0x1B, 0x61, a)
        fun bold(on: Boolean) = cmd(0x1B, 0x45, if (on) 0x01 else 0x00)
        fun normalSize() = cmd(0x1B, 0x21, 0x00)
        fun doubleHeight() = cmd(0x1B, 0x21, 0x10)
        fun doubleWidth() = cmd(0x1B, 0x21, 0x20)
        fun doubleSize() = cmd(0x1B, 0x21, 0x30)
        fun cut() = cmd(0x1D, 0x56, 0x41, 0x00)
        fun feed(lines: Int = 1) = apply { repeat(lines) { text("\n") } }

        fun divider(char: Char = '=') = apply {
            text(char.toString().repeat(width) + "\n")
        }

        fun build(): ByteArray = list.toByteArray()
    }
}
