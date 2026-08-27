package com.example.loyaltyapp.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.loyaltyapp.core.phone.PhoneNumber
import com.example.loyaltyapp.data.local.entity.CustomerRegistrationEntity
import com.example.loyaltyapp.data.local.entity.Product
import com.example.loyaltyapp.data.local.entity.SaleEntity
import com.example.loyaltyapp.ui.components.LoadingState
import com.example.loyaltyapp.ui.components.MoneyText
import com.example.loyaltyapp.ui.components.StatusPill
import com.example.loyaltyapp.ui.components.TabularText
import com.example.loyaltyapp.ui.components.toDisplayLabel
import com.example.loyaltyapp.ui.components.toPillTone
import com.example.loyaltyapp.ui.sale.initialsOf
import com.example.loyaltyapp.ui.theme.ColorBg
import com.example.loyaltyapp.ui.theme.ColorBorder
import com.example.loyaltyapp.ui.theme.ColorSurface
import com.example.loyaltyapp.ui.theme.ColorText
import com.example.loyaltyapp.ui.theme.ColorTextMuted
import com.example.loyaltyapp.ui.theme.ColorTextSecondary
import com.example.loyaltyapp.ui.theme.GwTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayDetailScreen(
    onBack: () -> Unit,
    viewModel: TodayDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(ColorBg).navigationBarsPadding()) {
        TopAppBar(
            title = { Text("Sale detail", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            }
        )

        when {
            state.sale != null -> SaleDetailBody(state.sale!!)
            state.registration != null -> RegistrationDetailBody(state.registration!!)
            state.loaded -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This sale is no longer available.", color = ColorTextSecondary, fontSize = 14.sp)
            }
            else -> LoadingState(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SaleDetailBody(sale: SaleEntity) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CustomerHeader(sale.customerName, sale.customerPhoneE164)

        AmountCard(
            label = "AMOUNT RECORDED",
            amount = sale.amountPaidKes,
            product = sale.product,
            capturedAtMillis = sale.capturedAtMillis
        ) {
            DetailRow("Sale reference", sale.serverSaleRef ?: sale.localSaleId.take(8).uppercase())
            DetailRow("Station", sale.stationName)
            DetailRow("Attendant", sale.attendantName)
            DetailRow("Sync status") { StatusPill(text = sale.syncStatus.toDisplayLabel(), tone = sale.syncStatus.toPillTone()) }
            DetailRow("Customer SMS") { StatusPill(text = sale.smsStatus.toDisplayLabel(), tone = sale.smsStatus.toPillTone()) }
            if (sale.capturedOffline) {
                DetailRow("Captured offline", "Yes — queued on this device")
            }
        }

        sale.lastSyncErrorMessage?.let { message ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GwTheme.extended.dangerTint, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text("Sync problem", color = GwTheme.extended.danger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(message, color = ColorTextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        // Cashback earned is deliberately not shown here — only the customer's SMS carries it,
        // so an attendant can't read it back and coordinate with the customer to game it.
    }
}

@Composable
private fun RegistrationDetailBody(registration: CustomerRegistrationEntity) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CustomerHeader(registration.customerFullName, registration.customerPhoneNumber)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GwTheme.extended.warningTint, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(
                "This is not a confirmed sale yet — a supervisor must approve the new customer and this sale together.",
                color = GwTheme.extended.warning,
                fontSize = 12.5.sp
            )
        }

        AmountCard(
            label = "AMOUNT SUBMITTED",
            amount = registration.amountPaid,
            product = registration.product,
            capturedAtMillis = registration.capturedAtMillis
        ) {
            DetailRow("Request reference", registration.serverRequestId ?: registration.localId.take(8).uppercase())
            DetailRow("Status") { StatusPill(text = registration.syncStatus.toDisplayLabel(), tone = registration.syncStatus.toPillTone()) }
            if (registration.capturedOffline) {
                DetailRow("Captured offline", "Yes — queued on this device")
            }
        }

        registration.lastSyncErrorMessage?.let { message ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GwTheme.extended.dangerTint, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text("Submission problem", color = GwTheme.extended.danger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(message, color = ColorTextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun CustomerHeader(name: String, phoneE164: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(44.dp).background(com.example.loyaltyapp.ui.theme.GwGreen50, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initialsOf(name),
                color = com.example.loyaltyapp.ui.theme.GwGreen700,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        Box(modifier = Modifier.width(12.dp))
        Column {
            Text(name, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            TabularText(
                PhoneNumber.normalize(phoneE164)?.displayGrouped ?: phoneE164,
                color = ColorTextSecondary,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun AmountCard(
    label: String,
    amount: java.math.BigDecimal,
    product: Product,
    capturedAtMillis: Long,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorSurface, RoundedCornerShape(12.dp))
            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
        ) {
            Text(label, color = ColorTextMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.5.sp)
            MoneyText(amount, fontSize = 32.sp, color = ColorText, modifier = Modifier.padding(top = 4.dp))
            Text(
                "${if (product == Product.PMS) "Petrol (PMS)" else "Diesel (AGO)"} · " +
                    SimpleDateFormat("EEEE d MMMM · HH:mm", Locale.getDefault()).format(Date(capturedAtMillis)),
                color = ColorTextSecondary,
                fontSize = 13.sp
            )
        }
        content()
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = ColorTextSecondary, fontSize = 13.sp)
        TabularText(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun DetailRow(label: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = ColorTextSecondary, fontSize = 13.sp)
        trailing()
    }
}
