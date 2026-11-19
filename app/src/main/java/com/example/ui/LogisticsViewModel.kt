package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.PreferencesStore
import com.example.core.ReminderNotificationManager
import com.example.core.UiState
import com.example.data.AppearanceMode
import com.example.data.BusinessDocument
import com.example.data.BusinessProfile
import com.example.data.ComplianceAlert
import com.example.data.ExtractedInvoiceRow
import com.example.data.GarageVehicle
import com.example.data.GeminiAssistantRepository
import com.example.data.LogisticsRepository
import com.example.data.RtoWebLookupService
import com.example.data.SheetCategory
import com.example.data.SheetRow
import com.example.data.TransportSheet
import com.example.data.VehicleLookupResult
import com.example.utils.ExportEngine
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LogisticsViewModel(
    private val repository: LogisticsRepository,
    private val geminiRepo: GeminiAssistantRepository,
    private val rtoLookup: RtoWebLookupService,
    private val preferencesStore: PreferencesStore,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedSheetId = MutableStateFlow("")
    val selectedSheetId: StateFlow<String> = _selectedSheetId.asStateFlow()

    val appearanceMode: StateFlow<AppearanceMode> = preferencesStore.appearanceMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppearanceMode.SYSTEM)

    val businessProfile: StateFlow<BusinessProfile> = preferencesStore.businessProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), BusinessProfile())

    val sheetsState: StateFlow<UiState<List<TransportSheet>>> = repository.observeSheets(currentUserId)
        .map<List<TransportSheet>, UiState<List<TransportSheet>>> { sheets ->
            if (_selectedSheetId.value.isBlank() && sheets.isNotEmpty()) {
                _selectedSheetId.value = sheets.first().id
            } else if (sheets.isNotEmpty() && sheets.none { it.id == _selectedSheetId.value }) {
                _selectedSheetId.value = sheets.first().id
            }
            UiState.Success(sheets)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(UiState.Error((firestoreError ?: error).message ?: "Unable to sync sheets."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val activeRowsState: StateFlow<UiState<List<SheetRow>>> = _selectedSheetId
        .flatMapLatest { sheetId ->
            if (sheetId.isBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeSheetRows(currentUserId, sheetId)
                    .map<List<SheetRow>, UiState<List<SheetRow>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(UiState.Error((firestoreError ?: error).message ?: "Unable to load rows."))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val vehiclesState: StateFlow<UiState<List<GarageVehicle>>> = repository.observeVehicles(currentUserId)
        .map<List<GarageVehicle>, UiState<List<GarageVehicle>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(UiState.Error((firestoreError ?: error).message ?: "Unable to load garage vehicles."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val upcomingAlerts: StateFlow<List<ComplianceAlert>> = vehiclesState
        .map { state ->
            if (state is UiState.Success) {
                ReminderNotificationManager.evaluateUpcomingAlerts(state.data)
            } else {
                emptyList()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val documentsState: StateFlow<UiState<List<BusinessDocument>>> = repository.observeDocuments(currentUserId)
        .map<List<BusinessDocument>, UiState<List<BusinessDocument>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(UiState.Error((firestoreError ?: error).message ?: "Unable to load document history."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _scanInvoiceState = MutableStateFlow<UiState<ExtractedInvoiceRow>?>(null)
    val scanInvoiceState: StateFlow<UiState<ExtractedInvoiceRow>?> = _scanInvoiceState.asStateFlow()

    private val _vehicleLookupState = MutableStateFlow<VehicleLookupResult?>(null)
    val vehicleLookupState: StateFlow<VehicleLookupResult?> = _vehicleLookupState.asStateFlow()

    private val _isLookingUpPlate = MutableStateFlow(false)
    val isLookingUpPlate: StateFlow<Boolean> = _isLookingUpPlate.asStateFlow()

    private val _scannedVehicleState = MutableStateFlow<UiState<GarageVehicle>?>(null)
    val scannedVehicleState: StateFlow<UiState<GarageVehicle>?> = _scannedVehicleState.asStateFlow()

    fun selectSheet(sheetId: String) {
        _selectedSheetId.value = sheetId
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun setAppearanceMode(mode: AppearanceMode) {
        viewModelScope.launch {
            preferencesStore.setAppearanceMode(mode)
        }
    }

    fun saveBusinessProfile(profile: BusinessProfile) {
        viewModelScope.launch {
            preferencesStore.updateBusinessProfile(profile)
            _toastMessage.value = "Office letterhead & alert settings saved."
        }
    }

    fun loadStarterTemplate() {
        viewModelScope.launch {
            val res = repository.createStarterTransportTemplate()
            res.onSuccess { id ->
                _selectedSheetId.value = id
                _toastMessage.value = "Transport ledger and fleet garage initialized."
            }.onFailure { e ->
                _toastMessage.value = e.localizedMessage ?: "Could not create starter ledger."
            }
        }
    }

    fun createSheet(name: String, category: SheetCategory, syncUrl: String) {
        viewModelScope.launch {
            val res = repository.createSheet(
                name = name,
                category = category,
                syncPlugUrl = syncUrl,
                syncEnabled = syncUrl.isNotBlank()
            )
            res.onSuccess { id ->
                _selectedSheetId.value = id
                _toastMessage.value = "Sheet '$name' created."
            }.onFailure { e ->
                _toastMessage.value = e.localizedMessage ?: "Failed to create sheet."
            }
        }
    }

    fun updateSheetConnection(sheetId: String, name: String, syncUrl: String) {
        viewModelScope.launch {
            repository.updateSheetConnection(
                sheetId = sheetId,
                name = name,
                syncPlugUrl = syncUrl,
                syncEnabled = syncUrl.isNotBlank()
            ).onSuccess {
                _toastMessage.value = "Sheet connection updated."
            }.onFailure { e ->
                _toastMessage.value = e.localizedMessage ?: "Failed to update connection."
            }
        }
    }

    fun saveRow(sheetId: String, row: SheetRow) {
        viewModelScope.launch {
            repository.upsertSheetRow(sheetId, row)
                .onSuccess { _toastMessage.value = "Ledger row saved." }
                .onFailure { e -> _toastMessage.value = e.localizedMessage ?: "Failed to save row." }
        }
    }

    fun deleteRow(sheetId: String, rowId: String) {
        viewModelScope.launch {
            repository.deleteSheetRow(sheetId, rowId)
                .onSuccess { _toastMessage.value = "Row deleted." }
                .onFailure { e -> _toastMessage.value = e.localizedMessage ?: "Failed to delete row." }
        }
    }

    fun importCsvIntoActiveSheet(context: Context, uri: Uri) {
        val sheetId = _selectedSheetId.value
        if (sheetId.isBlank()) {
            _toastMessage.value = "Create or select a sheet before importing."
            return
        }
        viewModelScope.launch {
            val rows = ExportEngine.parseCsvToRows(context, uri, sheetId)
            if (rows.isEmpty()) {
                _toastMessage.value = "No readable rows found in that CSV file."
                return@launch
            }
            var saved = 0
            for (r in rows) {
                if (repository.upsertSheetRow(sheetId, r).isSuccess) saved++
            }
            _toastMessage.value = "Imported $saved rows into active sheet."
        }
    }

    fun scanInvoicePhoto(bitmap: Bitmap) {
        viewModelScope.launch {
            _scanInvoiceState.value = UiState.Loading
            val res = geminiRepo.extractInvoiceToRow(bitmap)
            res.onSuccess { extracted ->
                _scanInvoiceState.value = UiState.Success(extracted)
            }.onFailure { err ->
                _scanInvoiceState.value = UiState.Error(err.localizedMessage ?: "Could not extract invoice details.")
            }
        }
    }

    fun clearInvoiceScan() {
        _scanInvoiceState.value = null
    }

    fun lookupVehiclePlate(rawPlate: String) {
        viewModelScope.launch {
            _isLookingUpPlate.value = true
            val result = rtoLookup.lookupPlate(rawPlate)
            _vehicleLookupState.value = result
            _isLookingUpPlate.value = false
        }
    }

    fun scanVehicleDocPhoto(bitmap: Bitmap) {
        viewModelScope.launch {
            _scannedVehicleState.value = UiState.Loading
            val res = geminiRepo.extractVehicleInfoFromDoc(bitmap)
            res.onSuccess { v ->
                _scannedVehicleState.value = UiState.Success(v)
            }.onFailure { err ->
                _scannedVehicleState.value = UiState.Error(err.localizedMessage ?: "Could not read vehicle document.")
            }
        }
    }

    fun clearScannedVehicle() {
        _scannedVehicleState.value = null
    }

    fun saveVehicle(vehicle: GarageVehicle) {
        viewModelScope.launch {
            repository.upsertVehicle(vehicle)
                .onSuccess { _toastMessage.value = "${vehicle.plateNumber} saved to Garage." }
                .onFailure { e -> _toastMessage.value = e.localizedMessage ?: "Failed to save vehicle." }
        }
    }

    fun deleteVehicle(vehicleId: String) {
        viewModelScope.launch {
            repository.deleteVehicle(vehicleId)
                .onSuccess { _toastMessage.value = "Vehicle removed from Garage." }
                .onFailure { e -> _toastMessage.value = e.localizedMessage ?: "Failed to remove vehicle." }
        }
    }

    fun saveDocumentHistory(doc: BusinessDocument) {
        viewModelScope.launch {
            repository.saveDocumentRecord(doc)
                .onFailure { e -> _toastMessage.value = e.localizedMessage ?: "Could not record document." }
        }
    }

    suspend fun generatePhotoCaption(bitmap: Bitmap, vehicleNo: String): String {
        return geminiRepo.writeBillPhotoCaption(bitmap, vehicleNo)
            .getOrDefault("Dispatch verification photo attached.")
    }

    fun triggerAlertNotificationsNow(context: Context) {
        val alerts = upcomingAlerts.value
        val sent = ReminderNotificationManager.dispatchAlertsIfPermitted(context, alerts)
        _toastMessage.value = if (alerts.isEmpty()) {
            "No challans, RTC permits, or insurance renewals due within 3 days."
        } else if (sent > 0) {
            "Sent $sent compliance alert notification(s)."
        } else {
            "Enable notification permission to receive 3-day early alerts."
        }
    }
}
