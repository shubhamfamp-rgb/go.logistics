package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppearanceMode
import com.example.data.VehicleClass
import com.example.ui.LogisticsViewModel
import com.example.ui.auth.signOutUser
import com.example.ui.components.AppleCard
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun ToolsScreen(
    viewModel: LogisticsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val appearanceMode by viewModel.appearanceMode.collectAsStateWithLifecycle()
    val profile by viewModel.businessProfile.collectAsStateWithLifecycle()

    // Trip Margin Calculator State
    var distanceKm by remember { mutableStateOf("420") }
    var mileageKmpl by remember { mutableStateOf("4.2") }
    var dieselPrice by remember { mutableStateOf("94.5") }
    var tollAndBata by remember { mutableStateOf("2800") }
    var freightQuote by remember { mutableStateOf("26500") }

    // GST & E-Way State
    var invoiceValue by remember { mutableStateOf("55000") }
    var isRcm5Pct by remember { mutableStateOf(true) }

    // Company Letterhead Editor State
    var companyName by remember(profile.companyName) { mutableStateOf(profile.companyName) }
    var gstin by remember(profile.gstin) { mutableStateOf(profile.gstin) }
    var address by remember(profile.officeAddress) { mutableStateOf(profile.officeAddress) }
    var phone by remember(profile.contactPhone) { mutableStateOf(profile.contactPhone) }
    var alertsOn by remember(profile.notificationsEnabled) { mutableStateOf(profile.notificationsEnabled) }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.triggerAlertNotificationsNow(context)
    }

    val km = distanceKm.toDoubleOrNull() ?: 0.0
    val kmpl = (mileageKmpl.toDoubleOrNull() ?: 1.0).coerceAtLeast(0.5)
    val rate = dieselPrice.toDoubleOrNull() ?: 0.0
    val extras = tollAndBata.toDoubleOrNull() ?: 0.0
    val quote = freightQuote.toDoubleOrNull() ?: 0.0
    val fuelCost = (km / kmpl) * rate
    val totalTripCost = fuelCost + extras
    val netTripProfit = quote - totalTripCost
    val costPerKm = if (km > 0) totalTripCost / km else 0.0

    val invAmt = invoiceValue.toDoubleOrNull() ?: 0.0
    val gstRate = if (isRcm5Pct) 0.05 else 0.12
    val gstTax = invAmt * gstRate
    val needsEwayBill = invAmt >= 50000.0

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Transport Tools & Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Calculators, axle guide, calm appearance & office letterhead",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // System / Light / Dark Appearance
        item {
            AppleCard {
                Text(
                    text = "Display Appearance",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Follow your phone's System appearance or choose a calm Light or Dark view.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AppearanceMode.entries.forEachIndexed { idx, mode ->
                        SegmentedButton(
                            selected = appearanceMode == mode,
                            onClick = { viewModel.setAppearanceMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(idx, AppearanceMode.entries.size)
                        ) {
                            Text(mode.label)
                        }
                    }
                }
            }
        }

        // Trip Freight & Diesel Profit Calculator
        item {
            AppleCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalGasStation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Trip Diesel & Freight Calculator", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = distanceKm,
                        onValueChange = { distanceKm = it },
                        label = { Text("Distance (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = mileageKmpl,
                        onValueChange = { mileageKmpl = it },
                        label = { Text("Avg (km/L)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dieselPrice,
                        onValueChange = { dieselPrice = it },
                        label = { Text("Diesel ₹/L") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tollAndBata,
                        onValueChange = { tollAndBata = it },
                        label = { Text("Toll + Driver ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = freightQuote,
                        onValueChange = { freightQuote = it },
                        label = { Text("Freight Quote ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Diesel + Trip Cost", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("₹${totalTripCost.toInt()} (₹${"%.1f".format(costPerKm)}/km)", style = MaterialTheme.typography.titleMedium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Estimated Net Profit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "₹${netTripProfit.toInt()}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (netTripProfit >= 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // GST & E-Way Bill Checker
        item {
            AppleCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GTA Freight GST & E-Way Helper", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = invoiceValue,
                    onValueChange = { invoiceValue = it },
                    label = { Text("Consignment / Freight Value ₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isRcm5Pct) "5% RCM (Reverse Charge)" else "12% FCM (Forward Charge)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isRcm5Pct, onCheckedChange = { isRcm5Pct = it })
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "SAC 996511 GST: ₹${gstTax.toInt()} · Total: ₹${(invAmt + gstTax).toInt()}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (needsEwayBill) "E-Way Bill Required (Consignment >= ₹50,000)" else "Below ₹50,000 E-Way threshold",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (needsEwayBill) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Fleet Axle & Payload Reference
        item {
            AppleCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Scale, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Standard Payload & Axle Guide", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                VehicleClass.entries.forEach { cls ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(cls.label, style = MaterialTheme.typography.titleMedium)
                            Text(cls.axlesLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(cls.maxPayloadTons, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Office Letterhead & 3-Day Notification Settings
        item {
            AppleCard {
                Text("Office Letterhead & 3-Day Alerts", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = { Text("Transport Company Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = gstin,
                        onValueChange = { gstin = it.uppercase() },
                        label = { Text("GSTIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Office Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("3-Day Early Challan & Insurance Alerts", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = alertsOn, onCheckedChange = { alertsOn = it })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.saveBusinessProfile(
                                    profile.copy(
                                        companyName = companyName,
                                        gstin = gstin,
                                        officeAddress = address,
                                        contactPhone = phone,
                                        notificationsEnabled = alertsOn
                                    )
                                )
                            }
                        ) {
                            Text("Save Office Info")
                        }
                        FilledTonalButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.triggerAlertNotificationsNow(context)
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Check Alerts")
                        }
                    }
                }
            }
        }

        // Account Sign Out
        item {
            AppleCard {
                val userEmail = Firebase.auth.currentUser?.email ?: "Signed in account"
                Text("Signed in as $userEmail", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { signOutUser(credentialManager, scope) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
