package com.example.loyaltyapp.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.loyaltyapp.ui.theme.ColorText
import com.example.loyaltyapp.ui.theme.GwTheme

/** Outcome of a scan (NFC tap or QR code) shown via [ScanResultOverlay] before the flow moves on. */
data class ScanResultUi(val success: Boolean, val message: String)

/**
 * Shared "did that scan work" feedback for every tap/scan entry point (customer NFC tag, customer
 * QR code, attendant badge) — shown briefly in place of the scanner UI so a tap/scan never jumps
 * straight to the next screen with no acknowledgement. The icon pops in with a spring so it reads
 * as a deliberate result, not a layout flicker.
 */
@Composable
fun ScanResultOverlay(result: ScanResultUi, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(result) {
        scale.snapTo(0f)
        alpha.snapTo(0f)
        alpha.animateTo(1f, animationSpec = tween(durationMillis = 150))
        scale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp).alpha(alpha.value),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(scale.value)
                .background(if (result.success) GwTheme.extended.successTint else GwTheme.extended.dangerTint, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (result.success) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = if (result.success) GwTheme.extended.success else GwTheme.extended.danger,
                modifier = Modifier.size(36.dp)
            )
        }
        Box(modifier = Modifier.height(14.dp))
        Text(
            result.message,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            color = ColorText
        )
    }
}
