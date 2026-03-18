package com.openshield.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.openshield.Amber
import com.openshield.Card1
import com.openshield.Green
import com.openshield.Red
import com.openshield.Surface2
import com.openshield.TextMuted
import com.openshield.TextPri
import com.openshield.TextSec
import com.openshield.data.db.PendingReviewEntity

/**
 * Uygulama açılınca şüpheli SMS'ler için kullanıcı karar dialog'u.
 * Kullanıcı "Spam" veya "Değil" der, kayıt silinir.
 *
 * Birden fazla pending varsa sırayla gösterilir (current = ilki).
 */
@Composable
fun SuspiciousReviewDialog(
    pending: PendingReviewEntity,
    remaining: Int,          // kaçtane daha var (bilgi amaçlı)
    onSpam: () -> Unit,
    onNotSpam: () -> Unit
) {
    Dialog(onDismissRequest = { /* zorunlu karar — dışarı tıklayınca kapanmaz */ }) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Surface2),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Başlık
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Şüpheli SMS",
                        color = Amber,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (remaining > 1) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            "$remaining bekliyor",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Numara
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Card1)
                        .padding(14.dp)
                ) {
                    Column {
                        Text("Gönderen", color = TextMuted, fontSize = 11.sp)
                        Text(pending.sender, color = TextPri, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text("Tespit Sebebi", color = TextMuted, fontSize = 11.sp)
                        Text(pending.reason, color = TextSec, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        // Skor göstergesi
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Risk Skoru", color = TextMuted, fontSize = 11.sp)
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Amber.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "%${(pending.score * 100).toInt()}",
                                    color = Amber,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    "Bu numara spam mı?",
                    color = TextSec,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(12.dp))

                // Butonlar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Değil butonu
                    OutlinedButton(
                        onClick = onNotSpam,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Green),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Green.copy(alpha = 0.5f))
                    ) {
                        Text("✓ Değil", fontWeight = FontWeight.SemiBold)
                    }

                    // Spam butonu
                    Button(
                        onClick = onSpam,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Red)
                    ) {
                        Text("🚫 Spam", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
