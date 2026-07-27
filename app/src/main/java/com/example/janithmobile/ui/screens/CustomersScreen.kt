package com.example.janithmobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.example.janithmobile.data.Customer
import com.example.janithmobile.ui.pos.PosViewModel
import com.example.janithmobile.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    viewModel: PosViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val customers by viewModel.customers.collectAsStateWithLifecycle()

    var showAddEditDialog by remember { mutableStateOf<Customer?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    var showPayDueDialog by remember { mutableStateOf<Customer?>(null) }
    var payAmount by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("👥 Customers Registry", fontWeight = FontWeight.Bold) },
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
                    showAddEditDialog = Customer(name = "", phone = null, address = null)
                    showDialog = true
                },
                containerColor = NeonCyan,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Customer")
            }
        },
        containerColor = CyberBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (customers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No customers registered yet", color = SlateText)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(customers) { cust ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                            border = BorderStroke(1.dp, CardBorder),
                            onClick = {
                                showAddEditDialog = cust
                                showDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(NeonPurple.copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            cust.name.first().toString().uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = NeonPurple,
                                            fontSize = 16.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(cust.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                        Text(cust.phone ?: "No phone number", fontSize = 11.sp, color = SlateText)
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    if (cust.totalDue > 0.0) {
                                        Text(
                                            "Rs " + String.format("%.2f", cust.totalDue),
                                            fontWeight = FontWeight.Black,
                                            color = RedDanger,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            "Dues Outstanding",
                                            fontSize = 9.sp,
                                            color = SlateText,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                        Button(
                                            onClick = { showPayDueDialog = cust },
                                            colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess.copy(alpha = 0.15f)),
                                            border = BorderStroke(1.dp, GreenSuccess),
                                            shape = RoundedCornerShape(4.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Settle Pay", color = GreenSuccess, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = GreenSuccess.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, GreenSuccess.copy(alpha = 0.3f))
                                        ) {
                                            Text(
                                                "✓ CLEARED",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = GreenSuccess,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showDialog && showAddEditDialog != null) {
        var name by remember { mutableStateOf(showAddEditDialog!!.name) }
        var phone by remember { mutableStateOf(showAddEditDialog!!.phone ?: "") }
        var address by remember { mutableStateOf(showAddEditDialog!!.address ?: "") }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = if (showAddEditDialog!!.id > 0) "Edit Customer" else "Register Customer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Customer Name *") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Address") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.trim().isEmpty()) {
                            Toast.makeText(context, "Customer name required", Toast.LENGTH_SHORT).show()
                        } else {
                            val savedCust = showAddEditDialog!!.copy(
                                name = name.trim(),
                                phone = if (phone.trim().isEmpty()) null else phone.trim(),
                                address = if (address.trim().isEmpty()) null else address.trim()
                            )
                            viewModel.saveCustomer(savedCust) {
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
                                viewModel.deleteCustomer(showAddEditDialog!!.id) {
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

    // Pay Dues / Settles Dialog
    if (showPayDueDialog != null) {
        val cust = showPayDueDialog!!
        AlertDialog(
            onDismissRequest = {
                showPayDueDialog = null
                payAmount = ""
            },
            title = { Text("Settle Loan Payment", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Customer: ${cust.name}", fontSize = 14.sp)
                    Text("Outstanding Balance: Rs " + String.format("%.2f", cust.totalDue), color = RedDanger, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = payAmount,
                        onValueChange = { payAmount = it },
                        label = { Text("Payment Received (Rs)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = payAmount.toDoubleOrNull()
                        if (amt == null || amt <= 0.0) {
                            Toast.makeText(context, "Invalid amount", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.recordCustomerDuePayment(cust.id, amt)
                            showPayDueDialog = null
                            payAmount = ""
                            Toast.makeText(context, "Payment logged!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                ) {
                    Text("Record Settle", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPayDueDialog = null
                        payAmount = ""
                    }
                ) {
                    Text("Cancel")
                }
            },
            containerColor = CyberBgSecondary
        )
    }
}
