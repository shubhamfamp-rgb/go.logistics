package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.data.ComplianceAlert
import com.example.data.GarageVehicle
import com.example.data.RowStatus
import com.example.data.SheetRow
import com.example.data.TransportSheet
import com.example.ui.LogisticsViewModel
import com.example.ui.components.AppleCard
import com.example.ui.components.MetricPillCard
import com.example.ui.components.SereneCashflowChart
import com.example.ui.components.StatusBadge
import java.text.NumberFormat
import java.util.Locale

@Composable
fun OverviewScreen(
    viewModel: LogisticsViewModel,
    onNavigateToTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetsState by viewModel.sheetsState.collectAsStateWithLifecycle()
    val rowsState by viewModel.activeRowsState.collectAsStateWithLifecycle()
    val vehiclesState by viewModel.vehiclesState.collectAsStateWithLifecycle()
    val alerts by viewModel.upcomingAlerts.collectAsStateWithLifecycle()
    val profile by viewModel.businessProfile.collectAsStateWithLifecycle()

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.triggerAlertNotificationsNow(context)
        }
    }

    val sheets = (sheetsState as? UiState.Success)?.data ?: emptyList()
    val rows = (rowsState as? UiState.Success)?.data ?: emptyList()
    val vehicles = (vehiclesState as? UiState.Success)?.data ?: emptyList()

    val totalFreight = rows.sumOf { it.freightAmount }
    val totalExpense = rows.sumOf { it.expenseAmount }
    val netMargin = totalFreight - totalExpense
    val pendingCount = rows.count { it.status == RowStatus.PENDING.name || it.status == RowStatus.IN_TRANSIT.name }

    val inrFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
    }

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
                    text = profile.companyName.ifBlank { "go.logistics" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Fleet & Ledger Overview",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Live figures synced from your active transport sheet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (sheetsState is UiState.Loading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        if (sheetsState is UiState.Success && sheets.isEmpty() && vehicles.isEmpty()) {
            item {
                AppleCard {
                    Text(
                        text = "Welcome to go.logistics",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Create your first transport ledger or load a ready-to-edit office register with Pune/Mumbai freight routes and fleet compliance dates.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { viewModel.loadStarterTemplate() },
                            modifier = Modifier.testTag("load_starter_button")
                        ) {
                            Text("Initialize Office Ledger")
                        }
                        OutlinedButton(onClick = { onNavigateToTab("sheets") }) {
                            Text("Open Blank Sheet")
                        }
                    }
                }
            }
        }

        // 3-Day Early Compliance Alert Banner
        if (alerts.isNotEmpty()) {
            item {
                ComplianceAlertCard(
                    alerts = alerts,
                    onNotifyNow = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.triggerAlertNotificationsNow(context)
                        }
                    },
                    onOpenGarage = { onNavigateToTab("garage") }
                )
            }
        }

        // Live KPI Grid (2x2)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricPillCard(
                        title = "Freight Billed",
                        value = inrFormat.format(totalFreight),
                        subtitle = "${rows.size} trips in sheet",
                        icon = Icons.AutoMirrored.Rounded.TrendingUp,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricPillCard(
                        title = "Diesel & Toll",
                        value = inrFormat.format(totalExpense),
                        subtitle = "$pendingCount active/pending",
                        icon = Icons.Rounded.AccountBalanceWallet,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricPillCard(
                        title = "Net Margin",
                        value = inrFormat.format(netMargin),
                        subtitle = if (totalFreight > 0) "${((netMargin / totalFreight) * 100).toInt()}% operating margin" else "Real-time calculation",
                        icon = Icons.Rounded.GridOn,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricPillCard(
                        title = "Garage Watch",
                        value = "${vehicles.size} Units",
                        subtitle = "${alerts.size} alerts (<=3 days)",
                        icon = Icons.Rounded.DirectionsCar,
                        accentColor = if (alerts.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Smooth Cashflow Curve Card
        if (rows.isNotEmpty()) {
            item {
                AppleCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Live Sheet Cashflow",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Freight vs Trip Expenses (latest entries)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusBadge(
                            text = "Synced",
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    val ordered = rows.reversed().takeLast(8)
                    SereneCashflowChart(
                        freightSeries = ordered.map { it.freightAmount },
                        expenseSeries = ordered.map { it.expenseAmount }
                    )
                }
            }
        }

        // Quick Actions Carousel
        item {
            Text(
                text = "Quick Workspace Actions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    QuickActionTile(
                        icon = Icons.Rounded.Add,
                        label = "Edit Sheets",
                        onClick = { onNavigateToTab("sheets") }
                    )
                }
                item {
                    QuickActionTile(
                        icon = Icons.Rounded.DocumentScanner,
                        label = "Bill to Excel",
                        onClick = { onNavigateToTab("sheets") }
                    )
                }
                item {
                    QuickActionTile(
                        icon = Icons.Rounded.DirectionsCar,
                        label = "Plate Lookup",
                        onClick = { onNavigateToTab("garage") }
                    )
                }
                item {
                    QuickActionTile(
                        icon = Icons.Rounded.PictureAsPdf,
                        label = "PDF & Photos",
                        onClick = { onNavigateToTab("studio") }
                    )
                }
            }
        }

        // Recent Entries Preview
        if (rows.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Ledger Updates",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    FilledTonalButton(onClick = { onNavigateToTab("sheets") }) {
                        Text("View All")
                    }
                }
            }

            items(rows.take(5), key = { it.id }) { row ->
                RecentRowItem(row = row, inrFormat = inrFormat)
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun ComplianceAlertCard(
    alerts: List<ComplianceAlert>,
    onNotifyNow: () -> Unit,
    onOpenGarage: () -> Unit
) {
    AppleCard(
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = "Upcoming compliance alert",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "3-Day Early Fleet Alerts (${alerts.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        alerts.take(3).forEach { alert ->
            Text(
                text = "• ${alert.title}: ${alert.detail}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = onNotifyNow) {
                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Push Alert")
            }
            OutlinedButton(onClick = onOpenGarage) {
                Text("Open Garage")
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun RecentRowItem(
    row: SheetRow,
    inrFormat: NumberFormat
) {
    val status = RowStatus.fromRaw(row.status)
    val badgeColor = when (status) {
        RowStatus.PAID -> MaterialTheme.colorScheme.secondary
        RowStatus.BILLED -> MaterialTheme.colorScheme.primary
        RowStatus.IN_TRANSIT -> MaterialTheme.colorScheme.tertiary
        RowStatus.PENDING -> MaterialTheme.colorScheme.error
    }

    AppleCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (row.vehicleNo.isNotBlank()) {
                        Text(
                            text = row.vehicleNo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = row.dateStr,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = row.partyOrRoute,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (row.notes.isNotBlank()) {
                    Text(
                        text = row.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = inrFormat.format(row.freightAmount - row.expenseAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                StatusBadge(text = status.label, color = badgeColor)
            }
        }
    }
}
