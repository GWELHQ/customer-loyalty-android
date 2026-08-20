package com.example.loyaltyapp.ui.syncqueue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loyaltyapp.data.local.entity.SaleEntity
import com.example.loyaltyapp.data.repository.SaleRepository
import com.example.loyaltyapp.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncQueueViewModel @Inject constructor(
    private val saleRepository: SaleRepository,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    val sales: StateFlow<List<SaleEntity>> = saleRepository.observeQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _retryingIds = MutableStateFlow<Set<String>>(emptySet())
    val retryingIds: StateFlow<Set<String>> = _retryingIds

    fun retry(sale: SaleEntity) {
        viewModelScope.launch {
            _retryingIds.value = _retryingIds.value + sale.localSaleId
            saleRepository.attemptSync(sale)
            _retryingIds.value = _retryingIds.value - sale.localSaleId
        }
    }

    fun syncAll() {
        syncScheduler.requestImmediateSync()
    }
}
