package com.openshield

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.openshield.data.db.BlockedLogEntity
import com.openshield.data.db.SpamDatabase
import com.openshield.data.db.SpamNumberEntity
import com.openshield.data.db.WhitelistEntity
import com.openshield.data.repository.SpamRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ─── Renkler ──────────────────────────────────────────────────────────────────
val BgDark = Color(0xFF080D18)
val Surface1 = Color(0xFF0F1623)
val Surface2 = Color(0xFF161F30)
val Card1 = Color(0xFF1C2840)
val AccentBlue = Color(0xFF3B82F6)
val AccentCyan = Color(0xFF22D3EE)
val Red = Color(0xFFEF4444)
val Amber = Color(0xFFF59E0B)
val Green = Color(0xFF22C55E)
val TextPri = Color(0xFFF8FAFC)
val TextSec = Color(0xFF94A3B8)
val TextMuted = Color(0xFF475569)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenShieldTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun OpenShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = BgDark, surface = Surface1, primary = AccentBlue,
            onBackground = TextPri, onSurface = TextPri,
        ),
        content = content
    )
}

enum class Tab { HOME, BLACKLIST, WHITELIST, LOG, SETTINGS }

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // DB & Repository direkt — ViewModel olmadan
    val db = remember { SpamDatabase.getInstance(context) }
    val repository = remember { SpamRepository(db) }

    val spamNumbers by repository.allSpamNumbers
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
        .collectAsState()
    val whitelist by repository.allWhitelist
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
        .collectAsState()
    val blockedLog by repository.recentBlocked
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
        .collectAsState()

    var activeTab by remember { mutableStateOf(Tab.HOME) }
    var isProtectionOn by remember { mutableStateOf(true) }
    var hasPermission by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms -> hasPermission = perms[Manifest.permission.RECEIVE_SMS] == true }

    Box(modifier = Modifier.fillMaxSize().background(BgDark)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when (activeTab) {
                    Tab.HOME -> HomeTab(
                        isOn = isProtectionOn,
                        hasPermission = hasPermission,
                        spamCount = spamNumbers.size,
                        blockedCount = blockedLog.size,
                        onToggle = { isProtectionOn = it },
                        onRequestPermission = {
                            val perms = buildList {
                                add(Manifest.permission.RECEIVE_SMS)
                                add(Manifest.permission.READ_SMS)
                                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionLauncher.launch(perms.toTypedArray())
                        }
                    )
                    Tab.BLACKLIST -> BlacklistTab(
                        numbers = spamNumbers,
                        onAdd = { num, label -> scope.launch(Dispatchers.IO) { repository.addSpam(num, label) } },
                        onRemove = { num -> scope.launch(Dispatchers.IO) { repository.removeSpam(num) } }
                    )
                    Tab.WHITELIST -> WhitelistTab(
                        numbers = whitelist,
                        onAdd = { num, name -> scope.launch(Dispatchers.IO) { repository.addWhitelist(num, name) } },
                        onRemove = { num -> scope.launch(Dispatchers.IO) { repository.removeWhitelist(num) } }
                    )
                    Tab.LOG -> LogTab(
                        logs = blockedLog,
                        onClear = { scope.launch(Dispatchers.IO) { repository.clearHistory() } },
                        onAddToBlacklist = { sender -> scope.launch(Dispatchers.IO) { repository.addSpam(sender, "Geçmişten eklendi") } }
                    )
                    Tab.SETTINGS -> SettingsTab(
                        onClearAll = {
                            scope.launch(Dispatchers.IO) {
                                repository.clearHistory()
                                // spam/whitelist temizle
                                spamNumbers.forEach { repository.removeSpam(it.number) }
                                whitelist.forEach { repository.removeWhitelist(it.number) }
                            }
                        }
                    )
                }
            }
            BottomNavBar(activeTab = activeTab, onTabChange = { activeTab = it })
        }
    }
}

// ─── Ana Sekme ────────────────────────────────────────────────────────────────

