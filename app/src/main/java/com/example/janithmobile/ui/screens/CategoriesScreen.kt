package com.example.janithmobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.janithmobile.data.Category
import com.example.janithmobile.ui.pos.PosViewModel
import com.example.janithmobile.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    viewModel: PosViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val products by viewModel.filteredProducts.collectAsStateWithLifecycle() // For counting associated products

    var showAddEditDialog by remember { mutableStateOf<Category?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🏷 Categories", fontWeight = FontWeight.Bold) },
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
                    showAddEditDialog = Category(name = "", icon = "🏷", color = "#00d4ff", sortOrder = 0)
                    showDialog = true
                },
                containerColor = NeonCyan,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Category")
            }
        },
        containerColor = CyberBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (categories.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No categories added yet. Tap + to add.", color = SlateText)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(categories) { cat ->
                        val count = viewModel.filteredProducts.value.count { it.categoryId == cat.id }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                            border = BorderStroke(1.dp, CardBorder),
                            onClick = {
                                showAddEditDialog = cat
                                showDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(android.graphics.Color.parseColor(cat.color ?: "#00d4ff")),
                                        modifier = Modifier.size(12.dp)
                                    ) {}
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "${cat.icon ?: "🏷"} ${cat.name}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = "$count products",
                                    fontSize = 12.sp,
                                    color = SlateText
                                )
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
        var icon by remember { mutableStateOf(showAddEditDialog!!.icon ?: "🏷") }
        var color by remember { mutableStateOf(showAddEditDialog!!.color ?: "#00d4ff") }

        val colorsList = listOf(
            "#00d4ff", "#7c3aed", "#ec4899", "#22c55e",
            "#f59e0b", "#ef4444", "#06b6d4", "#84cc16", "#f97316"
        )

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = if (showAddEditDialog!!.id > 0) "Edit Category" else "Add Category",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Category Name *") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = icon,
                        onValueChange = { icon = it },
                        label = { Text("Icon (Emoji) *") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Color swatches selection
                    Text("Select Theme Color:", fontSize = 12.sp, color = SlateText)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(80.dp)
                    ) {
                        items(colorsList) { cStr ->
                            val isSelected = color == cStr
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(cStr)))
                                    .border(
                                        width = 2.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { color = cStr }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.trim().isEmpty()) {
                            Toast.makeText(context, "Category name required", Toast.LENGTH_SHORT).show()
                        } else {
                            val savedCat = showAddEditDialog!!.copy(
                                name = name.trim(),
                                icon = icon.trim(),
                                color = color
                            )
                            viewModel.saveCategory(savedCat) {
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
                                viewModel.deleteCategory(showAddEditDialog!!.id) {
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
