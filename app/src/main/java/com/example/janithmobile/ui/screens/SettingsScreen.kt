package com.example.janithmobile.ui.screens

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.hardware.usb.UsbDevice
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.janithmobile.data.DeviceInfo
import com.example.janithmobile.data.PrinterManager
import com.example.janithmobile.ui.pos.PosViewModel
import com.example.janithmobile.theme.*
import org.json.JSONObject
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PosViewModel,
    onNavigateBack: () -> Unit,
    onNavigateTo: (Any) -> Unit
) {
    val context = LocalContext.current
    val currentDevice = viewModel.currentDeviceInfo.value
    val coroutineScope = rememberCoroutineScope()

    var showDeviceDialog by remember { mutableStateOf(false) }
    var showFirebaseDialog by remember { mutableStateOf(false) }
    var showStockDialog by remember { mutableStateOf(false) }
    var showPrinterDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚙️ App Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberBg)
            )
        },
        containerColor = CyberBg
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Navigation Links
            item {
                Text(
                    "QUICK NAVIGATE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateText,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column {
                        SettingsRow(icon = "🏷️", title = "Categories", subtitle = "Configure product category tags") {
                            onNavigateTo(com.example.janithmobile.Categories)
                        }
                        Divider(color = CardBorder)
                        SettingsRow(icon = "👥", title = "Customers", subtitle = "Register customers & loan debts") {
                            onNavigateTo(com.example.janithmobile.Customers)
                        }
                        Divider(color = CardBorder)
                        SettingsRow(icon = "📈", title = "Reports", subtitle = "View revenues & analytics statements") {
                            onNavigateTo(com.example.janithmobile.Reports)
                        }
                    }
                }
            }

            // Device Info
            item {
                Text(
                    "DEVICE PROFILE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateText,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    SettingsRow(
                        icon = "📱",
                        title = "Device Info",
                        subtitle = "${currentDevice?.cashier ?: "Cashier"} · ${currentDevice?.devName ?: "Counter"}"
                    ) {
                        showDeviceDialog = true
                    }
                }
            }

            // Firebase Config
            item {
                Text(
                    "CLOUD DATABASE SYNC",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateText,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column {
                        SettingsRow(
                            icon = "☁️",
                            title = "Firebase Credentials",
                            subtitle = if (viewModel.syncManager.isReady()) "✓ Connected to Cloud Firestore" else "Not Connected"
                        ) {
                            showFirebaseDialog = true
                        }
                        Divider(color = CardBorder)
                        SettingsRow(
                            icon = "⟳",
                            title = "Sync Local Database",
                            subtitle = "Force push offline products & sales to Firestore"
                        ) {
                            if (viewModel.syncManager.isReady()) {
                                coroutineScope.launch {
                                    viewModel.syncManager.syncNow()
                                    Toast.makeText(context, "Data synched successfully! ☁️", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Firebase not configured", Toast.LENGTH_SHORT).show()
                            }
                        }
                        Divider(color = CardBorder)
                        SettingsRow(
                            icon = "🔔",
                            title = "Low Stock Alert Threshold",
                            subtitle = "Alert when stock is below ${viewModel.lowStockThreshold.value} units"
                        ) {
                            showStockDialog = true
                        }
                    }
                }
            }

            // Thermal Printer section
            item {
                val connState by viewModel.printerManager.connectionState.collectAsStateWithLifecycle()
                val connName by viewModel.printerManager.connectedDeviceName.collectAsStateWithLifecycle()

                val connSubtitle = when (connState) {
                    PrinterManager.ConnectionType.USB -> "✅ USB: ${connName ?: "Printer"}"
                    PrinterManager.ConnectionType.BLUETOOTH -> "✅ BT: ${connName ?: "Printer"}"
                    PrinterManager.ConnectionType.NONE -> "No printer connected"
                }

                Text(
                    "RECEIPT THERMAL PRINTER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateText,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                    border = BorderStroke(1.dp, if (connState != PrinterManager.ConnectionType.NONE) NeonCyan else CardBorder)
                ) {
                    Column {
                        SettingsRow(
                            icon = if (connState != PrinterManager.ConnectionType.NONE) "🟢" else "🖨️",
                            title = "Printer Connection",
                            subtitle = connSubtitle
                        ) {
                            showPrinterDialog = true
                        }
                        Divider(color = CardBorder)
                        SettingsRow(
                            icon = "🧾",
                            title = "Test Print Receipt",
                            subtitle = "Print sample bill · Paper: ${viewModel.printerManager.paperWidthMm}mm · ${viewModel.printerManager.charsPerLine} chars/line"
                        ) {
                            if (viewModel.printerManager.isConnected()) {
                                val sampleSale = com.example.janithmobile.data.Sale(
                                    billNo = "TEST-01",
                                    saleType = "retail",
                                    subtotal = 1400.0,
                                    discount = 100.0,
                                    total = 1300.0,
                                    paymentMethod = "cash",
                                    paidAmount = 1500.0,
                                    changeAmount = 200.0,
                                    items = listOf(
                                        com.example.janithmobile.data.CartItem(productId = 1, name = "Sample Item A", price = 500.0, costPrice = 300.0, qty = 2, subtotal = 1000.0, mode = "retail"),
                                        com.example.janithmobile.data.CartItem(productId = 2, name = "Sample Item B", price = 400.0, costPrice = 250.0, qty = 1, subtotal = 400.0, mode = "retail")
                                    ),
                                    customerId = null,
                                    customerName = null,
                                    cashierName = currentDevice?.cashier,
                                    deviceId = currentDevice?.id,
                                    createdAt = System.currentTimeMillis()
                                )
                                val store = currentDevice?.store ?: "Jayaneth Mobile"
                                viewModel.printerManager.printSale(sampleSale, store)
                                Toast.makeText(context, "Printing test receipt...", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Connect a printer first (USB or Bluetooth)", Toast.LENGTH_SHORT).show()
                            }
                        }
                        if (connState != PrinterManager.ConnectionType.NONE) {
                            Divider(color = CardBorder)
                            SettingsRow(
                                icon = "❌",
                                title = "Disconnect Printer",
                                subtitle = "Disconnect current ${if (connState == PrinterManager.ConnectionType.USB) "USB" else "Bluetooth"} printer"
                            ) {
                                viewModel.printerManager.disconnectAll()
                                Toast.makeText(context, "Printer disconnected", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }

            item {
                var tapCount by remember { mutableStateOf(0) }
                var lastTapTime by remember { mutableStateOf(0L) }
                var showSecretDialog by remember { mutableStateOf(false) }
                var pinInput by remember { mutableStateOf("") }
                var isPinVerified by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp, bottom = 16.dp)
                        .clickable {
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime < 800) {
                                tapCount++
                            } else {
                                tapCount = 1
                            }
                            lastTapTime = now
                            if (tapCount >= 5) {
                                tapCount = 0
                                showSecretDialog = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Jayaneth POS v1.0 · Build 2026",
                        fontSize = 11.sp,
                        color = SlateText.copy(alpha = 0.5f)
                    )
                }

                if (showSecretDialog) {
                    AlertDialog(
                        onDismissRequest = { showSecretDialog = false; isPinVerified = false; pinInput = "" },
                        title = { Text("Master License Control", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (!isPinVerified) {
                                    Text("Enter Master Owner PIN to manage license:", fontSize = 13.sp, color = SlateText)
                                    OutlinedTextField(
                                        value = pinInput,
                                        onValueChange = { pinInput = it },
                                        label = { Text("Master Owner PIN") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Text("Device ID: ${currentDevice?.id ?: "UNKNOWN"}", fontSize = 12.sp, color = Color.White)
                                    Text("Current Status: ${if (viewModel.isLicenseBlocked.value) "BLOCKED" else "ACTIVE"}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (viewModel.isLicenseBlocked.value) RedDanger else NeonCyan)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        Button(
                                            onClick = {
                                                viewModel.setLicenseStatus("BLOCKED")
                                                Toast.makeText(context, "🚨 App BLOCKED Successfully!", Toast.LENGTH_SHORT).show()
                                                showSecretDialog = false
                                                isPinVerified = false
                                                pinInput = ""
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = RedDanger),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("🔒 Block App", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                        Button(
                                            onClick = {
                                                viewModel.setLicenseStatus("ACTIVE")
                                                Toast.makeText(context, "🟢 App ACTIVATED Successfully!", Toast.LENGTH_SHORT).show()
                                                showSecretDialog = false
                                                isPinVerified = false
                                                pinInput = ""
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("🔓 Activate App", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            if (!isPinVerified) {
                                Button(
                                    onClick = {
                                        if (pinInput == "9999") {
                                            isPinVerified = true
                                        } else {
                                            Toast.makeText(context, "❌ Invalid Master PIN", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                                ) {
                                    Text("Verify PIN", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSecretDialog = false; isPinVerified = false; pinInput = "" }) {
                                Text("Cancel")
                            }
                        },
                        containerColor = CyberBgSecondary
                    )
                }
            }
        }
    }

    // ===== DEVICE CONFIG DIALOG =====
    if (showDeviceDialog) {
        var storeName by remember { mutableStateOf(currentDevice?.store ?: "Janith Mobile") }
        var cashier by remember { mutableStateOf(currentDevice?.cashier ?: "") }
        var counter by remember { mutableStateOf(currentDevice?.devName ?: "") }

        AlertDialog(
            onDismissRequest = { showDeviceDialog = false },
            title = { Text("Update Device Info", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = storeName,
                        onValueChange = { storeName = it },
                        label = { Text("Store Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = cashier,
                        onValueChange = { cashier = it },
                        label = { Text("Cashier Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = counter,
                        onValueChange = { counter = it },
                        label = { Text("Device Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (cashier.trim().isNotEmpty() && counter.trim().isNotEmpty()) {
                            val newInfo = DeviceInfo(
                                id = currentDevice?.id ?: java.util.UUID.randomUUID().toString().substring(0, 8),
                                cashier = cashier.trim(),
                                devName = counter.trim(),
                                store = storeName.trim()
                            )
                            viewModel.updateDeviceInfo(newInfo)
                            showDeviceDialog = false
                            Toast.makeText(context, "Saved Device Profile", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeviceDialog = false }) { Text("Cancel") }
            },
            containerColor = CyberBgSecondary
        )
    }

    // ===== FIREBASE CONFIG DIALOG =====
    if (showFirebaseDialog) {
        var apiKey by remember { mutableStateOf("") }
        var projectId by remember { mutableStateOf("") }
        var appId by remember { mutableStateOf("") }
        var storeId by remember { mutableStateOf("janith-store-1") }

        // Load existing
        LaunchedEffect(Unit) {
            val configStr = viewModel.getFirebaseConfig()
            if (configStr != null) {
                try {
                    val json = JSONObject(configStr)
                    apiKey = json.optString("apiKey")
                    projectId = json.optString("projectId")
                    appId = json.optString("appId")
                    storeId = json.optString("storeId", "janith-store-1")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showFirebaseDialog = false },
            title = { Text("Firebase Credentials", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Connect to your remote Firestore database:", fontSize = 12.sp, color = SlateText)
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = projectId,
                        onValueChange = { projectId = it },
                        label = { Text("Project ID") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = appId,
                        onValueChange = { appId = it },
                        label = { Text("App ID (Web Application)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = storeId,
                        onValueChange = { storeId = it },
                        label = { Text("Store Identifier ID") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (apiKey.trim().isEmpty() || projectId.trim().isEmpty()) {
                            Toast.makeText(context, "API Key and Project ID required", Toast.LENGTH_SHORT).show()
                        } else {
                            val json = JSONObject().apply {
                                put("apiKey", apiKey.trim())
                                put("projectId", projectId.trim())
                                put("appId", appId.trim())
                                put("storeId", storeId.trim())
                            }
                            viewModel.saveFirebaseConfig(json.toString())
                            showFirebaseDialog = false
                            Toast.makeText(context, "Firebase configuration saved!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Connect", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFirebaseDialog = false }) { Text("Cancel") }
            },
            containerColor = CyberBgSecondary
        )
    }

    // ===== PRINTER CONFIG DIALOG =====
    if (showPrinterDialog) {
        var selectedTab by remember { mutableStateOf(0) } // 0=USB 1=Bluetooth
        var selectedWidthMm by remember { mutableStateOf(viewModel.printerManager.paperWidthMm) }
        var customWidthMm by remember { mutableStateOf("") }
        var footerText by remember { mutableStateOf(viewModel.printerManager.footerMessage) }
        var btConnecting by remember { mutableStateOf(false) }

        val usbDevices = viewModel.printerManager.getUsbDeviceList()
        val btDevices = remember { viewModel.printerManager.getPairedBluetoothDevices() }

        // BT permission launcher
        val btPermLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* Retry after grant */ }

        AlertDialog(
            onDismissRequest = { showPrinterDialog = false },
            title = { Text("🖨 Printer Setup", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── Connection Type Tab ──
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("USB", "Bluetooth").forEachIndexed { i, label ->
                                Button(
                                    onClick = { selectedTab = i },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedTab == i) NeonCyan else CyberBgTertiary
                                    )
                                ) {
                                    Text(label, color = if (selectedTab == i) Color.Black else Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // ── USB Device List ──
                    if (selectedTab == 0) {
                        item {
                            Text("USB Devices (via OTG)", fontSize = 11.sp, color = SlateText, fontWeight = FontWeight.Bold)
                        }
                        if (usbDevices.isEmpty()) {
                            item {
                                Text(
                                    "⚠ No USB device detected. Connect printer via OTG cable.",
                                    color = OrangeWarning, fontSize = 12.sp
                                )
                            }
                        } else {
                            items(usbDevices) { dev ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CyberBgTertiary, RoundedCornerShape(8.dp))
                                        .clickable {
                                            val ok = viewModel.printerManager.connectToUsbDevice(dev)
                                            if (ok) {
                                                Toast.makeText(context, "✅ USB Printer connected!", Toast.LENGTH_SHORT).show()
                                                showPrinterDialog = false
                                            } else {
                                                Toast.makeText(context, "Permission requested — try again after allowing", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(dev.productName ?: "USB Device", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("VendorID: ${dev.vendorId} · Class: ${dev.getInterface(0).interfaceClass}", fontSize = 10.sp, color = SlateText)
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = NeonCyan)
                                }
                            }
                        }
                    }

                    // ── Bluetooth Device List ──
                    if (selectedTab == 1) {
                        item {
                            Text("Paired Bluetooth Devices", fontSize = 11.sp, color = SlateText, fontWeight = FontWeight.Bold)
                        }
                        if (!viewModel.printerManager.hasBluetoothPermission()) {
                            item {
                                Button(
                                    onClick = {
                                        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                                        } else {
                                            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
                                        }
                                        btPermLauncher.launch(perms)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = OrangeWarning),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Grant Bluetooth Permission", color = Color.Black)
                                }
                            }
                        } else if (btDevices.isEmpty()) {
                            item {
                                Text(
                                    "No paired BT devices found. Pair your printer in Android Settings → Bluetooth first.",
                                    color = OrangeWarning, fontSize = 12.sp
                                )
                            }
                        } else {
                            items(btDevices) { dev ->
                                val devName = try { dev.name } catch (_: SecurityException) { dev.address }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CyberBgTertiary, RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (!btConnecting) {
                                                btConnecting = true
                                                viewModel.printerManager.connectToBluetoothDevice(dev) { ok ->
                                                    btConnecting = false
                                                    if (ok) {
                                                        Toast.makeText(context, "✅ BT Printer connected!", Toast.LENGTH_SHORT).show()
                                                        showPrinterDialog = false
                                                    } else {
                                                        Toast.makeText(context, "Connection failed — ensure printer is on & paired", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        }
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(devName ?: dev.address, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(dev.address, fontSize = 10.sp, color = SlateText)
                                    }
                                    if (btConnecting) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = NeonCyan, strokeWidth = 2.dp)
                                    else Icon(Icons.Default.ChevronRight, contentDescription = null, tint = NeonCyan)
                                }
                            }
                        }
                    }

                    // ── Paper Size Selector ──
                    item {
                        HorizontalDivider(color = CardBorder)
                        Spacer(Modifier.height(4.dp))
                        Text("Paper Width", fontSize = 11.sp, color = SlateText, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        var isCustomMode by remember { mutableStateOf(selectedWidthMm !in listOf(58, 72, 80)) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(58, 72, 80).forEach { mm ->
                                FilterChip(
                                    selected = !isCustomMode && selectedWidthMm == mm,
                                    onClick = {
                                        isCustomMode = false
                                        selectedWidthMm = mm
                                    },
                                    label = { Text("${mm}mm", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonCyan,
                                        selectedLabelColor = Color.Black,
                                        containerColor = CyberBgSecondary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = !isCustomMode && selectedWidthMm == mm,
                                        borderColor = CardBorder,
                                        selectedBorderColor = NeonCyan
                                    )
                                )
                            }
                            FilterChip(
                                selected = isCustomMode,
                                onClick = { isCustomMode = true },
                                label = { Text("✏ Custom", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan,
                                    selectedLabelColor = Color.Black,
                                    containerColor = CyberBgSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isCustomMode,
                                    borderColor = CardBorder,
                                    selectedBorderColor = NeonCyan
                                )
                            )
                        }

                        if (isCustomMode) {
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = customWidthMm,
                                onValueChange = { input ->
                                    customWidthMm = input
                                    input.toIntOrNull()?.let { v -> selectedWidthMm = v }
                                },
                                label = { Text("Enter Manual Paper Size in mm (e.g. 57, 100)") },
                                placeholder = { Text("e.g. 57") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                                )
                            )
                        }

                        val charsPreview = when (selectedWidthMm) {
                            58 -> 32
                            72 -> 40
                            80 -> 48
                            else -> (selectedWidthMm * 0.6).toInt().coerceAtLeast(24)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("→ ${selectedWidthMm}mm paper width (${charsPreview} chars per line)", fontSize = 11.sp, color = NeonCyan)
                    }

                    // ── Footer Text ──
                    item {
                        OutlinedTextField(
                            value = footerText,
                            onValueChange = { footerText = it },
                            label = { Text("Footer Message") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val wMm = if (selectedWidthMm !in listOf(58, 72, 80)) {
                            customWidthMm.toIntOrNull() ?: 80
                        } else selectedWidthMm
                        viewModel.printerManager.saveSettings(wMm, true, footerText)
                        Toast.makeText(context, "Printer settings saved! (${wMm}mm)", Toast.LENGTH_SHORT).show()
                        showPrinterDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Save Settings", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrinterDialog = false }) { Text("Close") }
            },
            containerColor = CyberBgSecondary
        )
    }

    // ===== LOW STOCK ALERT THRESHOLD DIALOG =====
    if (showStockDialog) {
        var limitInput by remember { mutableStateOf(viewModel.lowStockThreshold.value.toString()) }
        AlertDialog(
            onDismissRequest = { showStockDialog = false },
            title = { Text("Low Stock Threshold Limit", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Set threshold limit for low stock alerts in POS grid:", fontSize = 12.sp, color = SlateText)
                    OutlinedTextField(
                        value = limitInput,
                        onValueChange = { limitInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Limit (e.g., 5)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val limit = limitInput.toIntOrNull()
                        if (limit != null && limit >= 0) {
                            viewModel.updateLowStockThreshold(limit)
                            showStockDialog = false
                            Toast.makeText(context, "Stock threshold updated!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Enter a valid positive number", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStockDialog = false }) { Text("Cancel") }
            },
            containerColor = CyberBgSecondary
        )
    }
}

@Composable
fun SettingsRow(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(CyberBgTertiary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            Text(subtitle, fontSize = 11.sp, color = SlateText)
        }

        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = DimText)
    }
}
