package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.example.core.DateUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiAssistant"

class GeminiAssistantRepository {

    private val invoiceSchema = Schema.obj(
        properties = mapOf(
            "dateStr" to Schema.string(description = "Invoice date formatted YYYY-MM-DD"),
            "vehicleNo" to Schema.string(description = "Vehicle number plate if visible, else empty string"),
            "partyOrRoute" to Schema.string(description = "Vendor, party, or route description written like a human transport clerk"),
            "billNo" to Schema.string(description = "Invoice, LR, bilty, or receipt number"),
            "freightAmount" to Schema.double(description = "Freight or billed revenue amount if this is a client bill, else 0"),
            "expenseAmount" to Schema.double(description = "Fuel, toll, repair, or vendor expense amount if this is an expense slip, else 0"),
            "status" to Schema.enumeration(listOf("PAID", "PENDING", "IN_TRANSIT", "BILLED")),
            "notes" to Schema.string(description = "Concise human clerk note summarizing items, liters, or weight. No AI jargon or emojis.")
        )
    )

    private val vehicleDocSchema = Schema.obj(
        properties = mapOf(
            "plateNumber" to Schema.string(description = "Registration number plate uppercase without spaces"),
            "vehicleClass" to Schema.enumeration(listOf("LMV", "BIKE", "BUS", "TRUCK", "LONG_VEHICLE")),
            "nickname" to Schema.string(description = "Vehicle maker and model name"),
            "ownerName" to Schema.string(description = "Registered owner name"),
            "rtoOffice" to Schema.string(description = "RTO office name"),
            "fuelType" to Schema.string(description = "Fuel type e.g. Diesel, Petrol, CNG, Electric"),
            "challanCount" to Schema.integer(description = "Pending challan count if mentioned else 0"),
            "challanAmount" to Schema.double(description = "Total challan fine amount if mentioned else 0"),
            "challanDueDate" to Schema.string(description = "Challan due date YYYY-MM-DD or empty string"),
            "rtcPermitExpiry" to Schema.string(description = "RTC/RTO fitness or permit expiry date YYYY-MM-DD or empty string"),
            "insuranceProvider" to Schema.string(description = "Insurance company name or empty string"),
            "insurancePremium" to Schema.double(description = "Insurance premium amount if visible else 0"),
            "insuranceDueDate" to Schema.string(description = "Insurance expiry/due date YYYY-MM-DD or empty string")
        )
    )

    private val invoiceModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = invoiceSchema
            }
        )
    }

    private val vehicleDocModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = vehicleDocSchema
            }
        )
    }

    private val captionModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    suspend fun extractInvoiceToRow(bitmap: Bitmap): Result<ExtractedInvoiceRow> {
        return try {
            val response = withTimeout(60_000L) {
                invoiceModel.generateContent(
                    content {
                        image(bitmap)
                        text(
                            "Read this transport invoice, diesel slip, toll receipt, repair bill, or loading bilty. " +
                                "Extract exact figures into the schema. Write partyOrRoute and notes in plain, " +
                                "natural human office clerk language with no AI phrasing, no markdown, and no emojis. " +
                                "If date is unclear use ${DateUtil.todayIso()}. Classify fuel/toll/maintenance bills as " +
                                "expenseAmount and freight/transport client bills as freightAmount."
                        )
                    }
                )
            }
            val rawJson = response.text ?: throw IllegalStateException("No readable details found in image.")
            val json = JSONObject(rawJson)
            Result.success(
                ExtractedInvoiceRow(
                    dateStr = json.optString("dateStr", DateUtil.todayIso()).ifBlank { DateUtil.todayIso() },
                    vehicleNo = json.optString("vehicleNo", "").uppercase().replace(" ", ""),
                    partyOrRoute = json.optString("partyOrRoute", "Transport Invoice Entry").ifBlank { "Transport Invoice Entry" },
                    billNo = json.optString("billNo", ""),
                    freightAmount = json.optDouble("freightAmount", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                    expenseAmount = json.optDouble("expenseAmount", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                    status = RowStatus.fromRaw(json.optString("status", "PENDING")),
                    notes = json.optString("notes", "")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Invoice scan timed out", e)
            Result.failure(Exception("Reading the bill took too long. Please try a clearer photo."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Invoice scan failed", e)
            Result.failure(e)
        }
    }

    suspend fun extractVehicleInfoFromDoc(bitmap: Bitmap): Result<GarageVehicle> {
        return try {
            val response = withTimeout(60_000L) {
                vehicleDocModel.generateContent(
                    content {
                        image(bitmap)
                        text(
                            "Read this vehicle RC card, Parivahan/Vahan web screenshot, challan notice, or insurance policy. " +
                                "Extract all visible registration, owner, RTO, challan, permit, and insurance dates in YYYY-MM-DD format."
                        )
                    }
                )
            }
            val rawJson = response.text ?: throw IllegalStateException("Could not read vehicle document.")
            val json = JSONObject(rawJson)
            Result.success(
                GarageVehicle(
                    plateNumber = json.optString("plateNumber", "").uppercase().replace(" ", ""),
                    vehicleClass = VehicleClass.fromRaw(json.optString("vehicleClass", "TRUCK")).name,
                    nickname = json.optString("nickname", ""),
                    ownerName = json.optString("ownerName", ""),
                    rtoOffice = json.optString("rtoOffice", ""),
                    fuelType = json.optString("fuelType", "Diesel").ifBlank { "Diesel" },
                    challanCount = json.optLong("challanCount", 0L),
                    challanAmount = json.optDouble("challanAmount", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                    challanDueDate = json.optString("challanDueDate", ""),
                    rtcPermitExpiry = json.optString("rtcPermitExpiry", ""),
                    insuranceProvider = json.optString("insuranceProvider", ""),
                    insurancePremium = json.optDouble("insurancePremium", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                    insuranceDueDate = json.optString("insuranceDueDate", ""),
                    alertsEnabled = true
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Vehicle document read timed out", e)
            Result.failure(Exception("Document lookup timed out. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Vehicle document read failed", e)
            Result.failure(e)
        }
    }

    suspend fun writeBillPhotoCaption(bitmap: Bitmap, vehicleNo: String): Result<String> {
        return try {
            val response = withTimeout(45_000L) {
                captionModel.generateContent(
                    content {
                        image(bitmap)
                        text(
                            "Write one plain, realistic 6-to-12-word photo caption for a transport freight bill " +
                                "(for example: 'Loaded pallet cargo verified before dispatch' or 'Signed delivery challan at unloading dock'). " +
                                "Vehicle: ${vehicleNo.ifBlank { "Fleet unit" }}. Use natural clerk wording, no emojis, no quotes, no AI tone."
                        )
                    }
                )
            }
            val caption = response.text?.trim()?.replace("\"", "")?.take(120)
                ?: "Proof photo attached to bill."
            Result.success(caption)
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Caption request timed out."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Photo caption failed", e)
            Result.failure(e)
        }
    }
}
