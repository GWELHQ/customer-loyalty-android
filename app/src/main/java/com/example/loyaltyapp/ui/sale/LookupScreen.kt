package com.example.loyaltyapp.ui.sale

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.loyaltyapp.core.phone.PhoneNumber
import com.example.loyaltyapp.data.local.entity.CustomerEntity
import com.example.loyaltyapp.ui.components.GwCard
import com.example.loyaltyapp.ui.components.PrimaryButton
import com.example.loyaltyapp.ui.components.SecondaryButton
import com.example.loyaltyapp.ui.components.SectionLabel
import com.example.loyaltyapp.ui.components.TabularText
import com.example.loyaltyapp.ui.theme.ColorBorderStrong
import com.example.loyaltyapp.ui.theme.ColorPrimary
import com.example.loyaltyapp.ui.theme.ColorText
import com.example.loyaltyapp.ui.theme.ColorTextMuted
import com.example.loyaltyapp.ui.theme.ColorTextSecondary
import com.example.loyaltyapp.ui.theme.GwGreen50
import com.example.loyaltyapp.ui.theme.GwGreen700
import com.example.loyaltyapp.ui.theme.GwTheme

@Composable
fun LookupScreen(
    state: SaleUiState,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onPick: (String) -> Unit,
    onCreate: () -> Unit,
    onRetryLookup: () -> Unit,
    onScanQr: () -> Unit,
    onScanNfc: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text("New sale", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            Text(
                "Step 1 of 3 · Find the customer by phone number",
                color = ColorTextSecondary,
                fontSize = 13.sp
            )
        }

        GwCard {
            Text("Customer phone number", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .border(2.dp, if (state.queryDigits.isNotEmpty()) ColorPrimary else ColorBorderStrong, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabularText(
                    text = PhoneNumber.formatPartial(state.queryDigits),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorText
                )
                com.example.loyaltyapp.ui.components.BlinkingCursor(
                    modifier = Modifier.padding(start = 3.dp)
                )
                Box(modifier = Modifier.weight(1f))
                Text("${state.queryDigits.length}/${state.maxQueryDigits}", color = ColorTextMuted, fontSize = 12.sp)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(text = "Scan QR code", onClick = onScanQr, modifier = Modifier.weight(1f), height = 44.dp)
            SecondaryButton(text = "Tap NFC tag", onClick = onScanNfc, modifier = Modifier.weight(1f), height = 44.dp)
        }

        if (state.scanError != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GwTheme.extended.warningTint, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = GwTheme.extended.warning, modifier = Modifier.size(18.dp))
                    Box(modifier = Modifier.width(10.dp))
                    Text(state.scanError, color = ColorTextSecondary, fontSize = 13.sp)
                }
            }
        }

        if (state.lookupFailed) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GwTheme.extended.warningTint, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = GwTheme.extended.warning, modifier = Modifier.size(18.dp))
                    Box(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Couldn't reach the office", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "We couldn't check ${PhoneNumber.formatPartial(state.queryDigits)} against the customer list. Check your connection and try again — don't create a new account for a number we haven't confirmed.",
                            color = ColorTextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
                PrimaryButton(
                    text = "Try again",
                    onClick = onRetryLookup,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    height = 46.dp
                )
            }
        }

        val showNotFound = state.confirmedNotFound
        if (showNotFound) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GwTheme.extended.warningTint, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = GwTheme.extended.warning, modifier = Modifier.size(18.dp))
                    Box(modifier = Modifier.width(10.dp))
                    Column {
                        Text("No customer on ${PhoneNumber.formatPartial(state.queryDigits)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Check the number with the customer — one wrong digit is the usual cause. Searched all stations.",
                            color = ColorTextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(text = "Re-enter number", onClick = onClear, modifier = Modifier.weight(1f), height = 46.dp)
                    PrimaryButton(text = "Create", onClick = onCreate, modifier = Modifier.weight(1f), height = 46.dp)
                }
            }
        }

        Column {
            SectionLabel(
                text = if (state.searchedEnough) "Matches across all stations" else "Recent at this station"
            )
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.matches.forEach { customer ->
                    CustomerMatchRow(customer = customer, onClick = { onPick(customer.id) })
                }
            }
        }
    }
}

@Composable
private fun CustomerMatchRow(customer: CustomerEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, com.example.loyaltyapp.ui.theme.ColorBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(GwGreen50, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(initialsOf(customer.fullName), color = GwGreen700, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Box(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(customer.fullName, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TabularText(customer.phoneNumber, color = ColorTextSecondary, fontSize = 13.sp)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = ColorTextMuted)
    }
}
