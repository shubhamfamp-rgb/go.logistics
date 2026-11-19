package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.DateUtil
import com.example.core.UiState
import com.example.data.RowStatus
import com.example.data.SheetCategory
import com.example.data.SheetRow
import com.example.data.TransportSheet
import com.example.ui.LogisticsViewModel
import com.example.ui.components.AppleCard
import com.example.ui.components.StatusBadge
import com.example.utils.ExportEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetsScreen(
    viewModel: LogisticsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetsState by viewModel.sheetsState.collectAsStateWithLifecycle()
    val rowsState by viewModel.activeRowsState.collectAsStateWithLifecycle()
    val selectedSheetId by viewModel.selectedSheetId.collectAsStateWithLifecycle()
    val scanInvoiceState by viewModel.scanInvoiceState.collectAsStateWithLifecycle()

    val sheets = (sheetsState as? UiState.Success)?.data ?: emptyList()
    val activeSheet = sheets.firstOrNull { it.id == selectedSheetId } ?: sheets.firstOrNull()
    val rows = (rowsState as? UiState.Success)?.data ?: emptyList()

    var searchQuery by remember { mutableStateOf("") }
    var showNewSheetDialog by remember { mutableStateOf(false) }
    var showPlugInDialog by remember { mutableStateOf(false) }
    var editingRow by remember { mutableStateOf<SheetRow?>(null) }
    var showExportMenu by remember { mutableStateOf(false) }

    val invoicePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val decoded = ExportEngine.decodeSampledBitmap(context, it, maxDim = 1600)
            if (decoded != null) {
                viewModel.scanInvoicePhoto(decoded.first)
            }
        }
    }

    val csvImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importCsvIntoActiveSheet(context, it) }
    }

    // Open extracted invoice confirmation when Gemini finishes
    LaunchedEffect(scanInvoiceState) {
        val st = scanInvoiceState
        if (st is UiState.Success && activeSheet != null) {
            val ext = st.data
            editingRow = SheetRow(
                sheetId = activeSheet.id,
                dateStr = ext.dateStr,
                vehicleNo = ext.vehicleNo,
                partyOrRoute = ext.partyOrRoute,
                billNo = ext.billNo,
                freightAmount = ext.freightAmount,
                expenseAmount = ext.expenseAmount,
                status = ext.status.name,
                notes = ext.notes,
                sourceLabel = "Bill Scan"
            )
            viewModel.clearInvoiceScan()
        }
    }

    val filteredRows = remember(rows, searchQuery) {
        if (searchQuery.isBlank()) rows
        else rows.filter {
            it.vehicleNo.contains(searchQuery, true) ||
                it.partyOrRoute.contains(searchQuery, true) ||
                it.billNo.contains(searchQuery, true) ||
                it.notes.contains(searchQuery, true)
        }
    }

    val totalFreight = filteredRows.sumOf { it.freightAmount }
    val totalExpense = filteredRows.sumOf { it.expenseAmount }
    val netTotal = totalFreight - totalExpense

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Excel Sheets & Ledgers",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Inline editing, photo bill capture & .xlsx export",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = { showNewSheetDialog = true },
                    modifier = Modifier.testTag("new_sheet_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sheet")
                }
            }
        }

        // Sheet Tabs
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sheets, key = { it.id }) { sheet ->
                    FilterChip(
                        selected = sheet.id == activeSheet?.id,
                        onClick = { viewModel.selectSheet(sheet.id) },
                        label = { Text(sheet.name) }
                    )
                }
            }
        }

        // Toolbar: Scan Bill -> Excel, Add Row, Connect Excel/Plugins, Import CSV, Export XLSX/CSV
        item {
            AppleCard {
                Text(
                    text = activeSheet?.name ?: "No sheet selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (activeSheet?.syncEnabled == true && activeSheet.syncPlugUrl.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Connected link: ${activeSheet.syncPlugUrl}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Button(
                            onClick = {
                                if (activeSheet == null) {
                                    showNewSheetDialog = true
                                } else {
                                    editingRow = SheetRow(
                                        sheetId = activeSheet.id,
                                        dateStr = DateUtil.todayIso()
                                    )
                                }
                            },
                            modifier = Modifier.testTag("add_row_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Add Row")
                        }
                    }
                    item {
                        FilledTonalButton(
                            onClick = {
                                invoicePhotoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = activeSheet != null && scanInvoiceState !is UiState.Loading,
                            modifier = Modifier.testTag("scan_invoice_button")
                        ) {
                            if (scanInvoiceState is UiState.Loading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reading Bill...")
                            } else {
                                Icon(Icons.Rounded.DocumentScanner, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Bill Photo -> Sheet")
                            }
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { showPlugInDialog = true },
                            enabled = activeSheet != null
                        ) {
                            Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Connect Excel")
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
                            },
                            enabled = activeSheet != null
                        ) {
                            Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Import CSV")
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { showExportMenu = true },
                            enabled = activeSheet != null
                        ) {
                            Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Export .xlsx")
                        }
                    }
                }

                if (scanInvoiceState is UiState.Error) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = (scanInvoiceState as UiState.Error).message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search vehicle no, route, bill no...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Interactive Excel Grid
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Spreadsheet Grid (Tap any row to edit)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${filteredRows.size} rows",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    val hScroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(hScroll)
                    ) {
                        // Header Row
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GridHeaderCell("Date", 104)
                            GridHeaderCell("Vehicle No", 118)
                            GridHeaderCell("Party / Route", 190)
                            GridHeaderCell("LR / Bill", 96)
                            GridHeaderCell("Freight ₹", 100)
                            GridHeaderCell("Expense ₹", 100)
                            GridHeaderCell("Status", 108)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                        if (filteredRows.isEmpty()) {
                            Text(
                                text = "No entries yet. Tap '+ Add Row' or 'Bill Photo -> Sheet'.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(18.dp)
                            )
                        } else {
                            filteredRows.forEach { row ->
                                val status = RowStatus.fromRaw(row.status)
                                val statusColor = when (status) {
                                    RowStatus.PAID -> MaterialTheme.colorScheme.secondary
                                    RowStatus.BILLED -> MaterialTheme.colorScheme.primary
                                    RowStatus.IN_TRANSIT -> MaterialTheme.colorScheme.tertiary
                                    RowStatus.PENDING -> MaterialTheme.colorScheme.error
                                }
                                Row(
                                    modifier = Modifier
                                        .clickable { editingRow = row }
                                        .padding(horizontal = 12.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    GridValueCell(row.dateStr, 104, isMono = true)
                                    GridValueCell(row.vehicleNo.ifBlank { "-" }, 118, isMono = true, bold = true)
                                    GridValueCell(row.partyOrRoute, 190)
                                    GridValueCell(row.billNo.ifBlank { "-" }, 96, isMono = true)
                                    GridValueCell("%.0f".format(row.freightAmount), 100, isMono = true)
                                    GridValueCell("%.0f".format(row.expenseAmount), 100, isMono = true)
                                    Box(modifier = Modifier.width(108.dp)) {
                                        StatusBadge(text = status.label, color = statusColor)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                            }
                        }

                        // Formula Summary Footer
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GridHeaderCell("TOTAL", 104)
                            GridHeaderCell("", 118)
                            GridHeaderCell("Net Margin: ₹%.0f".format(netTotal), 190)
                            GridHeaderCell("", 96)
                            GridHeaderCell("₹%.0f".format(totalFreight), 100)
                            GridHeaderCell("₹%.0f".format(totalExpense), 100)
                            GridHeaderCell("", 108)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showNewSheetDialog) {
        NewSheetDialog(
            onDismiss = { showNewSheetDialog = false },
            onCreate = { name, cat, url ->
                viewModel.createSheet(name, cat, url)
                showNewSheetDialog = false
            }
        )
    }

    if (showPlugInDialog && activeSheet != null) {
        ConnectExcelPluginDialog(
            sheet = activeSheet,
            onDismiss = { showPlugInDialog = false },
            onSave = { newName, url ->
                viewModel.updateSheetConnection(activeSheet.id, newName, url)
                showPlugInDialog = false
            }
        )
    }

    if (showExportMenu && activeSheet != null) {
        AlertDialog(
            onDismissRequest = { showExportMenu = false },
            title = { Text("Export Workbook") },
            text = {
                Text("Choose an export format for '${activeSheet.name}' (${rows.size} rows).")
            },
            confirmButton = {
                Button(onClick = {
                    val file = ExportEngine.exportSheetToXlsxFile(context, activeSheet, rows)
                    ExportEngine.shareFile(
                        context,
                        file,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "Export ${activeSheet.name}.xlsx"
                    )
                    showExportMenu = false
                }) {
                    Text("Excel (.xlsx)")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val file = ExportEngine.exportSheetToCsvFile(context, activeSheet, rows)
                        ExportEngine.shareFile(context, file, "text/csv", "Export ${activeSheet.name}.csv")
                        showExportMenu = false
                    }) {
                        Text("CSV (.csv)")
                    }
                    TextButton(onClick = { showExportMenu = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    editingRow?.let { row ->
        EditRowDialog(
            initial = row,
            onDismiss = { editingRow = null },
            onSave = { updated ->
                viewModel.saveRow(row.sheetId.ifBlank { activeSheet?.id ?: "" }, updated)
                editingRow = null
            },
            onDelete = if (row.id.isNotBlank()) {
                {
                    viewModel.deleteRow(row.sheetId, row.id)
                    editingRow = null
                }
            } else null
        )
    }
}

@Composable
private fun GridHeaderCell(text: String, widthDp: Int) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(widthDp.dp)
    )
}

@Composable
private fun GridValueCell(
    text: String,
    widthDp: Int,
    isMono: Boolean = false,
    bold: Boolean = false
) {
    Text(
        text = text,
        style = if (isMono) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(widthDp.dp)
            .padding(end = 8.dp)
    )
}

@Composable
private fun NewSheetDialog(
    onDismiss: () -> Unit,
    onCreate: (String, SheetCategory, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var syncUrl by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SheetCategory.FREIGHT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Transport Sheet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Sheet Name") },
                    placeholder = { Text("e.g. December Fleet Ledger") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SheetCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = syncUrl,
                    onValueChange = { syncUrl = it },
                    label = { Text("Connected Excel / Sheet URL (Optional)") },
                    placeholder = { Text("https://docs.google.com/spreadsheets/...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name, category, syncUrl) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ConnectExcelPluginDialog(
    sheet: TransportSheet,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember(sheet.id) { mutableStateOf(sheet.name) }
    var syncUrl by remember(sheet.id) { mutableStateOf(sheet.syncPlugUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Excel & Sheet Connectors") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Link this live register to an external Microsoft Excel Online or Google Sheets URL, and manage built-in bill capture plugins.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Workbook Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = syncUrl,
                    onValueChange = { syncUrl = it },
                    label = { Text("External Excel / Google Sheet Link") },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AppleCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                    Text(
                        text = "Active Plugins",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Bill Photo to Sheet Extractor\n• OpenXML (.xlsx) Native Workbook Exporter\n• CSV Bulk Row Importer",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, syncUrl) },
                enabled = name.isNotBlank()
            ) { Text("Save Connection") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRowDialog(
    initial: SheetRow,
    onDismiss: () -> Unit,
    onSave: (SheetRow) -> Unit,
    onDelete: (() -> Unit)?
) {
    var dateStr by remember(initial) { mutableStateOf(initial.dateStr.ifBlank { DateUtil.todayIso() }) }
    var vehicleNo by remember(initial) { mutableStateOf(initial.vehicleNo) }
    var partyOrRoute by remember(initial) { mutableStateOf(initial.partyOrRoute) }
    var billNo by remember(initial) { mutableStateOf(initial.billNo) }
    var freightStr by remember(initial) { mutableStateOf(if (initial.freightAmount > 0) "%.0f".format(initial.freightAmount) else "") }
    var expenseStr by remember(initial) { mutableStateOf(if (initial.expenseAmount > 0) "%.0f".format(initial.expenseAmount) else "") }
    var status by remember(initial) { mutableStateOf(RowStatus.fromRaw(initial.status)) }
    var notes by remember(initial) { mutableStateOf(initial.notes) }
    var statusExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (initial.id.isBlank()) "Add Ledger Entry" else "Edit Ledger Row")
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete row",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateStr,
                        onValueChange = { dateStr = it },
                        label = { Text("Date") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = vehicleNo,
                        onValueChange = { vehicleNo = it.uppercase() },
                        label = { Text("Vehicle No") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = partyOrRoute,
                    onValueChange = { partyOrRoute = it },
                    label = { Text("Party / Route *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = billNo,
                    onValueChange = { billNo = it },
                    label = { Text("LR / Bill No") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = freightStr,
                        onValueChange = { freightStr = it },
                        label = { Text("Freight ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = expenseStr,
                        onValueChange = { expenseStr = it },
                        label = { Text("Expense ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = statusExpanded,
                    onExpandedChange = { statusExpanded = !statusExpanded }
                ) {
                    OutlinedTextField(
                        value = status.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Payment / Trip Status") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false }
                    ) {
                        RowStatus.entries.forEach { st ->
                            DropdownMenuItem(
                                text = { Text(st.label) },
                                onClick = {
                                    status = st
                                    statusExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Remarks") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            dateStr = dateStr,
                            vehicleNo = vehicleNo,
                            partyOrRoute = partyOrRoute,
                            billNo = billNo,
                            freightAmount = freightStr.toDoubleOrNull() ?: 0.0,
                            expenseAmount = expenseStr.toDoubleOrNull() ?: 0.0,
                            status = status.name,
                            notes = notes
                        )
                    )
                },
                enabled = partyOrRoute.isNotBlank()
            ) {
                Text("Save Row")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
