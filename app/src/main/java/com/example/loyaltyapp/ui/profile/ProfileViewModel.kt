package com.example.loyaltyapp.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loyaltyapp.core.connectivity.ConnectivityObserver
import com.example.loyaltyapp.core.session.AttendantSession
import com.example.loyaltyapp.data.repository.AuthRepository
import com.example.loyaltyapp.data.repository.CustomerRegistrationRepository
import com.example.loyaltyapp.data.repository.PriceRepository
import com.example.loyaltyapp.data.repository.SaleRepository
import com.example.loyaltyapp.data.repository.StationRepository
import com.example.loyaltyapp.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val session: AttendantSession? = null,
    val stationName: String = "",
    val isOnline: Boolean = true,
    val pendingSyncCount: Int = 0,
    val pricesUpToDate: Boolean = true,
    val signedOut: Boolean = false
)

enum class SyncToast { SYNCING, SUCCESS, FAILED }

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val saleRepository: SaleRepository,
    private val customerRegistrationRepository: CustomerRegistrationRepository,
    private val stationRepository: StationRepository,
    private val priceRepository: PriceRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState(session = authRepository.currentSession()))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _syncToast = MutableSharedFlow<SyncToast>(extraBufferCapacity = 1)
    val syncToast: SharedFlow<SyncToast> = _syncToast

    init {
        _uiState.value.session?.let { session ->
            viewModelScope.launch {
                val station = stationRepository.getById(session.assignedStationId)
                station?.let { s -> _uiState.update { it.copy(stationName = s.name) } }
            }
        }
        viewModelScope.launch {
            connectivityObserver.isOnline.collectLatest { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
        viewModelScope.launch {
            combine(
                saleRepository.observePendingCount(),
                customerRegistrationRepository.observePendingCount()
            ) { sales, registrations -> sales + registrations }
                .collectLatest { count -> _uiState.update { it.copy(pendingSyncCount = count) } }
        }
        viewModelScope.launch {
            val blocked = priceRepository.isBlockedForSale()
            _uiState.update { it.copy(pricesUpToDate = !blocked) }
        }
    }

    fun syncNow() {
        syncScheduler.requestImmediateSync()
        viewModelScope.launch {
            _syncToast.emit(SyncToast.SYNCING)
            if (!connectivityObserver.currentlyOnline()) {
                _syncToast.emit(SyncToast.FAILED)
                return@launch
            }
            try {
                saleRepository.syncAllPending()
                customerRegistrationRepository.retryAllPending()
                val stillPending = saleRepository.observePendingCount().first() +
                    customerRegistrationRepository.observePendingCount().first()
                _syncToast.emit(if (stillPending == 0) SyncToast.SUCCESS else SyncToast.FAILED)
            } catch (e: Exception) {
                _syncToast.emit(SyncToast.FAILED)
            }
        }
    }

    /** True when it's safe to sign out without a warning — no sales still waiting on this device. */
    fun canSignOutQuietly(): Boolean = _uiState.value.pendingSyncCount == 0

    fun confirmSignOut() {
        authRepository.signOut()
        _uiState.update { it.copy(signedOut = true) }
    }
}
