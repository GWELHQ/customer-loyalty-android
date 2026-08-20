package com.example.loyaltyapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.loyaltyapp.ui.theme.ColorSurface
import com.example.loyaltyapp.ui.theme.ColorTextSecondary
import com.example.loyaltyapp.ui.theme.GwAmber500
import com.example.loyaltyapp.ui.theme.GwAmber600
import com.example.loyaltyapp.ui.theme.GwTheme

/** Amber banner surfaced whenever there are sales still on this phone waiting to sync. */
@Composable
fun SyncBanner(
    pendingCount: Int,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val text = if (pendingCount == 1) {
        "1 sale is saved on this phone. It will send on its own once there is network."
    } else {
        "$pendingCount sales saved on this phone. They will send on their own once there is network."
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(GwTheme.extended.warningTint)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = ColorTextSecondary,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f)
        )
        Row(modifier = Modifier.width(10.dp)) {}
        Button(
            onClick = onSyncNow,
            colors = ButtonDefaults.buttonColors(containerColor = ColorSurface, contentColor = GwAmber600),
            border = androidx.compose.foundation.BorderStroke(1.dp, GwAmber500),
            shape = RoundedCornerShape(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 13.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Text("Sync now", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
        }
    }
}
