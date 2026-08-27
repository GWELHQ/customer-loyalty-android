package com.example.loyaltyapp.ui.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loyaltyapp.data.local.entity.CustomerRegistrationEntity
import com.example.loyaltyapp.data.local.entity.SaleEntity
import com.example.loyaltyapp.data.repository.CustomerRegistrationRepository
import com.example.loyaltyapp.data.repository.SaleRepository
import com.example.loyaltyapp.ui.navigation.TodayDetailType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TodayDetailUiState(
    val sale: SaleEntity? = null,
    val registration: CustomerRegistrationEntity? = null,
    val loaded: Boolean = false
)

@HiltViewModel
class TodayDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    saleRepository: SaleRepository,
    customerRegistrationRepository: CustomerRegistrationRepository
) : ViewModel() {

    val uiState: StateFlow<TodayDetailUiState> = run {
        val type = savedStateHandle.get<String>("type")
        val id = savedStateHandle.get<String>("id") ?: ""
        when (runCatching { type?.let(TodayDetailType::valueOf) }.getOrNull()) {
            TodayDetailType.SALE -> saleRepository.observeById(id)
                .map { TodayDetailUiState(sale = it, loaded = true) }
            else -> customerRegistrationRepository.observeById(id)
                .map { TodayDetailUiState(registration = it, loaded = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayDetailUiState())
}
