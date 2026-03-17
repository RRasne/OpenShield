package com.openshield.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openshield.Amber
import com.openshield.Card1
import com.openshield.Green
import com.openshield.Red
import com.openshield.Surface2
import com.openshield.TextMuted
import com.openshield.TextPri
import com.openshield.TextSec
import com.openshield.data.db.PendingReviewEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SuspiciousReviewDialog(
    pendingReviews: List<PendingReviewEntity>,
    onDecide: (PendingReviewEntity, Boolean) -> Unit
) {
    val current = pendingReviews.firstOrNull() ?: return

    val fmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale("tr")) }
    val date = remember(current.receivedAt) { fmt.format(Date(current.receivedAt)) }
    val scorePercent = (current.score * 100).toInt()

    AlertDialog(
        onDismissRequest = { },
        containerColor = Surface2,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚠️", fontSize = 20.sp)
                Spacer(Modifier.size(8.dp))
                Column {
                    Text("Şüpheli Mesaj", color = Amber, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(date, color = TextMuted, fontSize = 11.sp)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Card1)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Amber.copy(alpha = 0.15f))
                    ) {
                        Text("$scorePercent", color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.size(10.dp))
                    Column {
                        Text(current.sender, color = TextPri, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Spam skoru: %$scorePercent", color = TextSec, fontSize = 11.sp)
                    }
                }

                if (current.reason.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Card1)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("Şüphe nedenleri:", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        current.reason
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .forEach { rule ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(4.dp).clip(CircleShape).background(Amber))
                                    Spacer(Modifier.size(6.dp))
                                    Text(rule, color = TextSec, fontSize = 11.sp)
                                }
                            }
                    }
                }

                if (pendingReviews.size > 1) {
                    Text(
                        text = "${pendingReviews.size - 1} şüpheli mesaj daha bekliyor",
                        color = TextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onDecide(current, true) },
                colors = ButtonDefaults.buttonColors(containerColor = Red),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Spam - Kara Listeye Al")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { onDecide(current, false) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Green)
            ) {
                Text("Spam Degil - Gec")
            }
        }
    )
}
