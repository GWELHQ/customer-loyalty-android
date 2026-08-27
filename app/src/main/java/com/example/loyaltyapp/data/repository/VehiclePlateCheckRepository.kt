package com.example.loyaltyapp.data.repository

import com.example.loyaltyapp.core.connectivity.ConnectivityObserver
import com.example.loyaltyapp.data.remote.api.MobileApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class VehiclePlateCheckResult(
    val id: String,
    val detectedPlateNumber: String?,
    val matched: Boolean
)

/**
 * The vehicle-plate photo step (handover doc §3): captured between customer selection and
 * amount entry, but never a gate on the sale — a failed/offline check simply means no
 * `plateCheckId` is carried into the eventual sale, and the flow moves on regardless.
 */
@Singleton
class VehiclePlateCheckRepository @Inject constructor(
    private val mobileApi: MobileApi,
    private val connectivityObserver: ConnectivityObserver
) {
    /** Null means "couldn't run the check" (offline, upload failure, timeout) — never surfaced as a blocking error to the attendant. */
    suspend fun submit(imageFile: File, customerId: String): VehiclePlateCheckResult? {
        if (!connectivityObserver.currentlyOnline()) return null
        return try {
            val imagePart = MultipartBody.Part.createFormData(
                "image",
                imageFile.name,
                imageFile.asRequestBody("image/jpeg".toMediaType())
            )
            val customerIdPart = customerId.toRequestBody("text/plain".toMediaType())
            val response = mobileApi.submitVehiclePlateCheck(imagePart, customerIdPart)
            VehiclePlateCheckResult(
                id = response.id,
                detectedPlateNumber = response.detectedPlateNumber,
                matched = response.matched
            )
        } catch (e: Exception) {
            android.util.Log.w("VehiclePlateCheckRepo", "submit($customerId) failed: ${e::class.simpleName} ${e.message}", e)
            null
        }
    }
}
