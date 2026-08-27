package com.example.loyaltyapp.ui.sale

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.loyaltyapp.ui.components.AppBottomNav
import com.example.loyaltyapp.ui.components.BottomNavDestination
import com.example.loyaltyapp.ui.components.ConnectionState
import com.example.loyaltyapp.ui.components.SyncBanner
import com.example.loyaltyapp.ui.components.TopStatusBar
import com.example.loyaltyapp.ui.theme.ColorBg

@Composable
fun SaleFlowScreen(
    onGoToday: () -> Unit,
    onGoProfile: () -> Unit,
    viewModel: SaleFlowViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    val connectionState = when {
        state.screen == SaleScreen.BLOCKED -> ConnectionState.ATTENTION
        !state.isOnline -> ConnectionState.OFFLINE
        else -> ConnectionState.ONLINE
    }

    Column(modifier = Modifier.fillMaxSize().background(ColorBg)) {
        TopStatusBar(
            stationName = state.stationName,
            attendantName = state.attendantName,
            connectionState = connectionState
        )

        if (state.pendingSyncCount > 0 && (!state.isOnline || state.screen == SaleScreen.BLOCKED)) {
            SyncBanner(pendingCount = state.pendingSyncCount, onSyncNow = viewModel::syncNow)
        }

        // The pending-approval success screen owns a PullToRefreshBox, which needs a
        // bounded-height container — it can't sit inside the shared verticalScroll Column below
        // (unbounded height there breaks pull-to-refresh's own scrolling), so it gets its own
        // fixed-height Box instead.
        if (state.screen == SaleScreen.SUCCESS && state.lastRegistration != null) {
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                SuccessScreen(
                    state = state,
                    onRecordAnother = viewModel::recordAnother,
                    onGoToday = onGoToday,
                    onRefreshRegistration = viewModel::refreshRegistrationStatus
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                when (state.screen) {
                    SaleScreen.LOOKUP -> LookupScreen(
                        state = state,
                        onDigit = viewModel::onQueryDigit,
                        onBackspace = viewModel::onQueryBackspace,
                        onClear = viewModel::clearQuery,
                        onPick = viewModel::pickCustomer,
                        onCreate = viewModel::goCreate,
                        onRetryLookup = viewModel::retryLookup,
                        onScanQr = viewModel::goQrScan,
                        onScanNfc = viewModel::goNfcScan
                    )
                    SaleScreen.QR_SCAN -> QrScanScreen(
                        onCodeScanned = viewModel::onQrCodeScanned,
                        onCancel = viewModel::goLookup
                    )
                    SaleScreen.NFC_SCAN -> NfcScanScreen(
                        onTagRead = viewModel::onNfcTagRead,
                        onCancel = viewModel::goLookup
                    )
                    SaleScreen.PLATE_CHECK -> PlateCheckScreen(
                        state = state,
                        onCapture = viewModel::submitPlateCheck,
                        onSkip = viewModel::skipPlateCheck,
                        onRetry = viewModel::retryPlateCheck,
                        onContinue = viewModel::continueAfterPlateCheck
                    )
                    SaleScreen.CREATE -> CreateCustomerScreen(
                        state = state,
                        onBack = viewModel::goLookup,
                        onNameChange = viewModel::onNewCustomerNameChange,
                        onContinue = viewModel::continueToRegistrationDetails
                    )
                    SaleScreen.BLOCKED -> BlockedScreen(
                        monthLabel = "this month",
                        onRetry = viewModel::retryPrice
                    )
                    SaleScreen.ENTRY -> EntryScreen(
                        state = state,
                        onChangeCustomer = viewModel::goLookup,
                        onPickProduct = viewModel::pickProduct,
                        onAmountDigit = viewModel::onAmountDigit,
                        onAmountBackspace = viewModel::onAmountBackspace,
                        onClearAmount = viewModel::clearAmount,
                        onReview = viewModel::goReview
                    )
                    SaleScreen.REVIEW -> ReviewScreen(
                        state = state,
                        onEdit = viewModel::goEntry,
                        onSubmit = viewModel::submitSale
                    )
                    SaleScreen.SUCCESS -> SuccessScreen(
                        state = state,
                        onRecordAnother = viewModel::recordAnother,
                        onGoToday = onGoToday
                    )
                }
            }
        }

        if (state.screen == SaleScreen.LOOKUP) {
            com.example.loyaltyapp.ui.components.NumericKeypad(
                onDigit = viewModel::onQueryDigit,
                onBackspace = viewModel::onQueryBackspace,
                modifier = Modifier
                    .background(com.example.loyaltyapp.ui.theme.ColorSurface)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }

        AppBottomNav(
            current = BottomNavDestination.NEW_SALE,
            onSelect = { dest ->
                when (dest) {
                    BottomNavDestination.NEW_SALE -> Unit
                    BottomNavDestination.TODAY -> onGoToday()
                    BottomNavDestination.PROFILE -> onGoProfile()
                }
            }
        )
    }
}
