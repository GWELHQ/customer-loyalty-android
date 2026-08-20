package com.example.loyaltyapp.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.loyaltyapp.ui.theme.ColorBorder
import com.example.loyaltyapp.ui.theme.ColorBorderStrong
import com.example.loyaltyapp.ui.theme.ColorPrimary
import com.example.loyaltyapp.ui.theme.ColorSurface
import com.example.loyaltyapp.ui.theme.ColorText
import com.example.loyaltyapp.ui.theme.ColorTextMuted
import com.example.loyaltyapp.ui.theme.ColorTextSecondary
import com.example.loyaltyapp.ui.theme.GwRadius

@Composable
fun SectionLabel(text: String, trailing: String? = null, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text.uppercase(),
            color = ColorTextMuted,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 0.6.sp
        )
        if (trailing != null) {
            Text(text = trailing, color = ColorTextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: androidx.compose.ui.unit.Dp = 52.dp
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(height),
        shape = RoundedCornerShape(GwRadius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = ColorPrimary,
            contentColor = ColorSurface,
            disabledContainerColor = ColorBorderStrong,
            disabledContentColor = ColorSurface
        )
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: androidx.compose.ui.unit.Dp = 48.dp
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(height),
        shape = RoundedCornerShape(GwRadius.md),
        border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorderStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorText)
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun GwCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ColorSurface, RoundedCornerShape(GwRadius.lg))
            .border(1.dp, ColorBorder, RoundedCornerShape(GwRadius.lg))
            .padding(14.dp),
        content = content
    )
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String = "Loading…") {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = ColorPrimary)
        Box(modifier = Modifier.height(12.dp))
        Text(message, color = ColorTextSecondary, fontSize = 13.sp)
    }
}

/**
 * A blinking caret for the app's custom numeric-entry boxes (phone lookup, amount paid) — these
 * are driven entirely by the on-screen [NumericKeypad], not a real focused `TextField`, so there
 * is no system cursor to tell the attendant the box is already accepting input. This stands in
 * for that: always visible, blinking, right where the next digit will land.
 */
@Composable
fun BlinkingCursor(
    modifier: Modifier = Modifier,
    color: Color = ColorPrimary,
    width: Dp = 2.dp,
    height: Dp = 22.dp
) {
    val transition = rememberInfiniteTransition(label = "cursor-blink")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor-alpha"
    )
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .alpha(alpha)
            .background(color)
    )
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
        Box(modifier = Modifier.height(6.dp))
        Text(body, color = ColorTextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
