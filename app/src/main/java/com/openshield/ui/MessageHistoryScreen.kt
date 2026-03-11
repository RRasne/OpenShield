package com.openshield.ui

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openshield.AccentBlue
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

private data class ConversationItem(
    val sender: String,
    val messages: List<SmsHistoryItem>
) {
    val latestMessage: SmsHistoryItem = messages.maxByOrNull { it.receivedAt } ?: error("Conversation is empty")
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var search by remember { mutableStateOf("") }
    var selectedSender by remember { mutableStateOf<String?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    val filteredMessages = remember(messages, search) {
        if (search.isBlank()) {
            messages
        } else {
            val query = search.trim().lowercase(Locale.getDefault())
            messages.filter {
                it.sender.lowercase(Locale.getDefault()).contains(query) ||
                    it.body.lowercase(Locale.getDefault()).contains(query)
            }
        }
    }

    val conversations = remember(filteredMessages) {
        filteredMessages
            .groupBy { it.sender.ifBlank { "Bilinmeyen" } }
            .map { (sender, rows) -> ConversationItem(sender = sender, messages = rows.sortedByDescending { it.receivedAt }) }
            .sortedByDescending { it.latestMessage.receivedAt }
    }

    val selectedConversation = remember(selectedSender, conversations) {
        conversations.firstOrNull { it.sender == selectedSender }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = Surface2,
            title = { Text("Isaretleri temizle", color = TextPri) },
            text = { Text("Tum kullanici isaretleri silinecek.", color = TextSec) },
            confirmButton = {
                TextButton(onClick = {
                    onClearMarks()
                    showClearDialog = false
                }) { Text("Temizle", color = Red) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Iptal", color = TextSec) }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().background(Surface1)
                .statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedConversation != null) {
                    IconButton(onClick = { selectedSender = null }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = TextPri)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (selectedConversation == null) "Mesajlar" else selectedConversation.sender,
                        color = TextPri,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (selectedConversation == null) "Konusmalar ve kullanici geri bildirimleri" else "${selectedConversation.messages.size} mesaj",
                        color = TextSec,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Yenile", tint = TextSec)
                }
                if (feedback.isNotEmpty()) {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Temizle", tint = TextSec)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(if (selectedConversation == null) "Mesaj ara veya kisi ara" else "Bu konusmada ara") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = TextMuted,
                    focusedTextColor = TextPri,
                    unfocusedTextColor = TextPri,
                    cursorColor = AccentBlue
                )
            )
        }

        when {
            !hasPermission -> EmptyState("SMS izni gerekli", "Gecmis ve arama icin READ_SMS izni verin")
            messages.isEmpty() -> EmptyState("Mesaj bulunamadi", "Gelen SMS'ler burada listelenecek")
            selectedConversation == null -> ConversationList(
                conversations = conversations,
                feedback = feedback,
                summary = summary,
                onOpenConversation = { selectedSender = it }
            )
            else -> ConversationDetail(
                conversation = selectedConversation,
                feedback = feedback,
                search = search,
                onMark = onMark,
                onMarkSender = onMarkSender
            )
        }
    }
}

@Composable
private fun ConversationList(
    conversations: List<ConversationItem>,
    feedback: Map<Long, FeedbackVerdict>,
    summary: CommunityContributionSummary,
    onOpenConversation: (String) -> Unit
) {
    if (conversations.isEmpty()) {
        EmptyState("Sonuc yok", "Aramana uygun bir mesaj bulunamadi")
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            CommunitySummaryCard(summary = summary)
        }
        items(conversations, key = { it.sender }) { conversation ->
            val latestVerdict = conversation.messages
                .mapNotNull { feedback[it.id] }
                .firstOrNull()
            ConversationCard(
                conversation = conversation,
                latestVerdict = latestVerdict,
                onClick = { onOpenConversation(conversation.sender) }
            )
        }
    }
}

