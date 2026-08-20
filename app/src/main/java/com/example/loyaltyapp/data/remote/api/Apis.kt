package com.example.loyaltyapp.data.remote.api

import com.example.loyaltyapp.data.remote.dto.BootstrapResponseDto
import com.example.loyaltyapp.data.remote.dto.CustomerDto
import com.example.loyaltyapp.data.remote.dto.CustomerRegistrationRequestDto
import com.example.loyaltyapp.data.remote.dto.CustomerRegistrationResponseDto
import com.example.loyaltyapp.data.remote.dto.DailySummaryResponseDto
import com.example.loyaltyapp.data.remote.dto.LoginRequestDto
import com.example.loyaltyapp.data.remote.dto.LoginResponseDto
import com.example.loyaltyapp.data.remote.dto.PagedSalesDto
import com.example.loyaltyapp.data.remote.dto.PriceDto
import com.example.loyaltyapp.data.remote.dto.SaleRequestDto
import com.example.loyaltyapp.data.remote.dto.SaleResponseDto
import com.example.loyaltyapp.data.remote.dto.SyncOperationDto
import com.example.loyaltyapp.data.remote.dto.SyncRequestDto
import com.example.loyaltyapp.data.remote.dto.SyncResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Attendant PIN login — the only unauthenticated mobile route. */
interface AuthApi {
    @POST("auth/attendant/login")
    suspend fun login(@Body request: LoginRequestDto): LoginResponseDto
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
}
