package com.example.loyaltyapp.ui.sale

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.loyaltyapp.ui.components.SecondaryButton
import com.example.loyaltyapp.ui.theme.ColorSurface
import com.example.loyaltyapp.ui.theme.ColorTextMuted
import com.example.loyaltyapp.ui.theme.ColorTextSecondary
import com.example.loyaltyapp.ui.theme.GwTheme

@Composable
fun BlockedScreen(monthLabel: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GwTheme.extended.dangerTint, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Error, contentDescription = null, tint = GwTheme.extended.danger, modifier = Modifier.size(26.dp))
            Text(
                "Cannot record sales right now",
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "This phone has no price for $monthLabel and cannot reach the server. Sales are blocked rather than recorded against an unknown price.",
                color = ColorTextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .background(ColorSurface, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text("What to do", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                Text("1. Move to where there is network and tap Sync now.", fontSize = 13.sp, lineHeight = 20.sp)
                Text("2. If still blocked, tell your supervisor the price for $monthLabel is not set.", fontSize = 13.sp, lineHeight = 20.sp)
                Text("3. Record the sale on paper and do not guess a price.", fontSize = 13.sp, lineHeight = 20.sp)
            }
            Text(
                "Sales already saved on this phone are not affected.",
                color = ColorTextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        SecondaryButton(text = "Try again", onClick = onRetry, height = 48.dp)
        Text(
            "Sales already saved on this phone are safe.",
            color = ColorTextSecondary,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
