package com.openshield.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openshield.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import com.openshield.worker.CommunityReportWorker

private val BgDark     = Color(0xFF080D18)
private val Surface1   = Color(0xFF0F1623)
private val Surface2   = Color(0xFF161F30)
private val Card1      = Color(0xFF1C2840)
private val AccentBlue = Color(0xFF3B82F6)
private val AccentCyan = Color(0xFF22D3EE)
private val Green      = Color(0xFF22C55E)
private val Amber      = Color(0xFFF59E0B)
private val TextPri    = Color(0xFFF8FAFC)
private val TextSec    = Color(0xFF94A3B8)
private val TextMuted  = Color(0xFF475569)

@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (OnboardingCheck.isDone(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = BgDark, surface = Surface1,
                    primary = AccentBlue, onBackground = TextPri, onSurface = TextPri
                )
            ) {
                OnboardingFlow(
                    onComplete = { accepted ->
                        CommunityReportWorker.setConsent(this, accepted)
                        OnboardingCheck.markDone(this)
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun OnboardingFlow(onComplete: (Boolean) -> Unit) {
    var step by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { step = 3 }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            if (targetState > initialState)
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
            else
                slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
        },
        label = "onboarding"
    ) { s ->
        when (s) {
            0 -> WelcomeStep(onNext = { step = 1 })
            1 -> HowItWorksStep(onNext = { step = 2 }, onBack = { step = 0 })
            2 -> SmsPermissionStep(
                onGrant = {
                    val perms = buildList {
                        add(Manifest.permission.RECEIVE_SMS)
                        add(Manifest.permission.READ_SMS)
                        if (Build.VERSION.SDK_INT >= 33)
                            add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(perms.toTypedArray())
                },
                onSkip  = { step = 3 },
                onBack  = { step = 1 }
            )
            3 -> CommunityConsentStep(
                onAccept  = { onComplete(true) },
                onDecline = { onComplete(false) },
                onBack    = { step = 2 }
            )
        }
    }
}

// ─── Adım 1: Hoş Geldin ───────────────────────────────────────────────────────

@Composable
fun WelcomeStep(onNext: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "p")
    val scale by pulse.animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "s"
    )

    // fillMaxSize + verticalScroll → içerik ne kadar uzun olursa olsun buton her zaman görünür
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        StepIndicator(current = 0, total = 4)
        Spacer(Modifier.height(40.dp))

        // Animasyonlu logo
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(110.dp * scale)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(AccentBlue.copy(0.28f), Color.Transparent)))
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(AccentBlue, AccentCyan)))
            ) {
                Text("🛡", fontSize = 36.sp)
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("OpenShield", color = TextPri, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Gizliliğini koruyan\nSMS spam engelleyici",
            color = TextSec, fontSize = 15.sp,
            textAlign = TextAlign.Center, lineHeight = 23.sp
        )

        Spacer(Modifier.height(36.dp))

        FeatureRow("🔒", "Tamamen çevrimdışı",  "Hiçbir SMS içeriği cihazından çıkmaz")
        Spacer(Modifier.height(10.dp))
        FeatureRow("🧠", "Akıllı tespit",        "Kural tabanlı + topluluk listesi")
        Spacer(Modifier.height(10.dp))
        FeatureRow("🇹🇷", "Türkçe optimize",     "Kumar, dolandırıcılık, siyasi spam")
        Spacer(Modifier.height(10.dp))
        FeatureRow("🚫", "Reklam yok",            "Sıfır analitik, sıfır izleme")

        // Spacer ile buton en alta itilir ama scroll olduğu için kaybolmaz
        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
        ) {
            Text("Başlayalım  →", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ─── Adım 2: Nasıl Çalışır ────────────────────────────────────────────────────

@Composable
fun HowItWorksStep(onNext: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        StepIndicator(current = 1, total = 4)
        Spacer(Modifier.height(28.dp))

        Text("Nasıl Çalışır?", color = TextPri, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Her gelen SMS üç katmandan geçer.",
            color = TextSec, fontSize = 14.sp, textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        FlowStep("1", AccentBlue,          "📞", "Numara Kontrolü",
            "Gönderen numara kara listende mi? Topluluk spam listesinde mi? Beyaz listende mi?")
        FlowArrow()
        FlowStep("2", Amber,               "📝", "İçerik Analizi",
            "Türkçe/İngilizce spam kelimeleri, IBAN, URL, kumar sitesi, SMSRET kodu.")
        FlowArrow()
        FlowStep("3", Color(0xFF8B5CF6),   "⚖️", "Kombine Karar",
            "> 0.60 → Spam engellendi  ·  0.40–0.60 → Şüpheli uyarı  ·  < 0.40 → Temiz")

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Green.copy(0.1f))
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Text("🔒", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Hiçbir aşamada internet kullanılmaz. SMS içerikleri asla diske yazılmaz.",
                    color = Green, fontSize = 13.sp, lineHeight = 19.sp
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
        ) {
            Text("Devam  →", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Geri", color = TextMuted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ─── Adım 3: SMS İzni ─────────────────────────────────────────────────────────

@Composable
fun SmsPermissionStep(onGrant: () -> Unit, onSkip: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        StepIndicator(current = 2, total = 4)
        Spacer(Modifier.height(48.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(88.dp).clip(CircleShape).background(Amber.copy(0.15f))
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Amber, modifier = Modifier.size(40.dp))
        }

        Spacer(Modifier.height(24.dp))
        Text("SMS İzni", color = TextPri, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Gelen SMS'leri analiz edebilmek için SMS okuma iznine ihtiyaç var.\n\nSMS içerikleri hiçbir zaman cihazınızdan çıkmaz.",
            color = TextSec, fontSize = 15.sp,
            textAlign = TextAlign.Center, lineHeight = 22.sp
        )

        Spacer(Modifier.height(32.dp))

        PermissionCard("📨", "SMS Al (RECEIVE_SMS)", "Gelen mesajları analiz etmek için")
        Spacer(Modifier.height(10.dp))
        PermissionCard("📖", "SMS Oku (READ_SMS)", "Filtre yönetimi için")

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
        ) {
            Text("İzin Ver", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Şimdi değil", color = TextMuted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Geri", color = TextMuted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ─── Adım 4: Topluluk Katkısı ─────────────────────────────────────────────────

@Composable
fun CommunityConsentStep(onAccept: () -> Unit, onDecline: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        StepIndicator(current = 3, total = 4)
        Spacer(Modifier.height(28.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(88.dp).clip(CircleShape).background(Green.copy(0.15f))
        ) {
            Icon(Icons.Default.People, contentDescription = null, tint = Green, modifier = Modifier.size(40.dp))
        }

        Spacer(Modifier.height(18.dp))
        Text("Topluluğa Katkı", color = TextPri, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text("Bu adım isteğe bağlı — ama çok değerli.", color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

        Spacer(Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Surface2)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "OpenShield açık kaynak ve tamamen reklamsız. Spam tespitinin gelişmesi için tek şey topluluk verisi. Sen bildirirsin, herkes korunur.",
                    color = TextPri, fontSize = 14.sp, lineHeight = 21.sp
                )
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Card1)
                Spacer(Modifier.height(14.dp))

                Text("✅  Ne gönderilir?", color = AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ConsentRow("·", "Spam numaranın SHA-256 hash'i (tek yönlü, geri alınamaz)", true)
                ConsentRow("·", "Spam kategorisi: kumar, dolandırıcılık, siyasi vb.", true)
                ConsentRow("·", "Uygulama versiyonu ve dil kodu", true)

                Spacer(Modifier.height(12.dp))
                Text("🚫  Asla gönderilmez:", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ConsentRow("·", "Gerçek telefon numarası", false)
                ConsentRow("·", "SMS içeriği", false)
                ConsentRow("·", "Konum, kimlik veya herhangi bir kişisel veri", false)
            }
        }

        Spacer(Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = AccentBlue.copy(0.1f))
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📡", fontSize = 16.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Yalnızca internete bağlandığında arka planda gönderilir. Uygulamayı kullanmak için internet gerekmez.",
                    color = AccentCyan, fontSize = 12.sp, lineHeight = 18.sp
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Surface1)
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🐙", fontSize = 16.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Veriler GitHub üzerinden herkese açık toplanır. Şeffaf ve denetlenebilir.",
                    color = TextSec, fontSize = 12.sp, lineHeight = 18.sp
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Evet, anonim olarak paylaş", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onDecline,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSec),
            border = androidx.compose.foundation.BorderStroke(1.dp, TextMuted)
        ) {
            Text("Hayır, sadece cihazımda kalsın", fontSize = 14.sp)
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Geri", color = TextMuted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Bu tercih Ayarlar'dan her zaman değiştirilebilir.",
            color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
    }
}

// ─── Yardımcı Bileşenler ──────────────────────────────────────────────────────

@Composable
fun StepIndicator(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .width(if (i == current) 32.dp else 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            i < current  -> AccentBlue.copy(0.5f)
                            i == current -> AccentBlue
                            else         -> TextMuted
                        }
                    )
            )
        }
    }
}

@Composable
fun FeatureRow(icon: String, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title,    color = TextPri, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSec, fontSize = 12.sp)
        }
    }
}

@Composable
fun FlowStep(number: String, color: Color, icon: String, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(34.dp).clip(CircleShape).background(color.copy(0.18f))
        ) {
            Text(number, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 15.sp)
                Spacer(Modifier.width(6.dp))
                Text(title, color = TextPri, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(3.dp))
            Text(desc, color = TextSec, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
fun FlowArrow() {
    Box(Modifier.fillMaxWidth().padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
        Text("↓", color = TextMuted, fontSize = 16.sp)
    }
}

@Composable
fun PermissionCard(icon: String, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = TextPri, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(desc,  color = TextSec, fontSize = 11.sp)
        }
    }
}

@Composable
fun ConsentRow(icon: String, text: String, positive: Boolean) {
    Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(icon, fontSize = 13.sp, color = if (positive) AccentBlue else Color(0xFFEF4444))
        Spacer(Modifier.width(8.dp))
        Text(text, color = if (positive) TextSec else TextMuted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}