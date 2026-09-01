package com.example.loyaltyapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.loyaltyapp.ui.theme.ColorBorder
import com.example.loyaltyapp.ui.theme.ColorSurface
import com.example.loyaltyapp.ui.theme.GwRadius
import com.example.loyaltyapp.ui.theme.MonoFontFamily

private val ROWS = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf("", "0", "⌫")
)

/**
 * A large-touch-target 3x4 numeric keypad shared by phone-number entry and amount entry.
 * The blank cell in the last row is a spacer so 0 lines up under 8 and ⌫ sits at bottom-right.
 *
 * Built from plain Row/Column rather than LazyVerticalGrid: this keypad is always shown inside
 * a parent that already scrolls (the sale flow's content Column), and a lazy grid measured with
 * an unbounded height there throws — a fixed 12-cell layout has no need to be lazy anyway.
 */
@Composable
fun NumericKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        ROWS.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                row.forEach { key ->
                    KeypadKey(
                        label = key,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (key) {
                                "" -> Unit
                                "⌫" -> onBackspace()
                                else -> onDigit(key)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val enabled = label.isNotEmpty()
    val shape = RoundedCornerShape(GwRadius.md)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2.1f)
            .heightIn(max = 64.dp)
            .background(if (enabled) ColorSurface else Color.Transparent, shape)
            .then(if (enabled) Modifier.border(1.dp, ColorBorder, shape) else Modifier)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { if (label.isNotEmpty()) contentDescription = if (label == "⌫") "Backspace" else "Digit $label" },
        contentAlignment = Alignment.Center
    ) {
        if (enabled) {
            Text(text = label, fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 21.sp)
        }
    }
}