@Composable
fun HomeTab(
    isOn: Boolean, hasPermission: Boolean, spamCount: Int, blockedCount: Int,
    onToggle: (Boolean) -> Unit, onRequestPermission: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "p")
    val scale by pulse.animateFloat(
        initialValue = 0.97f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "s"
    )

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Surface1, BgDark)))
                    .padding(top = 60.dp, bottom = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.scale(if (isOn) scale else 1f).size(96.dp).clip(CircleShape)
                            .background(
                                if (isOn) Brush.radialGradient(listOf(AccentBlue.copy(0.25f), Color.Transparent))
                                else Brush.radialGradient(listOf(TextMuted.copy(0.1f), Color.Transparent))
                            )
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(68.dp).clip(CircleShape).background(
                                if (isOn) Brush.linearGradient(listOf(AccentBlue, AccentCyan))
                                else Brush.linearGradient(listOf(Surface2, Card1))
                            )
                        ) { Text("🛡", fontSize = 32.sp) }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("OpenShield", color = TextPri, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("SMS Spam Koruma", color = TextSec, fontSize = 14.sp)
                    Spacer(Modifier.height(20.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Card1)
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (isOn) Green else TextMuted))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isOn) "Koruma Aktif" else "Koruma Pasif",
                            color = if (isOn) Green else TextMuted,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = isOn, onCheckedChange = onToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White, checkedTrackColor = AccentBlue,
                                uncheckedThumbColor = TextMuted, uncheckedTrackColor = Surface2
                            )
                        )
                    }
                }
            }
        }

        if (!hasPermission) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Amber.copy(0.15f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Amber, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("SMS İzni Gerekli", color = Amber, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Spam SMS'leri engellemek için izin verin", color = TextSec, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onRequestPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = Amber),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text("İzin Ver", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(Modifier.weight(1f), blockedCount.toString(), "Engellenen", Red)
                StatCard(Modifier.weight(1f), spamCount.toString(), "Kara Liste", Amber)
                StatCard(Modifier.weight(1f), "Offline", "Mod", Green)
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Card1)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Nasıl Çalışır?", color = TextPri, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    InfoRow("🔍", "Numara kara liste kontrolü")
                    InfoRow("📝", "İçerik analizi (Türkçe + İngilizce)")
                    InfoRow("🔗", "URL ve IBAN tespiti")
                    InfoRow("🎰", "Kumar sitesi marka tespiti")
                    InfoRow("🏦", "Banka/fatura mesajları korunur")
                    InfoRow("🔕", "Sessiz bildirim — rahatsız etmez")
                }
            }
        }
    }
}

@Composable
fun InfoRow(icon: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Text(icon, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, color = TextSec, fontSize = 13.sp)
    }
}

// ─── Kara Liste ───────────────────────────────────────────────────────────────

@Composable
fun BlacklistTab(numbers: List<SpamNumberEntity>, onAdd: (String, String) -> Unit, onRemove: (String) -> Unit) {
    var number by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Surface2,
            title = { Text("Numara Ekle", color = TextPri) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = number, onValueChange = { number = it },
                        label = { Text("Telefon Numarası", color = TextSec) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        colors = outlinedTextFieldColors(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = label, onValueChange = { label = it },
                        label = { Text("Not (isteğe bağlı)", color = TextSec) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                        colors = outlinedTextFieldColors(), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (number.isNotBlank()) { onAdd(number.trim(), label.trim()); number = ""; label = ""; showDialog = false }
                }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("Ekle") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("İptal", color = TextSec) } }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ListHeader("Kara Liste", "${numbers.size} numara", "🚫") { showDialog = true }
        if (numbers.isEmpty()) EmptyState("Kara liste boş", "Spam numaraları buraya ekleyin")
        else LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(numbers.size) { i ->
                val e = numbers[i]
                NumberCard(e.number, e.label.ifBlank { "Manuel eklendi" }, Red, "🚫") { onRemove(e.number) }
            }
        }
    }
}

// ─── Beyaz Liste ──────────────────────────────────────────────────────────────

