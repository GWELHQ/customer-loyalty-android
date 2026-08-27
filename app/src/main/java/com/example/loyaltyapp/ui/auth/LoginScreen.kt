package com.example.loyaltyapp.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.loyaltyapp.R
import com.example.loyaltyapp.core.nfc.NfcTagReader
import com.example.loyaltyapp.core.nfc.findActivity
import com.example.loyaltyapp.core.session.AttendantSession
import com.example.loyaltyapp.ui.components.GwCard
import com.example.loyaltyapp.ui.components.LoadingState
import com.example.loyaltyapp.ui.components.PrimaryButton
import com.example.loyaltyapp.ui.components.SecondaryButton
import com.example.loyaltyapp.ui.theme.ColorBg
import com.example.loyaltyapp.ui.theme.ColorPrimary
import com.example.loyaltyapp.ui.theme.ColorTextSecondary
import com.example.loyaltyapp.ui.theme.GwBlue500
import com.example.loyaltyapp.ui.theme.GwGreen50
import com.example.loyaltyapp.ui.theme.GwGreen700
import com.example.loyaltyapp.ui.theme.GwTheme

@Composable
fun LoginScreen(
    onLoggedIn: (AttendantSession) -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.restoredSession) {
        state.restoredSession?.let(onLoggedIn)
    }

    if (state.isRestoring) {
        Column(modifier = Modifier.fillMaxSize().background(ColorBg), verticalArrangement = Arrangement.Center) {
            LoadingState(message = "Getting things ready…")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GwBlue500)
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.logo_mark),
                contentDescription = null,
                modifier = Modifier.size(44.dp)
            )
        }
        Box(modifier = Modifier.padding(top = 16.dp)) {}
        Text("GREEN WELLS LOYALTY", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        Text("Pump attendant app", color = com.example.loyaltyapp.ui.theme.GwBlue100, fontSize = 14.sp, modifier = Modifier.padding(bottom = 24.dp))

        GwCard {
            // Two independent ways to obtain a session (handover doc §3.1/§3.1b) — a plain mode
            // toggle, not a wizard step; switching modes never loses what's typed in the other.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoginModeTab(
                    text = "Tap badge",
                    selected = state.loginMode == LoginMode.BADGE,
                    onClick = { viewModel.setLoginMode(LoginMode.BADGE) },
                    modifier = Modifier.weight(1f)
                )
                LoginModeTab(
                    text = "ID + PIN",
                    selected = state.loginMode == LoginMode.PIN,
                    onClick = { viewModel.setLoginMode(LoginMode.PIN) },
                    modifier = Modifier.weight(1f)
                )
            }
            Box(modifier = Modifier.padding(top = 16.dp)) {}

            if (state.loginMode == LoginMode.PIN) {
                Text("Employee ID", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                OutlinedTextField(
                    value = state.employeeId,
                    onValueChange = viewModel::onEmployeeIdChange,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp),
                    placeholder = { Text("e.g. KIS1-042") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ColorPrimary)
                )

                Text("PIN", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                OutlinedTextField(
                    value = state.pin,
                    onValueChange = viewModel::onPinChange,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    placeholder = { Text("4-digit PIN") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ColorPrimary)
                )

                state.error?.let {
                    Text(it, color = GwTheme.extended.danger, fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp))
                }

                PrimaryButton(
                    text = if (state.isLoading) "Signing in…" else "Sign in",
                    onClick = viewModel::login,
                    enabled = !state.isLoading,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else {
                BadgeLoginFields(
                    isLoading = state.isLoading,
                    onTagRead = viewModel::loginWithBadge
                )

                state.error?.let {
                    Text(it, color = GwTheme.extended.danger, fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun LoginModeTab(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (selected) {
        PrimaryButton(text = text, onClick = onClick, modifier = modifier, height = 40.dp)
    } else {
        SecondaryButton(text = text, onClick = onClick, modifier = modifier, height = 40.dp)
    }
}

/**
 * Badge tap login (handover doc §3.1b) — reader mode is scoped to this composable being on
 * screen, same lifecycle pattern as [com.example.loyaltyapp.ui.sale.NfcScanScreen] for customer
 * tags. [onTagRead] is guarded by the caller against a re-tap firing a duplicate request while
 * one is already in flight (the reader keeps delivering reads for as long as the badge is held).
 */
@Composable
private fun BadgeLoginFields(isLoading: Boolean, onTagRead: (String) -> Unit) {
    val context = LocalContext.current
    val activity = remember { context.findActivity() }
    val reader = remember(activity) { activity?.let(::NfcTagReader) }
    val onTagReadState = rememberUpdatedState(onTagRead)

    DisposableEffect(reader) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        reader?.start { tagId -> mainHandler.post { onTagReadState.value(tagId) } }
        onDispose { reader?.stop() }
    }

    if (reader?.isAvailable == true) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
        ) {
            Box(
                modifier = Modifier.background(GwGreen50, CircleShape).padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 3.dp, color = GwGreen700)
                } else {
                    Icon(Icons.Filled.Nfc, contentDescription = null, tint = GwGreen700, modifier = Modifier.size(48.dp))
                }
            }
            Box(modifier = Modifier.padding(top = 14.dp)) {}
            Text(
                if (isLoading) "Checking badge…" else "Hold your badge against the back of the phone",
                color = ColorTextSecondary,
                fontSize = 13.sp
            )
        }
    } else {
        Text(
            "NFC isn't available on this device. Use your employee ID and PIN instead.",
            color = ColorTextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 20.dp)
        )
    }
}
