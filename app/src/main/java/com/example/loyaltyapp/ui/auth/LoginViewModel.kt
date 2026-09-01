package com.example.loyaltyapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loyaltyapp.core.result.AppResult
import com.example.loyaltyapp.core.session.AttendantSession
import com.example.loyaltyapp.data.repository.AuthRepository
import com.example.loyaltyapp.data.repository.BootstrapRepository
import com.example.loyaltyapp.sync.SyncScheduler
import com.example.loyaltyapp.ui.components.ScanResultUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Two independent ways to obtain the same kind of attendant session (handover doc §3.1/§3.1b) — never mutually exclusive, just a UI toggle. */
enum class LoginMode { PIN, BADGE }

data class LoginUiState(
    val employeeId: String = "",
    val pin: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val restoredSession: AttendantSession? = null,
    val isRestoring: Boolean = true,
    // PIN, not BADGE: badge/NFC login is disabled server-side for now (backend feature flag), and
    // its tab is hidden in LoginScreen — defaulting here to what's actually usable.
    val loginMode: LoginMode = LoginMode.PIN,
    /** Brief check/X feedback shown right after a badge tap resolves, before navigating away. */
    val scanResult: ScanResultUi? = null
)

/** How long the check/X [ScanResultUi] banner stays up before the flow moves on — matches [com.example.loyaltyapp.ui.sale.SaleFlowViewModel]'s scan-result banner. */
private const val SCAN_RESULT_DISPLAY_MILLIS = 1100L

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val bootstrapRepository: BootstrapRepository,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            syncScheduler.schedulePeriodicSync()
            val existing = authRepository.currentSession()
            val stillValid = existing != null && !authRepository.isSessionExpired()
            if (stillValid) {
                afterSuccessfulLogin()
            } else if (existing != null) {
                authRepository.signOut()
            }
            _uiState.update {
                it.copy(restoredSession = if (stillValid) existing else null, isRestoring = false)
            }
        }
    }

    fun onEmployeeIdChange(value: String) {
        _uiState.update { it.copy(employeeId = value, error = null) }
    }

    fun onPinChange(value: String) {
        if (value.length <= 6 && value.all { it.isDigit() }) {
            _uiState.update { it.copy(pin = value, error = null) }
        }
    }

    fun setLoginMode(mode: LoginMode) {
        _uiState.update { it.copy(loginMode = mode, error = null) }
    }

    fun login() {
        val state = _uiState.value
        if (state.employeeId.isBlank() || state.pin.isBlank()) {
            _uiState.update { it.copy(error = "Enter your employee ID and PIN.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = authRepository.login(state.employeeId, state.pin)) {
                is AppResult.Success -> {
                    afterSuccessfulLogin()
                    _uiState.update { it.copy(isLoading = false, restoredSession = result.data) }
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = result.message)
                }
            }
        }
    }

    /**
     * Badge tap login (handover doc §3.1b). Guarded on [LoginUiState.isLoading] so a badge held
     * against the reader for a moment too long doesn't fire a second overlapping request — the
     * NFC reader keeps delivering tag reads for as long as it's held there.
     */
    fun loginWithBadge(tagId: String) {
        if (_uiState.value.isLoading || _uiState.value.scanResult != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = authRepository.loginWithNfcTag(tagId)) {
                is AppResult.Success -> {
                    afterSuccessfulLogin()
                    _uiState.update {
                        it.copy(isLoading = false, scanResult = ScanResultUi(success = true, message = "Attendant ${result.data.fullName} authenticated"))
                    }
                    delay(SCAN_RESULT_DISPLAY_MILLIS)
                    // restoredSession is what actually triggers navigation (see LoginScreen's
                    // LaunchedEffect) — held back until the check banner above has had its moment.
                    _uiState.update { it.copy(scanResult = null, restoredSession = result.data) }
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false, scanResult = ScanResultUi(success = false, message = "Badge not recognized")) }
                    delay(SCAN_RESULT_DISPLAY_MILLIS)
                    _uiState.update { it.copy(scanResult = null, error = result.message) }
                }
            }
        }
    }

    private suspend fun afterSuccessfulLogin() {
        bootstrapRepository.refresh()
        syncScheduler.requestImmediateCustomerDirectorySync()
    }
}
