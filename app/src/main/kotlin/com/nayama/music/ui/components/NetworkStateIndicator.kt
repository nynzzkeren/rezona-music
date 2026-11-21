package com.nayama.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.ui.theme.DarkCharcoal
import com.nayama.music.ui.theme.Graphite
import com.nayama.music.ui.theme.MediumGray
import com.nayama.music.ui.theme.PureWhite

@Composable
fun MonochromeLoadingIndicator(
    modifier: Modifier = Modifier,
    message: String = "Memuat katalog musik..."
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = PureWhite,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                color = MediumGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun MonochromeErrorView(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCharcoal)
            .border(1.dp, Graphite, RoundedCornerShape(12.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "KONEKSI TERGANGGU",
                color = MediumGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = PureWhite,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(PureWhite)
                    .clickable { onRetry() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Coba Lagi",
                    color = com.nayama.music.ui.theme.PureBlack,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun MonochromeEmptyView(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = PureWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = MediumGray,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

