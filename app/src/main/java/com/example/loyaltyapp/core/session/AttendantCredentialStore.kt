package com.example.loyaltyapp.core.session

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.loyaltyapp.data.remote.dto.LoginResponseDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val LOCKOUT_THRESHOLD_ATTEMPTS = 5
private const val LOCKOUT_DURATION_MILLIS = 15L * 60 * 1000

/**
 * A refresh token and offline-login material retained for one attendant, independent of whether
 * they're currently signed into the UI.
 */
@Serializable
data class RetainedCredential(
    val attendantId: String,
    val employeeId: String,
    val fullName: String,
    val assignedStationId: String,
    val refreshToken: String,
    /** Most recent real accessToken — seeded into an offline-verified session as a placeholder; see [SessionManager.saveOffline]. */
    val lastAccessToken: String,
    /** Null for an NFC-only login (no PIN was ever entered on this device) — [verifyPinOffline] then reports [OfflineLoginResult.NoLocalRecord]. */
    val pinSaltBase64: String? = null,
    val pinHashBase64: String? = null,
    val failedPinAttempts: Int = 0,
    val lockedUntilMillis: Long = 0L
)

/** Outcome of [AttendantCredentialStore.verifyPinOffline]. */
sealed interface OfflineLoginResult {
    data class Success(val credential: RetainedCredential) : OfflineLoginResult
    object WrongPin : OfflineLoginResult
    data class LockedOut(val untilMillis: Long) : OfflineLoginResult
    /** No PIN hash on this device for that employee ID — either never logged in here, or only ever via NFC. */
    object NoLocalRecord : OfflineLoginResult
}

/**
 * Retains each attendant's refresh token and offline-login material (handover doc §3.1c/§5.6),
 * keyed by attendantId — separate from [SessionManager], which only ever holds the *one* attendant
 * currently signed into the UI. This is what lets a queued-but-unsynced sale/registration keep
 * syncing in the background after its attendant has been auto-logged-out (see
 * [AttendantSyncAuthenticator]), and what lets that same attendant log back in with no
 * connectivity at all (see [verifyPinOffline]). [SessionManager.clear] never touches this store,
 * and an entry here is retained indefinitely — the only removal path is a refresh token being
 * rejected server-side (`SessionDead` in [AttendantSyncAuthenticator]), never "nothing pending to
 * sync right now," since a dormant attendant may still need to log in again on a future shift.
 */
@Singleton
class AttendantCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("loyalty_retained_credentials", Context.MODE_PRIVATE)

    /**
     * Called after a real login. [pin] is the just-verified plaintext PIN for a PIN login (hashed
     * here, never stored raw) — pass `null` for an NFC login, which leaves any existing PIN
     * hash/lockout state untouched (NFC login proves nothing about the PIN).
     */
    @Synchronized
    fun upsertAfterLogin(dto: LoginResponseDto, pin: String?) {
        val map = readAll().toMutableMap()
        val existing = map[dto.attendant.attendantId]
        val salt = if (pin != null) PinHasher.newSalt() else existing?.pinSaltBase64
        val hash = if (pin != null && salt != null) PinHasher.hash(pin, salt) else existing?.pinHashBase64
        map[dto.attendant.attendantId] = RetainedCredential(
            attendantId = dto.attendant.attendantId,
            employeeId = dto.attendant.employeeId,
            fullName = dto.attendant.fullName,
            assignedStationId = dto.attendant.assignedStationId,
            refreshToken = dto.refreshToken,
            lastAccessToken = dto.accessToken,
            pinSaltBase64 = salt,
            pinHashBase64 = hash,
            failedPinAttempts = if (pin != null) 0 else existing?.failedPinAttempts ?: 0,
            lockedUntilMillis = if (pin != null) 0L else existing?.lockedUntilMillis ?: 0L
        )
        writeAll(map)
    }

    /** Called after a silent background refresh (see [AttendantSyncAuthenticator]) — no PIN involved, so PIN hash and lockout state carry over unchanged. */
    @Synchronized
    fun upsertAfterRefresh(dto: LoginResponseDto) {
        val map = readAll().toMutableMap()
        val existing = map[dto.attendant.attendantId]
        map[dto.attendant.attendantId] = RetainedCredential(
            attendantId = dto.attendant.attendantId,
            employeeId = dto.attendant.employeeId,
            fullName = dto.attendant.fullName,
            assignedStationId = dto.attendant.assignedStationId,
            refreshToken = dto.refreshToken,
            lastAccessToken = dto.accessToken,
            pinSaltBase64 = existing?.pinSaltBase64,
            pinHashBase64 = existing?.pinHashBase64,
            failedPinAttempts = existing?.failedPinAttempts ?: 0,
            lockedUntilMillis = existing?.lockedUntilMillis ?: 0L
        )
        writeAll(map)
    }

    /**
     * Verifies [pin] for [employeeId] against the locally stored hash, with a lockout mirroring
     * the server's own documented policy (5 consecutive wrong PINs → 15 minutes) — a PIN's real
     * entropy is tiny, so this lockout, not hashing cost, is what actually throttles an on-device
     * brute force through the UI.
     */
    @Synchronized
    fun verifyPinOffline(employeeId: String, pin: String): OfflineLoginResult {
        val map = readAll().toMutableMap()
        val entry = map.values.firstOrNull { it.employeeId == employeeId } ?: return OfflineLoginResult.NoLocalRecord
        val salt = entry.pinSaltBase64
        val hash = entry.pinHashBase64
        if (salt == null || hash == null) return OfflineLoginResult.NoLocalRecord

        val now = System.currentTimeMillis()
        if (entry.lockedUntilMillis > now) return OfflineLoginResult.LockedOut(entry.lockedUntilMillis)

        return if (PinHasher.matches(pin, salt, hash)) {
            map[entry.attendantId] = entry.copy(failedPinAttempts = 0, lockedUntilMillis = 0L)
            writeAll(map)
            OfflineLoginResult.Success(entry)
        } else {
            val attempts = entry.failedPinAttempts + 1
            val lockedUntil = if (attempts >= LOCKOUT_THRESHOLD_ATTEMPTS) now + LOCKOUT_DURATION_MILLIS else 0L
            map[entry.attendantId] = entry.copy(failedPinAttempts = attempts, lockedUntilMillis = lockedUntil)
            writeAll(map)
            if (lockedUntil > 0L) OfflineLoginResult.LockedOut(lockedUntil) else OfflineLoginResult.WrongPin
        }
    }

    @Synchronized
    fun get(attendantId: String): RetainedCredential? = readAll()[attendantId]

    @Synchronized
    fun all(): List<RetainedCredential> = readAll().values.toList()

    @Synchronized
    fun remove(attendantId: String) {
        val map = readAll().toMutableMap()
        if (map.remove(attendantId) != null) writeAll(map)
    }

    private fun readAll(): Map<String, RetainedCredential> {
        val raw = prefs.getString(KEY_BLOB, null) ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, RetainedCredential>>(raw) }.getOrDefault(emptyMap())
    }

    private fun writeAll(map: Map<String, RetainedCredential>) {
        prefs.edit(commit = true) { putString(KEY_BLOB, json.encodeToString(map)) }
    }

    private companion object {
        const val KEY_BLOB = "retained_credentials_json"
    }
}
