package com.example.loyaltyapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object GwRadius {
    val sm = 4.dp
    val md = 8.dp
    val lg = 12.dp
    val xl = 16.dp
    val full = 999.dp
}

object GwSpacing {
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 20.dp
    val x6 = 24.dp
    val x8 = 32.dp
}

/** Minimum touch target per the spec's accessibility requirement (44-48dp). */
val MinTouchTarget = 48.dp

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(GwRadius.sm),
    small = RoundedCornerShape(GwRadius.md),
    medium = RoundedCornerShape(GwRadius.lg),
    large = RoundedCornerShape(GwRadius.xl),
    extraLarge = RoundedCornerShape(GwRadius.full)
)
