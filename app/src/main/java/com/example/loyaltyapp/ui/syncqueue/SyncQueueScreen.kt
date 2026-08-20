package com.example.loyaltyapp.ui.syncqueue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.loyaltyapp.core.money.Money
import com.example.loyaltyapp.data.local.entity.SaleEntity
import com.example.loyaltyapp.data.local.entity.SyncStatus
import com.example.loyaltyapp.ui.components.EmptyState
import com.example.loyaltyapp.ui.components.SecondaryButton
import com.example.loyaltyapp.ui.components.StatusPill
import com.example.loyaltyapp.ui.components.TabularText
import com.example.loyaltyapp.ui.components.toDisplayLabel
import com.example.loyaltyapp.ui.components.toPillTone
import com.example.loyaltyapp.ui.theme.ColorBg
import com.example.loyaltyapp.ui.theme.ColorBorder
import com.example.loyaltyapp.ui.theme.ColorSurface
import com.example.loyaltyapp.ui.theme.ColorTextMuted
import com.example.loyaltyapp.ui.theme.ColorTextSecondary
import com.example.loyaltyapp.ui.theme.GwTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncQueueScreen(
    onBack: () -> Unit,
    viewModel: SyncQueueViewModel = hiltViewModel()
) {
    val sales by viewModel.sales.collectAsState()
    val retryingIds by viewModel.retryingIds.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(ColorBg).navigationBarsPadding()) {
        TopAppBar(
            title = { Text("Sync queue", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            }
        )

        val pending = sales.filter { it.syncStatus == SyncStatus.PENDING || it.syncStatus == SyncStatus.SYNCING }
        val failed = sales.filter { it.syncStatus == SyncStatus.FAILED }
        val conflicts = sales.filter { it.syncStatus == SyncStatus.CONFLICT }
        val needsReview = sales.filter { it.syncStatus == SyncStatus.NEEDS_REVIEW }
        val synced = sales.filter { it.syncStatus == SyncStatus.SYNCED }

        if (pending.isNotEmpty() || failed.isNotEmpty() || conflicts.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                SecondaryButton(text = "Sync now", onClick = viewModel::syncAll, height = 44.dp)
            }
        }

        if (sales.isEmpty()) {
            EmptyState("Nothing in the queue", "Sales you record will appear here until the office confirms them.")
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (conflicts.isNotEmpty()) item { SectionHeader("Needs attention") }
            items(conflicts, key = { it.localSaleId }) { QueueRow(it, isRetrying = false, onRetry = null) }

            if (needsReview.isNotEmpty()) item { SectionHeader("Price changed") }
            items(needsReview, key = { it.localSaleId }) { QueueRow(it, isRetrying = false, onRetry = null) }

            if (failed.isNotEmpty()) item { SectionHeader("Sync failed") }
            items(failed, key = { it.localSaleId }) {
                QueueRow(it, isRetrying = retryingIds.contains(it.localSaleId), onRetry = { viewModel.retry(it) })
            }

            if (pending.isNotEmpty()) item { SectionHeader("Waiting to sync") }
            items(pending, key = { it.localSaleId }) { QueueRow(it, isRetrying = false, onRetry = null) }

            if (synced.isNotEmpty()) item { SectionHeader("Synced") }
            items(synced, key = { it.localSaleId }) { QueueRow(it, isRetrying = false, onRetry = null) }

            item { Box(modifier = Modifier.padding(bottom = 12.dp)) {} }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        color = ColorTextMuted,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun QueueRow(sale: SaleEntity, isRetrying: Boolean, onRetry: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorSurface, RoundedCornerShape(12.dp))
            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
            .padding(13.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(sale.customerName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                TabularText(
                    "${SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(sale.capturedAtMillis))} · ${sale.localSaleId.take(8).uppercase()}",
                    color = ColorTextSecondary,
                    fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                TabularText(Money.formatKes(sale.amountPaidKes), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Row(modifier = Modifier.padding(top = 8.dp)) {
            StatusPill(text = sale.syncStatus.toDisplayLabel(), tone = sale.syncStatus.toPillTone())
        }
        sale.lastSyncErrorMessage?.let { message ->
            Text(
                message,
                color = if (sale.syncStatus == SyncStatus.CONFLICT) GwTheme.extended.danger else GwTheme.extended.warning,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (onRetry != null) {
            Box(modifier = Modifier.padding(top = 10.dp)) {
                SecondaryButton(
                    text = if (isRetrying) "Retrying…" else "Retry",
                    onClick = onRetry,
                    enabled = !isRetrying,
                    height = 40.dp
                )
            }
        }
    }
}
