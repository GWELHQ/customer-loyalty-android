package com.example.loyaltyapp.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loyaltyapp.core.result.AppResult
import com.example.loyaltyapp.data.remote.dto.DailySummaryRowDto
import com.example.loyaltyapp.data.repository.ReportingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DailySummaryUiState(
    val isLoading: Boolean = true,
    val rows: List<DailySummaryRowDto> = emptyList(),
    val error: String? = null
)

/**
 * End-of-shift reconciliation summary from `/mobile/daily-summary`. Deliberately shows only
 * counts and sale value per product — never cashback totals, matching the same anti-gaming
 * restraint already applied on [com.example.loyaltyapp.ui.today.TodayScreen].
 */
@HiltViewModel
class DailySummaryViewModel @Inject constructor(
    private val reportingRepository: ReportingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailySummaryUiState())
    val uiState: StateFlow<DailySummaryUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = reportingRepository.fetchDailySummary()) {
                is AppResult.Success -> _uiState.update { it.copy(isLoading = false, rows = result.data) }
                is AppResult.Failure -> _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }
}
