package com.example.loyaltyapp.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import com.example.loyaltyapp.data.remote.dto.DailySummaryRowDto
import com.example.loyaltyapp.ui.components.EmptyState
import com.example.loyaltyapp.ui.components.LoadingState
import com.example.loyaltyapp.ui.components.SecondaryButton
import com.example.loyaltyapp.ui.components.TabularText
import com.example.loyaltyapp.ui.theme.ColorBg
import com.example.loyaltyapp.ui.theme.ColorBorder
import com.example.loyaltyapp.ui.theme.ColorSurface
import com.example.loyaltyapp.ui.theme.ColorTextSecondary
import com.example.loyaltyapp.ui.theme.GwTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailySummaryScreen(
    onBack: () -> Unit,
    viewModel: DailySummaryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(ColorBg).navigationBarsPadding()) {
        TopAppBar(
            title = { Text("End-of-shift summary", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            }
        )

        when {
            state.isLoading -> LoadingState(message = "Loading today's summary…")
            state.error != null -> Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Text(state.error!!, color = GwTheme.extended.danger, fontSize = 13.sp)
                SecondaryButton(text = "Try again", onClick = viewModel::load, height = 44.dp, modifier = Modifier.padding(top = 10.dp))
            }
            state.rows.isEmpty() -> EmptyState("No sales yet today", "Reconciliation rows will show up here once sales are recorded.")
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.rows) { row -> SummaryRow(row) }
            }
        }
    }
}

@Composable
private fun SummaryRow(row: DailySummaryRowDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorSurface, RoundedCornerShape(12.dp))
            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
            .padding(13.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(row.product ?: "All products", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            TabularText(Money.formatKes(java.math.BigDecimal(row.loyaltySales.toString())), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
        Text(
            "of ${Money.formatKes(java.math.BigDecimal(row.totalSales.toString()))} total station sales",
            color = ColorTextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        row.status?.let { status ->
            Text(
                status.replace('_', ' ').replaceFirstChar { it.uppercase() },
                color = if (status == "healthy") ColorTextSecondary else GwTheme.extended.warning,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        // Deliberately no cashback total here either — see TodayViewModel's KDoc for why.
    }
}