@Composable
fun WhitelistTab(numbers: List<WhitelistEntity>, onAdd: (String, String) -> Unit, onRemove: (String) -> Unit) {
    var number by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Surface2,
            title = { Text("Güvenli Numara Ekle", color = TextPri) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = number, onValueChange = { number = it },
                        label = { Text("Telefon Numarası", color = TextSec) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        colors = outlinedTextFieldColors(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = name, onValueChange = { name = it },
                        label = { Text("İsim (isteğe bağlı)", color = TextSec) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                        colors = outlinedTextFieldColors(), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (number.isNotBlank()) { onAdd(number.trim(), name.trim()); number = ""; name = ""; showDialog = false }
                }, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Ekle", color = Color.Black) }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("İptal", color = TextSec) } }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ListHeader("Beyaz Liste", "${numbers.size} güvenli numara", "✅") { showDialog = true }
        if (numbers.isEmpty()) EmptyState("Beyaz liste boş", "Güvenilir numaraları buraya ekleyin")
        else LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(numbers.size) { i ->
                val e = numbers[i]
                NumberCard(e.number, e.name.ifBlank { "Güvenli numara" }, Green, "✅") { onRemove(e.number) }
            }
        }
    }
}

// ─── Geçmiş ───────────────────────────────────────────────────────────────────

@Composable
fun LogTab(logs: List<BlockedLogEntity>, onClear: () -> Unit, onAddToBlacklist: (String) -> Unit) {
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = Surface2,
            title = { Text("Geçmişi Temizle", color = TextPri) },
            text = { Text("Tüm engelleme geçmişi silinecek.", color = TextSec) },
            confirmButton = {
                Button(onClick = { onClear(); showClearDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("Temizle") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("İptal", color = TextSec) } }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Surface1)
                .padding(horizontal = 20.dp, vertical = 16.dp).statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📋", fontSize = 20.sp); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Engelleme Geçmişi", color = TextPri, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${logs.size} kayıt", color = TextSec, fontSize = 12.sp)
            }
            if (logs.isNotEmpty()) {
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Temizle", tint = TextSec)
                }
            }
        }

        if (logs.isEmpty()) EmptyState("Geçmiş boş", "Engellenen SMS'ler burada görünür")
        else LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(logs.size) { i -> LogCard(logs[i]) { onAddToBlacklist(logs[i].sender) } }
        }
    }
}

@Composable
fun LogCard(log: BlockedLogEntity, onAddToBlacklist: () -> Unit) {
    val scoreColor = when { log.score > 0.8f -> Red; log.score > 0.5f -> Amber; else -> Green }
    val isSpam = log.score > 0.6f
    val fmt = SimpleDateFormat("dd MMM HH:mm", Locale("tr"))

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Card1)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(scoreColor.copy(0.15f))
            ) {
                Text(if (isSpam) "🚫" else "⚠️", fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(log.sender, color = TextPri, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(log.reason, color = TextSec, fontSize = 11.sp, maxLines = 1)
                Text(fmt.format(Date(log.blockedAt)), color = TextMuted, fontSize = 10.sp)
            }
            IconButton(onClick = onAddToBlacklist, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Kara listeye ekle", tint = TextSec, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─── Ayarlar ──────────────────────────────────────────────────────────────────

@Composable
fun SettingsTab(onClearAll: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("openshield", android.content.Context.MODE_PRIVATE) }
    var dataSharing by remember { mutableStateOf(prefs.getBoolean("data_sharing", false)) }
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = Surface2,
            title = { Text("Tüm Verileri Sil", color = TextPri) },
            text = { Text("Kara liste, beyaz liste ve geçmiş tamamen silinecek. Bu işlem geri alınamaz.", color = TextSec) },
            confirmButton = {
                Button(onClick = { onClearAll(); showClearDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("İptal", color = TextSec) } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().background(Surface1)
                    .padding(horizontal = 20.dp, vertical = 16.dp).statusBarsPadding()
            ) {
                Text("⚙️  Ayarlar", color = TextPri, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Veri paylaşımı
        item {
            SettingsSection("Gizlilik") {
                SettingsToggleRow(
                    icon = "📡",
                    title = "Topluluk Veri Paylaşımı",
                    subtitle = "Spam numaralar anonim olarak paylaşılır, uygulama gelişir",
                    checked = dataSharing,
                    onCheckedChange = {
                        dataSharing = it
                        prefs.edit().putBoolean("data_sharing", it).apply()
                    }
                )
            }
        }

        // Veri yönetimi
        item {
            SettingsSection("Veri Yönetimi") {
                SettingsButtonRow(
                    icon = "🗑️",
                    title = "Tüm Verileri Sil",
                    subtitle = "Kara liste, beyaz liste ve geçmişi temizle",
                    color = Red,
                    onClick = { showClearDialog = true }
                )
            }
        }

        // Hakkında
        item {
            SettingsSection("Hakkında") {
                SettingsInfoRow("🛡️", "OpenShield", "SMS Spam Engelleme Uygulaması")
                SettingsInfoRow("📋", "Versiyon", "0.1.0-alpha")
                SettingsInfoRow("🔒", "Lisans", "GPL-3.0")
                SettingsInfoRow("✅", "İnternet İzni", "Sadece topluluk verisi için (isteğe bağlı)")
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, color = AccentBlue, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Card1)) {
            Column { content() }
        }
    }
}

@Composable
fun SettingsToggleRow(icon: String, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPri, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextSec, fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = AccentBlue,
                uncheckedThumbColor = TextMuted, uncheckedTrackColor = Surface2
            )
        )
    }
}

@Composable
fun SettingsButtonRow(icon: String, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextSec, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
    }
}

