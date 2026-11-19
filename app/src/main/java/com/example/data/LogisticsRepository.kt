package com.example.data

import android.content.Context
import com.example.R
import com.example.core.DateUtil
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class LogisticsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in with Google to sync fleet records.")
    }

    fun observeSheets(userId: String): Flow<List<TransportSheet>> = flow {
        val path = "users/$userId/sheets"
        emitAll(
            db.collection("users").document(userId).collection("sheets")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TransportSheet::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeSheetRows(userId: String, sheetId: String): Flow<List<SheetRow>> = flow {
        if (sheetId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/sheets/$sheetId/rows"
        emitAll(
            db.collection("users").document(userId)
                .collection("sheets").document(sheetId)
                .collection("rows")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SheetRow::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeVehicles(userId: String): Flow<List<GarageVehicle>> = flow {
        val path = "users/$userId/vehicles"
        emitAll(
            db.collection("users").document(userId).collection("vehicles")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        GarageVehicle::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeDocuments(userId: String): Flow<List<BusinessDocument>> = flow {
        val path = "users/$userId/documents"
        emitAll(
            db.collection("users").document(userId).collection("documents")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BusinessDocument::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createSheet(
        name: String,
        category: SheetCategory,
        syncPlugUrl: String = "",
        syncEnabled: Boolean = false
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/sheets"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "Sheet name cannot be empty." }
            val docRef = db.collection("users").document(uid).collection("sheets").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "category" to category.name,
                "syncPlugUrl" to syncPlugUrl.trim().take(300),
                "syncEnabled" to syncEnabled,
                "columns" to listOf("Date", "Vehicle No", "Party / Route", "LR / Bill", "Freight", "Expense", "Status"),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateSheetConnection(
        sheetId: String,
        name: String,
        syncPlugUrl: String,
        syncEnabled: Boolean
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/sheets/$sheetId"
        return try {
            val uid = requireUserId()
            require(sheetId.isNotBlank()) { "Sheet ID missing." }
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "Sheet name cannot be empty." }
            val docRef = db.collection("users").document(uid).collection("sheets").document(sheetId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "name" to cleanName,
                    "syncPlugUrl" to syncPlugUrl.trim().take(300),
                    "syncEnabled" to syncEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertSheetRow(sheetId: String, row: SheetRow): Result<Unit> {
        val isCreate = row.id.isBlank()
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/sheets/$sheetId/rows"
        return try {
            val uid = requireUserId()
            require(sheetId.isNotBlank()) { "Select or create a sheet first." }
            val partyClean = row.partyOrRoute.trim().take(140)
            require(partyClean.isNotEmpty()) { "Party or route name is required." }
            val freight = row.freightAmount.coerceIn(0.0, 100000000.0)
            val expense = row.expenseAmount.coerceIn(0.0, 100000000.0)
            val validStatus = RowStatus.fromRaw(row.status).name
            val rowsRef = db.collection("users").document(uid)
                .collection("sheets").document(sheetId)
                .collection("rows")

            if (isCreate) {
                val docRef = rowsRef.document()
                docPath = docRef.path
                val payload = mapOf(
                    "userId" to uid,
                    "sheetId" to sheetId,
                    "dateStr" to row.dateStr.trim().take(24),
                    "vehicleNo" to row.vehicleNo.trim().uppercase().take(24),
                    "partyOrRoute" to partyClean,
                    "billNo" to row.billNo.trim().take(40),
                    "freightAmount" to freight,
                    "expenseAmount" to expense,
                    "status" to validStatus,
                    "notes" to row.notes.trim().take(300),
                    "sourceLabel" to row.sourceLabel.ifBlank { "Manual Entry" }.take(40),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val docRef = rowsRef.document(row.id)
                docPath = docRef.path
                docRef.update(
                    mapOf(
                        "dateStr" to row.dateStr.trim().take(24),
                        "vehicleNo" to row.vehicleNo.trim().uppercase().take(24),
                        "partyOrRoute" to partyClean,
                        "billNo" to row.billNo.trim().take(40),
                        "freightAmount" to freight,
                        "expenseAmount" to expense,
                        "status" to validStatus,
                        "notes" to row.notes.trim().take(300),
                        "sourceLabel" to row.sourceLabel.ifBlank { "Manual Entry" }.take(40),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isCreate) OperationType.CREATE else OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSheetRow(sheetId: String, rowId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/sheets/$sheetId/rows/$rowId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
                .collection("sheets").document(sheetId)
                .collection("rows").document(rowId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertVehicle(vehicle: GarageVehicle): Result<Unit> {
        val isCreate = vehicle.id.isBlank()
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/vehicles"
        return try {
            val uid = requireUserId()
            val cleanPlate = vehicle.plateNumber.uppercase().replace(Regex("[^A-Z0-9]"), "").take(20)
            require(cleanPlate.length >= 4) { "Enter a valid registration number (min 4 characters)." }
            val validClass = VehicleClass.fromRaw(vehicle.vehicleClass).name
            val vehiclesRef = db.collection("users").document(uid).collection("vehicles")

            if (isCreate) {
                val docRef = vehiclesRef.document()
                docPath = docRef.path
                val payload = mapOf(
                    "userId" to uid,
                    "plateNumber" to cleanPlate,
                    "vehicleClass" to validClass,
                    "nickname" to vehicle.nickname.trim().take(60),
                    "ownerName" to vehicle.ownerName.trim().take(80),
                    "rtoOffice" to vehicle.rtoOffice.trim().take(80),
                    "fuelType" to vehicle.fuelType.ifBlank { "Diesel" }.trim().take(30),
                    "chassisMasked" to vehicle.chassisMasked.trim().take(40),
                    "challanCount" to vehicle.challanCount.coerceIn(0L, 1000L),
                    "challanAmount" to vehicle.challanAmount.coerceIn(0.0, 10000000.0),
                    "challanDueDate" to vehicle.challanDueDate.trim().take(20),
                    "rtcPermitExpiry" to vehicle.rtcPermitExpiry.trim().take(20),
                    "insuranceProvider" to vehicle.insuranceProvider.trim().take(80),
                    "insurancePremium" to vehicle.insurancePremium.coerceIn(0.0, 10000000.0),
                    "insuranceDueDate" to vehicle.insuranceDueDate.trim().take(20),
                    "alertsEnabled" to vehicle.alertsEnabled,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val docRef = vehiclesRef.document(vehicle.id)
                docPath = docRef.path
                docRef.update(
                    mapOf(
                        "plateNumber" to cleanPlate,
                        "vehicleClass" to validClass,
                        "nickname" to vehicle.nickname.trim().take(60),
                        "ownerName" to vehicle.ownerName.trim().take(80),
                        "rtoOffice" to vehicle.rtoOffice.trim().take(80),
                        "fuelType" to vehicle.fuelType.ifBlank { "Diesel" }.trim().take(30),
                        "chassisMasked" to vehicle.chassisMasked.trim().take(40),
                        "challanCount" to vehicle.challanCount.coerceIn(0L, 1000L),
                        "challanAmount" to vehicle.challanAmount.coerceIn(0.0, 10000000.0),
                        "challanDueDate" to vehicle.challanDueDate.trim().take(20),
                        "rtcPermitExpiry" to vehicle.rtcPermitExpiry.trim().take(20),
                        "insuranceProvider" to vehicle.insuranceProvider.trim().take(80),
                        "insurancePremium" to vehicle.insurancePremium.coerceIn(0.0, 10000000.0),
                        "insuranceDueDate" to vehicle.insuranceDueDate.trim().take(20),
                        "alertsEnabled" to vehicle.alertsEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isCreate) OperationType.CREATE else OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteVehicle(vehicleId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/vehicles/$vehicleId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("vehicles").document(vehicleId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveDocumentRecord(doc: BusinessDocument): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/documents"
        return try {
            val uid = requireUserId()
            val cleanTitle = doc.title.trim().take(120)
            require(cleanTitle.isNotEmpty()) { "Document title is required." }
            val docRef = db.collection("users").document(uid).collection("documents").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "docType" to DocType.fromRaw(doc.docType).name,
                "vehicleNo" to doc.vehicleNo.trim().uppercase().take(24),
                "partyName" to doc.partyName.trim().take(100),
                "amount" to doc.amount.coerceIn(0.0, 100000000.0),
                "photoCount" to doc.photoCount.coerceIn(0L, 20L),
                "summaryNote" to doc.summaryNote.trim().take(400),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createStarterTransportTemplate(): Result<String> {
        return try {
            val uid = requireUserId()
            val sheetResult = createSheet(
                name = "November Freight Register",
                category = SheetCategory.FREIGHT
            )
            val sheetId = sheetResult.getOrThrow()
            val starterRows = listOf(
                SheetRow(
                    dateStr = DateUtil.plusDaysIso(-2),
                    vehicleNo = "MH12PQ4821",
                    partyOrRoute = "Chakan MIDC to Bhiwandi Hub (Apex Steel)",
                    billNo = "LR-1142",
                    freightAmount = 24500.0,
                    expenseAmount = 7800.0,
                    status = RowStatus.PAID.name,
                    notes = "22 MT coils delivered. Unloading slip signed at gate 3.",
                    sourceLabel = "Office Entry"
                ),
                SheetRow(
                    dateStr = DateUtil.plusDaysIso(-1),
                    vehicleNo = "MH14JK8810",
                    partyOrRoute = "Pune to Surat Textile Market",
                    billNo = "LR-1143",
                    freightAmount = 38200.0,
                    expenseAmount = 14100.0,
                    status = RowStatus.BILLED.name,
                    notes = "Toll + diesel cleared by driver Rajinder.",
                    sourceLabel = "Office Entry"
                ),
                SheetRow(
                    dateStr = DateUtil.todayIso(),
                    vehicleNo = "MH04DL2904",
                    partyOrRoute = "Taloja Phase II to Nashik Ambad",
                    billNo = "LR-1144",
                    freightAmount = 18900.0,
                    expenseAmount = 5400.0,
                    status = RowStatus.IN_TRANSIT.name,
                    notes = "Expected delivery by 6:30 PM.",
                    sourceLabel = "Office Entry"
                )
            )
            starterRows.forEach { upsertSheetRow(sheetId, it) }

            val starterVehicles = listOf(
                GarageVehicle(
                    plateNumber = "MH12PQ4821",
                    vehicleClass = VehicleClass.TRUCK.name,
                    nickname = "BharatBenz 2823R",
                    ownerName = "Shree Ganesh Roadlines",
                    rtoOffice = "MH-12 Pune RTO",
                    fuelType = "Diesel",
                    chassisMasked = "MEC2823***4821",
                    challanCount = 1,
                    challanAmount = 1500.0,
                    challanDueDate = DateUtil.plusDaysIso(2),
                    rtcPermitExpiry = DateUtil.plusMonthsIso(4),
                    insuranceProvider = "New India Assurance",
                    insurancePremium = 28400.0,
                    insuranceDueDate = DateUtil.plusDaysIso(3),
                    alertsEnabled = true
                ),
                GarageVehicle(
                    plateNumber = "MH14JK8810",
                    vehicleClass = VehicleClass.LONG_VEHICLE.name,
                    nickname = "Tata Prima 5530.S Trailer",
                    ownerName = "Shree Ganesh Roadlines",
                    rtoOffice = "MH-14 Pimpri-Chinchwad RTO",
                    fuelType = "Diesel",
                    chassisMasked = "MAT5530***8810",
                    challanCount = 0,
                    challanAmount = 0.0,
                    challanDueDate = "",
                    rtcPermitExpiry = DateUtil.plusDaysIso(2),
                    insuranceProvider = "ICICI Lombard Motor",
                    insurancePremium = 42900.0,
                    insuranceDueDate = DateUtil.plusMonthsIso(2),
                    alertsEnabled = true
                )
            )
            starterVehicles.forEach { upsertVehicle(it) }
            Result.success(sheetId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
