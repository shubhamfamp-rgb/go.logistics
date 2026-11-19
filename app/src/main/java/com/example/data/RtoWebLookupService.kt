package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class RtoWebLookupService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val stateMap = mapOf(
        "MH" to "Maharashtra",
        "DL" to "Delhi NCT",
        "KA" to "Karnataka",
        "GJ" to "Gujarat",
        "RJ" to "Rajasthan",
        "TN" to "Tamil Nadu",
        "UP" to "Uttar Pradesh",
        "HR" to "Haryana",
        "PB" to "Punjab",
        "WB" to "West Bengal",
        "TS" to "Telangana",
        "TG" to "Telangana",
        "AP" to "Andhra Pradesh",
        "MP" to "Madhya Pradesh",
        "KL" to "Kerala",
        "GA" to "Goa",
        "OR" to "Odisha",
        "OD" to "Odisha",
        "BR" to "Bihar",
        "JH" to "Jharkhand",
        "CG" to "Chhattisgarh",
        "UK" to "Uttarakhand",
        "AS" to "Assam",
        "HP" to "Himachal Pradesh",
        "CH" to "Chandigarh"
    )

    private val rtoDistrictMap = mapOf(
        "MH01" to "Mumbai Central (Tardeo)",
        "MH02" to "Mumbai West (Andheri)",
        "MH03" to "Mumbai East (Wadala)",
        "MH04" to "Thane RTO",
        "MH05" to "Kalyan RTO",
        "MH06" to "Pen (Raigad) RTO",
        "MH09" to "Kolhapur RTO",
        "MH11" to "Satara RTO",
        "MH12" to "Pune RTO",
        "MH14" to "Pimpri-Chinchwad RTO",
        "MH15" to "Nashik RTO",
        "MH20" to "Chhatrapati Sambhajinagar RTO",
        "MH31" to "Nagpur Urban RTO",
        "MH40" to "Nagpur Rural RTO",
        "MH43" to "Navi Mumbai (Vashi) RTO",
        "MH46" to "Panvel RTO",
        "MH47" to "Mumbai North (Borivali) RTO",
        "MH48" to "Vasai-Virar RTO",
        "GJ01" to "Ahmedabad RTO",
        "GJ03" to "Rajkot RTO",
        "GJ05" to "Surat RTO",
        "GJ06" to "Vadodara RTO",
        "GJ12" to "Bhuj (Kutch) RTO",
        "KA01" to "Bengaluru Central (Koramangala)",
        "KA02" to "Bengaluru West (Rajajinagar)",
        "KA04" to "Bengaluru North (Yeshwanthpur)",
        "KA51" to "Bengaluru South (Electronic City)",
        "DL01" to "Delhi North (Mall Road)",
        "DL02" to "Delhi Central (Tilak Marg)",
        "DL04" to "Delhi West (Janakpuri)",
        "TN01" to "Chennai Central RTO",
        "TN09" to "Chennai West RTO",
        "HR26" to "Gurugram RTO",
        "HR55" to "Gurugram Commercial RTO",
        "UP16" to "Noida (Gautam Buddha Nagar)",
        "UP32" to "Lucknow RTO",
        "RJ14" to "Jaipur RTO"
    )

    suspend fun lookupPlate(rawInput: String): VehicleLookupResult = withContext(Dispatchers.IO) {
        val clean = rawInput.uppercase().replace(Regex("[^A-Z0-9]"), "")
        val standardRegex = Regex("^[A-Z]{2}[0-9]{1,2}[A-Z]{0,3}[0-9]{1,4}$")
        val bhSeriesRegex = Regex("^[0-9]{2}BH[0-9]{4}[A-Z]{1,2}$")
        val templatePlateRegex = Regex("^[A-Z]{2}[A-Z0-9]{2}[A-Z]{1,3}[0-9]{4}$")

        val isValid = standardRegex.matches(clean) || bhSeriesRegex.matches(clean) || templatePlateRegex.matches(clean)
        val stateCode = if (clean.length >= 2) clean.take(2) else ""
        val rtoCode = if (clean.length >= 4) clean.take(4) else stateCode
        val stateName = stateMap[stateCode] ?: if (bhSeriesRegex.matches(clean)) "Bharat Series (All India)" else "Indian RTO Registry"
        val rtoOffice = rtoDistrictMap[rtoCode] ?: if (stateName != "Indian RTO Registry") "$stateName · $rtoCode Zone" else "Regional Transport Office"

        val seriesLetters = clean.drop(4).takeWhile { it.isLetter() }
        val suggestedClass = when {
            seriesLetters.contains("T") || seriesLetters.contains("G") || seriesLetters.contains("Z") -> VehicleClass.TRUCK
            seriesLetters.contains("B") || seriesLetters.contains("P") -> VehicleClass.BUS
            seriesLetters.contains("S") || seriesLetters.contains("M") -> VehicleClass.BIKE
            else -> VehicleClass.LMV
        }

        val seriesNote = when {
            bhSeriesRegex.matches(clean) -> "BH-Series nationwide private registration"
            seriesLetters.isNotEmpty() -> "Series '$seriesLetters' decoded under $rtoCode"
            else -> "Standard state transport zone $rtoCode"
        }

        val parivahanUrl = "https://vahan.parivahan.gov.in/nrservices/faces/user/citizen/citizenlogin.xhtml"
        val echallanUrl = "https://echallan.parivahan.gov.in/index/accused-challan"

        val webReachability = try {
            val req = Request.Builder()
                .url("https://parivahan.gov.in/parivahan/")
                .head()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful || resp.code in 200..399) {
                    "Parivahan Portal Online (HTTP ${resp.code}) — Ready to verify or scan RC"
                } else {
                    "Portal responded (HTTP ${resp.code}) — Use Web Check or RC Scan"
                }
            }
        } catch (_: Exception) {
            "RTO Zone decoded locally — Tap Web Check for live captcha portal"
        }

        VehicleLookupResult(
            plateNumber = clean,
            isValidFormat = isValid,
            stateName = stateName,
            rtoCode = rtoCode,
            rtoOffice = rtoOffice,
            suggestedClass = suggestedClass,
            registrationSeriesNote = seriesNote,
            parivahanPortalUrl = parivahanUrl,
            echallanPortalUrl = echallanUrl,
            liveWebStatus = webReachability
        )
    }
}
