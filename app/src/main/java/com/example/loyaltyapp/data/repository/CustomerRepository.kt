package com.example.loyaltyapp.data.repository

import com.example.loyaltyapp.core.connectivity.ConnectivityObserver
import com.example.loyaltyapp.core.phone.PhoneNumber
import com.example.loyaltyapp.core.session.SessionManager
import com.example.loyaltyapp.data.local.dao.CustomerDao
import com.example.loyaltyapp.data.local.entity.CustomerEntity
import com.example.loyaltyapp.data.remote.api.MobileApi
import com.example.loyaltyapp.data.remote.dto.CustomerDto
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Default page size for `GET /mobile/customers` — matches the server default; under 1,000 total loyalty customers, one page covers a full sync. */
private const val CUSTOMER_SYNC_PAGE_LIMIT = 500

@Singleton
class CustomerRepository @Inject constructor(
    private val customerDao: CustomerDao,
    private val mobileApi: MobileApi,
    private val connectivityObserver: ConnectivityObserver,
    private val sessionManager: SessionManager
) {
    // The periodic and "right after login" one-shot sync requests have different unique
    // WorkManager names by design (so login always gets an immediate kick, independent of the
    // periodic schedule) — which means they can legally run concurrently. This guard just avoids
    // two redundant full syncs racing each other; safe as a plain in-memory flag since both
    // worker runs execute in this app process and share this singleton instance.
    private val syncInProgress = AtomicBoolean(false)

    /**
     * Cached-first list of customers matching the digits typed so far — filters immediately from
     * the first digit entered, entirely against the local Room cache, so it stays fast and never
     * fires a network call while the attendant is still typing.
     */
    fun searchLocal(digits: String): Flow<List<CustomerEntity>> =
        if (digits.isNotEmpty()) customerDao.searchByPhoneFragment(digits) else customerDao.recent(3)

    /**
     * Pulls the complete customer master list via `GET /mobile/customers`, paginating on
     * [CustomerListResponseDto.nextCursor] until exhausted. The first call after install (or
     * after this device has never synced) is a full pull; every call after that passes
     * `updatedSince` (the start time of the last successful sync) so it only fetches what
     * changed — cheap enough to run on every login and every periodic tick (see
     * [com.example.loyaltyapp.sync.SyncScheduler]), which is what actually keeps admin-side edits
     * (e.g. a customer's plate number) from going stale on the phone.
     */
    suspend fun syncCustomers() {
        if (!connectivityObserver.currentlyOnline()) return
        if (!syncInProgress.compareAndSet(false, true)) return // another sync is already running
        try {
            val updatedSince = sessionManager.lastCustomerSyncAtMillis()
                ?.let { Instant.ofEpochMilli(it).toString() }
            val syncStartedAtMillis = System.currentTimeMillis()

            var cursor: String? = null
            do {
                val response = mobileApi.listCustomers(
                    cursor = cursor,
                    limit = CUSTOMER_SYNC_PAGE_LIMIT,
                    updatedSince = updatedSince
                )
                // A soft-deleted customer (server sets deletedAt instead of hard-deleting, so
                // this delta pull actually sees it as a change) is removed locally instead of
                // upserted — otherwise it would linger in the cache forever on any device that
                // had already synced it.
                val (deleted, active) = response.items.partition { it.deletedAt != null }
                deleted.forEach { customerDao.deleteById(it.id) }
                if (active.isNotEmpty()) customerDao.upsertAll(active.map { it.toEntity() })
                cursor = response.nextCursor
            } while (cursor != null)

            sessionManager.saveLastCustomerSyncAtMillis(syncStartedAtMillis)
        } finally {
            syncInProgress.set(false)
        }
    }

    /** Full-number, online, authoritative lookup — used to decide whether to route to registration. */
    suspend fun searchRemoteExact(phoneDigits: String): List<CustomerEntity>? {
        if (!connectivityObserver.currentlyOnline()) return null
        return try {
            val remote = mobileApi.searchCustomers(phoneDigits)
            val entities = remote.map { it.toEntity() }
            customerDao.upsertAll(entities)
            entities
        } catch (e: Exception) {
            // Swallowed deliberately (callers treat null as "couldn't confirm, don't say not
            // found") but logged so a real, recurring failure — bad token, wrong host, timeout —
            // is visible in Logcat instead of silently looking like "the phone is offline."
            android.util.Log.w("CustomerRepository", "searchRemoteExact($phoneDigits) failed: ${e::class.simpleName} ${e.message}", e)
            null
        }
    }

    suspend fun findByPhone(phoneNumber: String): CustomerEntity? = customerDao.findByPhone(phoneNumber)

    /**
     * Resolves a scanned QR code (the code is simply the customer's own id) — network-first when
     * online so an admin-side edit (e.g. a renamed customer) is never shown stale just because
     * this device already had that customer cached; falls back to the local cache when offline or
     * when the request fails for a reason other than "definitely doesn't exist," and to a fresh
     * remote lookup so a code minted after this device's last sync still works. A 404 (customer
     * deleted, or a soft-deleted customer since the backend's own findById treats those as
     * not-found too) is NOT a fall-back case — it means the customer is genuinely gone, so the
     * stale cached row is dropped instead of being returned. Null means "not found or couldn't
     * reach the office"; callers can't distinguish the two, matching how a QR code that doesn't
     * resolve should be handled either way (ask to retry / use phone lookup instead — there's
     * nothing to "confirm not found" here the way phone search has).
     */
    suspend fun findById(customerId: String): CustomerEntity? {
        if (connectivityObserver.currentlyOnline()) {
            try {
                val remote = mobileApi.getCustomerById(customerId)
                val entity = remote.toEntity()
                customerDao.upsert(entity)
                return entity
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 404) {
                    customerDao.deleteById(customerId)
                    return null
                }
                android.util.Log.w("CustomerRepository", "findById($customerId) failed: ${e::class.simpleName} ${e.message}", e)
                // fall through to the local cache below
            } catch (e: Exception) {
                android.util.Log.w("CustomerRepository", "findById($customerId) failed: ${e::class.simpleName} ${e.message}", e)
                // fall through to the local cache below
            }
        }
        return customerDao.getById(customerId)
    }

    /** Resolves a tapped NFC tag's UID to a customer. Always a live lookup — tags are assigned from the web admin, so there's nothing useful to cache locally by tag id. */
    suspend fun findByNfcTag(tagId: String): CustomerEntity? {
        if (!connectivityObserver.currentlyOnline()) return null
        return try {
            val remote = mobileApi.getCustomerByNfc(tagId)
            val entity = remote.toEntity()
            customerDao.upsert(entity)
            entity
        } catch (e: Exception) {
            android.util.Log.w("CustomerRepository", "findByNfcTag($tagId) failed: ${e::class.simpleName} ${e.message}", e)
            null
        }
    }

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
            updatedAtMillis = updatedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: now,
            licensePlateNumbers = licensePlateNumbers,
            nfcTagId = nfcTagId
        )
    }
}
