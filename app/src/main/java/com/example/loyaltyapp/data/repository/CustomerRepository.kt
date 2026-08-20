package com.example.loyaltyapp.data.repository

import com.example.loyaltyapp.core.connectivity.ConnectivityObserver
import com.example.loyaltyapp.core.phone.PhoneNumber
import com.example.loyaltyapp.core.session.SessionManager
import com.example.loyaltyapp.data.local.dao.CustomerDao
import com.example.loyaltyapp.data.local.entity.CustomerEntity
import com.example.loyaltyapp.data.remote.api.MobileApi
import com.example.loyaltyapp.data.remote.dto.CustomerDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The documented API rate limit is 120 requests/60s, shared across every endpoint the app calls
 * — not just search. This spaces sweep requests out to well under that (~85/min), leaving
 * headroom for whatever else the attendant is doing (a sale submit, bootstrap, etc.) at the same
 * time.
 */
private const val DIRECTORY_SWEEP_DELAY_MILLIS = 700L

/** How long to back off after an unexpected 429 (the sweep is already throttled, so this should be rare) before retrying that one prefix. */
private const val RATE_LIMIT_BACKOFF_MILLIS = 8_000L

/**
 * One sweep call processes this many prefixes then returns — at ~700ms/request that's roughly
 * 4-5 minutes, safely inside a background worker's execution window. A full 2000-prefix pass
 * takes several such calls; [SessionManager.directorySweepIndex] tracks where the last one left
 * off so the periodic worker (see [com.example.loyaltyapp.sync.SyncScheduler]) resumes rather
 * than restarts each time.
 */
private const val DIRECTORY_SWEEP_CHUNK_SIZE = 400

@Singleton
class CustomerRepository @Inject constructor(
    private val customerDao: CustomerDao,
    private val mobileApi: MobileApi,
    private val connectivityObserver: ConnectivityObserver,
    private val sessionManager: SessionManager
) {
    // The periodic and "right after login" one-shot sweep requests have different unique
    // WorkManager names by design (so login always gets an immediate kick, independent of the
    // periodic schedule) — which means they can legally run concurrently. Without this guard,
    // two concurrent sweeps would both read the same resume index, redundantly re-sweep the same
    // chunk, and race to write the index back, corrupting resumption. Safe as a plain in-memory
    // flag: both worker runs execute in this app process and share this singleton instance.
    private val sweepInProgress = AtomicBoolean(false)

    /**
     * Cached-first list of customers matching the digits typed so far — filters immediately from
     * the first digit entered, entirely against the local Room cache, so it stays fast and never
     * fires a network call while the attendant is still typing.
     */
    fun searchLocal(digits: String): Flow<List<CustomerEntity>> =
        if (digits.isNotEmpty()) customerDao.searchByPhoneFragment(digits) else customerDao.recent(3)

    /**
     * Builds/refreshes a *complete* local customer cache, one bounded chunk of prefixes at a
     * time. There is no bulk "list customers" endpoint for attendants (the admin `/customers`
     * list explicitly rejects attendant tokens — "staff sessions only"), and
     * `/mobile/customers/search` requires a 4+ digit prefix. So this sweeps every possible
     * 4-digit national prefix (`7000`–`7999`, `1000`–`1999` — the full space of valid Kenyan
     * mobile numbers), throttled to stay under the documented rate limit, and caches whatever
     * comes back. Call this once after login (so a fresh device starts building its cache right
     * away) and periodically thereafter (see [com.example.loyaltyapp.sync.SyncScheduler]) — each
     * call covers the next chunk and wraps around to prefix 0 once a full pass completes.
     */
    suspend fun refreshFullDirectory() {
        if (!connectivityObserver.currentlyOnline()) return
        if (!sweepInProgress.compareAndSet(false, true)) return // another sweep is already running
        try {
            val allPrefixes = (0..999).map { "7%03d".format(it) } + (0..999).map { "1%03d".format(it) }
            val startIndex = sessionManager.directorySweepIndex().coerceIn(0, allPrefixes.lastIndex)
            val endIndex = minOf(startIndex + DIRECTORY_SWEEP_CHUNK_SIZE, allPrefixes.size)

            for (i in startIndex until endIndex) {
                if (!connectivityObserver.currentlyOnline()) break
                val results = fetchWithRateLimitRetry(allPrefixes[i])
                if (!results.isNullOrEmpty()) customerDao.upsertAll(results.map { it.toEntity() })
                delay(DIRECTORY_SWEEP_DELAY_MILLIS)
            }

            // Wrap around once a full pass completes, so the sweep keeps refreshing rather than stopping.
            sessionManager.saveDirectorySweepIndex(if (endIndex >= allPrefixes.size) 0 else endIndex)
        } finally {
            sweepInProgress.set(false)
        }
    }

    /** Null means "give up on this prefix for now" — a future sweep pass will retry it; never throws. */
    private suspend fun fetchWithRateLimitRetry(prefix: String): List<CustomerDto>? {
        repeat(2) { attempt ->
            try {
                return mobileApi.searchCustomers(prefix)
            } catch (e: HttpException) {
                if (e.code() == 429 && attempt == 0) {
                    delay(RATE_LIMIT_BACKOFF_MILLIS)
                } else {
                    return null
                }
            } catch (_: Exception) {
                return null
            }
        }
        return null
    }

    /** Full-number, online, authoritative lookup — used to decide whether to route to registration. */
    suspend fun searchRemoteExact(phoneDigits: String): List<CustomerEntity>? {
        if (!connectivityObserver.currentlyOnline()) return null
        return try {
            val remote = mobileApi.searchCustomers(phoneDigits)
            val entities = remote.map { it.toEntity() }
            customerDao.upsertAll(entities)
            entities
        } catch (_: Exception) {
            null
        }
    }

    suspend fun findByPhone(phoneNumber: String): CustomerEntity? = customerDao.findByPhone(phoneNumber)

    private fun CustomerDto.toEntity(): CustomerEntity {
        val normalized = runCatching { PhoneNumber.normalize(phoneNumber) }.getOrNull()
        val now = System.currentTimeMillis()
        return CustomerEntity(
            id = id,
            phoneNumber = normalized?.e164 ?: phoneNumber,
            fullName = fullName,
            homeStationId = homeStationId,
            specialRateKesPerLitre = specialRateKesPerLitre?.let { java.math.BigDecimal(it.toString()) },
            specialRateEffectiveFrom = specialRateEffectiveFrom?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
            specialRateEffectiveTo = specialRateEffectiveTo?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
            totalCashbackEarned = java.math.BigDecimal(totalCashbackEarned.toString()),
            updatedAtMillis = updatedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: now
        )
    }
}
