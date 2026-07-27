package com.example.janithmobile.ui.screens

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
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
import com.example.janithmobile.data.Expense
import com.example.janithmobile.data.Sale
import com.example.janithmobile.theme.*
import com.example.janithmobile.ui.pos.PosViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: PosViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()

    var selectedPeriod by remember { mutableStateOf("today") } // "today", "week", "month"

    val (salesForPeriod, expensesForPeriod) = remember(sales, expenses, selectedPeriod) {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        val startOfToday = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val periodStart = when (selectedPeriod) {
            "today" -> startOfToday
            "week" -> now - 7 * 24 * 60 * 60 * 1000L
            "month" -> now - 30 * 24 * 60 * 60 * 1000L
            else -> 0L
        }

        Pair(
            sales.filter { it.createdAt >= periodStart },
            expenses.filter { it.createdAt >= periodStart }
        )
    }

    // Key Calculations
    val totalRevenue = salesForPeriod.sumOf { it.total }
    val totalDiscounts = salesForPeriod.sumOf { it.discount }
    val avgBill = if (salesForPeriod.isNotEmpty()) totalRevenue / salesForPeriod.size else 0.0

    // COGS & Net Profit
    val totalCOGS = salesForPeriod.sumOf { sale ->
        sale.items.sumOf { item -> (item.costPrice ?: 0.0) * item.qty }
    }
    val grossProfit = totalRevenue - totalCOGS
    val totalExpenses = expensesForPeriod.sumOf { it.amount }
    val netProfit = grossProfit - totalExpenses

    // Grouping by type
    val modeSummary = remember(salesForPeriod) {
        val map = mutableMapOf<String, Pair<Int, Double>>()
        salesForPeriod.forEach { sale ->
            val prev = map[sale.saleType] ?: Pair(0, 0.0)
            map[sale.saleType] = Pair(prev.first + 1, prev.second + sale.total)
        }
        map.toList().sortedByDescending { it.second.second }
    }

    // Top Selling products extraction
    val topSellingItems = remember(salesForPeriod) {
        val map = mutableMapOf<String, Pair<Int, Double>>()
        salesForPeriod.forEach { sale ->
            sale.items.forEach { item ->
                val prev = map[item.name] ?: Pair(0, 0.0)
                map[item.name] = Pair(prev.first + item.qty, prev.second + item.subtotal)
            }
        }
        map.toList().sortedByDescending { it.second.second }.take(8)
    }

    val periodLabel = when (selectedPeriod) {
        "today" -> "Today"
        "week" -> "This Week"
        else -> "This Month"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📊 Sales & Profit Reports", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        exportReportToCsv(context, salesForPeriod, expensesForPeriod, periodLabel)
                    }) {
                        Icon(Icons.Default.TableChart, contentDescription = "Export CSV", tint = NeonCyan)
                    }
                    IconButton(onClick = {
                        val storeName = viewModel.currentDeviceInfo.value?.store ?: "Jayaneth Mobile"
                        exportReportToPdf(
                            context = context,
                            periodLabel = periodLabel,
                            totalRevenue = totalRevenue,
                            totalCOGS = totalCOGS,
                            grossProfit = grossProfit,
                            totalExpenses = totalExpenses,
                            netProfit = netProfit,
                            salesCount = salesForPeriod.size,
                            storeName = storeName
                        )
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = NeonPurple)
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
            // Period Selector Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(CyberBgSecondary, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val periods = listOf("today" to "Today", "week" to "This Week", "month" to "This Month")
                periods.forEach { (pKey, label) ->
                    val selected = selectedPeriod == pKey
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) NeonCyan.copy(alpha = 0.15f) else Color.Transparent)
                            .border(1.dp, if (selected) NeonCyan else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { selectedPeriod = pKey }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) NeonCyan else SlateText
                        )
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // ── NET PROFIT SUMMARY CARD ──
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                        border = BorderStroke(1.dp, if (netProfit >= 0) GreenSuccess else RedDanger),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("💰 NET PROFIT ANALYSIS ($periodLabel)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateText)
                                Surface(
                                    color = (if (netProfit >= 0) GreenSuccess else RedDanger).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        if (netProfit >= 0) "PROFITABLE" else "LOSS",
                                        color = if (netProfit >= 0) GreenSuccess else RedDanger,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Gross Revenue", fontSize = 11.sp, color = SlateText)
                                    Text("Rs " + String.format("%.2f", totalRevenue), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                                }
                                Column {
                                    Text("Est. COGS (ගන්නා මිල)", fontSize = 11.sp, color = SlateText)
                                    Text("Rs " + String.format("%.2f", totalCOGS), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Column {
                                    Text("Expenses", fontSize = 11.sp, color = SlateText)
                                    Text("- Rs " + String.format("%.2f", totalExpenses), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OrangeWarning)
                                }
                            }

                            HorizontalDivider(color = CardBorder, modifier = Modifier.padding(vertical = 12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("NET PROFIT (ශුද්ධ ලාභය):", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(
                                    text = "Rs " + String.format("%.2f", netProfit),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (netProfit >= 0) GreenSuccess else RedDanger
                                )
                            }
                        }
                    }
                }

                // Export Actions Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                exportReportToCsv(context, salesForPeriod, expensesForPeriod, periodLabel)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberBgTertiary),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export CSV", color = Color.White, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                val storeName = viewModel.currentDeviceInfo.value?.store ?: "Jayaneth Mobile"
                                exportReportToPdf(
                                    context = context,
                                    periodLabel = periodLabel,
                                    totalRevenue = totalRevenue,
                                    totalCOGS = totalCOGS,
                                    grossProfit = grossProfit,
                                    totalExpenses = totalExpenses,
                                    netProfit = netProfit,
                                    salesCount = salesForPeriod.size,
                                    storeName = storeName
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurpleVariant),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export PDF", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }

                // Key metrics Grid
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Revenue card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("Revenue", fontSize = 11.sp, color = SlateText)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Rs " + String.format("%.2f", totalRevenue),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = NeonCyan
                                    )
                                }
                            }
                            // Bill Count card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("Sales Count", fontSize = 11.sp, color = SlateText)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${salesForPeriod.size} Bills",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = GreenSuccess
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Average Bill card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("Avg. Bill Amount", fontSize = 11.sp, color = SlateText)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Rs " + String.format("%.2f", avgBill),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }
                            }
                            // Discounts card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("Discounts Given", fontSize = 11.sp, color = SlateText)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Rs " + String.format("%.2f", totalDiscounts),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        color = OrangeWarning
                                    )
                                }
                            }
                        }
                    }
                }

                // Modes breakdown
                item {
                    Column {
                        Text(
                            text = "BY SALE MODE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (modeSummary.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp), contentAlignment = Alignment.Center
                                ) {
                                    Text("No sales data available", color = SlateText, fontSize = 12.sp)
                                }
                            } else {
                                Column {
                                    modeSummary.forEach { (mode, info) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = mode.uppercase(),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Row {
                                                Text(
                                                    "${info.first} bills",
                                                    fontSize = 12.sp,
                                                    color = SlateText,
                                                    modifier = Modifier.padding(end = 12.dp)
                                                )
                                                Text(
                                                    "Rs " + String.format("%.2f", info.second),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = when (mode) {
                                                        "wsale" -> NeonPurple
                                                        "retail-loan" -> OrangeWarning
                                                        "wsale-loan" -> PinkVariant
                                                        else -> NeonCyan
                                                    }
                                                )
                                            }
                                        }
                                        HorizontalDivider(color = CardBorder)
                                    }
                                }
                            }
                        }
                    }
                }

                // Top Products
                item {
                    Column {
                        Text(
                            text = "TOP SELLING PRODUCTS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CyberBgSecondary),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (topSellingItems.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp), contentAlignment = Alignment.Center
                                ) {
                                    Text("No sales data available", color = SlateText, fontSize = 12.sp)
                                }
                            } else {
                                Column {
                                    topSellingItems.forEachIndexed { idx, (prodName, info) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "#${idx + 1}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = DimText,
                                                    modifier = Modifier.width(24.dp)
                                                )
                                                Text(
                                                    prodName,
                                                    fontSize = 13.sp,
                                                    color = Color.White,
                                                    maxLines = 1
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "×${info.first}",
                                                    fontSize = 11.sp,
                                                    color = SlateText,
                                                    modifier = Modifier.padding(end = 12.dp)
                                                )
                                                Text(
                                                    "Rs " + String.format("%.2f", info.second),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = NeonCyan
                                                )
                                            }
                                        }
                                        HorizontalDivider(color = CardBorder)
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

// ──── CSV EXPORT HELPER ───────────────────────────────────────────────────────
private fun exportReportToCsv(
    context: Context,
    sales: List<Sale>,
    expenses: List<Expense>,
    periodLabel: String
) {
    try {
        val csvText = buildString {
            append("Jayaneth POS - Financial Report ($periodLabel)\n")
            append("Date Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}\n\n")

            append("SALES TRANSACTIONS\n")
            append("Bill No,Date,Sale Type,Payment Method,Customer,Total Amount (Rs),Discount (Rs),Items Count\n")
            sales.forEach { s ->
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(s.createdAt))
                append("${s.billNo},$dateStr,${s.saleType},${s.paymentMethod},\"${s.customerName ?: "Walk-in"}\",${s.total},${s.discount},${s.items.size}\n")
            }

            append("\nEXPENSES\n")
            append("ID,Type,Description,Amount (Rs),Date\n")
            expenses.forEach { e ->
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(e.createdAt))
                append("${e.id},${e.type},\"${e.description ?: ""}\",${e.amount},$dateStr\n")
            }
        }

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, "Jayaneth_Sales_Report_${System.currentTimeMillis()}.csv")
        file.writeText(csvText)

        Toast.makeText(context, "✅ CSV saved to Downloads:\n${file.name}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "CSV export failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// ──── PDF EXPORT HELPER ───────────────────────────────────────────────────────
private fun exportReportToPdf(
    context: Context,
    periodLabel: String,
    totalRevenue: Double,
    totalCOGS: Double,
    grossProfit: Double,
    totalExpenses: Double,
    netProfit: Double,
    salesCount: Int,
    storeName: String
) {
    try {
        val pdfDoc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = pdfDoc.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint()

        // Header Background
        paint.color = android.graphics.Color.parseColor("#0f172a")
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        // Store Name Title
        paint.color = android.graphics.Color.parseColor("#00d4ff")
        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText(storeName.uppercase(), 30f, 40f, paint)

        // Subtitle
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText("FINANCIAL & NET PROFIT REPORT ($periodLabel)", 30f, 68f, paint)

        // Date
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        paint.color = android.graphics.Color.LTGRAY
        paint.textSize = 10f
        canvas.drawText("Generated: $dateStr", 390f, 68f, paint)

        var y = 130f
        paint.color = android.graphics.Color.BLACK
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("FINANCIAL SUMMARY", 30f, y, paint)
        y += 15f

        paint.strokeWidth = 1f
        paint.color = android.graphics.Color.GRAY
        canvas.drawLine(30f, y, 565f, y, paint)
        y += 25f

        paint.textSize = 12f
        paint.isFakeBoldText = false
        fun drawRow(label: String, valStr: String, isBold: Boolean = false, colorHex: String = "#000000") {
            paint.color = android.graphics.Color.parseColor(colorHex)
            paint.isFakeBoldText = isBold
            canvas.drawText(label, 40f, y, paint)
            canvas.drawText(valStr, 400f, y, paint)
            y += 24f
        }

        drawRow("Total Sales Count:", "$salesCount Bills")
        drawRow("Gross Sales Revenue:", "Rs " + String.format("%.2f", totalRevenue), true, "#0284c7")
        drawRow("Cost of Goods Sold (COGS):", "Rs " + String.format("%.2f", totalCOGS))
        drawRow("Gross Profit:", "Rs " + String.format("%.2f", grossProfit), true, "#16a34a")
        drawRow("Total Operating Expenses:", "- Rs " + String.format("%.2f", totalExpenses), false, "#dc2626")

        y += 10f
        canvas.drawLine(30f, y, 565f, y, paint)
        y += 25f

        val netColor = if (netProfit >= 0) "#16a34a" else "#dc2626"
        drawRow("NET PROFIT (ශුද්ධ ලාභය):", "Rs " + String.format("%.2f", netProfit), true, netColor)

        pdfDoc.finishPage(page)

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, "Jayaneth_Profit_Report_${System.currentTimeMillis()}.pdf")
        val fos = FileOutputStream(file)
        pdfDoc.writeTo(fos)
        fos.close()
        pdfDoc.close()

        Toast.makeText(context, "✅ PDF saved to Downloads:\n${file.name}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "PDF export failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
