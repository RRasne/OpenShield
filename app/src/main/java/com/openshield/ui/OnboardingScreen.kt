package com.openshield.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.openshield.*

/**
 * Onboarding ekranı — ilk açılışta gösterilir.
 * 3 adım:
 *   1. Hoş geldin + nasıl çalışır
 *   2. SMS izni
 *   3. Topluluk veri paylaşımı (isteğe bağlı)
 */
@Composable
fun OnboardingScreen(
    onComplete: (communityConsent: Boolean) -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var communityConsent by remember { mutableStateOf(false) }
    var hasSmsPerm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        hasSmsPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasSmsPerm = results[Manifest.permission.RECEIVE_SMS] == true
        if (hasSmsPerm) step = 2
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .systemBarsPadding()
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
            },
            label = "onboarding"
        ) { currentStep ->
            when (currentStep) {
                0 -> StepWelcome(onNext = { step = 1 })
                1 -> StepPermission(
                    hasPermission = hasSmsPerm,
                    onRequest = {
                        val perms = buildList {
                            add(Manifest.permission.RECEIVE_SMS)
                            add(Manifest.permission.READ_SMS)
                            if (Build.VERSION.SDK_INT >= 33)
                                add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permLauncher.launch(perms.toTypedArray())
                    },
                    onNext = { step = 2 }
                )
                2 -> StepCommunity(
                    onAccept = { communityConsent = true;  onComplete(true) },
                    onDecline = { communityConsent = false; onComplete(false) }
                )
            }
        }

        // Adım göstergesi
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .size(if (i == step) 24.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(if (i == step) AccentBlue else TextMuted)
                )
            }
        }
    }
}

// ─── Adım 1: Hoş Geldin ───────────────────────────────────────────────────────

@Composable
fun StepWelcome(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        // Logo
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(AccentBlue, AccentCyan)))
        ) {
            Text("🛡", fontSize = 40.sp)
        }

        Spacer(Modifier.height(24.dp))
        Text("OpenShield", color = TextPri, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("SMS Spam Engelleyici", color = TextSec, fontSize = 15.sp)
        Spacer(Modifier.height(40.dp))

        // Nasıl çalışır
        InfoCard(
            title = "Nasıl Çalışır?",
            items = listOf(
                "📩" to "Gelen SMS'ler arka planda analiz edilir",
                "🧠" to "Kural motoru spam kalıplarını tanır",
                "🔴" to "Spam → sessizce kayıt altına alınır, bildirim çıkmaz",
                "🟡" to "Şüpheli → uygulamayı açınca sana sorulur",
                "🟢" to "Temiz → hiçbir şey yapılmaz, mesaj normal gelir",
            )
        )

        Spacer(Modifier.height(16.dp))

        InfoCard(
            title = "Ne Yapılmaz?",
            items = listOf(
                "❌" to "SMS içeriği hiçbir zaman kaydedilmez",
                "❌" to "Uygulama SMS'leri silemez veya gizleyemez",
                "❌" to "İnternet izni varsayılan olarak kullanılmaz",
                "❌" to "Reklam, analitik veya izleme kodu yoktur",
            )
        )

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
        ) {
            Text("Devam", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

// ─── Adım 2: SMS İzni ─────────────────────────────────────────────────────────

@Composable
fun StepPermission(
    hasPermission: Boolean,
    onRequest: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📋", fontSize = 56.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Text("SMS İzni", color = TextPri, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "OpenShield'in çalışması için SMS okuma ve alma izni gereklidir. " +
                    "Bu izin yalnızca spam analizi için kullanılır.",
            color = TextSec, fontSize = 14.sp, textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(32.dp))

        if (hasPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Green.copy(0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✅", fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Text("İzin verildi", color = Green, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Devam", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 6.dp))
            }
        } else {
            Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("İzin Ver", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 6.dp))
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text("Şimdi değil", color = TextMuted, fontSize = 14.sp)
            }
            Text(
                "İzin vermeden spam tespiti çalışmaz. Daha sonra Ayarlar'dan verebilirsiniz.",
                color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// ─── Adım 3: Topluluk Veri Paylaşımı ─────────────────────────────────────────

@Composable
fun StepCommunity(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text("👥", fontSize = 56.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Text("Topluluk Katkısı", color = TextPri, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Bu özellik tamamen isteğe bağlıdır. " +
                    "Kabul ederseniz spam tespitleriniz anonim olarak paylaşılır " +
                    "ve topluluk listesi cihazınıza indirilir.",
            color = TextSec, fontSize = 14.sp, textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(24.dp))

        // Gönderilecekler
        InfoCard(
            title = "✅ Gönderilecek (anonim)",
            accentColor = Green,
            items = listOf(
                "🔑" to "Numaranın SHA-256 hash'i — orijinal geri elde edilemez",
                "📋" to "Tetiklenen kural isimleri (örn. GAMBLING_BRAND)",
                "👍" to "Spam / spam değil oyunuz",
            )
        )

        Spacer(Modifier.height(12.dp))

        InfoCard(
            title = "❌ Asla Gönderilmeyecek",
            accentColor = Red,
            items = listOf(
                "📵" to "Gerçek telefon numarası",
                "✉️" to "SMS içeriği — hiçbir koşulda",
                "📱" to "Cihaz kimliği veya tanımlayıcı",
                "📡" to "Mobil veri kullanılmaz — sadece Wi-Fi",
            )
        )

        Spacer(Modifier.height(12.dp))

        // Nasıl çalışır
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Card1)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Oylama Sistemi", color = TextSec, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Bir numara için spam oyları toplamın %50'sini geçerse topluluk listesine girer. " +
                            "\"Spam değil\" oyları bu oranı düşürür. Yanlış kayıtlar proje yöneticisi tarafından düzeltilebilir.",
                    color = TextMuted, fontSize = 12.sp, lineHeight = 19.sp
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
        ) {
            Text("Katıl — Anonim Veri Paylaş", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 6.dp))
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onDecline,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSec),
            border = androidx.compose.foundation.BorderStroke(1.dp, TextMuted.copy(0.4f))
        ) {
            Text("Hayır, Sadece Cihazımda Çalışsın", fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 6.dp))
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Bu tercihi Ayarlar → Gizlilik menüsünden istediğiniz zaman değiştirebilirsiniz.",
            color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(64.dp))
    }
}

// ─── Yardımcı Bileşenler ──────────────────────────────────────────────────────

@Composable
fun InfoCard(
    title: String,
    items: List<Pair<String, String>>,
    accentColor: Color = AccentBlue
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Card1)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            items.forEach { (icon, text) ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(icon, fontSize = 14.sp, modifier = Modifier.width(28.dp))
                    Text(text, color = TextSec, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}