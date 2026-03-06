# OpenShield — Topluluk Spam Listesi Mimarisi

## Karar: Bundled (Uygulama Güncellemesiyle)

Kullanıcılar spam bildirirse → sadece **kendi cihazlarında** kaydedilir.  
Bu veriler **Anthropic/GitHub üzerinden** derlenerek bir sonraki APK güncellemesine eklenir.  
İnternet bağlantısı **asla gerekli değil**.

---

## Veri Akışı

```
Kullanıcı "Spam Bildir" der
        │
        ▼
[Yerel DB'ye kaydedilir]
  community_spam_numbers tablosu
  numberHash: SHA-256(numara)
  reportCount: 1
  category: PHISHING / PROMOTION / vb.
        │
        │  (Uygulama güncellemesi sırasında)
        ▼
[Geliştirici assets günceller]
  app/src/main/assets/community_numbers.json
        │
        ▼
[APK içine paketlenir, Play Store'dan dağıtılır]
        │
        ▼
[Uygulama ilk açılışta assets'i DB'ye yükler]
  CommunityListLoader.kt → Room DB
```

---

## Dosya Yapısı

### `assets/community_numbers.json`
```json
{
  "version": 3,
  "updated_at": "2026-03-06",
  "numbers": [
    {
      "hash": "a3f8c2...",
      "report_count": 47,
      "category": "GAMBLING",
      "added_version": 2
    },
    {
      "hash": "b91d44...",
      "report_count": 12,
      "category": "PHISHING",
      "added_version": 3
    }
  ]
}
```
> Numaralar SHA-256 hash olarak saklanır. Orijinal numara asla dosyaya yazılmaz.

---

## Skor Etkisi

`SpamDetectionEngine.analyzeNumber()` içinde:

```kotlin
numberRepository.getCommunityReportCount(sender) > 50  → 0.95f
numberRepository.getCommunityReportCount(sender) > 10  → 0.85f
numberRepository.getCommunityReportCount(sender) > 3   → 0.55f
```

Topluluk listesindeki bir numara → numara skoru otomatik yükselir.  
Kullanıcının beyaz listesinde varsa → her şey geçersiz, 0.0f döner.

---

## Gizlilik Garantisi

- Numara hash'leri tek yönlü → tersine çevrilemez
- Kullanıcı cihazından hiçbir veri sunucuya gitmiyor
- `INTERNET` izni yok → ağ erişimi mümkün değil
- Topluluk verisi sadece geliştirici tarafından elle derlenir

---

## Uygulama Açılışında Yükleme (CommunityListLoader)

```kotlin
@Singleton
class CommunityListLoader @Inject constructor(
    private val context: Context,
    private val db: SpamDatabase
) {
    suspend fun loadIfNeeded() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("openshield", Context.MODE_PRIVATE)
        val loadedVersion = prefs.getInt("community_list_version", 0)

        val json = context.assets.open("community_numbers.json")
            .bufferedReader().readText()
        val data = JSONObject(json)
        val fileVersion = data.getInt("version")

        if (fileVersion <= loadedVersion) return@withContext  // Zaten yüklü

        val numbers = data.getJSONArray("numbers")
        for (i in 0 until numbers.length()) {
            val item = numbers.getJSONObject(i)
            db.communityNumberDao().insertOrUpdate(
                CommunityNumberEntity(
                    numberHash = item.getString("hash"),
                    reportCount = item.getInt("report_count"),
                    category = item.getString("category")
                )
            )
        }

        prefs.edit().putInt("community_list_version", fileVersion).apply()
    }
}
```

---

## Kullanıcı Arayüzü: Spam Bildir

Geçmiş sekmesindeki her engellenen SMS için:
- **"Kara Listeye Ekle"** → kullanıcının kişisel kara listesine ekler (mevcut)
- **"Spam Bildir"** → yerel `community_spam_numbers` tablosuna ekler (yeni)

Beyaz liste ve kara liste tamamen kullanıcıya ait:
- **Kara liste**: Kullanıcının kendi engellediği numaralar → her zaman SPAM
- **Beyaz liste**: Kullanıcının güvendiği numaralar → her zaman CLEAN, hiçbir kural işlemez
- **Topluluk listesi**: Diğer kullanıcıların bildirdiği numaralar → skoru yükseltir ama beyaz liste önceliklidir

---

## Öncelik Sırası

```
1. Kullanıcı Beyaz Listesi  → CLEAN (0.0) — kesinlikle atla
2. Kullanıcı Kara Listesi   → SPAM  (1.0) — kesinlikle engelle
3. Topluluk Listesi         → numberScore yüksek (0.55–0.95)
4. RuleEngine               → içerik analizi
5. TFLiteClassifier         → ML skoru (ileride)
```
