# Gizlilik Politikası — OpenShield

*Son güncelleme: Mart 2026*

---

## Özet

OpenShield'in temel felsefesi: **Verileriniz size aittir.**

Spam tespiti tamamen cihazınızda gerçekleşir. İnternet bağlantısı, yalnızca siz açıkça onay verirseniz ve yalnızca anonim topluluk verileri için kullanılır.

---

## Cihaz Üzerinde Her Zaman Geçerli Olanlar

OpenShield'in temel spam koruması internet bağlantısı **gerektirmez** ve şunları **asla yapmaz:**

- SMS içeriğini kaydetmez, loglamaz veya iletmez
- Gerçek telefon numarasını veritabanına düz metin olarak yazmaz
- Herhangi bir analitik, crash reporting veya üçüncü taraf SDK kullanmaz

---

## Cihaz Üzerinde Saklanan Veriler

| Veri | Format | Nerede |
|---|---|---|
| Kullanıcının eklediği spam numaralar | Düz metin (kullanıcı girişi) | Room DB |
| Kullanıcının eklediği beyaz liste numaraları | Düz metin (kullanıcı girişi) | Room DB |
| Topluluk raporları (gelen + bildirilen) | **SHA-256 hash** — geri dönüştürülemez | Room DB |
| Engelleme geçmişi | Gönderici hash'i · skor · neden · zaman | Room DB |
| Şüpheli mesajlar (karar bekleniyor) | Gönderici · skor · neden · zaman (SMS içeriği **yok**) | Room DB |
| Topluluk sync zamanı | Unix timestamp | SharedPreferences |
| Topluluk onayı | true / false | SharedPreferences |

**Not:** Kullanıcının kendi eklediği kara/beyaz liste numaraları düz metin saklanır çünkü kullanıcı bunları kendisi girir ve tekrar görmesi gerekir. Topluluk raporları ve engelleme geçmişindeki numaralar ise daima hash'lenir.

### Şüpheli Mesaj Akışı

Bir SMS "şüpheli" (skor 0.40–0.60) olarak sınıflandırıldığında:

- Bildirim **gösterilmez**
- Gönderici, skor ve tetiklenen kurallar `pending_review` tablosuna kaydedilir (**SMS içeriği kaydedilmez**)
- Siz uygulamayı bir sonraki açışınızda size "Bu numara spam mı?" diye sorulur
- "Spam" derseniz → engelleme geçmişine eklenir, topluluk raporuna sayılır
- "Değil" derseniz → kayıt silinir, hiçbir şey iletilmez

---

## İsteğe Bağlı: Topluluk Katkısı

Onboarding sırasında veya **Ayarlar → Gizlilik** menüsünden "Topluluk Veri Paylaşımı"nı açarsanız aşağıdaki veriler anonim olarak iletilir.

**Bu özelliği kapatırsanız hiçbir ağ isteği yapılmaz.**

### Ne Gönderilir?

| Gönderilen | Format | Amaç |
|---|---|---|
| Numaranın SHA-256 hash'i | 64 karakter hex | Topluluk listesi |
| Tetiklenen kural isimleri | Metin (örn. `GAMBLING_BRAND`) | Sınıflandırma |
| Spam skoru | Ondalık sayı (örn. `0.87`) | Güvenilirlik |
| Spam kategorisi | Metin (örn. `GAMBLING`) | Bölgesel analiz |

### Ne Asla Gönderilmez?

- Gerçek telefon numarası
- SMS içeriği
- Cihaz kimliği veya reklam tanımlayıcısı
- Konum bilgisi
- IP adresi (Cloudflare tarafından alınsa da OpenShield tarafından gönderilmez/loglanmaz)

SHA-256 hash tek yönlüdür — orijinal numara **matematiksel olarak geri elde edilemez.**

### Ne Zaman Gönderilir?

Topluluk verisi yalnızca **Wi-Fi bağlantısında** iletilir. Mobil veri kullanılmaz.

Uygulama Wi-Fi'ye bağlandığında şu kontroller yapılır:
1. Kullanıcı onayı var mı? → hayırsa dur
2. Son göndermeden bu yana en az 6 saat geçti mi? → hayırsa dur
3. Her ikisi de evet → yalnızca o oturumdaki yeni raporlar gönderilir

### Eşik Sistemi

Bir numaranın topluluk listesine girmesi için **en az 5 farklı kullanıcıdan** bildirim alması gerekir. Tek kişinin bildirimi yalnızca o kişinin cihazında kaydedilir, listeye girmez.

---

## İzinler

| İzin | Neden |
|---|---|
| `RECEIVE_SMS` | Gelen SMS'leri spam analizi için almak |
| `READ_SMS` | SMS geçmişini göstermek (isteğe bağlı) |
| `POST_NOTIFICATIONS` | Engellenen spam için sessiz bildirim |
| `INTERNET` | Yalnızca topluluk onayı varsa anonim veri iletimi için |
| `ACCESS_NETWORK_STATE` | Wi-Fi bağlantısını kontrol etmek için |

Temel spam koruması `INTERNET` izni kullanılmadan çalışır.

---

## Veri Silme

**Ayarlar → Tüm Verileri Sil** ile yerel veritabanındaki tüm kayıtlar anında silinir. Uygulamanın kaldırılması da tüm yerel verileri siler.

Topluluk paylaşımını **Ayarlar → Gizlilik → Topluluk Veri Paylaşımı** toggle'ından her zaman kapatabilirsiniz. Kapatıldıktan sonra hiçbir ağ isteği yapılmaz.

---

## Açık Kaynak ve Denetlenebilirlik

Kaynak kodun tamamı [github.com/RRasne/OpenShield](https://github.com/RRasne/OpenShield) adresinde herkese açıktır. Cloudflare Worker kodu da aynı repoda [`cloudflare/worker.js`](cloudflare/worker.js) olarak bulunmaktadır. Bu belgede yazılanları kaynak koddan kendiniz doğrulayabilirsiniz.

---

## İletişim

Sorularınız veya gizlilik ile ilgili endişeleriniz için: [GitHub Issues](https://github.com/RRasne/OpenShield/issues)
