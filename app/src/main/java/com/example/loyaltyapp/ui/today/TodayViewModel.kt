package com.example.loyaltyapp.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loyaltyapp.core.connectivity.ConnectivityObserver
import com.example.loyaltyapp.core.notify.RegistrationApprovalNotifier
import com.example.loyaltyapp.data.local.entity.CustomerRegistrationEntity
import com.example.loyaltyapp.data.local.entity.Product
import com.example.loyaltyapp.data.local.entity.SaleEntity
import com.example.loyaltyapp.data.repository.AuthRepository
import com.example.loyaltyapp.data.repository.CustomerRegistrationRepository
import com.example.loyaltyapp.data.repository.SaleRepository
import com.example.loyaltyapp.data.repository.StationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** A row in "Today's sales" — either a confirmed sale or a new-customer request still awaiting approval. */
sealed class TodayListItem {
    abstract val capturedAtMillis: Long

    data class SaleItem(val sale: SaleEntity) : TodayListItem() {
        override val capturedAtMillis get() = sale.capturedAtMillis
    }

    data class PendingRegistrationItem(val registration: CustomerRegistrationEntity) : TodayListItem() {
        override val capturedAtMillis get() = registration.capturedAtMillis
    }
}

/**
 * Deliberately excludes cashback totals and any comparison against total pump sales — those
 * figures never reach the attendant UI (see [com.example.loyaltyapp.ui.today.TodayScreen]);
 * showing them would let an attendant and a customer coordinate to game the loyalty program.
 * The sales-must-not-exceed-total invariant is enforced by the backend at sync time, not here.
 *
 * [items] merges confirmed sales with still-pending new-customer registrations (newest first) so
 * an attendant can see a request they just submitted without it looking like a confirmed sale —
 * [loyaltySalesCount]/[loyaltySalesValue]/[pmsValue]/[agoValue] are computed from [sales] only,
 * since a pending registration isn't a real sale yet.
 */
data class TodayUiState(
    val stationName: String = "",
    val attendantName: String = "",
    val isOnline: Boolean = true,
    val sales: List<SaleEntity> = emptyList(),
    val pendingRegistrations: List<CustomerRegistrationEntity> = emptyList(),
    val items: List<TodayListItem> = emptyList(),
    val loyaltySalesCount: Int = 0,
    val loyaltySalesValue: BigDecimal = BigDecimal.ZERO,
    val pmsValue: BigDecimal = BigDecimal.ZERO,
    val agoValue: BigDecimal = BigDecimal.ZERO,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val saleRepository: SaleRepository,
    private val customerRegistrationRepository: CustomerRegistrationRepository,
    private val registrationApprovalNotifier: RegistrationApprovalNotifier,
    private val authRepository: AuthRepository,
    private val stationRepository: StationRepository,
    private val connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        val session = authRepository.currentSession()
        if (session != null) {
            _uiState.update { it.copy(attendantName = session.fullName) }
            viewModelScope.launch {
                val station = stationRepository.getById(session.assignedStationId)
                station?.let { s -> _uiState.update { it.copy(stationName = s.name) } }
            }
            viewModelScope.launch {
                connectivityObserver.isOnline.collectLatest { online ->
                    _uiState.update { it.copy(isOnline = online) }
                }
            }
            // Each device's Room DB only ever gets written to by its own capture/sync calls —
            // without this, a second device signed into the same attendant account would never
            // see a sale recorded on the first one. Fires once per screen open, using the same
            // full sync as pull-to-refresh (not just the download half) so opening this tab also
            // flushes anything still stuck locally and catches any approval that landed while the
            // attendant was elsewhere — not just newly available server-side data.
            viewModelScope.launch {
                syncFromServer()
            }
            val startOfDay = LocalDate.now(ZoneId.systemDefault())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            viewModelScope.launch {
                combine(
                    saleRepository.observeTodayForAttendant(session.attendantId),
                    customerRegistrationRepository.observeTodayUnapproved(startOfDay)
                ) { sales, pending -> sales to pending }
                    .collectLatest { (sales, pending) ->
                        val value = sales.sumOf { it.amountPaidKes }
                        val pms = sales.filter { it.product == Product.PMS }.sumOf { it.amountPaidKes }
                        val ago = sales.filter { it.product == Product.AGO }.sumOf { it.amountPaidKes }
                        val items = (sales.map { TodayListItem.SaleItem(it) } +
                            pending.map { TodayListItem.PendingRegistrationItem(it) })
                            .sortedByDescending { it.capturedAtMillis }
                        _uiState.update {
                            it.copy(
                                sales = sales,
                                pendingRegistrations = pending,
                                items = items,
                                loyaltySalesCount = sales.size,
                                loyaltySalesValue = value,
                                pmsValue = pms,
                                agoValue = ago
                            )
                        }
                    }
            }
        }
    }

    /**
     * Pull-to-refresh: checks whether any pending new-customer registration has since been
     * approved (see `CustomerRegistrationRepository.reconcileApprovals`), flushes any sales still
     * waiting to sync, and pulls down any sale recorded on a *different* device signed into this
     * same attendant account — so "stuck on pending approval", "approved sale missing from
     * Today", and "sale from my other phone isn't showing" all resolve without waiting for the
     * next background sync.
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                syncFromServer()
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private suspend fun syncFromServer() {
        customerRegistrationRepository.reconcileApprovals()
            .forEach { registrationApprovalNotifier.notifyApproved(it) }
        saleRepository.syncAllPending()
        saleRepository.pullTodayFromServer()
    }
}
