package com.example.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.ComplianceAlert
import com.example.data.ComplianceAlertType
import com.example.data.GarageVehicle

object ReminderNotificationManager {

    private const val CHANNEL_ID = "go_logistics_compliance_alerts"
    private const val CHANNEL_NAME = "Fleet Compliance & Due Alerts"
    private const val CHANNEL_DESC = "3-day early reminders for Challans, RTC permits, and Insurance renewals."

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun evaluateUpcomingAlerts(
        vehicles: List<GarageVehicle>
    ): List<ComplianceAlert> {
        val alerts = mutableListOf<ComplianceAlert>()
        for (vehicle in vehicles) {
            if (!vehicle.alertsEnabled) continue

            DateUtil.daysUntil(vehicle.challanDueDate)?.let { daysRemaining ->
                if (daysRemaining <= 3L && (vehicle.challanCount > 0 || vehicle.challanAmount > 0)) {
                    alerts.add(
                        ComplianceAlert(
                            id = "${vehicle.id}_challan",
                            vehicleId = vehicle.id,
                            plateNumber = vehicle.plateNumber,
                            type = ComplianceAlertType.CHALLAN,
                            dueDateStr = vehicle.challanDueDate,
                            daysRemaining = daysRemaining,
                            amount = vehicle.challanAmount,
                            title = "${vehicle.plateNumber} · Challan Due",
                            detail = formatUrgency(
                                daysRemaining,
                                "${vehicle.challanCount} challan(s) totaling Rs ${vehicle.challanAmount.toInt()}"
                            )
                        )
                    )
                }
            }

            DateUtil.daysUntil(vehicle.rtcPermitExpiry)?.let { daysRemaining ->
                if (daysRemaining <= 3L) {
                    alerts.add(
                        ComplianceAlert(
                            id = "${vehicle.id}_rtc",
                            vehicleId = vehicle.id,
                            plateNumber = vehicle.plateNumber,
                            type = ComplianceAlertType.RTC_PERMIT,
                            dueDateStr = vehicle.rtcPermitExpiry,
                            daysRemaining = daysRemaining,
                            amount = 0.0,
                            title = "${vehicle.plateNumber} · RTC / Permit Renewal",
                            detail = formatUrgency(daysRemaining, "RTO fitness & permit renewal")
                        )
                    )
                }
            }

            DateUtil.daysUntil(vehicle.insuranceDueDate)?.let { daysRemaining ->
                if (daysRemaining <= 3L) {
                    val provider = vehicle.insuranceProvider.ifBlank { "Motor Insurance" }
                    val amtText = if (vehicle.insurancePremium > 0) {
                        " (Premium Rs ${vehicle.insurancePremium.toInt()})"
                    } else ""
                    alerts.add(
                        ComplianceAlert(
                            id = "${vehicle.id}_insurance",
                            vehicleId = vehicle.id,
                            plateNumber = vehicle.plateNumber,
                            type = ComplianceAlertType.INSURANCE,
                            dueDateStr = vehicle.insuranceDueDate,
                            daysRemaining = daysRemaining,
                            amount = vehicle.insurancePremium,
                            title = "${vehicle.plateNumber} · Insurance Due",
                            detail = formatUrgency(daysRemaining, "$provider$amtText")
                        )
                    )
                }
            }
        }
        return alerts.sortedBy { it.daysRemaining }
    }

    fun dispatchAlertsIfPermitted(context: Context, alerts: List<ComplianceAlert>): Int {
        if (alerts.isEmpty()) return 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return 0
        }
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        var sentCount = 0
        alerts.take(5).forEachIndexed { index, alert ->
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(alert.title)
                .setContentText(alert.detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(alert.detail))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            manager.notify(4100 + index, notification)
            sentCount++
        }
        return sentCount
    }

    private fun formatUrgency(daysRemaining: Long, subject: String): String {
        return when {
            daysRemaining < 0 -> "$subject is overdue by ${-daysRemaining} day(s)."
            daysRemaining == 0L -> "$subject is due today."
            daysRemaining == 1L -> "$subject is due tomorrow (1 day left)."
            else -> "$subject is due in $daysRemaining days (3-day early alert)."
        }
    }
}
