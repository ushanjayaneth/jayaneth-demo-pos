package com.example.janithmobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.example.janithmobile.data.*
import com.example.janithmobile.*
import com.example.janithmobile.ui.pos.PosViewModel
import com.example.janithmobile.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    viewModel: PosViewModel,
    onNavigate: (Any) -> Unit,
    onStartScan: () -> Unit
) {
    val context = LocalContext.current
    val products by viewModel.filteredProducts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()

    var showCartSheet by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var showSaleCompleteDialog by remember { mutableStateOf<Sale?>(null) }
    var showWhatsAppDialog by remember { mutableStateOf<Sale?>(null) }
    var whatsAppPhoneNumber by remember { mutableStateOf("") }
    var showAddCategoryDialog by remember { mutableStateOf(false) }

    val cameraScannerLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val scannedCode = result.contents.trim()
            val matchedProduct = products.find { prod -> prod.barcode == scannedCode || prod.name.equals(scannedCode, ignoreCase = true) }
            if (matchedProduct != null) {
                viewModel.addToCart(matchedProduct)
                Toast.makeText(context, "✅ Added ${matchedProduct.name} to cart!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "⚠️ No product found with barcode: $scannedCode", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun launchBackCameraScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            setPrompt("Scan Product Barcode or QR Code")
            setCameraId(0) // Back camera
            setBeepEnabled(true)
            setBarcodeImageEnabled(false)
            setOrientationLocked(false)
        }
        cameraScannerLauncher.launch(options)
    }

    // Temporary cash payment state
    var cashAmountInput by remember { mutableStateOf("") }
    var payMethod by remember { mutableStateOf("cash") }

    val currentMode = viewModel.selectedSaleMode.value
    val device = viewModel.currentDeviceInfo.value
    val cartCount = cartItems.sumOf { it.qty }
    val cartTotal = viewModel.getCartTotal()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = CyberBgSecondary,
                drawerContentColor = Color.White
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Jayaneth Demo POS",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonCyan,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Divider(color = CardBorder, modifier = Modifier.padding(vertical = 8.dp))
                
                val items = listOf(
                    Triple("🏪 POS (Sales)", Main, Icons.Default.ShoppingCart),
                    Triple("📦 Products (Inventory)", Products, Icons.Default.List),
                    Triple("🏷️ Categories", Categories, Icons.Default.Star),
                    Triple("📜 Sales History", History, Icons.Default.DateRange),
                    Triple("👥 Customers & Loans", Customers, Icons.Default.Person),
                    Triple("🏷️ Barcodes", Barcodes, Icons.Default.Search),
                    Triple("💰 Day End & Expenses", DayEnd, Icons.Default.AccountBalanceWallet),
                    Triple("🔧 Repairs", Repairs, Icons.Default.Build),
                    Triple("🔄 Returns", Returns, Icons.Default.Refresh),
                    Triple("📊 Reports & Stats", Reports, Icons.Default.Info),
                    Triple("⚙️ Settings", Settings, Icons.Default.Settings)
                )
                
                items.forEach { (label, key, icon) ->
                    NavigationDrawerItem(
                        icon = { Icon(icon, contentDescription = null, tint = NeonCyan) },
                        label = { Text(label, fontWeight = FontWeight.Bold, color = Color.White) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            if (key != Main) {
                                onNavigate(key)
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = device?.store ?: "Jayaneth Mobile",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "${device?.cashier ?: "Cashier"} · ${device?.devName ?: "Counter"}",
                                fontSize = 11.sp,
                                color = SlateText
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Drawer", tint = NeonCyan)
                        }
                    },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (viewModel.isFirebaseOnline.value) GreenSuccess else RedDanger,
                                modifier = Modifier.size(8.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (viewModel.isFirebaseOnline.value) "Online" else "Offline",
                                fontSize = 12.sp,
                                color = SlateText
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberBg)
                )
            },
        bottomBar = {
            // Mini Cart Indicator / Checkout Bar at bottom of screen
            if (cartCount > 0) {
                Surface(
                    color = NeonCyan.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
                    onClick = { showCartSheet = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(NeonCyan, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cartCount.toString(),
                                    color = Color.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "items in cart",
                                fontSize = 13.sp,
                                color = SlateText
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Rs " + String.format("%.2f", cartTotal),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Expand",
                                tint = NeonCyan
                            )
                        }
                    }
                }
            }
        },
        containerColor = CyberBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Mode Selectors
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .background(CyberBgSecondary, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val modes = listOf(
                    "retail" to "🏪 Retail",
                    "wsale" to "📦 Wholesale",
                    "retail-loan" to "💳 Retail Loan",
                    "wsale-loan" to "💳 Wholesale Loan"
                )
                modes.forEach { (modeKey, label) ->
                    val selected = currentMode == modeKey
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (selected) {
                                    when (modeKey) {
                                        "wsale" -> NeonPurple.copy(alpha = 0.2f)
                                        "retail-loan" -> OrangeWarning.copy(alpha = 0.2f)
                                        "wsale-loan" -> PinkVariant.copy(alpha = 0.2f)
                                        else -> NeonCyan.copy(alpha = 0.2f)
                                    }
                                } else Color.Transparent
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) {
                                    when (modeKey) {
                                        "wsale" -> NeonPurple
                                        "retail-loan" -> OrangeWarning
                                        "wsale-loan" -> PinkVariant
                                        else -> NeonCyan
                                    }
                                } else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                viewModel.selectedSaleMode.value = modeKey
                                viewModel.clearCart() // Reset cart to align with price changes
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) {
                                when (modeKey) {
                                    "wsale" -> NeonPurple
                                    "retail-loan" -> OrangeWarning
                                    "wsale-loan" -> PinkVariant
                                    else -> NeonCyan
                                }
                            } else SlateText
                        )
                    }
                }
            }

            // Search Bar & Scanner Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = viewModel.searchQuery.value,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = { Text("Search products...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = SlateText) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CyberBgSecondary,
                        unfocusedContainerColor = CyberBgSecondary,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(100.dp)
                )

                IconButton(
                    onClick = { launchBackCameraScanner() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(CyberBgSecondary, CircleShape)
                        .border(1.dp, CardBorder, CircleShape)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode", tint = NeonCyan)
                }

                IconButton(
                    onClick = { showCartSheet = true },
                    modifier = Modifier
                        .size(48.dp)
                        .background(CyberBgSecondary, CircleShape)
                        .border(1.dp, CardBorder, CircleShape)
                ) {
                    BadgedBox(
                        badge = {
                            if (cartCount > 0) {
                                Badge(containerColor = RedDanger) {
                                    Text(cartCount.toString(), color = Color.White)
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = "Cart", tint = NeonCyan)
                    }
                }
            }

            // Category Chips Bar
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    val selected = viewModel.selectedCategoryId.value == null
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (selected) NeonCyan.copy(alpha = 0.15f) else CyberBgSecondary)
                            .border(
                                1.dp,
                                if (selected) NeonCyan else CardBorder,
                                RoundedCornerShape(100.dp)
                            )
                            .clickable { viewModel.selectedCategoryId.value = null }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "All",
                            color = if (selected) NeonCyan else SlateText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                items(categories) { cat ->
                    val selected = viewModel.selectedCategoryId.value == cat.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (selected) NeonCyan.copy(alpha = 0.15f) else CyberBgSecondary)
                            .border(
                                1.dp,
                                if (selected) NeonCyan else CardBorder,
                                RoundedCornerShape(100.dp)
                            )
                            .clickable { viewModel.selectedCategoryId.value = cat.id }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(cat.icon ?: "📦", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = cat.name,
                                color = if (selected) NeonCyan else SlateText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Product Grid
            if (products.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Empty",
                            tint = DimText,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No products found",
                            color = SlateText,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(products) { prod ->
                        val price = if (currentMode.startsWith("wsale")) {
                            prod.wsalePrice ?: prod.retailPrice
                        } else prod.retailPrice

                        val isLowStock = prod.stock != null && prod.stock <= viewModel.lowStockThreshold.value
                        val cat = categories.find { it.id == prod.categoryId }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                            border = BorderStroke(1.dp, CardBorder),
                            onClick = { viewModel.addToCart(prod) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = prod.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.height(36.dp)
                                )

                                if (cat != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(android.graphics.Color.parseColor(cat.color ?: "#00d4ff")),
                                            modifier = Modifier.size(6.dp)
                                        ) {}
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(cat.name, fontSize = 10.sp, color = SlateText)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val isWholesaleMode = currentMode.startsWith("wsale")
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Rs " + String.format("%.2f", price),
                                            color = when (currentMode) {
                                                "wsale" -> NeonPurple
                                                "retail-loan" -> OrangeWarning
                                                "wsale-loan" -> PinkVariant
                                                else -> NeonCyan
                                            },
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        if (isWholesaleMode) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(
                                                color = NeonPurple.copy(alpha = 0.25f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    "Wholesale",
                                                    color = NeonPurple,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (!isWholesaleMode && prod.wsalePrice != null && prod.wsalePrice > 0.0) {
                                        Text("WS: Rs " + String.format("%.2f", prod.wsalePrice), fontSize = 9.sp, color = NeonPurple.copy(alpha = 0.8f))
                                    }
                                }

                                if (prod.stock != null) {
                                    Text(
                                        text = "${if (isLowStock) "⚠️ " else ""}Stock: ${prod.stock}",
                                        color = if (isLowStock) OrangeWarning else SlateText,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ===== CART SHEET =====
    if (showCartSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCartSheet = false },
            containerColor = CyberBgSecondary,
            dragHandle = { BottomSheetDefaults.DragHandle(color = CardBorder) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🛒 Cart",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        // Mode badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when (currentMode) {
                                "wsale" -> NeonPurple.copy(alpha = 0.15f)
                                "retail-loan" -> OrangeWarning.copy(alpha = 0.15f)
                                "wsale-loan" -> PinkVariant.copy(alpha = 0.15f)
                                else -> NeonCyan.copy(alpha = 0.15f)
                            },
                            border = BorderStroke(
                                1.dp,
                                when (currentMode) {
                                    "wsale" -> NeonPurple
                                    "retail-loan" -> OrangeWarning
                                    "wsale-loan" -> PinkVariant
                                    else -> NeonCyan
                                }
                            )
                        ) {
                            Text(
                                text = currentMode.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (currentMode) {
                                    "wsale" -> NeonPurple
                                    "retail-loan" -> OrangeWarning
                                    "wsale-loan" -> PinkVariant
                                    else -> NeonCyan
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            viewModel.clearCart()
                            showCartSheet = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = RedDanger)
                    ) {
                        Text("Clear All")
                    }
                }

                Divider(color = CardBorder, modifier = Modifier.padding(vertical = 4.dp))

                // Loan Customer Picker Option (if loan mode)
                if (currentMode.contains("loan")) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Loan Customer:", fontSize = 12.sp, color = SlateText)
                        Surface(
                            color = CyberBgTertiary,
                            border = BorderStroke(1.dp, CardBorder),
                            shape = RoundedCornerShape(6.dp),
                            onClick = { showCustomerPicker = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = viewModel.selectedCustomer.value?.name ?: "Select Customer",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (viewModel.selectedCustomer.value != null) OrangeWarning else Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = SlateText)
                            }
                        }
                    }
                    Divider(color = CardBorder, modifier = Modifier.padding(vertical = 4.dp))
                }

                // Cart items list
                if (cartItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cart is empty", color = SlateText)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(cartItems) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        "Rs " + String.format("%.2f", item.price) + " each",
                                        fontSize = 11.sp,
                                        color = SlateText
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(CyberBgTertiary, CircleShape)
                                            .border(1.dp, CardBorder, CircleShape)
                                            .clickable { viewModel.updateCartQty(item.productId, -1) }
                                    ) {
                                        Text("−", color = Color.White, fontWeight = FontWeight.Bold)
                                    }

                                    Text(
                                        item.qty.toString(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(CyberBgTertiary, CircleShape)
                                            .border(1.dp, CardBorder, CircleShape)
                                            .clickable { viewModel.updateCartQty(item.productId, 1) }
                                    ) {
                                        Text("+", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Text(
                                    "Rs " + String.format("%.2f", item.subtotal),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NeonCyan,
                                    modifier = Modifier
                                        .width(90.dp)
                                        .padding(start = 12.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }

                Divider(color = CardBorder, modifier = Modifier.padding(vertical = 4.dp))

                // Discount setup
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Discount:", fontSize = 12.sp, color = SlateText)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = if (viewModel.discountValue.value == 0.0) "" else viewModel.discountValue.value.toString(),
                            onValueChange = {
                                viewModel.discountValue.value = it.toDoubleOrNull() ?: 0.0
                            },
                            placeholder = { Text("0", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CyberBgTertiary,
                                unfocusedContainerColor = CyberBgTertiary,
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .width(80.dp)
                                .height(46.dp)
                        )

                        val discountModes = listOf("a" to "Rs", "p" to "%")
                        discountModes.forEach { (key, label) ->
                            val selected = viewModel.discountType.value == key
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) NeonCyan else CyberBgTertiary)
                                    .clickable { viewModel.discountType.value = key }
                            ) {
                                Text(
                                    label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }

                // Summary Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("TOTAL:", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text(
                        "Rs " + String.format("%.2f", cartTotal),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonCyan
                    )
                }

                // Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.holdCurrentBill()
                            showCartSheet = false
                            Toast.makeText(context, "Bill Held", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeWarning.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, OrangeWarning),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("⏸ Hold Bill", color = OrangeWarning, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (currentMode.contains("loan") && viewModel.selectedCustomer.value == null) {
                                Toast.makeText(context, "Select a customer first", Toast.LENGTH_SHORT).show()
                            } else {
                                cashAmountInput = ""
                                payMethod = if (currentMode.contains("loan")) "loan" else "cash"
                                showPaymentDialog = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("💳 Pay Now", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // ===== PAYMENT DIALOG =====
    if (showPaymentDialog) {
        val total = cartTotal
        val cashPaid = cashAmountInput.toDoubleOrNull() ?: 0.0
        val change = (cashPaid - total).coerceAtLeast(0.0)

        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("💳 Payment", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Rs " + String.format("%.2f", total), color = NeonCyan, fontWeight = FontWeight.Black)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (currentMode.contains("loan")) {
                        Text(
                            "Paying on Loan Account of:",
                            fontSize = 12.sp,
                            color = SlateText
                        )
                        Text(
                            "👤 " + (viewModel.selectedCustomer.value?.name ?: "Unknown"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrangeWarning
                        )
                    } else {
                        // Payment Method Choice
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val methods = listOf("cash" to "💵 Cash", "card" to "💳 Card", "transfer" to "📲 Transfer")
                            methods.forEach { (mKey, label) ->
                                val selected = payMethod == mKey
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) NeonCyan else CyberBgSecondary)
                                        .border(1.dp, if (selected) NeonCyan else CardBorder, RoundedCornerShape(8.dp))
                                        .clickable {
                                            payMethod = mKey
                                            cashAmountInput = ""
                                        }
                                        .padding(vertical = 10.dp)
                                ) {
                                    Text(
                                        label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.Black else Color.White
                                    )
                                }
                            }
                        }

                        if (payMethod == "cash") {
                            // Custom Cash Pad UI
                            Text(
                                "Cash Received:",
                                fontSize = 12.sp,
                                color = SlateText
                            )

                            Text(
                                text = if (cashAmountInput.isEmpty()) "Rs 0" else "Rs $cashAmountInput",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonCyan,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )

                            // Quick Numpad layout
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val keys = listOf(
                                    listOf("1", "2", "3"),
                                    listOf("4", "5", "6"),
                                    listOf("7", "8", "9"),
                                    listOf(".", "0", "⌫")
                                )
                                keys.forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        row.forEach { k ->
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .background(CyberBgSecondary, RoundedCornerShape(4.dp))
                                                    .clickable {
                                                        if (k == "⌫") {
                                                            if (cashAmountInput.isNotEmpty()) {
                                                                cashAmountInput = cashAmountInput.dropLast(1)
                                                            }
                                                        } else if (k == ".") {
                                                            if (!cashAmountInput.contains(".")) {
                                                                cashAmountInput += "."
                                                            }
                                                        } else {
                                                            cashAmountInput += k
                                                        }
                                                    }
                                                    .padding(vertical = 12.dp)
                                            ) {
                                                Text(k, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }

                            // Change
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CyberBgTertiary, RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Change Amount", fontSize = 12.sp, color = SlateText)
                                Text("Rs " + String.format("%.2f", change), fontWeight = FontWeight.Bold, color = GreenSuccess)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val paid = if (payMethod == "cash") cashPaid else total
                        if (payMethod == "cash" && paid < total) {
                            Toast.makeText(context, "Insufficient cash received", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.completeSale(paid, payMethod) { sale ->
                                showSaleCompleteDialog = sale
                            }
                            showPaymentDialog = false
                            showCartSheet = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                ) {
                    Text("✓ Complete Sale", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPaymentDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = CyberBgSecondary
        )
    }

    // ===== CUSTOMER PICKER DIALOG =====
    if (showCustomerPicker) {
        AlertDialog(
            onDismissRequest = { showCustomerPicker = false },
            title = { Text("Select Loan Customer", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                if (customers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("No customers found", color = SlateText)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        items(customers) { cust ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectedCustomer.value = cust
                                        showCustomerPicker = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(NeonPurple.copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            cust.name.first().toString().uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = NeonPurple
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(cust.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                        Text(cust.phone ?: "No phone", fontSize = 11.sp, color = SlateText)
                                    }
                                }
                                if (cust.totalDue > 0.0) {
                                    Text(
                                        "Rs " + String.format("%.2f", cust.totalDue),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RedDanger
                                    )
                                }
                            }
                            Divider(color = CardBorder)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomerPicker = false }) {
                    Text("Close")
                }
            },
            containerColor = CyberBgSecondary
        )
    }

    // ===== SALE COMPLETE DIALOG =====
    if (showSaleCompleteDialog != null) {
        val completedSale = showSaleCompleteDialog!!
        AlertDialog(
            onDismissRequest = { showSaleCompleteDialog = null },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("✅", fontSize = 48.sp)
                    Text("Sale Complete!", fontSize = 20.sp, fontWeight = FontWeight.Black, color = GreenSuccess)
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Rs " + String.format("%.2f", completedSale.total),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonCyan
                    )
                    
                    if (completedSale.paymentMethod == "cash" && (completedSale.changeAmount ?: 0.0) > 0.0) {
                        Text(
                            text = "Change: Rs " + String.format("%.2f", completedSale.changeAmount),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText
                        )
                    }

                    if (completedSale.paymentMethod == "loan") {
                        Text(
                            text = "Charged to ${completedSale.customerName}'s Account",
                            fontSize = 12.sp,
                            color = OrangeWarning,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.printerManager.printSale(completedSale, device?.store ?: "Jayaneth Mobile")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberBgTertiary),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🖨 Print", color = Color.White, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                whatsAppPhoneNumber = ""
                                showWhatsAppDialog = completedSale
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurpleVariant),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("💬 WhatsApp", color = Color.White, fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = { showSaleCompleteDialog = null },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("New Sale", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            containerColor = CyberBgSecondary
        )
    }

    if (showWhatsAppDialog != null) {
        val sale = showWhatsAppDialog!!
        AlertDialog(
            onDismissRequest = { showWhatsAppDialog = null },
            title = { Text("Send via WhatsApp", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = whatsAppPhoneNumber,
                        onValueChange = { whatsAppPhoneNumber = it },
                        label = { Text("WhatsApp Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val phoneClean = whatsAppPhoneNumber.trim().replace("+", "").removePrefix("0")
                        val phoneFormatted = if (phoneClean.startsWith("94")) phoneClean else "94$phoneClean"
                        
                        val storeName = device?.store ?: "Jayaneth Mobile"
                        val billText = buildString {
                            append("*$storeName*\n")
                            append("----------------------------\n")
                            append("Bill No: ${sale.billNo}\n")
                            append("Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(sale.createdAt))}\n")
                            append("----------------------------\n")
                            sale.items.forEach { item ->
                                append("${item.name}\n")
                                append("  ${item.qty} x Rs ${String.format("%.2f", item.price)} = Rs ${String.format("%.2f", item.subtotal)}\n")
                            }
                            append("----------------------------\n")
                            if (sale.discount > 0.0) {
                                append("Subtotal: Rs ${String.format("%.2f", sale.subtotal)}\n")
                                append("Discount: Rs ${String.format("%.2f", sale.discount)}\n")
                            }
                            append("*Total: Rs ${String.format("%.2f", sale.total)}*\n")
                            append("----------------------------\n")
                            append("Thank you! Come again.")
                        }

                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                val encoded = java.net.URLEncoder.encode(billText, "UTF-8")
                                data = android.net.Uri.parse("https://api.whatsapp.com/send?phone=$phoneFormatted&text=$encoded")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        showWhatsAppDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Send", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWhatsAppDialog = null }) {
                    Text("Cancel")
                }
            },
            containerColor = CyberBgSecondary
        )
    }
    }
}
