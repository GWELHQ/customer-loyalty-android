package com.example.loyaltyapp.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loyaltyapp.core.connectivity.ConnectivityObserver
import com.example.loyaltyapp.data.local.entity.Product
import com.example.loyaltyapp.data.local.entity.SaleEntity
import com.example.loyaltyapp.data.repository.AuthRepository
import com.example.loyaltyapp.data.repository.SaleRepository
import com.example.loyaltyapp.data.repository.StationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Deliberately excludes cashback totals and any comparison against total pump sales — those
 * figures never reach the attendant UI (see [com.example.loyaltyapp.ui.today.TodayScreen]);
 * showing them would let an attendant and a customer coordinate to game the loyalty program.
 * The sales-must-not-exceed-total invariant is enforced by the backend at sync time, not here.
 */
data class TodayUiState(
    val stationName: String = "",
    val attendantName: String = "",
    val isOnline: Boolean = true,
    val sales: List<SaleEntity> = emptyList(),
    val loyaltySalesCount: Int = 0,
    val loyaltySalesValue: BigDecimal = BigDecimal.ZERO,
    val pmsValue: BigDecimal = BigDecimal.ZERO,
    val agoValue: BigDecimal = BigDecimal.ZERO
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val saleRepository: SaleRepository,
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
            viewModelScope.launch {
                saleRepository.observeTodayForAttendant(session.attendantId).collectLatest { sales ->
                    val value = sales.sumOf { it.amountPaidKes }
                    val pms = sales.filter { it.product == Product.PMS }.sumOf { it.amountPaidKes }
                    val ago = sales.filter { it.product == Product.AGO }.sumOf { it.amountPaidKes }
                    _uiState.update {
                        it.copy(
                            sales = sales,
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
}
