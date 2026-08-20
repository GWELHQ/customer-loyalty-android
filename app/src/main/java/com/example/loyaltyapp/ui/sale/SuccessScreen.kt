package com.example.loyaltyapp.ui.sale

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.loyaltyapp.core.money.Money
import com.example.loyaltyapp.data.local.entity.Product
import com.example.loyaltyapp.data.local.entity.SmsStatus
import com.example.loyaltyapp.ui.components.MoneyText
import com.example.loyaltyapp.ui.components.PrimaryButton
import com.example.loyaltyapp.ui.components.SecondaryButton
import com.example.loyaltyapp.ui.components.StatusPill
import com.example.loyaltyapp.ui.components.TabularText
import com.example.loyaltyapp.ui.components.toDisplayLabel
import com.example.loyaltyapp.ui.components.toPillTone
import com.example.loyaltyapp.ui.theme.ColorBorder
import com.example.loyaltyapp.ui.theme.ColorSurface
import com.example.loyaltyapp.ui.theme.ColorText
import com.example.loyaltyapp.ui.theme.ColorTextMuted
import com.example.loyaltyapp.ui.theme.ColorTextSecondary
import com.example.loyaltyapp.ui.theme.GwTheme

@Composable
fun SuccessScreen(
    state: SaleUiState,
    onRecordAnother: () -> Unit,
    onGoToday: () -> Unit
) {
    val registration = state.lastRegistration
    if (registration != null) {
        RegistrationSuccessScreen(registration, state.lastSaleWasOffline, onRecordAnother, onGoToday)
        return
    }
    val sale = state.lastSale ?: return
    val offline = state.lastSaleWasOffline

    Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(if (offline) GwTheme.extended.warningTint else GwTheme.extended.successTint, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (offline) Icons.Filled.Upload else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (offline) GwTheme.extended.warning else GwTheme.extended.success,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                if (offline) "Saved on this device" else "Sale recorded",
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                if (offline) "Will sync when online. It sends on its own once there is network — do not enter it again."
                else "The customer has been sent their SMS.",
                color = ColorTextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ColorSurface, RoundedCornerShape(12.dp))
                .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                Text("AMOUNT RECORDED", color = ColorTextMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.5.sp)
                MoneyText(sale.amountPaidKes, fontSize = 36.sp, color = ColorText, modifier = Modifier.padding(top = 4.dp))
                Text(
                    "${sale.customerName} · ${if (sale.product == Product.PMS) "Petrol (PMS)" else "Diesel (AGO)"}",
                    color = ColorTextSecondary,
                    fontSize = 13.sp
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (offline) "Reference on this phone" else "Sale reference", color = ColorTextSecondary, fontSize = 13.sp)
                TabularText(sale.serverSaleRef ?: sale.localSaleId.take(8).uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Customer SMS", color = ColorTextSecondary, fontSize = 13.sp)
                StatusPill(text = sale.smsStatus.toDisplayLabel(), tone = sale.smsStatus.toPillTone())
            }
            // Cashback earned is deliberately not shown here — only the customer's SMS carries
            // it, so an attendant can't read it back and coordinate with the customer to game it.
        }

        PrimaryButton(text = "Record another sale", onClick = onRecordAnother, height = 52.dp)
        SecondaryButton(text = "Go to today's sales", onClick = onGoToday, height = 46.dp)
    }
}

@Composable
private fun RegistrationSuccessScreen(
    registration: com.example.loyaltyapp.data.local.entity.CustomerRegistrationEntity,
    offline: Boolean,
    onRecordAnother: () -> Unit,
    onGoToday: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(GwTheme.extended.warningTint, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (offline) Icons.Filled.Upload else Icons.Filled.HourglassTop,
                    contentDescription = null,
                    tint = GwTheme.extended.warning,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                "Pending supervisor approval",
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                if (offline)
                    "Saved on this device — will submit for approval once there is network. This is not a confirmed sale yet."
                else "This is not a confirmed sale yet — a supervisor must approve the new customer and this sale together.",
                color = ColorTextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ColorSurface, RoundedCornerShape(12.dp))
                .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                Text("AMOUNT SUBMITTED", color = ColorTextMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.5.sp)
                MoneyText(registration.amountPaid, fontSize = 36.sp, color = ColorText, modifier = Modifier.padding(top = 4.dp))
                Text(
                    "${registration.customerFullName} · ${if (registration.product == Product.PMS) "Petrol (PMS)" else "Diesel (AGO)"}",
                    color = ColorTextSecondary,
                    fontSize = 13.sp
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Request reference", color = ColorTextSecondary, fontSize = 13.sp)
                TabularText(
                    registration.serverRequestId ?: registration.localId.take(8).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Status", color = ColorTextSecondary, fontSize = 13.sp)
                Text("Pending approval", color = GwTheme.extended.warning, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        PrimaryButton(text = "Record another sale", onClick = onRecordAnother, height = 52.dp)
        SecondaryButton(text = "Go to today's sales", onClick = onGoToday, height = 46.dp)
    }
}
