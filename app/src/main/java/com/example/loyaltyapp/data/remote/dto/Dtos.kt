package com.example.loyaltyapp.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---- Auth --------------------------------------------------------------

@Serializable
data class LoginRequestDto(
    val employeeId: String,
    val pin: String
)

/** Badge tap login (handover doc §3.1b) — tagId is the raw UID read off the tapped badge, normalized server-side. */
@Serializable
data class NfcLoginRequestDto(
    val tagId: String
)

@Serializable
data class AttendantDto(
    val kind: String = "attendant",
    val attendantId: String,
    val employeeId: String,
    val fullName: String,
    val role: String = "attendant",
    val assignedStationId: String
)

@Serializable
data class LoginResponseDto(
    val accessToken: String,
    val attendant: AttendantDto
)

// ---- Bootstrap / station / prices --------------------------------------

@Serializable
data class StationDto(
    val id: String,
    val name: String,
    val code: String,
    val location: String? = null,
    val active: Boolean = true,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class PriceDto(
    val id: String,
    val product: String, // "PMS" | "AGO"
    val pricePerLitre: Double,
    val effectiveFrom: String,
    val effectiveTo: String? = null,
    val createdByUserId: String? = null,
    val createdByName: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class BootstrapResponseDto(
    val attendant: AttendantDto,
    val station: StationDto,
    // Keyed by product code (e.g. "PMS"/"AGO"), not an array — verified against the live server,
    // which disagrees with the handover doc's example here.
    val prices: Map<String, PriceDto>,
    val configVersion: Int,
    val serverTime: String
)

// ---- Customers -----------------------------------------------------------

@Serializable
data class CustomerDto(
    val id: String,
    val fullName: String,
    val phoneNumber: String,
    val homeStationId: String? = null,
    val specialRateId: String? = null,
    val specialRateKesPerLitre: Double? = null,
    val specialRateEffectiveFrom: String? = null,
    val specialRateEffectiveTo: String? = null,
    val totalCashbackEarned: Double = 0.0,
    val source: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    // A customer can have more than one vehicle on file (family car + motorcycle, etc.); absent
    // or [] means none set. nfcTagId is likewise absent, not an error, when no tag is assigned.
    // Both are optional per-customer extras added by staff from the web admin, with no in-app
    // registration flow for either.
    val licensePlateNumbers: List<String> = emptyList(),
    val nfcTagId: String? = null
)

/** Response for `GET /mobile/customers` — full/incremental customer sync, paginated. */
@Serializable
data class CustomerListResponseDto(
    val items: List<CustomerDto>,
    val nextCursor: String? = null
)

// ---- Sales -----------------------------------------------------------

@Serializable
data class SaleRequestDto(
    val customerPhone: String,
    val product: String,
    val amountPaid: Double,
    val stationId: String,
    val saleDate: String? = null,
    val idempotencyKey: String,
    val clientLocalId: String? = null,
    val claimedPricePerLitre: Double? = null,
    val claimedCashbackEarned: Double? = null,
    // Optional: only present when a vehicle-plate photo was captured for this sale (see
    // VehiclePlateCheckDto). The backend re-validates it server-side (same customer, <60min old)
    // and silently ignores it otherwise — never an error, so this is safe to omit or get stale.
    val plateCheckId: String? = null
)

@Serializable
data class SaleSnapshotDto(
    val litres: Double,
    val wholeLitres: Int,
    val pricePerLitre: Double,
    val cashbackRatePerLitre: Double,
    val cashbackEarned: Double
)

@Serializable
data class SaleResponseDto(
    val id: String,
    val customerId: String,
    val customerPhoneAtSale: String,
    val product: String,
    val amountPaid: Double,
    val stationId: String,
    val stationNameAtSale: String,
    val attendantId: String,
    val attendantNameAtSale: String,
    val saleDate: String,
    val snapshot: SaleSnapshotDto,
    val specialRateIdAtSale: String? = null,
    val idempotencyKey: String,
    val clientLocalId: String? = null,
    val source: String? = null,
    val smsStatus: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    // Present on the immediate `/mobile/sales` create response — the backend no longer sends the
    // SMS itself for app-created sales, so this app sends it directly using this figure (see
    // AfricasTalkingSmsSender). Null on responses that don't carry it (e.g. `/mobile/sales/mine`).
    val monthToDateCashback: Double? = null,
    // Present only if a valid plateCheckId was carried through on the request (see
    // SaleRequestDto.plateCheckId) — absent otherwise, same "absent means not set" rule as above.
    val licensePlateCheck: LicensePlateCheckSummaryDto? = null
)

@Serializable
data class LicensePlateCheckSummaryDto(
    val plateCheckId: String,
    val detectedPlateNumber: String? = null,
    val matched: Boolean
)

// ---- Vehicle-plate photo verification -------------------------------------

@Serializable
data class VehiclePlateCheckDto(
    val id: String,
    val customerId: String,
    val customerNameAtCheck: String? = null,
    val attendantId: String? = null,
    val stationId: String? = null,
    val imageUrl: String? = null,
    val detectedPlateNumber: String? = null,
    val matched: Boolean,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

// ---- Bulk sync -----------------------------------------------------------

@Serializable
data class SyncRequestDto(
    val sales: List<SaleRequestDto>
)

@Serializable
data class SyncResultDto(
    val clientLocalId: String? = null,
    val idempotencyKey: String,
    val result: String, // accepted | needs_review | already_processed | rejected
    val saleId: String? = null,
    val errorReason: String? = null,
    // Present only when result is "accepted" or "needs_review" — a sale was actually created (or
    // re-affirmed) by this batch and the app should send the confirmation SMS itself. Absent for
    // "already_processed"/"rejected": either already handled or no sale exists to text about.
    val customerPhone: String? = null,
    val cashbackEarned: Double? = null,
    val monthToDateCashback: Double? = null
)

@Serializable
data class SyncResponseDto(
    val results: List<SyncResultDto>
)

// ---- SMS delivery report (app -> API, fire-and-forget) --------------------

@Serializable
data class SmsStatusReportDto(
    val success: Boolean,
    val providerResponse: String? = null,
    val errorReason: String? = null
)

@Serializable
data class SyncOperationDto(
    val clientLocalId: String? = null,
    val idempotencyKey: String,
    val result: String,
    val saleId: String? = null,
    val errorReason: String? = null,
    val createdAt: String? = null
)

// ---- Customer registration (new-customer pending-approval flow) ----------

@Serializable
data class CustomerRegistrationRequestDto(
    val customerFullName: String,
    val customerPhoneNumber: String,
    val product: String,
    val amountPaid: Double,
    val saleDate: String? = null,
    val idempotencyKey: String
)

@Serializable
data class CustomerRegistrationResponseDto(
    val id: String,
    val customerFullName: String,
    val customerPhoneNumber: String,
    val product: String,
    val amountPaid: Double,
    val saleDate: String,
    val status: String, // "pending" initially
    val idempotencyKey: String,
    val createdAt: String? = null
)

// ---- Daily summary / sales list -------------------------------------------

// Real shape verified against the live server — a per-product reconciliation row, not a simple
// attendant sale count/value pair as the field names might suggest.
@Serializable
data class DailySummaryRowDto(
    val stationId: String? = null,
    val date: String? = null,
    val product: String? = null,
    val loyaltySales: Double = 0.0,
    val totalSales: Double = 0.0,
    val headroom: Double = 0.0,
    val percentage: Double = 0.0,
    val status: String? = null
)

// Verified against the live server: a wrapper object, not a bare array.
@Serializable
data class DailySummaryResponseDto(
    val date: String,
    val reconciliation: List<DailySummaryRowDto>
)

@Serializable
data class PagedSalesDto(
    val items: List<SaleResponseDto>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val nextCursor: String? = null
)

// ---- Error envelope --------------------------------------------------------

@Serializable
data class ApiErrorDto(
    val statusCode: Int,
    val error: String? = null,
    val message: JsonElement? = null,
    val path: String? = null,
    val timestamp: String? = null
)
