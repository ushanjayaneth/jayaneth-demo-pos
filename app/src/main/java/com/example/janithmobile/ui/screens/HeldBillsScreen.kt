package com.example.janithmobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.janithmobile.data.HeldBill
import com.example.janithmobile.ui.pos.PosViewModel
import com.example.janithmobile.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeldBillsScreen(
    viewModel: PosViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val heldBills by viewModel.heldBills.collectAsStateWithLifecycle()
    var selectedHeldBill by remember { mutableStateOf<HeldBill?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⏸ Held Bills", fontWeight = FontWeight.Bold) },
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
        ) {
            if (heldBills.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No suspended bills found", color = SlateText)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(heldBills) { bill ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                            border = BorderStroke(1.dp, CardBorder),
                            onClick = { selectedHeldBill = bill },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = bill.billNo,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        color = NeonCyan
                                    )
                                    // Mode badge
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = when (bill.saleType) {
                                            "wsale" -> NeonPurple.copy(alpha = 0.15f)
                                            "retail-loan" -> OrangeWarning.copy(alpha = 0.15f)
                                            "wsale-loan" -> PinkVariant.copy(alpha = 0.15f)
                                            else -> NeonCyan.copy(alpha = 0.15f)
                                        }
                                    ) {
                                        Text(
                                            text = bill.saleType.uppercase(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (bill.saleType) {
                                                "wsale" -> NeonPurple
                                                "retail-loan" -> OrangeWarning
                                                "wsale-loan" -> PinkVariant
                                                else -> NeonCyan
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Text(
                                    text = "${bill.cashierName ?: "Cashier"} · " + 
                                            java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(bill.createdAt)),
                                    fontSize = 11.sp,
                                    color = SlateText
                                )

                                if (bill.customerName != null) {
                                    Text(
                                        text = "👤 ${bill.customerName}",
                                        fontSize = 12.sp,
                                        color = OrangeWarning,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Text(
                                        text = "${bill.items.size} item(s)",
                                        fontSize = 12.sp,
                                        color = SlateText
                                    )
                                    Text(
                                        text = "Rs " + String.format("%.2f", bill.total),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Bill Details Action Dialog
    if (selectedHeldBill != null) {
        val bill = selectedHeldBill!!
        AlertDialog(
            onDismissRequest = { selectedHeldBill = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(bill.billNo, fontWeight = FontWeight.Bold)
                    Text("Rs " + String.format("%.2f", bill.total), color = NeonCyan, fontWeight = FontWeight.Black)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Items summary:", fontSize = 12.sp, color = SlateText)
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp)) {
                        items(bill.items) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${item.name} ×${item.qty}", fontSize = 13.sp, color = Color.White)
                                Text("Rs " + String.format("%.2f", item.subtotal), fontSize = 13.sp, color = SlateText)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.deleteHeldBill(bill.id)
                            selectedHeldBill = null
                            Toast.makeText(context, "Held bill deleted", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedDanger),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }

                    Button(
                        onClick = {
                            viewModel.resumeHeldBill(bill)
                            selectedHeldBill = null
                            onNavigateBack() // Go back to POS
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resume Bill", color = Color.White)
                    }
                }
            },
            containerColor = CyberBgSecondary
        )
    }
}
