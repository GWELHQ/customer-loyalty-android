package com.example.loyaltyapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.loyaltyapp.R
import com.example.loyaltyapp.ui.theme.GwBlue100
import com.example.loyaltyapp.ui.theme.GwBlue500

enum class ConnectionState { ONLINE, OFFLINE, ATTENTION }

private data class ConnVisual(val label: String, val chipBg: Color, val chipFg: Color, val dot: Color)

private fun ConnectionState.visual(): ConnVisual = when (this) {
    ConnectionState.ATTENTION -> ConnVisual("Attention needed", Color(0x38D64545), Color.White, Color(0xFFFFB3B3))
    ConnectionState.OFFLINE -> ConnVisual("Offline", Color(0x47E2962E), Color.White, Color(0xFFE2962E))
    ConnectionState.ONLINE -> ConnVisual("Online", Color(0x4733AD5C), Color.White, Color(0xFF6CCF98))
}

@Composable
fun TopStatusBar(
    stationName: String,
    attendantName: String,
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    val visual = connectionState.visual()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(GwBlue500)
            // The blue background paints edge-to-edge behind the system status bar for a
            // seamless look, but the actual content must pad below it or it overlaps the
            // clock/battery icons.
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.logo_mark),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "GREEN WELLS LOYALTY",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$stationName · $attendantName",
                        color = GwBlue100,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                modifier = Modifier
                    .background(visual.chipBg, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).background(visual.dot, CircleShape))
                Box(modifier = Modifier.width(6.dp))
                Text(text = visual.label, color = visual.chipFg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
