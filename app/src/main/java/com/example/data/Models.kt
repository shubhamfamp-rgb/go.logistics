package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AppearanceMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class SheetCategory(val label: String) {
    FREIGHT("Freight"),
    EXPENSE("Expense"),
    BILLING("Billing"),
    CUSTOM("Custom");

    companion object {
        fun fromRaw(raw: String): SheetCategory =
            entries.firstOrNull { it.name == raw } ?: FREIGHT
    }
}

enum class RowStatus(val label: String) {
    PAID("Paid"),
    PENDING("Pending"),
    IN_TRANSIT("In Transit"),
    BILLED("Billed");

    companion object {
        fun fromRaw(raw: String): RowStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class VehicleClass(val label: String, val axlesLabel: String, val maxPayloadTons: String) {
    LMV("LMV", "4 Wheels · Light", "1.5 – 3.5 Tons"),
    BIKE("Bike", "2 Wheels · Express", "0.15 Tons"),
    BUS("Bus", "4–6 Wheels · Coach", "52 Seats"),
    TRUCK("Truck", "6–10 Wheels · Rigid", "16 – 28 Tons"),
    LONG_VEHICLE("Long Vehicle", "14–18 Wheels · Trailer", "35 – 49 Tons");

    companion object {
        fun fromRaw(raw: String): VehicleClass =
            entries.firstOrNull { it.name == raw } ?: TRUCK
    }
}

enum class DocType(val label: String) {
    FREIGHT_INVOICE("Freight Invoice"),
    OFFICIAL_STATEMENT("Official Statement"),
    POD_BILL("POD & Photo Bill");

    companion object {
        fun fromRaw(raw: String): DocType =
            entries.firstOrNull { it.name == raw } ?: FREIGHT_INVOICE
    }
}

@Keep
@IgnoreExtraProperties
data class TransportSheet(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val category: String = SheetCategory.FREIGHT.name,
    val syncPlugUrl: String = "",
    val syncEnabled: Boolean = false,
    val columns: List<String> = listOf("Date", "Vehicle No", "Route / Party", "LR / Bill", "Freight", "Expense", "Status"),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SheetRow(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sheetId: String = "",
    val dateStr: String = "",
    val vehicleNo: String = "",
    val partyOrRoute: String = "",
    val billNo: String = "",
    val freightAmount: Double = 0.0,
    val expenseAmount: Double = 0.0,
    val status: String = RowStatus.PENDING.name,
    val notes: String = "",
    val sourceLabel: String = "Manual Entry",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GarageVehicle(
    @DocumentId val id: String = "",
    val userId: String = "",
    val plateNumber: String = "",
    val vehicleClass: String = VehicleClass.TRUCK.name,
    val nickname: String = "",
    val ownerName: String = "",
    val rtoOffice: String = "",
    val fuelType: String = "Diesel",
    val chassisMasked: String = "",
    val challanCount: Long = 0L,
    val challanAmount: Double = 0.0,
    val challanDueDate: String = "",
    val rtcPermitExpiry: String = "",
    val insuranceProvider: String = "",
    val insurancePremium: Double = 0.0,
    val insuranceDueDate: String = "",
    val alertsEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BusinessDocument(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val docType: String = DocType.FREIGHT_INVOICE.name,
    val vehicleNo: String = "",
    val partyName: String = "",
    val amount: Double = 0.0,
    val photoCount: Long = 0L,
    val summaryNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class ComplianceAlertType(val label: String) {
    CHALLAN("Challan"),
    RTC_PERMIT("RTC / Permit"),
    INSURANCE("Insurance")
}

data class ComplianceAlert(
    val id: String,
    val vehicleId: String,
    val plateNumber: String,
    val type: ComplianceAlertType,
    val dueDateStr: String,
    val daysRemaining: Long,
    val amount: Double,
    val title: String,
    val detail: String
)

data class BusinessProfile(
    val companyName: String = "Shree Ganesh Roadlines",
    val gstin: String = "27AABCU9603R1ZM",
    val officeAddress: String = "Transport Nagar, Nigdi, Pune 411044",
    val contactPhone: String = "+91 98220 45120",
    val notificationsEnabled: Boolean = true
)

data class ExtractedInvoiceRow(
    val dateStr: String,
    val vehicleNo: String,
    val partyOrRoute: String,
    val billNo: String,
    val freightAmount: Double,
    val expenseAmount: Double,
    val status: RowStatus,
    val notes: String
)

data class VehicleLookupResult(
    val plateNumber: String,
    val isValidFormat: Boolean,
    val stateName: String,
    val rtoCode: String,
    val rtoOffice: String,
    val suggestedClass: VehicleClass,
    val registrationSeriesNote: String,
    val parivahanPortalUrl: String,
    val echallanPortalUrl: String,
    val liveWebStatus: String
)
