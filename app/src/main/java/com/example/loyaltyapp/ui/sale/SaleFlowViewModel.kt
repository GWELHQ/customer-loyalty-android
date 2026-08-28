package com.example.loyaltyapp.ui.sale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loyaltyapp.common.FeatureFlags
import com.example.loyaltyapp.core.connectivity.ConnectivityObserver
import com.example.loyaltyapp.core.notify.RegistrationApprovalNotifier
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
import com.example.loyaltyapp.data.repository.VehiclePlateCheckRepository
import com.example.loyaltyapp.sync.SyncScheduler
import com.example.loyaltyapp.ui.components.ScanResultUi
import java.io.File
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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

/** How long the check/X [ScanResultUi] banner stays up before the flow moves on — long enough to register as a deliberate result, short enough not to feel like a wait. */
private const val SCAN_RESULT_DISPLAY_MILLIS = 1100L

@HiltViewModel
class SaleFlowViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val customerRepository: CustomerRepository,
    private val customerRegistrationRepository: CustomerRegistrationRepository,
    private val registrationApprovalNotifier: RegistrationApprovalNotifier,
    private val stationRepository: StationRepository,
    private val priceRepository: PriceRepository,
    private val saleRepository: SaleRepository,
    private val vehiclePlateCheckRepository: VehiclePlateCheckRepository,
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
                    // A prior full-number lookup couldn't reach the server — now that we're back
                    // online, retry it automatically rather than leaving the attendant stuck.
                    if (_uiState.value.lookupFailed) checkFullNumberIfNeeded()
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
            s.copy(queryDigits = newDigits, searchedEnough = newDigits.isNotEmpty(), confirmedNotFound = false, lookupFailed = false)
        }
        checkFullNumberIfNeeded()
    }

    fun onQueryBackspace() {
        _uiState.update { it.copy(queryDigits = it.queryDigits.dropLast(1), confirmedNotFound = false, lookupFailed = false) }
    }

    fun clearQuery() {
        _uiState.update { it.copy(queryDigits = "", searchedEnough = false, confirmedNotFound = false, lookupFailed = false) }
    }

    /**
     * Every keystroke only filters the local cache (see [SaleUiState.matches], backed by
     * [CustomerRepository.searchLocal]) — no network call. Only once a full national number is
     * entered (9 significant digits, however the attendant typed it — 07xxxxxxxx, 01xxxxxxxx,
     * 7xxxxxxxx, or 1xxxxxxxx) do we check the server. Always hits the server when online, even if
     * the number is already cached locally — an admin-side edit (e.g. a renamed customer) must not
     * stay stale just because this phone has seen the number before; the real "not found ⇒
     * register" decision (§4.2/4.4 of the handover) needs a live round trip to be trustworthy
     * regardless. Offline, falls back to whatever the local cache already knows.
     */
    private fun checkFullNumberIfNeeded() {
        val state = _uiState.value
        val digits = state.queryDigits
        val national = state.nationalQueryDigits
        if (national.length != 9) return
        viewModelScope.launch {
            val normalized = PhoneNumber.normalize(digits)
            val cachedLocally = normalized?.let { customerRepository.findByPhone(it.e164) } != null

            val wasOnline = connectivityObserver.currentlyOnline()
            val remote = if (wasOnline) customerRepository.searchRemoteExact(national) else null
            val stillCurrent = _uiState.value.queryDigits == digits
            if (!stillCurrent) return@launch

            when {
                remote != null -> _uiState.update { it.copy(confirmedNotFound = remote.isEmpty(), lookupFailed = false) }
                cachedLocally -> _uiState.update { it.copy(confirmedNotFound = false, lookupFailed = false) }
                !wasOnline -> _uiState.update {
                    // Genuinely offline: fall back to whatever the local cache already knows.
                    it.copy(confirmedNotFound = it.matches.isEmpty(), lookupFailed = false)
                }
                else -> {
                    // Online, but the lookup call itself failed (timeout, 5xx, rate limit, ...),
                    // and this number wasn't already cached either. Never claim "not found" here —
                    // that would route a real customer into a duplicate "create" registration.
                    // Surface a distinct retryable error instead.
                    _uiState.update { it.copy(confirmedNotFound = false, lookupFailed = true) }
                }
            }
        }
    }

    /** Manual retry for a full-number lookup that previously failed (see [SaleUiState.lookupFailed]). */
    fun retryLookup() = checkFullNumberIfNeeded()

    fun pickCustomer(customerId: String) {
        val customer = _uiState.value.matches.firstOrNull { it.id == customerId } ?: return
        selectCustomer(customer)
    }

    fun goQrScan() {
        _uiState.update { it.copy(screen = SaleScreen.QR_SCAN, scanError = null, scanResult = null) }
    }

    fun goNfcScan() {
        _uiState.update { it.copy(screen = SaleScreen.NFC_SCAN, scanError = null, scanResult = null) }
    }

    /**
     * [scannedText] is the QR code's raw payload. As of 2026-08-27 this is a full URL
     * (`https://loyalty-points-413d5.web.app/qr/<customerId>`, so a generic QR scanner lands
     * somewhere useful instead of a bare id) rather than the old plain-id payload — the customer
     * id is always everything after the last `/`, which also happens to be a no-op on the old
     * bare-id format (no slashes), so no format detection is needed (handover doc §4).
     */
    fun onQrCodeScanned(scannedText: String) {
        // The camera analyzer keeps delivering decoded frames while the result banner is showing
        // (and holding the tag/pointing the camera doesn't stop) — ignore reads until this one has
        // finished being shown, or the same tag/code fires a second overlapping lookup.
        if (_uiState.value.scanResult != null) return
        val scannedId = scannedText.substringAfterLast('/')
        viewModelScope.launch {
            val customer = customerRepository.findById(scannedId)
            if (customer != null) {
                showScanResultThenSelectCustomer(customer)
            } else {
                showScanResultThenReturnToLookup("That QR code didn't match a customer. Try again or use the phone number.")
            }
        }
    }

    fun onNfcTagRead(tagId: String) {
        if (_uiState.value.scanResult != null) return
        viewModelScope.launch {
            val customer = customerRepository.findByNfcTag(tagId)
            if (customer != null) {
                showScanResultThenSelectCustomer(customer)
            } else {
                showScanResultThenReturnToLookup("That tag isn't assigned to a customer. Try again or use the phone number.")
            }
        }
    }

    private suspend fun showScanResultThenSelectCustomer(customer: com.example.loyaltyapp.data.local.entity.CustomerEntity) {
        _uiState.update { it.copy(scanResult = ScanResultUi(success = true, message = "Customer ${customer.fullName} found")) }
        delay(SCAN_RESULT_DISPLAY_MILLIS)
        selectCustomer(customer)
    }

    private suspend fun showScanResultThenReturnToLookup(scanError: String) {
        _uiState.update { it.copy(scanResult = ScanResultUi(success = false, message = scanError)) }
        delay(SCAN_RESULT_DISPLAY_MILLIS)
        _uiState.update { it.copy(screen = SaleScreen.LOOKUP, scanError = scanError, scanResult = null) }
    }

    private fun selectCustomer(customer: com.example.loyaltyapp.data.local.entity.CustomerEntity) {
        _uiState.update {
            it.copy(
                customer = customer,
                isNewCustomerRegistration = false,
                // Plate-check OCR is disabled server-side (see FeatureFlags) — skip straight to
                // ENTRY rather than a step that would only 400 against
                // POST /mobile/vehicle-plate-checks. plateCheck stays null, so no plateCheckId
                // is ever attached to the sale.
                screen = if (FeatureFlags.PLATE_CHECK_ENABLED) SaleScreen.PLATE_CHECK else SaleScreen.ENTRY,
                plateCheck = null,
                plateCheckFailed = false,
                product = null,
                amountDigits = "",
                scanResult = null
            )
        }
    }

    // --- Vehicle plate check --------------------------------------------

    /**
     * Never blocks the sale on its own — a failed/offline check just means no plateCheckId is
     * carried into the sale — but unlike a mismatch (a *completed* check the attendant should be
     * able to see and move past), a check that couldn't run at all is surfaced explicitly
     * ([SaleUiState.plateCheckFailed]) rather than silently continuing to ENTRY: this attendant
     * still needs to *notice* it never got a result, especially while the office side of this
     * feature is being brought up.
     */
    fun submitPlateCheck(imageFile: File) {
        val customer = _uiState.value.customer ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingPlateCheck = true, plateCheckFailed = false) }
            val result = vehiclePlateCheckRepository.submit(imageFile, customer.id)
            _uiState.update {
                it.copy(isSubmittingPlateCheck = false, plateCheck = result, plateCheckFailed = result == null)
            }
            imageFile.delete()
        }
    }

    fun skipPlateCheck() {
        _uiState.update { it.copy(screen = SaleScreen.ENTRY, plateCheck = null, plateCheckFailed = false) }
    }

    /** Re-shoot the photo without leaving the plate-check step — offered both after a completed mismatch and after a failed check. */
    fun retryPlateCheck() {
        _uiState.update { it.copy(plateCheck = null, plateCheckFailed = false) }
    }

    fun continueAfterPlateCheck() {
        _uiState.update { it.copy(screen = SaleScreen.ENTRY) }
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
        _uiState.update { it.copy(screen = SaleScreen.LOOKUP, plateCheck = null, plateCheckFailed = false, scanError = null, scanResult = null) }
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
                    pricePerLitre = price.pricePerLitre,
                    plateCheckId = state.plateCheck?.id
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
                lastRegistration = null,
                plateCheck = null,
                plateCheckFailed = false,
                scanError = null
            )
        }
    }

    /**
     * Pull-to-refresh on the pending-approval success screen: there's no status-poll endpoint
     * (see `CustomerRegistrationRepository.reconcileApprovals`), so this reconciles against
     * `/mobile/sales/mine` and, if the attendant's own registration was found approved, updates
     * the on-screen status and notifies them — without having to leave this screen.
     */
    fun refreshRegistrationStatus() {
        val current = _uiState.value.lastRegistration ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingRegistration = true) }
            try {
                customerRegistrationRepository.reconcileApprovals()
                    .forEach { registrationApprovalNotifier.notifyApproved(it) }
                val refreshed = customerRegistrationRepository.getById(current.localId) ?: current
                _uiState.update { it.copy(lastRegistration = refreshed) }
            } finally {
                _uiState.update { it.copy(isRefreshingRegistration = false) }
            }
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
