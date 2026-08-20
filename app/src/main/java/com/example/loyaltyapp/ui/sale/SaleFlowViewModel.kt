package com.example.loyaltyapp.ui.sale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loyaltyapp.core.connectivity.ConnectivityObserver
import com.example.loyaltyapp.core.phone.PhoneNumber
import com.example.loyaltyapp.data.local.entity.Product
import com.example.loyaltyapp.data.repository.AuthRepository
import com.example.loyaltyapp.data.repository.CustomerRegistrationRepository
import com.example.loyaltyapp.data.repository.CustomerRepository
import com.example.loyaltyapp.data.repository.NewCustomerRegistrationInput
import com.example.loyaltyapp.data.repository.NewSaleInput
import com.example.loyaltyapp.data.repository.PriceRepository
import com.example.loyaltyapp.data.repository.SaleRepository
import com.example.loyaltyapp.data.repository.StationRepository
import com.example.loyaltyapp.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SaleFlowViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val customerRepository: CustomerRepository,
    private val customerRegistrationRepository: CustomerRegistrationRepository,
    private val stationRepository: StationRepository,
    private val priceRepository: PriceRepository,
    private val saleRepository: SaleRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(SaleUiState())
    val uiState: StateFlow<SaleUiState> = _uiState.asStateFlow()

    init {
        val session = authRepository.currentSession()
        if (session != null) {
            _uiState.update {
                it.copy(
                    stationId = session.assignedStationId,
                    attendantId = session.attendantId,
                    attendantName = session.fullName
                )
            }
            viewModelScope.launch {
                val station = stationRepository.getById(session.assignedStationId)
                station?.let { s -> _uiState.update { it.copy(stationName = s.name) } }
            }
        }
        observeConnectivity()
        observePendingCount()
        observeCustomerMatches()
        loadPrices()
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.isOnline.collectLatest { online ->
                _uiState.update { it.copy(isOnline = online) }
                refreshBlockedState()
                if (online) {
                    priceRepository.refreshFromRemote()
                    loadPrices()
                }
            }
        }
    }

    private fun observePendingCount() {
        viewModelScope.launch {
            combine(
                saleRepository.observePendingCount(),
                customerRegistrationRepository.observePendingCount()
            ) { sales, registrations -> sales + registrations }
                .collectLatest { count -> _uiState.update { it.copy(pendingSyncCount = count) } }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeCustomerMatches() {
        viewModelScope.launch {
            _uiState.map { it.nationalQueryDigits }
                .distinctUntilChanged()
                .flatMapLatest { digits -> customerRepository.searchLocal(digits) }
                .collectLatest { matches ->
                    _uiState.update { it.copy(matches = matches) }
                }
        }
    }

    private fun loadPrices() {
        viewModelScope.launch {
            val pms = priceRepository.getCachedPrice(Product.PMS)
            val ago = priceRepository.getCachedPrice(Product.AGO)
            val map = buildMap {
                pms?.let { put(Product.PMS, it) }
                ago?.let { put(Product.AGO, it) }
            }
            _uiState.update { it.copy(prices = map) }
            refreshBlockedState()
        }
    }

    private fun refreshBlockedState() {
        viewModelScope.launch {
            val blocked = priceRepository.isBlockedForSale()
            _uiState.update { s ->
                val newScreen = if (blocked && s.screen != SaleScreen.SUCCESS) SaleScreen.BLOCKED else s.screen
                s.copy(blockedNoPrice = blocked, screen = newScreen)
            }
        }
    }

    // --- Phone lookup -------------------------------------------------

    fun onQueryDigit(digit: String) {
        _uiState.update { s ->
            // Accepts 07xxxxxxxx/01xxxxxxxx (10 raw digits) as well as 7xxxxxxxx/1xxxxxxxx (9) —
            // the cap only widens to 10 once a leading 0 is actually present (see maxQueryDigits).
            if (s.queryDigits.length >= s.maxQueryDigits) return@update s
            val newDigits = s.queryDigits + digit
            s.copy(queryDigits = newDigits, searchedEnough = newDigits.isNotEmpty(), confirmedNotFound = false)
        }
        checkFullNumberIfNeeded()
    }

    fun onQueryBackspace() {
        _uiState.update { it.copy(queryDigits = it.queryDigits.dropLast(1), confirmedNotFound = false) }
    }

    fun clearQuery() {
        _uiState.update { it.copy(queryDigits = "", searchedEnough = false, confirmedNotFound = false) }
    }

    /**
     * Every keystroke only filters the local cache (see [SaleUiState.matches], backed by
     * [CustomerRepository.searchLocal]) — no network call. Only once a full national number is
     * entered (9 significant digits, however the attendant typed it — 07xxxxxxxx, 01xxxxxxxx,
     * 7xxxxxxxx, or 1xxxxxxxx) do we check the server, and only if that number isn't already
     * cached locally: the real "not found ⇒ register" decision (§4.2/4.4 of the handover) needs
     * a live round trip to be trustworthy, but there's no point spending one on a number the
     * phone already knows.
     */
    private fun checkFullNumberIfNeeded() {
        val state = _uiState.value
        val digits = state.queryDigits
        val national = state.nationalQueryDigits
        if (national.length != 9) return
        viewModelScope.launch {
            val normalized = PhoneNumber.normalize(digits)
            val cachedLocally = normalized?.let { customerRepository.findByPhone(it.e164) } != null
            if (cachedLocally) return@launch

            val remote = customerRepository.searchRemoteExact(national)
            val stillCurrent = _uiState.value.queryDigits == digits
            if (stillCurrent) {
                val notFound = when {
                    remote != null -> remote.isEmpty()
                    else -> _uiState.value.matches.isEmpty() // offline: fall back to local cache
                }
                _uiState.update { it.copy(confirmedNotFound = notFound) }
            }
        }
    }

    fun pickCustomer(customerId: String) {
        val customer = _uiState.value.matches.firstOrNull { it.id == customerId } ?: return
        _uiState.update {
            it.copy(
                customer = customer,
                isNewCustomerRegistration = false,
                screen = SaleScreen.ENTRY,
                product = null,
                amountDigits = ""
            )
        }
    }

    fun goCreate() {
        _uiState.update {
            it.copy(
                screen = SaleScreen.CREATE,
                isNewCustomerRegistration = true,
                customer = null,
                newCustomerName = "",
                createError = null
            )
        }
    }

    fun onNewCustomerNameChange(name: String) {
        _uiState.update { it.copy(newCustomerName = name, createError = null) }
    }

    /** Registration collects name here, then product+amount on the shared ENTRY/REVIEW screens — submitted as one request. */
    fun continueToRegistrationDetails() {
        val state = _uiState.value
        val normalized = PhoneNumber.normalize(state.queryDigits)
            ?: return _uiState.update { it.copy(createError = "That phone number doesn't look right.") }
        if (state.newCustomerName.isBlank()) {
            _uiState.update { it.copy(createError = "Enter the customer's name.") }
            return
        }
        _uiState.update { it.copy(screen = SaleScreen.ENTRY, product = null, amountDigits = "") }
    }

    fun goLookup() {
        _uiState.update { it.copy(screen = SaleScreen.LOOKUP) }
    }

    // --- Product + amount -----------------------------------------------

    fun pickProduct(product: Product) {
        _uiState.update { it.copy(product = product) }
    }

    fun onAmountDigit(digit: String) {
        _uiState.update { s ->
            if (s.amountDigits.length >= 7) return@update s
            val newDigits = (if (s.amountDigits == "0") "" else s.amountDigits) + digit
            s.copy(amountDigits = newDigits)
        }
    }

    fun onAmountBackspace() {
        _uiState.update { it.copy(amountDigits = it.amountDigits.dropLast(1)) }
    }

    fun clearAmount() {
        _uiState.update { it.copy(amountDigits = "") }
    }

    fun goEntry() {
        _uiState.update { it.copy(screen = SaleScreen.ENTRY) }
    }

    fun goReview() {
        if (!_uiState.value.readyForReview) return
        _uiState.update { it.copy(screen = SaleScreen.REVIEW) }
    }

    // --- Submit -----------------------------------------------------------

    fun submitSale() {
        val state = _uiState.value
        val product = state.product ?: return
        val price = state.selectedPrice ?: return
        if (state.isSubmitting) return

        if (state.isNewCustomerRegistration) {
            val normalized = PhoneNumber.normalize(state.queryDigits) ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(isSubmitting = true) }
                val wasOffline = !state.isOnline
                val registration = customerRegistrationRepository.submit(
                    NewCustomerRegistrationInput(
                        fullName = state.newCustomerName.trim(),
                        phoneNumber = normalized.e164,
                        product = product,
                        amountPaid = state.amountPaid
                    )
                )
                syncScheduler.requestImmediateSync()
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        screen = SaleScreen.SUCCESS,
                        lastRegistration = registration,
                        lastSaleWasOffline = wasOffline
                    )
                }
            }
            return
        }

        val customer = state.customer ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val wasOffline = !state.isOnline
            val sale = saleRepository.recordSale(
                NewSaleInput(
                    stationId = state.stationId,
                    stationName = state.stationName,
                    attendantId = state.attendantId,
                    attendantName = state.attendantName,
                    customer = customer,
                    product = product,
                    amountPaidKes = state.amountPaid,
                    pricePerLitre = price.pricePerLitre
                )
            )
            syncScheduler.requestImmediateSync()
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    screen = SaleScreen.SUCCESS,
                    lastSale = sale,
                    lastSaleWasOffline = wasOffline
                )
            }
        }
    }

    fun recordAnother() {
        _uiState.update {
            it.copy(
                screen = SaleScreen.LOOKUP,
                queryDigits = "",
                searchedEnough = false,
                confirmedNotFound = false,
                customer = null,
                isNewCustomerRegistration = false,
                product = null,
                amountDigits = "",
                lastSale = null,
                lastRegistration = null
            )
        }
    }

    fun retryPrice() {
        viewModelScope.launch {
            priceRepository.refreshFromRemote()
            loadPrices()
        }
    }

    fun syncNow() {
        syncScheduler.requestImmediateSync()
    }
}
