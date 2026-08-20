package com.example.loyaltyapp.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---- Auth --------------------------------------------------------------

@Serializable
data class LoginRequestDto(
    val employeeId: String,
    val pin: String
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
    val updatedAt: String? = null
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
    val claimedCashbackEarned: Double? = null
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
    val errorReason: String? = null
)

@Serializable
data class SyncResponseDto(
    val results: List<SyncResultDto>
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

@Serializable
data class DailySummaryRowDto(
    val stationId: String? = null,
    val date: String? = null,
    val product: String? = null,
    val loyaltySalesCount: Int = 0,
    val loyaltySalesValue: Double = 0.0
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
