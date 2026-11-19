package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.DateUtil
import com.example.core.ReminderNotificationManager
import com.example.core.UiState
import com.example.data.GarageVehicle
import com.example.data.VehicleClass
import com.example.ui.LogisticsViewModel
import com.example.ui.components.AppleCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.Vehicle3DShowcase
import com.example.utils.ExportEngine

@Composable
fun GarageScreen(
    viewModel: LogisticsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vehiclesState by viewModel.vehiclesState.collectAsStateWithLifecycle()
    val lookupResult by viewModel.vehicleLookupState.collectAsStateWithLifecycle()
    val isLookingUp by viewModel.isLookingUpPlate.collectAsStateWithLifecycle()
    val scannedVehicleState by viewModel.scannedVehicleState.collectAsStateWithLifecycle()
    val alerts by viewModel.upcomingAlerts.collectAsStateWithLifecycle()

    val vehicles = (vehiclesState as? UiState.Success)?.data ?: emptyList()

    var plateQuery by remember { mutableStateOf("") }
    var selected3DClass by remember { mutableStateOf(VehicleClass.TRUCK) }
    var selected3DPlate by remember { mutableStateOf("MH12PQ4821") }
    var editingVehicle by remember { mutableStateOf<GarageVehicle?>(null) }

    val rcDocPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val decoded = ExportEngine.decodeSampledBitmap(context, it, maxDim = 1600)
            if (decoded != null) {
                viewModel.scanVehicleDocPhoto(decoded.first)
            }
        }
    }

    LaunchedEffect(scannedVehicleState) {
        val st = scannedVehicleState
        if (st is UiState.Success) {
            editingVehicle = st.data
            viewModel.clearScannedVehicle()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Garage & Vehicle Info",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "3D fleet models, RTO lookup & 3-day early alerts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = { editingVehicle = GarageVehicle(alertsEnabled = true) },
                    modifier = Modifier.testTag("add_vehicle_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }

        // Vehicle Info Web Lookup Card
        item {
            AppleCard {
                Text(
                    text = "Vehicle Info Lookup",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Enter any plate like MH12PQ4821 or MHXXA0000 to decode RTO data, open official Parivahan web records, or scan an RC/Challan screenshot.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = plateQuery,
                        onValueChange = { plateQuery = it.uppercase() },
                        placeholder = { Text("e.g. MH12PQ4821") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("plate_lookup_input")
                    )
                    Button(
                        onClick = { viewModel.lookupVehiclePlate(plateQuery) },
                        enabled = plateQuery.trim().length >= 4 && !isLookingUp,
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("plate_lookup_button")
                    ) {
                        if (isLookingUp) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Rounded.Search, contentDescription = "Lookup plate")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        rcDocPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = scannedVehicleState !is UiState.Loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (scannedVehicleState is UiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reading RC / Challan Document...")
                    } else {
                        Icon(Icons.Rounded.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import from RC / Vahan / Insurance Photo")
                    }
                }

                if (scannedVehicleState is UiState.Error) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = (scannedVehicleState as UiState.Error).message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                lookupResult?.let { res ->
                    Spacer(modifier = Modifier.height(14.dp))
                    AppleCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = res.plateNumber,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            StatusBadge(
                                text = if (res.isValidFormat) "Valid Format" else "Check Format",
                                color = if (res.isValidFormat) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("State: ${res.stateName}", style = MaterialTheme.typography.bodyMedium)
                        Text("RTO Office: ${res.rtoOffice}", style = MaterialTheme.typography.bodyMedium)
                        Text("Notes: ${res.registrationSeriesNote}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(res.liveWebStatus, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                Button(onClick = {
                                    selected3DClass = res.suggestedClass
                                    selected3DPlate = res.plateNumber
                                    editingVehicle = GarageVehicle(
                                        plateNumber = res.plateNumber,
                                        vehicleClass = res.suggestedClass.name,
                                        rtoOffice = res.rtoOffice,
                                        alertsEnabled = true
                                    )
                                }) {
                                    Text("Add to Garage")
                                }
                            }
                            item {
                                FilledTonalButton(onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(res.parivahanPortalUrl)))
                                }) {
                                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Parivahan Vahan")
                                }
                            }
                            item {
                                OutlinedButton(onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(res.echallanPortalUrl)))
                                }) {
                                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("e-Challan Web")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Interactive 3D Models Switcher + 3D Studio
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "3D Vehicle Models",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(VehicleClass.entries) { vClass ->
                        FilterChip(
                            selected = selected3DClass == vClass,
                            onClick = { selected3DClass = vClass },
                            label = { Text(vClass.label) }
                        )
                    }
                }
                Vehicle3DShowcase(
                    vehicleClass = selected3DClass,
                    plateNumber = selected3DPlate
                )
            }
        }

        // Saved Garage Watchlist
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Garage Watchlist (${vehicles.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (alerts.isNotEmpty()) {
                    StatusBadge(
                        text = "${alerts.size} Alert(s)",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (vehicles.isEmpty()) {
            item {
                AppleCard {
                    Text(
                        text = "Your garage is empty",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add vehicles above to track pending challans, RTC/RTO permit expiry, and insurance renewal dates with automatic 3-day early reminders.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(vehicles, key = { it.id }) { vehicle ->
                val vClass = VehicleClass.fromRaw(vehicle.vehicleClass)
                val vAlerts = ReminderNotificationManager.evaluateUpcomingAlerts(listOf(vehicle))

                AppleCard(
                    onClick = {
                        selected3DClass = vClass
                        selected3DPlate = vehicle.plateNumber
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.DirectionsCar,
                                contentDescription = vClass.label,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = vehicle.plateNumber,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${vehicle.nickname.ifBlank { vClass.label }} · ${vClass.label}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row {
                            IconButton(onClick = { editingVehicle = vehicle }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit vehicle")
                            }
                        }
                    }

                    if (vehicle.rtoOffice.isNotBlank() || vehicle.ownerName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${vehicle.ownerName.ifBlank { "Registered Unit" }} · ${vehicle.rtoOffice.ifBlank { "RTO" }} (${vehicle.fuelType})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ComplianceChip(
                            label = "Challan",
                            value = if (vehicle.challanCount > 0) "${vehicle.challanCount} (₹${vehicle.challanAmount.toInt()})" else "Clear",
                            dueDate = vehicle.challanDueDate,
                            isWarning = vAlerts.any { it.id.endsWith("challan") },
                            modifier = Modifier.weight(1f)
                        )
                        ComplianceChip(
                            label = "RTC / RTO",
                            value = vehicle.rtcPermitExpiry.ifBlank { "Not Set" },
                            dueDate = "",
                            isWarning = vAlerts.any { it.id.endsWith("rtc") },
                            modifier = Modifier.weight(1f)
                        )
                        ComplianceChip(
                            label = "Insurance",
                            value = if (vehicle.insurancePremium > 0) "₹${vehicle.insurancePremium.toInt()}" else "Active",
                            dueDate = vehicle.insuranceDueDate,
                            isWarning = vAlerts.any { it.id.endsWith("insurance") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (vAlerts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = vAlerts.first().detail,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    editingVehicle?.let { v ->
        VehicleEditDialog(
            initial = v,
            onDismiss = { editingVehicle = null },
            onSave = { updated ->
                viewModel.saveVehicle(updated)
                selected3DClass = VehicleClass.fromRaw(updated.vehicleClass)
                selected3DPlate = updated.plateNumber
                editingVehicle = null
            },
            onDelete = if (v.id.isNotBlank()) {
                {
                    viewModel.deleteVehicle(v.id)
                    editingVehicle = null
                }
            } else null
        )
    }
}

@Composable
private fun ComplianceChip(
    label: String,
    value: String,
    dueDate: String,
    isWarning: Boolean,
    modifier: Modifier = Modifier
) {
    val bg = if (isWarning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val fg = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = fg
        )
        if (dueDate.isNotBlank()) {
            Text(
                text = dueDate,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VehicleEditDialog(
    initial: GarageVehicle,
    onDismiss: () -> Unit,
    onSave: (GarageVehicle) -> Unit,
    onDelete: (() -> Unit)?
) {
    var plate by remember(initial) { mutableStateOf(initial.plateNumber) }
    var vClass by remember(initial) { mutableStateOf(VehicleClass.fromRaw(initial.vehicleClass)) }
    var nickname by remember(initial) { mutableStateOf(initial.nickname) }
    var ownerName by remember(initial) { mutableStateOf(initial.ownerName) }
    var rtoOffice by remember(initial) { mutableStateOf(initial.rtoOffice) }
    var fuelType by remember(initial) { mutableStateOf(initial.fuelType.ifBlank { "Diesel" }) }
    var challanCount by remember(initial) { mutableStateOf(if (initial.challanCount > 0) initial.challanCount.toString() else "0") }
    var challanAmount by remember(initial) { mutableStateOf(if (initial.challanAmount > 0) initial.challanAmount.toInt().toString() else "0") }
    var challanDueDate by remember(initial) { mutableStateOf(initial.challanDueDate) }
    var rtcExpiry by remember(initial) { mutableStateOf(initial.rtcPermitExpiry) }
    var insuranceProvider by remember(initial) { mutableStateOf(initial.insuranceProvider) }
    var insurancePremium by remember(initial) { mutableStateOf(if (initial.insurancePremium > 0) initial.insurancePremium.toInt().toString() else "") }
    var insuranceDueDate by remember(initial) { mutableStateOf(initial.insuranceDueDate) }
    var alertsEnabled by remember(initial) { mutableStateOf(initial.alertsEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (initial.id.isBlank()) "Add Vehicle to Garage" else "Edit Vehicle")
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete vehicle", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = plate,
                    onValueChange = { plate = it.uppercase() },
                    label = { Text("Registration Plate *") },
                    placeholder = { Text("MH12PQ4821") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("3D Vehicle Class", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(VehicleClass.entries) { cls ->
                        FilterChip(
                            selected = vClass == cls,
                            onClick = { vClass = cls },
                            label = { Text(cls.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Model / Fleet Nickname") },
                    placeholder = { Text("e.g. BharatBenz 2823R") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = { Text("Owner") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fuelType,
                        onValueChange = { fuelType = it },
                        label = { Text("Fuel") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }
                OutlinedTextField(
                    value = rtoOffice,
                    onValueChange = { rtoOffice = it },
                    label = { Text("RTO Office") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Compliance & 3-Day Early Reminders (YYYY-MM-DD)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = challanCount,
                        onValueChange = { challanCount = it },
                        label = { Text("Challans") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                    OutlinedTextField(
                        value = challanAmount,
                        onValueChange = { challanAmount = it },
                        label = { Text("Fine ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = challanDueDate,
                    onValueChange = { challanDueDate = it },
                    label = { Text("Challan Due Date (YYYY-MM-DD)") },
                    placeholder = { Text(DateUtil.plusDaysIso(2)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rtcExpiry,
                    onValueChange = { rtcExpiry = it },
                    label = { Text("RTC / RTO Permit Expiry (YYYY-MM-DD)") },
                    placeholder = { Text(DateUtil.plusDaysIso(3)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = insuranceProvider,
                        onValueChange = { insuranceProvider = it },
                        label = { Text("Insurer") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = insurancePremium,
                        onValueChange = { insurancePremium = it },
                        label = { Text("Premium ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.9f)
                    )
                }
                OutlinedTextField(
                    value = insuranceDueDate,
                    onValueChange = { insuranceDueDate = it },
                    label = { Text("Insurance Due Date (YYYY-MM-DD)") },
                    placeholder = { Text(DateUtil.plusDaysIso(3)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Notify 3 days before due dates", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = alertsEnabled, onCheckedChange = { alertsEnabled = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            plateNumber = plate,
                            vehicleClass = vClass.name,
                            nickname = nickname,
                            ownerName = ownerName,
                            rtoOffice = rtoOffice,
                            fuelType = fuelType,
                            challanCount = challanCount.toLongOrNull() ?: 0L,
                            challanAmount = challanAmount.toDoubleOrNull() ?: 0.0,
                            challanDueDate = challanDueDate,
                            rtcPermitExpiry = rtcExpiry,
                            insuranceProvider = insuranceProvider,
                            insurancePremium = insurancePremium.toDoubleOrNull() ?: 0.0,
                            insuranceDueDate = insuranceDueDate,
                            alertsEnabled = alertsEnabled
                        )
                    )
                },
                enabled = plate.trim().length >= 4
            ) {
                Text("Save Vehicle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
