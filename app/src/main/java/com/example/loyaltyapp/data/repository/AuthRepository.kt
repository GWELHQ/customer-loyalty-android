package com.example.loyaltyapp.data.repository

import com.example.loyaltyapp.core.result.AppResult
import com.example.loyaltyapp.core.session.AttendantSession
import com.example.loyaltyapp.core.session.SessionManager
import com.example.loyaltyapp.data.remote.NetworkErrors
import com.example.loyaltyapp.data.remote.api.AuthApi
import com.example.loyaltyapp.data.remote.dto.LoginRequestDto
import kotlinx.coroutines.flow.StateFlow
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val sessionManager: SessionManager
) {
    val session: StateFlow<AttendantSession?> = sessionManager.session

    fun currentSession(): AttendantSession? = sessionManager.currentSession()

    fun isSessionExpired(): Boolean = sessionManager.isExpired()

    suspend fun login(employeeId: String, pin: String): AppResult<AttendantSession> {
        return try {
            val dto = authApi.login(LoginRequestDto(employeeId = employeeId.trim(), pin = pin.trim()))
            sessionManager.save(dto, capturedAtMillis = System.currentTimeMillis())
            AppResult.Success(sessionManager.currentSession()!!)
        } catch (e: HttpException) {
            AppResult.Failure(NetworkErrors.messageFor(e, "Could not sign in. Check your employee ID and PIN."), e)
        } catch (e: IOException) {
            AppResult.Failure("Could not reach the office. Check your connection and try again.", e)
        } catch (e: Exception) {
            AppResult.Failure("Something went wrong signing in. Please try again.", e)
        }
    }

    /**
     * Signs the attendant out. Callers must confirm with the attendant first when there are
     * pending sales still on this device — sign-out never discards them, but this app only
     * reaches the office from the device that captured the sale.
     */
    fun signOut() {
        sessionManager.clear()
    }
}
