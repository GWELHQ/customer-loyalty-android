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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.loyaltyapp.core.money.Money
import com.example.loyaltyapp.data.local.entity.Product
import com.example.loyaltyapp.ui.components.GwCard
import com.example.loyaltyapp.ui.components.NumericKeypad
import com.example.loyaltyapp.ui.components.PrimaryButton
import com.example.loyaltyapp.ui.components.TabularText
import com.example.loyaltyapp.ui.theme.ColorBorder
import com.example.loyaltyapp.ui.theme.ColorPrimary
import com.example.loyaltyapp.ui.theme.ColorPrimaryTint
import com.example.loyaltyapp.ui.theme.ColorSurface
import com.example.loyaltyapp.ui.theme.ColorText
import com.example.loyaltyapp.ui.theme.ColorTextMuted
import com.example.loyaltyapp.ui.theme.ColorTextSecondary
import com.example.loyaltyapp.ui.theme.GwGreen50
import com.example.loyaltyapp.ui.theme.GwGreen700
import com.example.loyaltyapp.ui.theme.GwTheme

@Composable
fun EntryScreen(
    state: SaleUiState,
    onChangeCustomer: () -> Unit,
    onPickProduct: (Product) -> Unit,
    onAmountDigit: (String) -> Unit,
    onAmountBackspace: () -> Unit,
    onClearAmount: () -> Unit,
    onReview: () -> Unit
) {
    val customer = state.customer
    if (customer == null && !state.isNewCustomerRegistration) return
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GwCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val displayName = customer?.fullName ?: state.newCustomerName
                Box(modifier = Modifier.size(40.dp).background(GwGreen50, CircleShape), contentAlignment = Alignment.Center) {
                    Text(initialsOf(displayName), color = GwGreen700, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Box(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(displayName, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TabularText(
                        customer?.phoneNumber ?: com.example.loyaltyapp.core.phone.PhoneNumber.formatPartial(state.queryDigits),
                        color = ColorTextSecondary,
                        fontSize = 13.sp
                    )
                }
                Text(
                    "Change",
                    color = com.example.loyaltyapp.ui.theme.GwBlue500,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(onClick = onChangeCustomer).padding(6.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .border(1.dp, ColorBorder, RoundedCornerShape(0.dp))
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = GwTheme.extended.success, modifier = Modifier.size(15.dp))
                Box(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        state.isNewCustomerRegistration -> "New customer · pending supervisor approval"
                        customer?.specialRateKesPerLitre != null -> "Special cashback rate applies · earns at all stations"
                        else -> "Verified customer · earns at all stations"
                    },
                    color = ColorTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("STEP 2 · PRODUCT", color = ColorTextMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.6.sp)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProductCard(
                    title = "Petrol", code = "PMS",
                    price = state.prices[Product.PMS]?.pricePerLitre,
                    selected = state.product == Product.PMS,
                    modifier = Modifier.weight(1f),
                    onClick = { onPickProduct(Product.PMS) }
                )
                ProductCard(
                    title = "Diesel", code = "AGO",
                    price = state.prices[Product.AGO]?.pricePerLitre,
                    selected = state.product == Product.AGO,
                    modifier = Modifier.weight(1f),
                    onClick = { onPickProduct(Product.AGO) }
                )
            }
        }

        if (state.product != null) {
            GwCard {
                Text("STEP 3 · AMOUNT PAID", color = ColorTextMuted, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, letterSpacing = 0.6.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .border(2.dp, if (state.amountDigits.isNotEmpty()) ColorPrimary else com.example.loyaltyapp.ui.theme.ColorBorderStrong, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("KSh", color = ColorTextMuted, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Box(modifier = Modifier.width(10.dp))
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TabularText(
                            text = if (state.amountDigits.isEmpty()) "0" else Money.formatKesPlain(state.amountPaid),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (state.amountDigits.isEmpty()) com.example.loyaltyapp.ui.theme.ColorBorderStrong else ColorText
                        )
                        com.example.loyaltyapp.ui.components.BlinkingCursor(
                            modifier = Modifier.padding(start = 4.dp),
                            height = 26.dp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .border(1.dp, ColorBorder, RoundedCornerShape(8.dp))
                            .clickable(onClick = onClearAmount),
                        contentAlignment = Alignment.Center
                    ) { Text("C", color = ColorTextSecondary, fontSize = 15.sp) }
                }

                // Cashback is calculated and stored on the sale, but is never shown to the
                // attendant — surfacing the formula or amount here would let an attendant and
                // customer coordinate to game it. Only the customer learns their cashback, by SMS.

                state.entryBlockReason?.let {
                    Text(it, color = GwTheme.extended.warning, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }

                PrimaryButton(
                    text = "Review sale",
                    onClick = onReview,
                    enabled = state.readyForReview,
                    modifier = Modifier.padding(top = 8.dp),
                    height = 50.dp
                )
            }

            NumericKeypad(onDigit = onAmountDigit, onBackspace = onAmountBackspace)
        }
    }
}

@Composable
private fun ProductCard(
    title: String,
    code: String,
    price: java.math.BigDecimal?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .background(if (selected) ColorPrimaryTint else ColorSurface, RoundedCornerShape(12.dp))
            .border(2.dp, if (selected) ColorPrimary else ColorBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = ColorText)
        Text(code, color = ColorTextMuted, fontSize = 12.sp)
        price?.let {
            TabularText(Money.formatPricePerLitre(it), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
