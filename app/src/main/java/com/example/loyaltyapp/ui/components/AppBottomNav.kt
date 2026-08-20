package com.example.loyaltyapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.loyaltyapp.ui.theme.ColorBorder
import com.example.loyaltyapp.ui.theme.ColorSurface
import com.example.loyaltyapp.ui.theme.ColorTextMuted
import com.example.loyaltyapp.ui.theme.GwBlue500

enum class BottomNavDestination(val label: String, val icon: ImageVector) {
    NEW_SALE("New sale", Icons.Filled.Add),
    TODAY("Today", Icons.Filled.Receipt),
    PROFILE("Profile", Icons.Filled.Person)
}

@Composable
fun AppBottomNav(
    current: BottomNavDestination,
    onSelect: (BottomNavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ColorSurface)
            .border(androidx.compose.foundation.BorderStroke(1.dp, ColorBorder))
            // Edge-to-edge lets content draw behind the gesture nav bar; pad so the tap
            // targets and labels sit above it instead of underneath it.
            .navigationBarsPadding()
    ) {
        BottomNavDestination.entries.forEach { dest ->
            val active = dest == current
            val color = if (active) GwBlue500 else ColorTextMuted
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(dest) }
                    .padding(top = 3.dp, bottom = 10.dp)
                    .height(58.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(if (active) GwBlue500 else Color.Transparent)
                ) {}
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = dest.icon, contentDescription = dest.label, tint = color, modifier = Modifier.padding(bottom = 4.dp))
                    Text(text = dest.label, color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}
