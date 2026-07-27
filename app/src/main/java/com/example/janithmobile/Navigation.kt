package com.example.janithmobile

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.janithmobile.data.DeviceInfo
import com.example.janithmobile.theme.*
import com.example.janithmobile.ui.pos.PosViewModel
import com.example.janithmobile.ui.screens.*

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)
  val viewModel: PosViewModel = viewModel()
  val deviceSetup = viewModel.currentDeviceInfo.value
  val isBlocked = viewModel.isLicenseBlocked.value

  Box(modifier = Modifier.fillMaxSize()) {
    NavDisplay(
      backStack = backStack,
      onBack = { backStack.removeLastOrNull() },
      entryProvider = entryProvider {

        entry<Main> {
          if (deviceSetup == null) {
            SetupScreen(
              onSetupComplete = { info -> viewModel.updateDeviceInfo(info) }
            )
          } else {
            PosScreen(
              viewModel = viewModel,
              onNavigate = { navKey -> backStack.add(navKey as NavKey) },
              onStartScan = {}
            )
          }
        }

        entry<Products> {
          ProductsScreen(
            viewModel = viewModel,
            onNavigateBack = { backStack.removeLastOrNull() },
            onStartScan = { }
          )
        }

        entry<Categories> {
          CategoriesScreen(
            viewModel = viewModel,
            onNavigateBack = { backStack.removeLastOrNull() }
          )
        }

        entry<HeldBills> {
          HeldBillsScreen(
            viewModel = viewModel,
            onNavigateBack = { backStack.removeLastOrNull() }
          )
        }

        entry<History> {
          HistoryScreen(
            viewModel = viewModel,
            onNavigateBack = { backStack.removeLastOrNull() }
          )
        }

        entry<Customers> {
          CustomersScreen(
            viewModel = viewModel,
            onNavigateBack = { backStack.removeLastOrNull() }
          )
        }

        entry<Reports> {
          ReportsScreen(
            viewModel = viewModel,
            onNavigateBack = { backStack.removeLastOrNull() }
          )
        }

        entry<Settings> {
          SettingsScreen(
            viewModel = viewModel,
            onNavigateBack = { backStack.removeLastOrNull() },
            onNavigateTo = { navKey -> backStack.add(navKey as NavKey) }
          )
        }

        entry<Barcodes> {
          BarcodesScreen(
            viewModel = viewModel,
            onNavigateBack = { backStack.removeLastOrNull() }
          )
        }

        entry<DayEnd> {
          DayEndScreen(
            viewModel = viewModel,
            onNavigateBack = { backStack.removeLastOrNull() }
          )
        }

        entry<Repairs> {
          RepairsScreen(
            viewModel = viewModel,
            onNavigateBack = { backStack.removeLastOrNull() }
          )
        }

        entry<Returns> {
          ReturnsScreen(
            viewModel = viewModel,
            onNavigateBack = { backStack.removeLastOrNull() }
          )
        }
      },
    )

    if (isBlocked) {
      AppLockOverlay(viewModel = viewModel, deviceInfo = deviceSetup)
    }
  }
}

@Composable
fun AppLockOverlay(
    viewModel: PosViewModel,
    deviceInfo: DeviceInfo?
) {
    val context = LocalContext.current
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }

    var tapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }

    val lockReason = viewModel.lockReason.value
    val isOfflineExpired = lockReason == "OFFLINE_EXPIRED"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .clickable(enabled = false) {}, // Intercept all touch events
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(CyberBgSecondary, RoundedCornerShape(16.dp))
                .border(2.dp, if (isOfflineExpired) OrangeWarning else RedDanger, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Icon(
                imageVector = if (isOfflineExpired) Icons.Default.WifiOff else Icons.Default.Lock,
                contentDescription = "App Locked",
                tint = if (isOfflineExpired) OrangeWarning else RedDanger,
                modifier = Modifier
                    .size(64.dp)
                    .clickable {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 800) {
                            tapCount++
                        } else {
                            tapCount = 1
                        }
                        lastTapTime = now
                        if (tapCount >= 5) {
                            tapCount = 0
                            showPinDialog = true
                        }
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isOfflineExpired) "🌐 INTERNET REQUIRED" else "🚨 APP LICENSE EXPIRED",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = if (isOfflineExpired) OrangeWarning else RedDanger,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isOfflineExpired) "LICENSE VERIFICATION REQUIRED" else "PAYMENT OVERDUE / ACCOUNT BLOCKED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = Color.Black.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (isOfflineExpired) {
                        Text(
                            text = "කරුණාකර බලපත්‍රය සනාථ කරගැනීමට උපාංගය අන්තර්ජාලයට (Wi-Fi / Mobile Data) සම්බන්ධ කරන්න.",
                            fontSize = 13.sp,
                            color = Color.White,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Please connect device to Internet (Wi-Fi / Mobile Data) to verify application license.",
                            fontSize = 11.sp,
                            color = SlateText
                        )
                    } else {
                        Text(
                            text = "මෙම POS ඇප් එකෙහි ⁣සේවා කාලය / ගෙවීම් කාලසීමාව අවසන් වී ඇති බැවින් ක්‍රියාකාරීත්වය අත්හිටුවා ඇත.",
                            fontSize = 13.sp,
                            color = Color.White,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "කරුණාකර පද්ධති පරිපාලක (Admin) සම්බන්ධ කරගන්න.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = CardBorder)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Store: ${deviceInfo?.store ?: "Janith Mobile"}", fontSize = 11.sp, color = SlateText)
                    Text("Device ID: ${deviceInfo?.id ?: "UNKNOWN"}", fontSize = 11.sp, color = SlateText)
                    Text(
                        "Status: ${if (isOfflineExpired) "⚠️ OFFLINE EXPIRED" else "🔴 BLOCKED"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOfflineExpired) OrangeWarning else RedDanger
                    )
                }
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false; pinInput = "" },
            title = { Text("Master Admin Unlock", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter Master Owner PIN to unlock app:", fontSize = 13.sp, color = SlateText)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        label = { Text("Master PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
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
                        if (pinInput == "9999") {
                            viewModel.setLicenseStatus("ACTIVE")
                            showPinDialog = false
                            pinInput = ""
                            Toast.makeText(context, "✅ App License Unlocked Successfully!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "❌ Invalid Master PIN", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Unlock App", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false; pinInput = "" }) { Text("Cancel") }
            },
            containerColor = CyberBgSecondary
        )
    }
}
