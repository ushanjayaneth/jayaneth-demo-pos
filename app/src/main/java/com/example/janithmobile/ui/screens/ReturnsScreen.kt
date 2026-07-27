package com.example.janithmobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.janithmobile.data.CartItem
import com.example.janithmobile.data.Sale
import com.example.janithmobile.ui.pos.PosViewModel
import com.example.janithmobile.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReturnsScreen(
    viewModel: PosViewModel,
    onNavigateBack: () -> Unit
) {
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val returnsList by viewModel.returns.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedSaleForReturn by remember { mutableStateOf<Sale?>(null) }
    
    // Map of productId to returned quantity
    val returnedQuantities = remember { mutableStateMapOf<Int, Int>() }

    val filteredSales = remember(sales, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else sales.filter { it.billNo.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔄 Sales Returns", fontWeight = FontWeight.Bold) },
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
            if (selectedSaleForReturn == null) {
                // Search sale mode
                Text(
                    text = "Find Sale to Return Items",
                    color = LightText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Enter Bill No (e.g. B1234567)") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SlateText) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Divider(color = CardBorder, modifier = Modifier.padding(bottom = 16.dp))

                if (filteredSales.isEmpty() && searchQuery.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No matching sales found.", color = DimText)
                    }
                } else if (searchQuery.isBlank()) {
                    // Show history of returns processed
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Recently Processed Returns",
                            color = SlateText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (returnsList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No returns processed yet.", color = DimText, fontSize = 12.sp)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(returnsList) { ret ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                                        border = BorderStroke(1.dp, CardBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Bill No: ${ret.saleBillNo}", fontWeight = FontWeight.Bold, color = LightText)
                                                Text(
                                                    text = "Refund: Rs ${String.format("%.2f", ret.refundAmount)}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = RedDanger
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Items: " + ret.returnedItems.joinToString { "${it.name} (x${it.qty})" },
                                                color = SlateText,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredSales) { sale ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSaleForReturn = sale
                                        returnedQuantities.clear()
                                        sale.items.forEach { item ->
                                            returnedQuantities[item.productId] = 0
                                        }
                                    }
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Bill No: ${sale.billNo}", fontWeight = FontWeight.Bold, color = NeonCyan)
                                        Text("Total: Rs ${String.format("%.2f", sale.total)}", fontWeight = FontWeight.Bold, color = LightText)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Customer: ${sale.customerName ?: "Walk-in"}", color = SlateText, fontSize = 12.sp)
                                    Text("Method: ${sale.paymentMethod.uppercase()}", color = SlateText, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                // Return processing mode for selected sale
                val sale = selectedSaleForReturn!!
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedSaleForReturn = null }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to list", tint = LightText)
                    }
                    Text(
                        text = "Process Return for ${sale.billNo}",
                        fontWeight = FontWeight.Bold,
                        color = LightText,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(48.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sale.items) { item ->
                        val currentRetQty = returnedQuantities[item.productId] ?: 0
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.Bold, color = LightText)
                                    Text("Purchased: ${item.qty} @ Rs ${String.format("%.2f", item.price)}", color = SlateText, fontSize = 12.sp)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (currentRetQty > 0) {
                                                returnedQuantities[item.productId] = currentRetQty - 1
                                            }
                                        },
                                        enabled = currentRetQty > 0
                                    ) {
                                        Text("-", color = if (currentRetQty > 0) NeonCyan else DimText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                    }
                                    
                                    Text(
                                        text = currentRetQty.toString(),
                                        color = if (currentRetQty > 0) NeonCyan else LightText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )

                                    IconButton(
                                        onClick = {
                                            if (currentRetQty < item.qty) {
                                                returnedQuantities[item.productId] = currentRetQty + 1
                                            }
                                        },
                                        enabled = currentRetQty < item.qty
                                    ) {
                                        Text("+", color = if (currentRetQty < item.qty) NeonCyan else DimText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Compute Refund
                val totalRefund = remember(returnedQuantities) {
                    sale.items.sumOf { item ->
                        val qty = returnedQuantities[item.productId] ?: 0
                        qty * item.price
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                    border = BorderStroke(1.dp, RedDanger),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TOTAL REFUND", color = SlateText, fontSize = 12.sp)
                            Text("Rs ${String.format("%.2f", totalRefund)}", fontWeight = FontWeight.Black, fontSize = 20.sp, color = RedDanger)
                        }

                        Button(
                            onClick = {
                                val returnedItemsList = mutableListOf<CartItem>()
                                sale.items.forEach { item ->
                                    val qty = returnedQuantities[item.productId] ?: 0
                                    if (qty > 0) {
                                        returnedItemsList.add(item.copy(qty = qty, subtotal = qty * item.price))
                                    }
                                }
                                if (returnedItemsList.isNotEmpty()) {
                                    viewModel.recordReturn(
                                        saleBillNo = sale.billNo,
                                        returnedItems = returnedItemsList,
                                        refundAmount = totalRefund
                                    ) {
                                        selectedSaleForReturn = null
                                        searchQuery = ""
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RedDanger),
                            enabled = totalRefund > 0.0
                        ) {
                            Icon(Icons.Default.AssignmentReturn, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Confirm Return", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