@Composable
private fun CommunitySummaryCard(summary: CommunityContributionSummary) {
    val fmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale("tr")) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Card1)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Topluluk katkisi", color = TextPri, fontWeight = FontWeight.SemiBold)
            Text(
                "Spam: ${summary.spamCount}   Supheli: ${summary.suspiciousCount}   Spam degil: ${summary.notSpamCount}",
                color = TextSec,
                fontSize = 12.sp
            )
            Text(
                summary.lastSyncAt?.let { "Son Wi-Fi senkronu: ${fmt.format(Date(it))}" } ?: "Henuz topluluk senkronu yok",
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ConversationCard(
    conversation: ConversationItem,
    latestVerdict: FeedbackVerdict?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Card1)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.background(AccentBlue.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(conversation.messages.size.toString(), color = AccentBlue, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(conversation.sender, color = TextPri, fontWeight = FontWeight.SemiBold)
                Text(conversation.latestMessage.body, color = TextSec, fontSize = 12.sp, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatDate(conversation.latestMessage.receivedAt), color = TextMuted, fontSize = 10.sp)
                if (latestVerdict != null) {
                    Spacer(Modifier.height(6.dp))
                    VerdictBadge(verdict = latestVerdict)
                }
            }
        }
    }
}

@Composable
private fun ConversationDetail(
    conversation: ConversationItem,
    feedback: Map<Long, FeedbackVerdict>,
    search: String,
    onMark: (SmsHistoryItem, FeedbackVerdict) -> Unit,
    onMarkSender: (List<SmsHistoryItem>, FeedbackVerdict) -> Unit
) {
    val visibleMessages = remember(conversation, search) {
        if (search.isBlank()) {
            conversation.messages
        } else {
            val query = search.trim().lowercase(Locale.getDefault())
            conversation.messages.filter { it.body.lowercase(Locale.getDefault()).contains(query) }
        }
    }

    if (visibleMessages.isEmpty()) {
        EmptyState("Sonuc yok", "Bu konusmada aramana uyan mesaj bulunamadi")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Card1)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bu gondericinin tum mesajlari", color = TextSec, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VerdictButton(
                            text = "Tumunu Spam",
                            active = false,
                            activeColor = Red,
                            onClick = { onMarkSender(conversation.messages, FeedbackVerdict.SPAM) }
                        )
                        VerdictButton(
                            text = "Tumunu Supheli",
                            active = false,
                            activeColor = AccentBlue,
                            onClick = { onMarkSender(conversation.messages, FeedbackVerdict.SUSPICIOUS) }
                        )
                        VerdictButton(
                            text = "Tumunu Temiz",
                            active = false,
                            activeColor = Green,
                            onClick = { onMarkSender(conversation.messages, FeedbackVerdict.NOT_SPAM) }
                        )
                    }
                }
            }
        }
        items(visibleMessages, key = { it.id }) { message ->
            MessageBubble(
                message = message,
                verdict = feedback[message.id],
                onMark = onMark
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: SmsHistoryItem,
    verdict: FeedbackVerdict?,
    onMark: (SmsHistoryItem, FeedbackVerdict) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Card1)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(message.body.ifBlank { "(Bos SMS)" }, color = TextPri, fontSize = 13.sp)
            Text(formatDate(message.receivedAt), color = TextMuted, fontSize = 10.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VerdictButton(
                    text = "Spam",
                    active = verdict == FeedbackVerdict.SPAM,
                    activeColor = Red,
                    onClick = { onMark(message, FeedbackVerdict.SPAM) }
                )
                VerdictButton(
                    text = "Supheli",
                    active = verdict == FeedbackVerdict.SUSPICIOUS,
                    activeColor = AccentBlue,
                    onClick = { onMark(message, FeedbackVerdict.SUSPICIOUS) }
                )
                VerdictButton(
                    text = "Spam degil",
                    active = verdict == FeedbackVerdict.NOT_SPAM,
                    activeColor = Green,
                    onClick = { onMark(message, FeedbackVerdict.NOT_SPAM) }
                )
            }
        }
    }
}

@Composable
private fun VerdictButton(
    text: String,
    active: Boolean,
    activeColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (active) activeColor.copy(alpha = 0.22f) else Surface2,
            contentColor = if (active) activeColor else TextSec
        )
    ) {
        Text(text)
    }
}

@Composable
private fun VerdictBadge(verdict: FeedbackVerdict) {
    val color = when (verdict) {
        FeedbackVerdict.SPAM -> Red
        FeedbackVerdict.SUSPICIOUS -> AccentBlue
        FeedbackVerdict.NOT_SPAM -> Green
    }
    val text = when (verdict) {
        FeedbackVerdict.SPAM -> "Spam"
        FeedbackVerdict.SUSPICIOUS -> "Supheli"
        FeedbackVerdict.NOT_SPAM -> "Temiz"
    }

    Box(
        modifier = Modifier.background(color.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatDate(timestamp: Long): String {
    val fmt = SimpleDateFormat("dd MMM HH:mm", Locale("tr"))
    return fmt.format(Date(timestamp))
}






