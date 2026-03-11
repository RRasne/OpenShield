package com.openshield.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openshield.AccentBlue
import com.openshield.Amber
import com.openshield.Card1
import com.openshield.EmptyState
import com.openshield.Green
import com.openshield.Red
import com.openshield.Surface1
import com.openshield.Surface2
import com.openshield.TextMuted
import com.openshield.TextPri
import com.openshield.TextSec
import com.openshield.data.model.CommunityContributionSummary
import com.openshield.data.model.FeedbackVerdict
import com.openshield.data.model.SmsHistoryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageHistoryScreen(
    messages: List<SmsHistoryItem>,
    feedback: Map<Long, FeedbackVerdict>,
    summary: CommunityContributionSummary,
    hasPermission: Boolean,
    onRefresh: () -> Unit,
    onClearMarks: () -> Unit,
    onMark: (SmsHistoryItem, FeedbackVerdict) -> Unit,
    onMarkSender: (List<SmsHistoryItem>, FeedbackVerdict) -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = Surface2,
            title = { Text("İşaretleri Temizle", color = TextPri) },
            text = { Text("Tüm spam / şüpheli / temiz işaretleri silinecek.", color = TextSec) },
            confirmButton = {
                Button(
                    onClick = {
                        onClearMarks()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) { Text("Temizle") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("İptal", color = TextSec)
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface1)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("SMS Geçmişi", color = TextPri, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${messages.size} mesaj", color = TextSec, fontSize = 12.sp)
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Yenile", tint = TextSec)
            }
            if (feedback.isNotEmpty()) {
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "İşaretleri temizle", tint = TextSec)
                }
            }
        }

        when {
            !hasPermission -> EmptyState("SMS izni gerekli", "Geçmiş için READ_SMS izni verin")
            messages.isEmpty() -> EmptyState("Geçmiş boş", "Gelen SMS kayıtları burada görünür")
            else -> {
                val grouped = remember(messages) {
                    messages.groupBy { it.sender.ifBlank { "Bilinmeyen" } }
                        .toList()
                        .sortedByDescending { (_, senderMessages) ->
                            senderMessages.maxOfOrNull { it.receivedAt } ?: 0L
                        }
                }

                LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 16.dp)) {
                    item {
                        CommunitySummaryCard(summary = summary)
                    }
                    items(grouped, key = { it.first }) { (sender, senderMessages) ->
                        SenderGroup(
                            sender = sender,
                            messages = senderMessages.sortedByDescending { it.receivedAt },
                            feedback = feedback,
                            onMark = onMark,
                            onMarkAllSpam = { onMarkSender(senderMessages, FeedbackVerdict.SPAM) },
                            onMarkAllSuspicious = { onMarkSender(senderMessages, FeedbackVerdict.SUSPICIOUS) },
                            onMarkAllClean = { onMarkSender(senderMessages, FeedbackVerdict.NOT_SPAM) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunitySummaryCard(summary: CommunityContributionSummary) {
    val fmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale("tr")) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Card1)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Topluluk katkısı", color = TextPri, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Spam ${summary.spamCount}  •  Şüpheli ${summary.suspiciousCount}  •  Temiz ${summary.notSpamCount}",
                color = TextSec,
                fontSize = 11.sp
            )
            Text(
                summary.lastSyncAt?.let { "Son Wi-Fi senkronu: ${fmt.format(Date(it))}" } ?: "Henüz topluluk senkronu yok",
                color = TextMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun SenderGroup(
    sender: String,
    messages: List<SmsHistoryItem>,
    feedback: Map<Long, FeedbackVerdict>,
    onMark: (SmsHistoryItem, FeedbackVerdict) -> Unit,
    onMarkAllSpam: () -> Unit,
    onMarkAllSuspicious: () -> Unit,
    onMarkAllClean: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val markedCount = messages.count { feedback.containsKey(it.id) }
    val spamCount = messages.count { feedback[it.id] == FeedbackVerdict.SPAM }
    val suspiciousCount = messages.count { feedback[it.id] == FeedbackVerdict.SUSPICIOUS }
    val cleanCount = messages.count { feedback[it.id] == FeedbackVerdict.NOT_SPAM }

    val accent = when {
        spamCount > 0 -> Red
        suspiciousCount > 0 -> Amber
        cleanCount > 0 -> Green
        else -> AccentBlue
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Card1)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f))
                ) {
                    Text(
                        sender.take(1).uppercase(),
                        color = accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        sender,
                        color = TextPri,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildString {
                            append("${messages.size} mesaj")
                            if (markedCount > 0) append(" • $markedCount işaretli")
                        },
                        color = TextSec,
                        fontSize = 11.sp
                    )
                }

                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    HorizontalDivider(
                        color = Surface2,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BulkActionButton(
                            modifier = Modifier.weight(1f),
                            text = "Tümünü Spam",
                            icon = Icons.Default.Block,
                            color = Red,
                            onClick = onMarkAllSpam
                        )
                        BulkActionButton(
                            modifier = Modifier.weight(1f),
                            text = "Tümünü Şüpheli",
                            icon = Icons.Default.HelpOutline,
                            color = Amber,
                            onClick = onMarkAllSuspicious
                        )
                        BulkActionButton(
                            modifier = Modifier.weight(1f),
                            text = "Tümünü Temiz",
                            icon = Icons.Default.CheckCircle,
                            color = Green,
                            onClick = onMarkAllClean
                        )
                    }

                    messages.forEach { msg ->
                        SmsMessageCard(
                            message = msg,
                            verdict = feedback[msg.id],
                            onMarkSpam = { onMark(msg, FeedbackVerdict.SPAM) },
                            onMarkSuspicious = { onMark(msg, FeedbackVerdict.SUSPICIOUS) },
                            onMarkClean = { onMark(msg, FeedbackVerdict.NOT_SPAM) }
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun BulkActionButton(
    modifier: Modifier = Modifier,
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = color.copy(alpha = 0.15f),
            contentColor = color
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun SmsMessageCard(
    message: SmsHistoryItem,
    verdict: FeedbackVerdict?,
    onMarkSpam: () -> Unit,
    onMarkSuspicious: () -> Unit,
    onMarkClean: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale("tr")) }

    val bgColor = when (verdict) {
        FeedbackVerdict.SPAM -> Red.copy(alpha = 0.06f)
        FeedbackVerdict.SUSPICIOUS -> Amber.copy(alpha = 0.08f)
        FeedbackVerdict.NOT_SPAM -> Green.copy(alpha = 0.06f)
        null -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            message.body.ifBlank { "(Boş SMS)" },
            color = TextSec,
            fontSize = 12.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                fmt.format(Date(message.receivedAt)),
                color = TextMuted,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactActionChip(
                    text = "Spam",
                    icon = Icons.Default.Block,
                    active = verdict == FeedbackVerdict.SPAM,
                    activeColor = Red,
                    onClick = onMarkSpam
                )
                CompactActionChip(
                    text = "Şüpheli",
                    icon = Icons.Default.HelpOutline,
                    active = verdict == FeedbackVerdict.SUSPICIOUS,
                    activeColor = Amber,
                    onClick = onMarkSuspicious
                )
                CompactActionChip(
                    text = "Temiz",
                    icon = Icons.Default.Check,
                    active = verdict == FeedbackVerdict.NOT_SPAM,
                    activeColor = Green,
                    onClick = onMarkClean
                )
            }
        }

        if (verdict != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                when (verdict) {
                    FeedbackVerdict.SPAM -> "Spam olarak işaretlendi"
                    FeedbackVerdict.SUSPICIOUS -> "Şüpheli olarak işaretlendi"
                    FeedbackVerdict.NOT_SPAM -> "Temiz olarak işaretlendi"
                },
                color = when (verdict) {
                    FeedbackVerdict.SPAM -> Red.copy(alpha = 0.75f)
                    FeedbackVerdict.SUSPICIOUS -> Amber.copy(alpha = 0.85f)
                    FeedbackVerdict.NOT_SPAM -> Green.copy(alpha = 0.75f)
                },
                fontSize = 10.sp
            )
        }

        HorizontalDivider(
            color = Surface2.copy(alpha = 0.5f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun CompactActionChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (active) activeColor.copy(alpha = 0.20f) else Surface2,
        modifier = Modifier.height(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = text,
                tint = if (active) activeColor else TextMuted,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text,
                color = if (active) activeColor else TextMuted,
                fontSize = 11.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}
