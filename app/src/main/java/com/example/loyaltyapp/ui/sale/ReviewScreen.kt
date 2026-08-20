package com.example.loyaltyapp.ui.sale

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.loyaltyapp.core.money.Money
import com.example.loyaltyapp.ui.components.PrimaryButton
import com.example.loyaltyapp.ui.components.TabularText
import com.example.loyaltyapp.ui.theme.ColorBorder
import com.example.loyaltyapp.ui.theme.ColorSurface
import com.example.loyaltyapp.ui.theme.ColorSurfaceSunken
import com.example.loyaltyapp.ui.theme.ColorTextSecondary
import com.example.loyaltyapp.ui.theme.GwTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReviewScreen(
    state: SaleUiState,
    onEdit: () -> Unit,
    onSubmit: () -> Unit
) {
    val customer = state.customer
    if (customer == null && !state.isNewCustomerRegistration) return
    val product = state.product ?: return
    val calc = state.calculation

    val rows = buildList {
        add("Customer" to (customer?.fullName ?: state.newCustomerName))
        add("Phone number" to (customer?.phoneNumber ?: com.example.loyaltyapp.core.phone.PhoneNumber.formatPartial(state.queryDigits)))
        add("Product" to if (product == com.example.loyaltyapp.data.local.entity.Product.PMS) "Petrol (PMS)" else "Diesel (AGO)")
        add("Amount paid" to Money.formatKes(state.amountPaid))
        add("Price per litre" to (state.selectedPrice?.let { Money.formatPricePerLitre(it.pricePerLitre) } ?: "—"))
        add("Litres" to (calc?.let { Money.formatLitres(it.litres) } ?: "—"))
        add("Station · attendant" to "${state.stationName} · ${state.attendantName}")
        add("Recorded" to SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date()))
    }

    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "← Edit amount",
            color = com.example.loyaltyapp.ui.theme.GwBlue500,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.clickable(onClick = onEdit)
        )
        Column {
            Text(
                if (state.isNewCustomerRegistration) "Check before you submit" else "Check before you record",
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
            )
            Text(
                if (state.isNewCustomerRegistration)
                    "This submits a request for supervisor approval — it is not a confirmed sale yet."
                else "Nothing here can be changed after you submit.",
                color = ColorTextSecondary,
                fontSize = 13.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ColorSurface, RoundedCornerShape(12.dp))
                .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
        ) {
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(androidx.compose.foundation.BorderStroke(0.5.dp, ColorBorder))
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, color = ColorTextSecondary, fontSize = 13.sp)
                    TabularText(value, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            Row(modifier = Modifier.fillMaxWidth().background(ColorSurfaceSunken).padding(14.dp)) {
                Text(
                    if (state.isNewCustomerRegistration)
                        "A supervisor reviews this before the customer or sale is created — cashback is worked out only after approval."
                    else "The customer's cashback is worked out by the system and sent to them by SMS.",
                    color = ColorTextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            }
        }

        if (!state.isOnline) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GwTheme.extended.warningTint, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    if (state.isNewCustomerRegistration)
                        "You are offline. This request saves on this phone and sends as soon as the phone connects."
                    else "You are offline. The sale saves on this phone and the SMS is sent as soon as the phone connects.",
                    color = ColorTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        PrimaryButton(
            text = when {
                state.isSubmitting -> "Submitting…"
                state.isNewCustomerRegistration -> "Submit for approval"
                !state.isOnline -> "Record sale (saves on phone)"
                else -> "Record sale & send SMS"
            },
            onClick = onSubmit,
            enabled = !state.isSubmitting,
            height = 52.dp
        )
    }
}
