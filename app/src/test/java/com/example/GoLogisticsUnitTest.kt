package com.example

import com.example.core.DateUtil
import com.example.core.ReminderNotificationManager
import com.example.data.ComplianceAlertType
import com.example.data.GarageVehicle
import com.example.data.RtoWebLookupService
import com.example.data.VehicleClass
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoLogisticsUnitTest {

    @Test
    fun `early alert fires within 3 days for challan, rtc permit, and insurance`() {
        val dueIn3Days = DateUtil.plusDaysIso(3)
        val dueIn10Days = DateUtil.plusDaysIso(10)

        val vehicleAlerting = GarageVehicle(
            id = "v1",
            plateNumber = "MH12PQ4821",
            vehicleClass = VehicleClass.TRUCK.name,
            challanCount = 1,
            challanAmount = 1500.0,
            challanDueDate = dueIn3Days,
            rtcPermitExpiry = dueIn3Days,
            insuranceProvider = "New India Assurance",
            insurancePremium = 28000.0,
            insuranceDueDate = dueIn3Days,
            alertsEnabled = true
        )

        val vehicleFarOut = GarageVehicle(
            id = "v2",
            plateNumber = "MH14JK8810",
            vehicleClass = VehicleClass.LONG_VEHICLE.name,
            challanCount = 0,
            challanAmount = 0.0,
            challanDueDate = dueIn10Days,
            rtcPermitExpiry = dueIn10Days,
            insuranceProvider = "ICICI Lombard",
            insurancePremium = 35000.0,
            insuranceDueDate = dueIn10Days,
            alertsEnabled = true
        )

        val alerts = ReminderNotificationManager.evaluateUpcomingAlerts(listOf(vehicleAlerting, vehicleFarOut))
        assertEquals(3, alerts.size)
        assertTrue(alerts.any { it.type == ComplianceAlertType.CHALLAN })
        assertTrue(alerts.any { it.type == ComplianceAlertType.RTC_PERMIT })
        assertTrue(alerts.any { it.type == ComplianceAlertType.INSURANCE })
        assertFalse(alerts.any { it.vehicleId == "v2" })
    }

    @Test
    fun `rto plate lookup decodes Maharashtra Pune and template plates`() = runTest {
        val service = RtoWebLookupService()
        val pune = service.lookupPlate("MH12PQ4821")
        assertEquals("MH12PQ4821", pune.plateNumber)
        assertTrue(pune.isValidFormat)
        assertEquals("Maharashtra", pune.stateName)
        assertTrue(pune.rtoOffice.contains("Pune"))

        val sample = service.lookupPlate("MHXXA0000")
        assertEquals("MHXXA0000", sample.plateNumber)
        assertEquals("Maharashtra", sample.stateName)
    }
}
