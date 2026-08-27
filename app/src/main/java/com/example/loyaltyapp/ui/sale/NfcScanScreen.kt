package com.example.loyaltyapp.ui.sale

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.loyaltyapp.core.nfc.NfcTagReader
import com.example.loyaltyapp.core.nfc.findActivity
import com.example.loyaltyapp.ui.components.GwCard
import com.example.loyaltyapp.ui.components.SecondaryButton
import com.example.loyaltyapp.ui.theme.ColorTextSecondary
import com.example.loyaltyapp.ui.theme.GwGreen50
import com.example.loyaltyapp.ui.theme.GwGreen700

/**
 * Reads a tapped NFC tag's UID as an alternate way to pick a customer (handover doc §2) — staff
 * assign the tag to a customer once from the web admin, there's no in-app registration flow.
 * Reader mode is scoped to exactly this screen being on-screen (see [NfcTagReader]).
 */
@Composable
fun NfcScanScreen(
    onTagRead: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember { context.findActivity() }
    val reader = remember(activity) { activity?.let(::NfcTagReader) }
    val onTagReadState = rememberUpdatedState(onTagRead)

    DisposableEffect(reader) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        reader?.start { tagId -> mainHandler.post { onTagReadState.value(tagId) } }
        onDispose { reader?.stop() }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text("Tap NFC tag", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            Text(
                "Hold the customer's NFC tag against the back of the phone",
                color = ColorTextSecondary,
                fontSize = 13.sp
            )
        }

        if (reader?.isAvailable == true) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1.4f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.background(GwGreen50, CircleShape).padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Nfc, contentDescription = null, tint = GwGreen700, modifier = Modifier.size(64.dp))
                }
            }
        } else {
            GwCard {
                Text("NFC not available", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "This device doesn't support NFC, or it's turned off. Use the phone number instead.",
                    color = ColorTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        SecondaryButton(text = "Use phone number instead", onClick = onCancel)
    }
}
