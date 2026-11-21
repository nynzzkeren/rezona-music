package com.nayama.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.ui.theme.MediumGray
import com.nayama.music.ui.theme.PureWhite

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Premium accent bar
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .drawBehind {
                            drawRect(color = PureWhite)
                        }
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    color = PureWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = MediumGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(top = 3.dp, start = 13.dp)
                )
            }
        }

        if (actionText != null) {
            Text(
                text = actionText,
                color = MediumGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .then(if (onActionClick != null) Modifier.clickable { onActionClick() } else Modifier)
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
            )
        }
    }
}
