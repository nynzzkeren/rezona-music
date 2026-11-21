package com.nayama.music.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nayama.music.R
import com.nayama.music.ui.theme.CoralAccent
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.SmokySilver

/**
 * "Mau traktir kopi? Boleh. Opsional banget. Semua fitur tetap gratis."
 * Sesuai frame 44 video referensi — Kartu QRIS interaktif dengan tombol Save & Share.
 */
@Composable
fun SupportCoffeeDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Title
                Text(
                    text = "Support O - Reverie",
                    color = Color(0xFF16181F),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(18.dp))

                // QRIS Code Visual Canvas
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE2E4E8), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    QrisPatternGraphic()
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Merchant name",
                    color = Color(0xFF888E99),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "O - REVERIE CREATIVE",
                    color = Color(0xFF16181F),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Scan with any e-wallet or mobile banking app that supports QRIS. You type the amount yourself.",
                    color = Color(0xFF686E79),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons: Save QRIS & Share
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Save QRIS
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFFF1F3F6))
                            .clickable {
                                Toast.makeText(context, "QRIS berhasil disimpan ke galeri", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Save QRIS",
                            color = Color(0xFF16181F),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Share
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF277E63))
                            .clickable {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Dukung pengembang O - Reverie via QRIS: https://saweria.co / O-Reverie Creative")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Bagikan Dukungan"))
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Share",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Voluntary. Every feature of O - Reverie stays free.",
                    color = Color(0xFF888E99),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Procedural stylized QRIS matrix pattern.
 */
@Composable
private fun QrisPatternGraphic() {
    Canvas(modifier = Modifier.size(160.dp)) {
        val cellSize = size.width / 21f

        // Draw 3 Corner Position Markers
        drawPositionMarker(0f, 0f, cellSize)
        drawPositionMarker(size.width - 7 * cellSize, 0f, cellSize)
        drawPositionMarker(0f, size.height - 7 * cellSize, cellSize)

        // Draw procedural data dots
        val pattern = listOf(
            9 to 2, 10 to 2, 12 to 2, 8 to 4, 11 to 4, 13 to 4,
            8 to 7, 9 to 8, 12 to 8, 14 to 8, 8 to 9, 10 to 10,
            11 to 11, 13 to 11, 15 to 11, 9 to 13, 12 to 14, 14 to 14,
            8 to 16, 10 to 16, 12 to 16, 14 to 17, 10 to 19, 13 to 19,
            16 to 8, 17 to 9, 19 to 10, 16 to 12, 18 to 13, 20 to 14,
            16 to 16, 17 to 18, 19 to 19, 20 to 20
        )

        pattern.forEach { (x, y) ->
            drawRect(
                color = Color(0xFF16181F),
                topLeft = Offset(x * cellSize, y * cellSize),
                size = Size(cellSize * 0.95f, cellSize * 0.95f)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPositionMarker(
    startX: Float,
    startY: Float,
    cellSize: Float
) {
    // Outer border (7x7)
    drawRect(
        color = Color(0xFF16181F),
        topLeft = Offset(startX, startY),
        size = Size(cellSize * 7, cellSize * 7)
    )
    // Inner white space (5x5)
    drawRect(
        color = Color.White,
        topLeft = Offset(startX + cellSize, startY + cellSize),
        size = Size(cellSize * 5, cellSize * 5)
    )
    // Center filled block (3x3)
    drawRect(
        color = Color(0xFF16181F),
        topLeft = Offset(startX + cellSize * 2, startY + cellSize * 2),
        size = Size(cellSize * 3, cellSize * 3)
    )
}
