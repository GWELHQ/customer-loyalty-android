package com.example.loyaltyapp.core.session

import com.example.loyaltyapp.data.remote.api.AuthApi
import com.example.loyaltyapp.data.remote.dto.RefreshRequestDto
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of trying to obtain a bearer token to sync one attendant's queued work in the background. */
sealed interface TokenResult {
    /** [isForegroundSession] tells the caller which API variant to use — the implicit-auth one
     * for the attendant currently signed into the UI (matches today's behavior exactly), or the
     * explicit-header one otherwise (see [com.example.loyaltyapp.data.remote.api.MobileApi.syncAs]). */
    data class Available(val accessToken: String, val isForegroundSession: Boolean) : TokenResult

    /** The stored refresh token itself was rejected (expired/malformed/attendant deactivated) —
     * dropped from [AttendantCredentialStore] already; this attendant's queue stays put until they
     * do a fresh PIN login, at which point it flushes with no special-case code (see SaleRepository). */
    object SessionDead : TokenResult

    /** Transient failure (network, etc.) — retry on the next sync tick, credential kept. */
    object Unavailable : TokenResult
}

/**
 * Resolves a currently-usable access token for a given attendant, whether or not they're signed
 * into the UI right now — the backend-doc-mandated (§3.1c/§5.6) building block for syncing a
 * logged-out attendant's queued sales/registrations in the background.
 */
@Singleton
class AttendantSyncAuthenticator @Inject constructor(
    private val authApi: AuthApi,
    private val sessionManager: SessionManager,
    private val credentialStore: AttendantCredentialStore
) {
    suspend fun accessTokenFor(attendantId: String): TokenResult {
        sessionManager.currentSession()?.let { current ->
            if (current.attendantId == attendantId) {
                return TokenResult.Available(current.accessToken, isForegroundSession = true)
            }
        }

        val stored = credentialStore.get(attendantId) ?: return TokenResult.Unavailable
        return try {
            val dto = authApi.refresh(RefreshRequestDto(stored.refreshToken))
            credentialStore.upsertAfterRefresh(dto)
            TokenResult.Available(dto.accessToken, isForegroundSession = false)
        } catch (e: HttpException) {
            if (e.code() == 401) {
                credentialStore.remove(attendantId)
                TokenResult.SessionDead
            } else {
                TokenResult.Unavailable
            }
        } catch (e: Exception) {
            TokenResult.Unavailable
        }
    }
}
