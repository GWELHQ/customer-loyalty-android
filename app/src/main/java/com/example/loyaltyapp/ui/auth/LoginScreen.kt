package com.example.loyaltyapp.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.loyaltyapp.R
import com.example.loyaltyapp.core.session.AttendantSession
import com.example.loyaltyapp.ui.components.GwCard
import com.example.loyaltyapp.ui.components.LoadingState
import com.example.loyaltyapp.ui.components.PrimaryButton
import com.example.loyaltyapp.ui.theme.ColorBg
import com.example.loyaltyapp.ui.theme.ColorPrimary
import com.example.loyaltyapp.ui.theme.GwBlue500
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
        }
    }
}
