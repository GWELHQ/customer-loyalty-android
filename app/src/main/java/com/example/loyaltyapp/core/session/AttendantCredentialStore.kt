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

/** A refresh token retained for one attendant, independent of whether they're currently signed into the UI. */
@Serializable
data class RetainedCredential(
    val attendantId: String,
    val employeeId: String,
    val fullName: String,
    val refreshToken: String
)

/**
 * Retains each attendant's refresh token (handover doc §3.1c/§5.6) across sign-out, keyed by
 * attendantId — separate from [SessionManager], which only ever holds the *one* attendant
 * currently signed into the UI. This is what lets a queued-but-unsynced sale/registration keep
 * syncing in the background after its attendant has been auto-logged-out (see
 * [AttendantSyncAuthenticator]), and it's why [SessionManager.clear] never touches this store: an
 * entry here is only ever removed once that attendant's queue is fully synced (see
 * [AttendantCredentialGarbageCollector]) or their refresh token itself is rejected server-side.
 */
@Singleton
class AttendantCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("loyalty_retained_credentials", Context.MODE_PRIVATE)

    @Synchronized
    fun upsert(dto: LoginResponseDto) {
        val map = readAll().toMutableMap()
        map[dto.attendant.attendantId] = RetainedCredential(
            attendantId = dto.attendant.attendantId,
            employeeId = dto.attendant.employeeId,
            fullName = dto.attendant.fullName,
            refreshToken = dto.refreshToken
        )
        writeAll(map)
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
