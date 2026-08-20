package com.example.loyaltyapp.ui.sale

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.loyaltyapp.core.phone.PhoneNumber
import com.example.loyaltyapp.ui.components.GwCard
import com.example.loyaltyapp.ui.components.PrimaryButton
import com.example.loyaltyapp.ui.components.TabularText
import com.example.loyaltyapp.ui.theme.ColorPrimary
import com.example.loyaltyapp.ui.theme.ColorSecondaryTint
import com.example.loyaltyapp.ui.theme.ColorTextSecondary
import com.example.loyaltyapp.ui.theme.GwBlue700
import com.example.loyaltyapp.ui.theme.GwTheme

@Composable
fun CreateCustomerScreen(
    state: SaleUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onContinue: () -> Unit
) {
    val normalized = PhoneNumber.normalize(state.queryDigits)

    Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "← Back to search",
            color = com.example.loyaltyapp.ui.theme.GwBlue500,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.clickable(onClick = onBack)
        )
        Column {
            Text("New customer", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            Text(
                "This is not a confirmed sale yet — a supervisor must approve it before it counts.",
                color = ColorTextSecondary,
                fontSize = 13.sp
            )
        }

        GwCard {
            Text("Phone number", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .background(com.example.loyaltyapp.ui.theme.ColorSurfaceSunken, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                TabularText(normalized?.displayGrouped ?: "Invalid number", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "Checked against all stations — no existing customer on this number.",
                color = com.example.loyaltyapp.ui.theme.ColorTextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 5.dp, bottom = 14.dp)
            )

            Text("Customer name", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            OutlinedTextField(
                value = state.newCustomerName,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                placeholder = { Text("Full name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ColorPrimary)
            )

            if (state.createError != null) {
                Text(
                    state.createError,
                    color = GwTheme.extended.danger,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 14.dp)
                    .background(ColorSecondaryTint, RoundedCornerShape(8.dp))
                    .padding(11.dp)
            ) {
                Text(
                    "Next you'll pick the product and amount, then submit — a supervisor approves the customer and sale together.",
                    color = GwBlue700,
                    fontSize = 13.sp
                )
            }

            PrimaryButton(
                text = "Continue",
                onClick = onContinue,
                enabled = normalized != null && state.newCustomerName.isNotBlank()
            )
        }
    }
}