@Composable
fun SettingsInfoRow(icon: String, title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 18.sp)
        Spacer(Modifier.width(12.dp))
        Text(title, color = TextPri, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextSec, fontSize = 12.sp)
    }
}

// ─── Ortak Bileşenler ─────────────────────────────────────────────────────────

@Composable
fun ListHeader(title: String, subtitle: String, icon: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Surface1)
            .padding(horizontal = 20.dp, vertical = 16.dp).statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 20.sp); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPri, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSec, fontSize = 12.sp)
        }
        FloatingActionButton(onClick = onAdd, containerColor = AccentBlue, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Ekle", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun NumberCard(number: String, subtitle: String, accentColor: Color, icon: String, onDelete: () -> Unit) {
    var showDelete by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable { showDelete = !showDelete },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Card1)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center,
                modifier = Modifier.size(42.dp).clip(CircleShape).background(accentColor.copy(0.15f))
            ) { Text(icon, fontSize = 16.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(number, color = TextPri, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextSec, fontSize = 12.sp)
            }
            if (showDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Red)
                }
            }
        }
    }
}

@Composable
fun StatCard(modifier: Modifier, value: String, label: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Card1).padding(vertical = 16.dp)
    ) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(label, color = TextSec, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📭", fontSize = 48.sp); Spacer(Modifier.height(12.dp))
            Text(title, color = TextPri, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSec, fontSize = 13.sp)
        }
    }
}

@Composable
fun BottomNavBar(activeTab: Tab, onTabChange: (Tab) -> Unit) {
    NavigationBar(containerColor = Surface1, tonalElevation = 0.dp) {
        listOf(
            Triple(Tab.HOME, Icons.Default.Home, "Ana Sayfa"),
            Triple(Tab.BLACKLIST, Icons.Default.Block, "Kara Liste"),
            Triple(Tab.WHITELIST, Icons.Default.CheckCircle, "Beyaz Liste"),
            Triple(Tab.LOG, Icons.Default.List, "Geçmiş"),
            Triple(Tab.SETTINGS, Icons.Default.Settings, "Ayarlar"),
        ).forEach { (tab, icon, label) ->
            NavigationBarItem(
                selected = activeTab == tab,
                onClick = { onTabChange(tab) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AccentBlue, selectedTextColor = AccentBlue,
                    unselectedIconColor = TextMuted, unselectedTextColor = TextMuted,
                    indicatorColor = AccentBlue.copy(0.15f)
                )
            )
        }
    }
}

@Composable
fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentBlue, unfocusedBorderColor = TextMuted,
    focusedTextColor = TextPri, unfocusedTextColor = TextPri, cursorColor = AccentBlue
)

private fun <T> androidx.compose.foundation.lazy.LazyListScope.items(
    count: Int, itemContent: @Composable (Int) -> Unit
) { repeat(count) { item { itemContent(it) } } }