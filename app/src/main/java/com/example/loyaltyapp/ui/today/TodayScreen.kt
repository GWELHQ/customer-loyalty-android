package com.example.loyaltyapp.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.loyaltyapp.core.money.Money
import com.example.loyaltyapp.data.local.entity.CustomerRegistrationEntity
import com.example.loyaltyapp.data.local.entity.Product
import com.example.loyaltyapp.data.local.entity.SaleEntity
import com.example.loyaltyapp.ui.components.AppBottomNav
import com.example.loyaltyapp.ui.components.BottomNavDestination
import com.example.loyaltyapp.ui.components.ConnectionState
import com.example.loyaltyapp.ui.components.EmptyState
import com.example.loyaltyapp.ui.components.StatusPill
import com.example.loyaltyapp.ui.components.PillTone
import com.example.loyaltyapp.ui.components.TabularText
import com.example.loyaltyapp.ui.components.TopStatusBar
import com.example.loyaltyapp.ui.components.toDisplayLabel
import com.example.loyaltyapp.ui.components.toPillTone
import com.example.loyaltyapp.ui.theme.ColorBg
import com.example.loyaltyapp.ui.theme.ColorBorder
import com.example.loyaltyapp.ui.theme.ColorSurface
import com.example.loyaltyapp.ui.theme.ColorText
import com.example.loyaltyapp.ui.theme.ColorTextMuted
import com.example.loyaltyapp.ui.theme.ColorTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onGoNewSale: () -> Unit,
    onGoProfile: () -> Unit,
    onOpenDetail: (com.example.loyaltyapp.ui.navigation.TodayDetailType, String) -> Unit,
    viewModel: TodayViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(ColorBg)) {
        TopStatusBar(
            stationName = state.stationName,
            attendantName = state.attendantName,
            connectionState = if (state.isOnline) ConnectionState.ONLINE else ConnectionState.OFFLINE
        )

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.weight(1f).fillMaxSize()
        ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
            Text("Today", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            Text(
                "${SimpleDateFormat("EEEE d MMMM yyyy", Locale.getDefault()).format(Date())} · ${state.stationName}",
                color = ColorTextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
            )

            // Deliberately no cashback figures and no comparison against total pump sales here —
            // showing an attendant how close their loyalty sales are to any limit, or how much
            // cashback they've issued, would let them and a customer coordinate to game it. The
            // invariant is still enforced centrally; the attendant just never sees the numbers
            // behind it.

            // Plain Row/Column rather than LazyVerticalGrid: this sits inside a Column that
            // already scrolls, and a lazy grid measured with unbounded height there throws —
            // four fixed cards have no need to be lazy.
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        "Loyalty sales", state.loyaltySalesCount.toString(),
                        "PMS ${state.sales.count { it.product == Product.PMS }} · AGO ${state.sales.count { it.product == Product.AGO }}",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard("Sale value", Money.formatKes(state.loyaltySalesValue), "Your loyalty sales today", modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Petrol", Money.formatKes(state.pmsValue), "${state.sales.count { it.product == Product.PMS }} sales", modifier = Modifier.weight(1f))
                    StatCard("Diesel", Money.formatKes(state.agoValue), "${state.sales.count { it.product == Product.AGO }} sales", modifier = Modifier.weight(1f))
                }
            }

            Box(modifier = Modifier.padding(top = 10.dp)) {}
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("YOUR SALES TODAY", color = ColorTextMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.6.sp)
                Text("Newest first", color = ColorTextMuted, fontSize = 12.sp)
            }
            Box(modifier = Modifier.padding(top = 8.dp)) {}

            if (state.items.isEmpty()) {
                EmptyState("No sales yet today", "Recorded sales will show up here as soon as you record one.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.items.forEach { item ->
                        when (item) {
                            is TodayListItem.SaleItem -> SaleRow(item.sale) {
                                onOpenDetail(com.example.loyaltyapp.ui.navigation.TodayDetailType.SALE, item.sale.localSaleId)
                            }
                            is TodayListItem.PendingRegistrationItem -> PendingRegistrationRow(item.registration) {
                                onOpenDetail(com.example.loyaltyapp.ui.navigation.TodayDetailType.REGISTRATION, item.registration.localId)
                            }
                        }
                    }
                }
            }
        }
        }

        AppBottomNav(
            current = BottomNavDestination.TODAY,
            onSelect = { dest ->
                when (dest) {
                    BottomNavDestination.NEW_SALE -> onGoNewSale()
                    BottomNavDestination.TODAY -> Unit
                    BottomNavDestination.PROFILE -> onGoProfile()
                }
            }
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, note: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ColorSurface, RoundedCornerShape(12.dp))
            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
            .padding(13.dp)
    ) {
        Text(label, color = ColorTextSecondary, fontSize = 12.sp)
        TabularText(value, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, modifier = Modifier.padding(top = 3.dp))
        Text(note, color = ColorTextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun SaleRow(sale: SaleEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorSurface, RoundedCornerShape(12.dp))
            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(13.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(sale.customerName, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TabularText(
                    "${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sale.capturedAtMillis))} · ${if (sale.product == Product.PMS) "Petrol" else "Diesel"}",
                    color = ColorTextSecondary,
                    fontSize = 12.sp
                )
            }
            TabularText(Money.formatKes(sale.amountPaidKes), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = ColorText)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            StatusPill(text = sale.syncStatus.toDisplayLabel(), tone = sale.syncStatus.toPillTone())
            Box(modifier = Modifier.padding(start = 6.dp)) {}
            StatusPill(text = sale.smsStatus.toDisplayLabel(), tone = sale.smsStatus.toPillTone())
            sale.lastSyncErrorMessage?.let {
                Box(modifier = Modifier.weight(1f))
                Text("See your supervisor", color = ColorTextMuted, fontSize = 11.5.sp)
            }
        }
    }
}

/** A new-customer request still awaiting a supervisor's decision — not a confirmed sale yet. */
@Composable
private fun PendingRegistrationRow(registration: CustomerRegistrationEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorSurface, RoundedCornerShape(12.dp))
            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(13.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(registration.customerFullName, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TabularText(
                    "${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(registration.capturedAtMillis))} · ${if (registration.product == Product.PMS) "Petrol" else "Diesel"} · New customer",
                    color = ColorTextSecondary,
                    fontSize = 12.sp
                )
            }
            TabularText(Money.formatKes(registration.amountPaid), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = ColorText)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            StatusPill(text = "Pending approval", tone = PillTone.WARN)
        }
    }
}
