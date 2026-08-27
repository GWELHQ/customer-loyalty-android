package com.example.loyaltyapp.data.remote.api

import com.example.loyaltyapp.data.remote.dto.BootstrapResponseDto
import com.example.loyaltyapp.data.remote.dto.CustomerDto
import com.example.loyaltyapp.data.remote.dto.CustomerListResponseDto
import com.example.loyaltyapp.data.remote.dto.CustomerRegistrationRequestDto
import com.example.loyaltyapp.data.remote.dto.CustomerRegistrationResponseDto
import com.example.loyaltyapp.data.remote.dto.DailySummaryResponseDto
import com.example.loyaltyapp.data.remote.dto.LoginRequestDto
import com.example.loyaltyapp.data.remote.dto.LoginResponseDto
import com.example.loyaltyapp.data.remote.dto.NfcLoginRequestDto
import com.example.loyaltyapp.data.remote.dto.PagedSalesDto
import com.example.loyaltyapp.data.remote.dto.PriceDto
import com.example.loyaltyapp.data.remote.dto.SaleRequestDto
import com.example.loyaltyapp.data.remote.dto.SaleResponseDto
import com.example.loyaltyapp.data.remote.dto.SmsStatusReportDto
import com.example.loyaltyapp.data.remote.dto.SyncOperationDto
import com.example.loyaltyapp.data.remote.dto.SyncRequestDto
import com.example.loyaltyapp.data.remote.dto.SyncResponseDto
import com.example.loyaltyapp.data.remote.dto.VehiclePlateCheckDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Attendant login — the only unauthenticated mobile routes. */
interface AuthApi {
    @POST("auth/attendant/login")
    suspend fun login(@Body request: LoginRequestDto): LoginResponseDto

    /** Badge tap login (handover doc §3.1b) — same response shape/session as PIN login, tap-only, no PIN. */
    @POST("auth/attendant/nfc-login")
    suspend fun nfcLogin(@Body request: NfcLoginRequestDto): LoginResponseDto
}

/**
 * Every other mobile route, all attendant-authenticated and automatically scoped server-side
 * to the calling attendant's own identity and assigned station.
 */
interface MobileApi {
    @GET("mobile/bootstrap")
    suspend fun bootstrap(): BootstrapResponseDto

    @GET("mobile/customers/search")
    suspend fun searchCustomers(@Query("phone") phone: String): List<CustomerDto>

    /**
     * Full or incremental customer master-list sync, paginated. Pass [updatedSince] (ISO8601)
     * to fetch only customers changed since a prior full pull; omit for a full sync. Not
     * station-scoped, same as `customers/search`.
     */
    @GET("mobile/customers")
    suspend fun listCustomers(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("updatedSince") updatedSince: String? = null
    ): CustomerListResponseDto

    /** Resolves a scanned QR code (which simply encodes the customer's own id as plain text) to a customer. Throws HttpException(404) if the id doesn't exist. */
    @GET("mobile/customers/{id}")
    suspend fun getCustomerById(@Path("id") id: String): CustomerDto

    /** Resolves a tapped NFC tag's UID to a customer. Tag ids are matched case-insensitively server-side. Throws HttpException(404) if no customer has that tag assigned. */
    @GET("mobile/customers/nfc/{tagId}")
    suspend fun getCustomerByNfc(@Path("tagId") tagId: String): CustomerDto

    /**
     * Vehicle-plate photo verification — captured between customer selection and amount entry.
     * Never blocks the sale on a mismatch or failed OCR; the returned id is optionally carried
     * into `submitSale`/`sync` as `plateCheckId`.
     */
    @Multipart
    @POST("mobile/vehicle-plate-checks")
    suspend fun submitVehiclePlateCheck(
        @Part image: MultipartBody.Part,
        @Part("customerId") customerId: RequestBody
    ): VehiclePlateCheckDto

    // Keyed by product code, not an array — verified against the live server.
    @GET("mobile/prices/current")
    suspend fun currentPrices(): Map<String, PriceDto>

    @POST("mobile/sales")
    suspend fun submitSale(@Body request: SaleRequestDto): SaleResponseDto

    @POST("mobile/sync")
    suspend fun sync(@Body request: SyncRequestDto): SyncResponseDto

    @GET("mobile/sync-status")
    suspend fun syncStatus(): List<SyncOperationDto>

    @POST("mobile/customer-registrations")
    suspend fun registerCustomer(@Body request: CustomerRegistrationRequestDto): CustomerRegistrationResponseDto

    @GET("mobile/daily-summary")
    suspend fun dailySummary(@Query("date") date: String? = null): DailySummaryResponseDto

    @GET("mobile/sales/mine")
    suspend fun salesMine(@Query("date") date: String? = null): PagedSalesDto

    /**
     * Fire-and-forget: reports the outcome of this app's own direct-to-Africa's-Talking SMS send
     * (see `AfricasTalkingSmsSender`) so staff still see delivery status in the web admin app.
     * Raw [ResponseBody] return type deliberately skips response-body parsing entirely — nothing
     * the caller does depends on what comes back, only on the call not throwing.
     */
    @POST("mobile/sales/{id}/sms-status")
    suspend fun reportSmsStatus(@Path("id") saleId: String, @Body request: SmsStatusReportDto): Response<ResponseBody>
}
