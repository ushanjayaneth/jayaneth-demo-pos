package com.example.janithmobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
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
import com.example.janithmobile.data.RepairJob
import com.example.janithmobile.ui.pos.PosViewModel
import com.example.janithmobile.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepairsScreen(
    viewModel: PosViewModel,
    onNavigateBack: () -> Unit
) {
    val repairs by viewModel.repairs.collectAsStateWithLifecycle()
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("All") }
    
    var showAddJobDialog by remember { mutableStateOf(false) }
    var showStatusUpdateDialog by remember { mutableStateOf<RepairJob?>(null) }

    // Job inputs
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var deviceModel by remember { mutableStateOf("") }
    var issueDesc by remember { mutableStateOf("") }
    var estimatedCost by remember { mutableStateOf("") }

    val filteredRepairs = remember(repairs, searchQuery, selectedStatusFilter) {
        repairs.filter { job ->
            val matchQuery = searchQuery.isEmpty() ||
                    job.customerName.contains(searchQuery, ignoreCase = true) ||
                    (job.customerPhone ?: "").contains(searchQuery) ||
                    job.deviceModel.contains(searchQuery, ignoreCase = true)
            
            val matchStatus = selectedStatusFilter == "All" || job.status == selectedStatusFilter
            matchQuery && matchStatus
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔧 Repair Tracker", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddJobDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = NeonCyan)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Job", tint = Color.Black)
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
                label = { Text("Search customer, phone or model...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SlateText) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Status Filter row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Received", "In Progress", "Ready", "Delivered").forEach { status ->
                    FilterChip(
                        selected = selectedStatusFilter == status,
                        onClick = { selectedStatusFilter = status },
                        label = { Text(status, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan,
                            selectedLabelColor = Color.Black,
                            containerColor = CyberBgSecondary,
                            labelColor = SlateText
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedStatusFilter == status,
                            borderColor = if (selectedStatusFilter == status) NeonCyan else CardBorder,
                            selectedBorderColor = NeonCyan
                        )
                    )
                }
            }

            Divider(color = CardBorder, modifier = Modifier.padding(bottom = 16.dp))

            if (filteredRepairs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No repair jobs found", color = DimText)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredRepairs) { job ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        Text(
                                            text = job.deviceModel,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = "Customer: ${job.customerName}",
                                            color = LightText,
                                            fontSize = 13.sp
                                        )
                                    }
                                    
                                    // Status Badge
                                    val badgeColor = when (job.status) {
                                        "Received" -> OrangeWarning
                                        "In Progress" -> NeonPurple
                                        "Ready" -> NeonCyan
                                        "Delivered" -> GreenSuccess
                                        else -> SlateText
                                    }
                                    
                                    Surface(
                                        color = badgeColor.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, badgeColor),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = job.status,
                                            color = badgeColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Issue: ${job.issueDescription}",
                                    color = SlateText,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Divider(color = CardBorder.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        Text("Estimated Cost", fontSize = 11.sp, color = DimText)
                                        Text("Rs ${String.format("%.2f", job.estimatedCost)}", fontWeight = FontWeight.Bold, color = NeonCyan)
                                    }
                                    
                                    Button(
                                        onClick = { showStatusUpdateDialog = job },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberBgTertiary),
                                        border = BorderStroke(1.dp, CardBorder),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Update Status", modifier = Modifier.size(14.dp), tint = NeonCyan)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Status", fontSize = 11.sp, color = NeonCyan)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Job Dialog
    if (showAddJobDialog) {
        Dialog(onDismissRequest = { showAddJobDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                border = BorderStroke(1.dp, NeonCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Add Repair Job",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("Customer Name") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedTextColor = Color.White)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text("Phone Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedTextColor = Color.White)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = deviceModel,
                        onValueChange = { deviceModel = it },
                        label = { Text("Device Model (e.g. iPhone 13)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedTextColor = Color.White)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = issueDesc,
                        onValueChange = { issueDesc = it },
                        label = { Text("Issue Details / Description") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedTextColor = Color.White)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = estimatedCost,
                        onValueChange = { estimatedCost = it },
                        label = { Text("Estimated Cost (Rs)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedTextColor = Color.White)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { showAddJobDialog = false },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, CardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateText)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val cost = estimatedCost.toDoubleOrNull() ?: 0.0
                                if (customerName.isNotBlank() && deviceModel.isNotBlank()) {
                                    viewModel.saveRepairJob(
                                        RepairJob(
                                            customerName = customerName.trim(),
                                            customerPhone = customerPhone.trim(),
                                            deviceModel = deviceModel.trim(),
                                            issueDescription = issueDesc.trim(),
                                            estimatedCost = cost,
                                            status = "Received",
                                            createdAt = System.currentTimeMillis()
                                        )
                                    )
                                }
                                showAddJobDialog = false
                                // Reset fields
                                customerName = ""
                                customerPhone = ""
                                deviceModel = ""
                                issueDesc = ""
                                estimatedCost = ""
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                        ) {
                            Text("Save", color = Color.Black)
                        }
                    }
                }
            }
        }
    }

    // Status Update Dialog
    showStatusUpdateDialog?.let { job ->
        Dialog(onDismissRequest = { showStatusUpdateDialog = null }) {
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
                        text = "Update Status",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "${job.deviceModel} - ${job.customerName}",
                        color = SlateText,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                    )

                    listOf("Received", "In Progress", "Ready", "Delivered").forEach { status ->
                        Button(
                            onClick = {
                                viewModel.updateRepairStatus(job.id, status)
                                showStatusUpdateDialog = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (job.status == status) NeonPurple else CyberBgTertiary
                            ),
                            border = BorderStroke(1.dp, if (job.status == status) NeonPurple else CardBorder)
                        ) {
                            Text(status, color = if (job.status == status) Color.White else LightText)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = { showStatusUpdateDialog = null },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, CardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateText)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
