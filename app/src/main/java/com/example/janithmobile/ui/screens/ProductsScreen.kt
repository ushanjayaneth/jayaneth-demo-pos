package com.example.janithmobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.example.janithmobile.data.*
import com.example.janithmobile.ui.pos.PosViewModel
import com.example.janithmobile.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    viewModel: PosViewModel,
    onNavigateBack: () -> Unit,
    onStartScan: ((String) -> Unit) -> Unit = {}
) {
    val context = LocalContext.current
    val products by viewModel.filteredProducts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var showAddEditDialog by remember { mutableStateOf<Product?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val barcodeScannerLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val code = result.contents.trim()
            showAddEditDialog?.let { current ->
                showAddEditDialog = current.copy(barcode = code)
            }
            Toast.makeText(context, "✅ Scanned barcode: $code", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📦 Products List", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberBg)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    showAddEditDialog = Product(name = "", retailPrice = 0.0, barcode = null, categoryId = null, wsalePrice = null, stock = null, description = null)
                    showDialog = true
                },
                containerColor = NeonCyan,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Product")
            }
        },
        containerColor = CyberBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Input
            OutlinedTextField(
                value = viewModel.searchQuery.value,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text("Search by name or barcode...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = SlateText) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CyberBgSecondary,
                    unfocusedContainerColor = CyberBgSecondary,
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(100.dp)
            )

            if (products.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No products added yet. Tap + to add.", color = SlateText)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(products) { prod ->
                        val cat = categories.find { it.id == prod.categoryId }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAddEditDialog = prod
                                    showDialog = true
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CyberBgSecondary)
                                        .border(1.dp, CardBorder, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(cat?.icon ?: "📦", fontSize = 18.sp)
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = prod.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${cat?.name ?: "Uncategorized"} · Stock: ${prod.stock ?: "-"}",
                                        fontSize = 11.sp,
                                        color = SlateText
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Rs " + String.format("%.2f", prod.retailPrice),
                                    fontWeight = FontWeight.Black,
                                    color = NeonCyan,
                                    fontSize = 14.sp
                                )
                                if (prod.wsalePrice != null) {
                                    Text(
                                        text = "WS: Rs " + String.format("%.2f", prod.wsalePrice),
                                        fontSize = 11.sp,
                                        color = NeonPurple
                                    )
                                }
                                if (prod.costPrice != null && prod.costPrice > 0.0) {
                                    Text(
                                        text = "Cost: Rs " + String.format("%.2f", prod.costPrice),
                                        fontSize = 10.sp,
                                        color = SlateText
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = CardBorder)
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showDialog && showAddEditDialog != null) {
        var name by remember { mutableStateOf(showAddEditDialog!!.name) }
        var barcode by remember { mutableStateOf(showAddEditDialog!!.barcode ?: "") }
        var retailPrice by remember { mutableStateOf(if (showAddEditDialog!!.id > 0) showAddEditDialog!!.retailPrice.toString() else "") }
        var wsalePrice by remember { mutableStateOf(showAddEditDialog!!.wsalePrice?.toString() ?: "") }
        var costPrice by remember { mutableStateOf(showAddEditDialog!!.costPrice?.toString() ?: "") }
        var stock by remember { mutableStateOf(showAddEditDialog!!.stock?.toString() ?: "") }
        var categoryId by remember { mutableStateOf(showAddEditDialog!!.categoryId) }
        var description by remember { mutableStateOf(showAddEditDialog!!.description ?: "") }

        var categoryExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = if (showAddEditDialog!!.id > 0) "Edit Product" else "Add Product",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Product Name *") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = retailPrice,
                                onValueChange = { retailPrice = it },
                                label = { Text("Retail Price *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = wsalePrice,
                                onValueChange = { wsalePrice = it },
                                label = { Text("Wholesale") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = costPrice,
                            onValueChange = { costPrice = it },
                            label = { Text("Cost Price (ගන්නා මිල - Net Profit සඳහා)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = barcode,
                                onValueChange = { barcode = it },
                                label = { Text("Barcode") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val options = ScanOptions().apply {
                                        setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                                        setPrompt("Scan Product Barcode or QR Code")
                                        setCameraId(0) // Back camera
                                        setBeepEnabled(true)
                                        setBarcodeImageEnabled(false)
                                        setOrientationLocked(false)
                                    }
                                    barcodeScannerLauncher.launch(options)
                                },
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .size(48.dp)
                                    .background(CyberBgTertiary, CircleShape)
                                    .border(1.dp, CardBorder, CircleShape)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode", tint = NeonCyan)
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = stock,
                            onValueChange = { stock = it },
                            label = { Text("Stock Level") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        val selectedCat = categories.find { it.id == categoryId }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedCat?.let { "${it.icon ?: "📦"} ${it.name}" } ?: "Uncategorized",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Category") },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { categoryExpanded = true }
                            )
                            DropdownMenu(
                                expanded = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f).background(CyberBgSecondary)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Uncategorized", color = Color.White) },
                                    onClick = {
                                        categoryId = null
                                        categoryExpanded = false
                                    }
                                )
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text("${cat.icon ?: "📦"} ${cat.name}", color = Color.White) },
                                        onClick = {
                                            categoryId = cat.id
                                            categoryExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val rPrice = retailPrice.toDoubleOrNull()
                        if (name.trim().isEmpty() || rPrice == null) {
                            Toast.makeText(context, "Please fill required fields", Toast.LENGTH_SHORT).show()
                        } else {
                            val savedProd = showAddEditDialog!!.copy(
                                name = name.trim(),
                                barcode = if (barcode.trim().isEmpty()) null else barcode.trim(),
                                retailPrice = rPrice,
                                wsalePrice = wsalePrice.toDoubleOrNull(),
                                costPrice = costPrice.toDoubleOrNull(),
                                stock = stock.toIntOrNull(),
                                categoryId = categoryId,
                                description = if (description.trim().isEmpty()) null else description.trim()
                            )
                            viewModel.saveProduct(savedProd) {
                                showDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    if (showAddEditDialog!!.id > 0) {
                        IconButton(
                            onClick = {
                                viewModel.deleteProduct(showAddEditDialog!!.id) {
                                    showDialog = false
                                }
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RedDanger)
                        }
                    }
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
            containerColor = CyberBgSecondary
        )
    }
}
