package com.example.janithmobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import android.widget.Toast
import com.example.janithmobile.ui.pos.PosViewModel
import com.example.janithmobile.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: PosViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val sales by viewModel.sales.collectAsStateWithLifecycle()

    var filterPeriod by remember { mutableStateOf("all") } // "today", "all"
    var selectedSaleOptions by remember { mutableStateOf<com.example.janithmobile.data.Sale?>(null) }
    var showWhatsAppDialog by remember { mutableStateOf<com.example.janithmobile.data.Sale?>(null) }
    var whatsAppPhoneNumber by remember { mutableStateOf("") }
    
    val filteredSales = remember(sales, filterPeriod) {
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        if (filterPeriod == "today") {
            sales.filter { it.createdAt >= todayStart }
        } else {
            sales
        }
    }

    val revenue = filteredSales.sumOf { it.total }
    val loansCount = filteredSales.count { it.paymentMethod == "loan" }
    val loansTotal = filteredSales.filter { it.paymentMethod == "loan" }.sumOf { it.total }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📊 Sales History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Row(modifier = Modifier.padding(end = 8.dp)) {
                        TextButton(
                            onClick = { filterPeriod = "today" },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (filterPeriod == "today") NeonCyan else SlateText
                            )
                        ) {
                            Text("Today")
                        }
                        TextButton(
                            onClick = { filterPeriod = "all" },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (filterPeriod == "all") NeonCyan else SlateText
                            )
                        ) {
                            Text("All")
                        }
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
        ) {
            // Stats Panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Revenue", fontSize = 11.sp, color = SlateText)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Rs " + String.format("%.2f", revenue),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = NeonCyan
                        )
                        Text(
                            text = "${filteredSales.size} bills printed",
                            fontSize = 10.sp,
                            color = DimText,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("On Loan / Dues", fontSize = 11.sp, color = SlateText)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Rs " + String.format("%.2f", loansTotal),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = OrangeWarning
                        )
                        Text(
                            text = "$loansCount loan invoices",
                            fontSize = 10.sp,
                            color = DimText,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Divider(color = CardBorder, modifier = Modifier.padding(horizontal = 16.dp))

            if (filteredSales.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No transactions found for this period", color = SlateText)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSales) { sale ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberBgSecondary, RoundedCornerShape(8.dp))
                                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                                .clickable { selectedSaleOptions = sale }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = sale.billNo,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "${sale.cashierName} · " + 
                                            java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(sale.createdAt)),
                                    fontSize = 11.sp,
                                    color = SlateText
                                )
                                if (sale.customerName != null) {
                                    Text(
                                        text = "👤 ${sale.customerName}",
                                        fontSize = 11.sp,
                                        color = OrangeWarning,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Rs " + String.format("%.2f", sale.total),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = when (sale.saleType) {
                                        "wsale" -> NeonPurple
                                        "retail-loan" -> OrangeWarning
                                        "wsale-loan" -> PinkVariant
                                        else -> NeonCyan
                                    }
                                )
                                Text(
                                    text = sale.paymentMethod.uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateText,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedSaleOptions?.let { sale ->
        AlertDialog(
            onDismissRequest = { selectedSaleOptions = null },
            title = { Text("Bill Action - ${sale.billNo}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Customer: ${sale.customerName ?: "Walk-in"}", color = LightText)
                    Text("Total Amount: Rs ${String.format("%.2f", sale.total)}", color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val storeName = viewModel.currentDeviceInfo.value?.store ?: "Jayaneth Mobile"
                            viewModel.printerManager.printSale(sale, storeName)
                            selectedSaleOptions = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberBgTertiary),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🖨 Print", color = Color.White)
                    }
                    Button(
                        onClick = {
                            whatsAppPhoneNumber = ""
                            showWhatsAppDialog = sale
                            selectedSaleOptions = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurpleVariant),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("💬 WhatsApp", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedSaleOptions = null }) {
                    Text("Close")
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
                        
                        val storeName = viewModel.currentDeviceInfo.value?.store ?: "Jayaneth Mobile"
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
