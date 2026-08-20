package com.example.loyaltyapp.core.session

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.loyaltyapp.data.remote.dto.LoginResponseDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AttendantSession(
    val attendantId: String,
    val employeeId: String,
    val fullName: String,
    val assignedStationId: String,
    val accessToken: String,
    val tokenIssuedAtMillis: Long
)

/** Server-documented attendant JWT lifetime; used only as a client-side courtesy check. */
private const val ATTENDANT_JWT_TTL_MILLIS = 12L * 60 * 60 * 1000

/** Persists the signed-in attendant's session so the app restores it across cold starts. */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("loyalty_session", Context.MODE_PRIVATE)

    private val _session = MutableStateFlow(readFromPrefs())
    val session: StateFlow<AttendantSession?> = _session

    fun currentSession(): AttendantSession? = _session.value

    fun currentAccessToken(): String? = _session.value?.accessToken

    fun save(dto: LoginResponseDto, capturedAtMillis: Long) {
        prefs.edit {
            putString(KEY_ATTENDANT_ID, dto.attendant.attendantId)
            putString(KEY_EMPLOYEE_ID, dto.attendant.employeeId)
            putString(KEY_FULL_NAME, dto.attendant.fullName)
            putString(KEY_STATION_ID, dto.attendant.assignedStationId)
            putString(KEY_TOKEN, dto.accessToken)
            putLong(KEY_ISSUED_AT, capturedAtMillis)
        }
        _session.value = readFromPrefs()
    }

    fun clear() {
        prefs.edit { clear() }
        _session.value = null
    }

    /** Bumped by the backend whenever bootstrap-relevant reference data changes shape. */
    fun cachedConfigVersion(): Int = prefs.getInt(KEY_CONFIG_VERSION, -1)

    fun saveConfigVersion(version: Int) {
        prefs.edit { putInt(KEY_CONFIG_VERSION, version) }
    }

    /**
     * Where the customer-directory sweep left off (an index into its prefix list) — the sweep
     * is rate-limit-bound (see [com.example.loyaltyapp.data.repository.CustomerRepository]) and
     * spans multiple short worker runs rather than one long one, so it needs to resume rather
     * than restart from zero every time.
     */
    fun directorySweepIndex(): Int = prefs.getInt(KEY_DIRECTORY_SWEEP_INDEX, 0)

    fun saveDirectorySweepIndex(index: Int) {
        prefs.edit { putInt(KEY_DIRECTORY_SWEEP_INDEX, index) }
    }

    /** True once the client-known 12h TTL has elapsed — a courtesy check; the server is authoritative via 401. */
    fun isExpired(): Boolean {
        val session = _session.value ?: return true
        return System.currentTimeMillis() - session.tokenIssuedAtMillis >= ATTENDANT_JWT_TTL_MILLIS
    }

    private fun readFromPrefs(): AttendantSession? {
        val attendantId = prefs.getString(KEY_ATTENDANT_ID, null) ?: return null
        return AttendantSession(
            attendantId = attendantId,
            employeeId = prefs.getString(KEY_EMPLOYEE_ID, "") ?: "",
            fullName = prefs.getString(KEY_FULL_NAME, "") ?: "",
            assignedStationId = prefs.getString(KEY_STATION_ID, "") ?: "",
            accessToken = prefs.getString(KEY_TOKEN, "") ?: "",
            tokenIssuedAtMillis = prefs.getLong(KEY_ISSUED_AT, 0L)
        )
    }

    private companion object {
        const val KEY_ATTENDANT_ID = "attendant_id"
        const val KEY_EMPLOYEE_ID = "employee_id"
        const val KEY_FULL_NAME = "full_name"
        const val KEY_STATION_ID = "station_id"
        const val KEY_TOKEN = "auth_token"
        const val KEY_ISSUED_AT = "token_issued_at"
        const val KEY_CONFIG_VERSION = "config_version"
        const val KEY_DIRECTORY_SWEEP_INDEX = "directory_sweep_index"
    }
}
