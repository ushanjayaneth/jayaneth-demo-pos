package com.example.janithmobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.janithmobile.data.Product
import com.example.janithmobile.ui.pos.PosViewModel
import com.example.janithmobile.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodesScreen(
    viewModel: PosViewModel,
    onNavigateBack: () -> Unit
) {
    val products by viewModel.filteredProducts.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    
    // Dialog states
    var selectedProductForBarcode by remember { mutableStateOf<Product?>(null) }
    var showEditBarcodeDialog by remember { mutableStateOf<Product?>(null) }
    var editBarcodeValue by remember { mutableStateOf("") }

    // Update query inside viewmodel
    LaunchedEffect(searchQuery) {
        viewModel.searchQuery.value = searchQuery
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🏷️ Barcode Manager", fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search Products...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SlateText) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = CardBorder,
                    focusedLabelColor = NeonCyan,
                    unfocusedLabelColor = SlateText,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Divider(color = CardBorder, modifier = Modifier.padding(bottom = 16.dp))

            if (products.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No products found", color = DimText)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(products) { product ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = product.name,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (product.barcode.isNullOrEmpty()) "No Barcode" else "Code: ${product.barcode}",
                                        color = if (product.barcode.isNullOrEmpty()) OrangeWarning else NeonCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Stock: ${product.stock ?: 0}",
                                        color = SlateText,
                                        fontSize = 12.sp
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!product.barcode.isNullOrEmpty()) {
                                        Button(
                                            onClick = { selectedProductForBarcode = product },
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurpleVariant)
                                        ) {
                                            Icon(
                                                Icons.Default.QrCode,
                                                contentDescription = "View Barcode",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("View", fontSize = 12.sp)
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            showEditBarcodeDialog = product
                                            editBarcodeValue = product.barcode ?: ""
                                        },
                                        border = BorderStroke(1.dp, NeonCyan),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                                    ) {
                                        Text(if (product.barcode.isNullOrEmpty()) "Add" else "Edit", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog for viewing barcode
    selectedProductForBarcode?.let { product ->
        Dialog(onDismissRequest = { selectedProductForBarcode = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                border = BorderStroke(1.dp, NeonCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = product.name,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Draw a barcode mockup
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Canvas(modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)) {
                                val barcodeStr = product.barcode ?: "00000000"
                                val numBars = barcodeStr.length * 8 + 10
                                val barWidth = size.width / numBars
                                
                                // Draw start quiet zone and guard bars
                                drawRect(color = Color.Black, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(barWidth * 2, size.height))
                                
                                // Draw content bars based on barcode string hash/value characters
                                var currentX = barWidth * 3
                                val hash = barcodeStr.hashCode().toString()
                                for (i in 0 until 40) {
                                    val isBar = (hash.getOrNull(i % hash.length)?.code ?: 0) % 2 == 0
                                    val currentBarWidth = if ((i % 5 == 0)) barWidth * 2 else barWidth
                                    if (isBar) {
                                        drawRect(color = Color.Black, topLeft = Offset(currentX, 0f), size = androidx.compose.ui.geometry.Size(currentBarWidth, size.height))
                                    }
                                    currentX += currentBarWidth + barWidth
                                }
                                
                                // Draw end guard
                                drawRect(color = Color.Black, topLeft = Offset(size.width - barWidth * 2, 0f), size = androidx.compose.ui.geometry.Size(barWidth * 2, size.height))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = product.barcode ?: "",
                                color = Color.Black,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { selectedProductForBarcode = null },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, CardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateText)
                        ) {
                            Text("Close")
                        }
                        Button(
                            onClick = {
                                // Trigger printing
                                viewModel.printerManager.printBarcode(product.name, product.barcode ?: "")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                        ) {
                            Text("Print Label", color = Color.Black)
                        }
                    }
                }
            }
        }
    }

    // Dialog for adding/editing barcode
    showEditBarcodeDialog?.let { product ->
        Dialog(onDismissRequest = { showEditBarcodeDialog = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                border = BorderStroke(1.dp, NeonPurple),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Edit Barcode",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Text(
                        text = product.name,
                        color = SlateText,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = editBarcodeValue,
                        onValueChange = { editBarcodeValue = it },
                        label = { Text("Barcode Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonPurple,
                            unfocusedBorderColor = CardBorder,
                            focusedLabelColor = NeonPurple,
                            unfocusedLabelColor = SlateText,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { showEditBarcodeDialog = null },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, CardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateText)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val updated = product.copy(barcode = editBarcodeValue.trim())
                                viewModel.saveProduct(updated)
                                showEditBarcodeDialog = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                        ) {
                            Text("Save", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
