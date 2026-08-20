package com.example.loyaltyapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.loyaltyapp.data.local.entity.SmsStatus
import com.example.loyaltyapp.data.local.entity.SyncStatus
import com.example.loyaltyapp.ui.theme.GwTheme

enum class PillTone { OK, WARN, BAD, INFO }

@Composable
fun StatusPill(
    text: String,
    tone: PillTone,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (tone) {
        PillTone.OK -> GwTheme.extended.successTint to GwTheme.extended.success
        PillTone.WARN -> GwTheme.extended.warningTint to GwTheme.extended.warning
        PillTone.BAD -> GwTheme.extended.dangerTint to GwTheme.extended.danger
        PillTone.INFO -> GwTheme.extended.infoTint to GwTheme.extended.info
    }
    Box(
        modifier = modifier
            .background(bg, shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(text = text, color = fg, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

fun SyncStatus.toPillTone(): PillTone = when (this) {
    SyncStatus.SYNCED -> PillTone.OK
    SyncStatus.PENDING, SyncStatus.SYNCING -> PillTone.WARN
    SyncStatus.NEEDS_REVIEW -> PillTone.WARN
    SyncStatus.FAILED, SyncStatus.CONFLICT -> PillTone.BAD
}

fun SyncStatus.toDisplayLabel(): String = when (this) {
    SyncStatus.SYNCED -> "Synced"
    SyncStatus.PENDING -> "Saved on phone"
    SyncStatus.SYNCING -> "Syncing…"
    SyncStatus.NEEDS_REVIEW -> "Price changed"
    SyncStatus.FAILED -> "Sync failed"
    SyncStatus.CONFLICT -> "Needs review"
}

fun SmsStatus.toPillTone(): PillTone = when (this) {
    SmsStatus.SENT -> PillTone.OK
    SmsStatus.PENDING -> PillTone.WARN
    SmsStatus.FAILED -> PillTone.BAD
    SmsStatus.NOT_APPLICABLE -> PillTone.INFO
}

fun SmsStatus.toDisplayLabel(): String = when (this) {
    SmsStatus.SENT -> "SMS sent"
    SmsStatus.PENDING -> "SMS waiting"
    SmsStatus.FAILED -> "SMS failed"
    SmsStatus.NOT_APPLICABLE -> "No SMS"
}

/** Small colored dot, used inside the connection pill in the top bar. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(color, CircleShape).padding(0.dp))
}
