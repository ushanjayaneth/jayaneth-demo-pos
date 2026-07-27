package com.example.janithmobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.janithmobile.ui.pos.PosViewModel
import com.example.janithmobile.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEndScreen(
    viewModel: PosViewModel,
    onNavigateBack: () -> Unit
) {
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()

    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var expenseAmount by remember { mutableStateOf("") }
    var expenseDesc by remember { mutableStateOf("") }
    var expenseType by remember { mutableStateOf("General") }

    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val todaySales = remember(sales) {
        sales.filter { it.createdAt >= todayStart }
    }

    val todayExpenses = remember(expenses) {
        expenses.filter { it.createdAt >= todayStart }
    }

    // Calculations
    val totalRevenue = todaySales.sumOf { it.total }
    val cashSales = todaySales.filter { it.paymentMethod == "cash" }.sumOf { it.total }
    val cardSales = todaySales.filter { it.paymentMethod == "card" }.sumOf { it.total }
    val loanSales = todaySales.filter { it.paymentMethod == "loan" }.sumOf { it.total }

    val totalExpenses = todayExpenses.sumOf { it.amount }
    val netCash = totalRevenue - totalExpenses

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🌅 Day End Summary", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        val storeName = viewModel.currentDeviceInfo.value?.store ?: "Jayaneth Mobile"
                        viewModel.printerManager.printDayEndReport(todaySales, todayExpenses, dateStr, storeName)
                    }) {
                        Icon(Icons.Default.Print, contentDescription = "Print Report", tint = NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberBg)
            )
        },
        containerColor = CyberBg
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Net income compare summary Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                    border = BorderStroke(1.dp, if (netCash >= 0) NeonCyan else RedDanger),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NET CASH BALANCE", color = SlateText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Rs " + String.format("%.2f", netCash),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = if (netCash >= 0) NeonCyan else RedDanger
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total Revenue", fontSize = 11.sp, color = DimText)
                                Text("Rs ${String.format("%.2f", totalRevenue)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LightText)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total Expenses", fontSize = 11.sp, color = DimText)
                                Text("Rs ${String.format("%.2f", totalExpenses)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RedDanger)
                            }
                        }
                    }
                }
            }

            // Sales breakdown details
            item {
                Text(
                    text = "Sales Breakdown",
                    color = LightText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("💵 Cash Sales", color = SlateText, fontSize = 14.sp)
                            Text("Rs ${String.format("%.2f", cashSales)}", color = LightText, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("💳 Card Sales", color = SlateText, fontSize = 14.sp)
                            Text("Rs ${String.format("%.2f", cardSales)}", color = LightText, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("🤝 Loan Sales", color = SlateText, fontSize = 14.sp)
                            Text("Rs ${String.format("%.2f", loanSales)}", color = OrangeWarning, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Expenses Tracker header with "Add" button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's Expenses",
                        color = LightText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    IconButton(
                        onClick = { showAddExpenseDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = NeonPurpleVariant)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Expense", tint = Color.White)
                    }
                }
            }

            if (todayExpenses.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No expenses recorded today.", color = DimText, fontSize = 14.sp)
                    }
                }
            } else {
                items(todayExpenses) { expense ->
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
                            Column {
                                Text(
                                    text = expense.description ?: expense.type,
                                    fontWeight = FontWeight.Bold,
                                    color = LightText
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Category: ${expense.type}",
                                    color = SlateText,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = "Rs " + String.format("%.2f", expense.amount),
                                fontWeight = FontWeight.Black,
                                color = RedDanger
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Expense Dialog
    if (showAddExpenseDialog) {
        Dialog(onDismissRequest = { showAddExpenseDialog = false }) {
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
                        text = "Record Expense",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = expenseAmount,
                        onValueChange = { expenseAmount = it },
                        label = { Text("Amount (Rs)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonPurple,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = expenseDesc,
                        onValueChange = { expenseDesc = it },
                        label = { Text("Description / Note") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonPurple,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Simple Dropdown mock or text input for expense type
                    OutlinedTextField(
                        value = expenseType,
                        onValueChange = { expenseType = it },
                        label = { Text("Category (Rent, Utilities, Salary, etc.)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonPurple,
                            unfocusedBorderColor = CardBorder,
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
                            onClick = { showAddExpenseDialog = false },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, CardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateText)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val amt = expenseAmount.toDoubleOrNull() ?: 0.0
                                if (amt > 0) {
                                    viewModel.addExpense(amt, expenseDesc.trim(), expenseType.trim())
                                }
                                showAddExpenseDialog = false
                                expenseAmount = ""
                                expenseDesc = ""
                                expenseType = "General"
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
