package com.example.loyaltyapp.data.repository

import com.example.loyaltyapp.core.connectivity.ConnectivityObserver
import com.example.loyaltyapp.core.result.AppResult
import com.example.loyaltyapp.core.session.AttendantCredentialStore
import com.example.loyaltyapp.core.session.AttendantSession
import com.example.loyaltyapp.core.session.OfflineLoginResult
import com.example.loyaltyapp.core.session.SessionManager
import com.example.loyaltyapp.data.remote.NetworkErrors
import com.example.loyaltyapp.data.remote.api.AuthApi
import com.example.loyaltyapp.data.remote.dto.LoginRequestDto
import com.example.loyaltyapp.data.remote.dto.NfcLoginRequestDto
import kotlinx.coroutines.flow.StateFlow
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val sessionManager: SessionManager,
    private val credentialStore: AttendantCredentialStore,
    private val connectivityObserver: ConnectivityObserver
) {
    val session: StateFlow<AttendantSession?> = sessionManager.session

    fun currentSession(): AttendantSession? = sessionManager.currentSession()

    fun isSessionExpired(): Boolean = sessionManager.isExpired()

    /**
     * Online path is tried first whenever the device looks reachable; a genuinely offline device,
     * or an [IOException] on the online attempt (network looked up but this call still failed —
     * flaky connection), falls back to [loginOffline] rather than stranding the attendant —
     * critical once auto-logout-after-every-sale means re-authenticating constantly through a
     * shift, including while offline, which is this app's whole reason for existing.
     */
    suspend fun login(employeeId: String, pin: String): AppResult<AttendantSession> {
        val trimmedId = employeeId.trim()
        val trimmedPin = pin.trim()
        if (!connectivityObserver.currentlyOnline()) {
            return loginOffline(trimmedId, trimmedPin)
        }
        return try {
            val dto = authApi.login(LoginRequestDto(employeeId = trimmedId, pin = trimmedPin))
            sessionManager.save(dto, capturedAtMillis = System.currentTimeMillis())
            credentialStore.upsertAfterLogin(dto, trimmedPin)
            AppResult.Success(sessionManager.currentSession()!!)
        } catch (e: HttpException) {
            AppResult.Failure(NetworkErrors.messageFor(e, "Could not sign in. Check your employee ID and PIN."), e)
        } catch (e: IOException) {
            loginOffline(trimmedId, trimmedPin)
        } catch (e: Exception) {
            AppResult.Failure("Something went wrong signing in. Please try again.", e)
        }
    }

    /**
     * Verifies [pin] against the hash captured at this attendant's last online login on this
     * device (see [AttendantCredentialStore.verifyPinOffline]) — no network call. `WrongPin` and
     * `NoLocalRecord` deliberately share one message: a distinct "this device doesn't know that
     * employee ID" response would let someone offline-enumerate which employee IDs are real.
     */
    private fun loginOffline(employeeId: String, pin: String): AppResult<AttendantSession> {
        return when (val result = credentialStore.verifyPinOffline(employeeId, pin)) {
            is OfflineLoginResult.Success -> {
                sessionManager.saveOffline(result.credential, capturedAtMillis = System.currentTimeMillis())
                AppResult.Success(sessionManager.currentSession()!!)
            }
            OfflineLoginResult.WrongPin, OfflineLoginResult.NoLocalRecord ->
                AppResult.Failure("Invalid employee ID or PIN.")
            is OfflineLoginResult.LockedOut -> AppResult.Failure(lockedOutMessage(result.untilMillis))
        }
    }

    private fun lockedOutMessage(untilMillis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(untilMillis - System.currentTimeMillis() + 999).coerceAtLeast(1)
        return "Too many wrong PINs. Try again in $minutes minute${if (minutes == 1L) "" else "s"}."
    }

    /**
     * Badge tap login (handover doc §3.1b) — same session type/TTL as PIN login, just a second
     * way to obtain one. Deliberately weaker than a PIN (tag UID alone is the credential); the
     * server is the sole gatekeeper on whether a tag is currently assigned to an active attendant.
     */
    suspend fun loginWithNfcTag(tagId: String): AppResult<AttendantSession> {
        return try {
            val dto = authApi.nfcLogin(NfcLoginRequestDto(tagId = tagId))
            sessionManager.save(dto, capturedAtMillis = System.currentTimeMillis())
            credentialStore.upsertAfterLogin(dto, pin = null)
            AppResult.Success(sessionManager.currentSession()!!)
        } catch (e: HttpException) {
            AppResult.Failure(NetworkErrors.messageFor(e, "Badge not recognized. Contact your supervisor, or sign in with your employee ID and PIN."), e)
        } catch (e: IOException) {
            AppResult.Failure("Could not reach the office. Check your connection and try again.", e)
        } catch (e: Exception) {
            AppResult.Failure("Something went wrong signing in. Please try again.", e)
        }
    }

    /**
     * Signs the attendant out of the UI only — deliberately does NOT touch
     * [AttendantCredentialStore]. Any pending sales/registrations keep syncing in the background
     * via that attendant's retained refresh token (see AttendantSyncAuthenticator) even after
     * this returns; nothing is discarded.
     */
    fun signOut() {
        sessionManager.clear()
    }
}
