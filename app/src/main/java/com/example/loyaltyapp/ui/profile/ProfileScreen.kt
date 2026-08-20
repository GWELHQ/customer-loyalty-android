package com.example.loyaltyapp.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.loyaltyapp.ui.components.AppBottomNav
import com.example.loyaltyapp.ui.components.BottomNavDestination
import com.example.loyaltyapp.ui.components.ConnectionState
import com.example.loyaltyapp.ui.components.GwCard
import com.example.loyaltyapp.ui.components.SecondaryButton
import com.example.loyaltyapp.ui.components.SyncBanner
import com.example.loyaltyapp.ui.components.TopStatusBar
import com.example.loyaltyapp.ui.sale.initialsOf
import com.example.loyaltyapp.ui.theme.ColorBg
import com.example.loyaltyapp.ui.theme.ColorSecondaryTint
import com.example.loyaltyapp.ui.theme.ColorTextMuted
import com.example.loyaltyapp.ui.theme.ColorTextSecondary
import com.example.loyaltyapp.ui.theme.GwBlue500
import com.example.loyaltyapp.ui.theme.GwTheme

@Composable
fun ProfileScreen(
    onGoNewSale: () -> Unit,
    onGoToday: () -> Unit,
    onSyncQueue: () -> Unit,
    onDailySummary: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showSignOutWarning by remember { mutableStateOf(false) }
    val session = state.session

    LaunchedEffect(state.signedOut) {
        if (state.signedOut) onSignedOut()
    }

    Column(modifier = Modifier.fillMaxSize().background(ColorBg)) {
        TopStatusBar(
            stationName = state.stationName,
            attendantName = session?.fullName.orEmpty(),
            connectionState = if (state.isOnline) ConnectionState.ONLINE else ConnectionState.OFFLINE
        )
        if (state.pendingSyncCount > 0 && !state.isOnline) {
            SyncBanner(pendingCount = state.pendingSyncCount, onSyncNow = viewModel::syncNow)
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GwCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(52.dp).background(ColorSecondaryTint, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(initialsOf(session?.fullName.orEmpty()), color = GwBlue500, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Box(modifier = Modifier.padding(start = 13.dp)) {
                        Column {
                            Text(session?.fullName.orEmpty(), fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                            Text("Pump attendant · ${state.stationName}", color = ColorTextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(com.example.loyaltyapp.ui.theme.ColorSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, com.example.loyaltyapp.ui.theme.ColorBorder, RoundedCornerShape(12.dp))
            ) {
                Text(
                    "APP HEALTH",
                    color = ColorTextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(14.dp)
                )
                HealthRow("Connection", if (state.isOnline) "Online" else "Offline", ok = state.isOnline)
                HealthRow("Sales still on this phone", state.pendingSyncCount.toString(), ok = state.pendingSyncCount == 0)
                HealthRow("Prices", if (state.pricesUpToDate) "Up to date" else "Missing this month", ok = state.pricesUpToDate)
                HealthRow("App version", "1.0.0", ok = true)
                Column(modifier = Modifier.padding(13.dp)) {
                    SecondaryButton(text = "Sync now", onClick = viewModel::syncNow, height = 44.dp)
                    Text(
                        "Sales sync on their own whenever the phone has network. Use this only if a sale has been waiting a long time.",
                        color = ColorTextMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    androidx.compose.material3.TextButton(onClick = onSyncQueue, modifier = Modifier.padding(top = 6.dp)) {
                        Text("View sync queue")
                    }
                }
            }

            GwCard {
                Text("End of shift", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "See today's reconciliation summary before you sign out.",
                    color = ColorTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )
                SecondaryButton(text = "End-of-shift summary", onClick = onDailySummary, height = 44.dp)
            }

            GwCard {
                Text("How a sale works", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HelpLine("Find the customer by phone number, choose petrol or diesel, enter the amount paid.")
                    HelpLine("The system works out the customer's cashback and sends them an SMS.")
                    HelpLine("You never set or change a customer's cashback.")
                }
            }

            GwCard {
                Text("When to call your supervisor", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HelpLine("A sale still says Needs review after you tap Sync now.")
                    HelpLine("The app says the price for this month is missing.")
                    HelpLine("A customer says they did not get their SMS.")
                    Text("Supervisor on duty: Beatrice Ochieng · +254 733 118 400", color = ColorTextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }

            if (state.pendingSyncCount > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GwTheme.extended.warningTint, RoundedCornerShape(12.dp))
                        .padding(13.dp)
                ) {
                    Text(
                        "${state.pendingSyncCount} sales are still on this phone. Signing out keeps them, but they only reach the office from this phone. Sync before you sign out.",
                        color = ColorTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            SecondaryButton(
                text = "Sign out",
                onClick = {
                    if (viewModel.canSignOutQuietly()) viewModel.confirmSignOut() else showSignOutWarning = true
                },
                height = 46.dp
            )
        }

        AppBottomNav(
            current = BottomNavDestination.PROFILE,
            onSelect = { dest ->
                when (dest) {
                    BottomNavDestination.NEW_SALE -> onGoNewSale()
                    BottomNavDestination.TODAY -> onGoToday()
                    BottomNavDestination.PROFILE -> Unit
                }
            }
        )
    }

    if (showSignOutWarning) {
        AlertDialog(
            onDismissRequest = { showSignOutWarning = false },
            title = { Text("Sales still on this phone") },
            text = {
                Text(
                    "${state.pendingSyncCount} sale(s) have not reached the office yet. They will stay saved on this phone and try again automatically, but only this phone can send them. Sign out anyway?"
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showSignOutWarning = false
                    viewModel.confirmSignOut()
                }) { Text("Sign out anyway") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showSignOutWarning = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HealthRow(label: String, value: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = ColorTextSecondary, fontSize = 13.sp)
        Text(
            value,
            color = if (ok) com.example.loyaltyapp.ui.theme.ColorText else GwTheme.extended.warning,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun HelpLine(text: String) {
    Text(text, color = ColorTextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
}
