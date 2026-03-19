# Gizlilik Politikası — OpenShield

*Son güncelleme: Mart 2026*

---

## Özet

OpenShield'in temel felsefesi: **Verileriniz size aittir.**

Spam tespiti tamamen cihazınızda gerçekleşir. SMS içeriği hiçbir zaman kaydedilmez veya iletilmez. İnternet bağlantısı yalnızca siz açıkça onay verirseniz ve yalnızca anonim topluluk verileri için kullanılır.

---

## Her Zaman Geçerli Olan Kurallar

Onay verseniz de vermesen de OpenShield şunları **asla yapmaz:**

- SMS içeriğini kaydetmez, loglamaz veya iletmez
- SMS içeriğini hiçbir sunucuya göndermez
- Gerçek telefon numarasını topluluk sistemine göndermez
- Cihaz kimliği, reklam tanımlayıcısı veya konum bilgisi kullanmaz
- Herhangi bir analitik, crash reporting veya üçüncü taraf SDK çalıştırmaz
- Mobil veri bağlantısı kullanmaz

---

## Cihazda Saklanan Veriler

| Veri | Format | Neden |
|---|---|---|
| Kullanıcı kara listesi numaraları | Düz metin | Kullanıcı girişi, tekrar görmesi gerekir |
| Kullanıcı beyaz listesi numaraları | Düz metin | Kullanıcı girişi, tekrar görmesi gerekir |
| Topluluk listesinden gelen hash'ler | SHA-256 hash | Numara eşleştirme — orijinal bilinemez |
| Engelleme geçmişi | Gönderici, skor, sebep, zaman | Geçmiş ekranı |
| Şüpheli mesajlar | Gönderici, skor, tetiklenen kurallar | Kullanıcı kararı bekliyor |
| Wi-Fi'de gönderilemeyen raporlar | Hash + oy türü | Geçici kuyruk — gönderince silinir |
| Topluluk onayı | true/false | SharedPreferences |
| Son sync zamanı | Unix timestamp | 6 saatlik aralığı hesaplamak için |

**Önemli:** Şüpheli mesaj kaydında SMS içeriği yer almaz. Yalnızca gönderici numarası, skor ve hangi kuralların tetiklendiği tutulur.

---

## Şüpheli Mesaj Akışı

Bir SMS "şüpheli" (skor 0.40–0.60) sınıflandırıldığında:

1. Bildirim gösterilmez, ses/titreşim olmaz
2. Gönderici numarası, skor ve tetiklenen kurallar `pending_review` tablosuna yazılır
3. Siz uygulamayı açtığınızda "Bu numara spam mı?" diye sorulur
4. **"Spam"** derseniz → kara listeye eklenir, geçmişe yazılır, onay varsa topluluk'a spam oyu gönderilir
5. **"Değil"** derseniz → kayıt silinir, onay varsa topluluk'a "spam değil" oyu gönderilir

---

## İsteğe Bağlı: Topluluk Veri Paylaşımı

Bu özellik varsayılan olarak **kapalıdır.** İlk kurulumda veya Ayarlar ekranından açabilirsiniz.

**Kapalıysa:** Hiçbir ağ isteği yapılmaz. Uygulama tamamen çevrimdışı çalışır.

**Açıksa:** Aşağıdaki veriler anonim olarak iletilir.

### Ne Gönderilir?

| Gönderilen | Format | Amaç |
|---|---|---|
| Numaranın SHA-256 hash'i | 64 karakter hex | Topluluk listesi eşleştirme |
| Tetiklenen kural isimleri | Metin (örn. `GAMBLING_BRAND`, `IBAN`) | Kural kalitesini ölçme |
| Oy türü | `spam` veya `not_spam` | Oylama sistemi |

### Ne Gönderilmez?

- Gerçek telefon numarası
- SMS içeriği — hiçbir koşulda
- Cihaz kimliği veya reklam tanımlayıcısı
- Konum bilgisi
- IP adresi (Cloudflare altyapısı tarafından görülse de OpenShield tarafından loglanmaz/gönderilmez)

SHA-256 hash tek yönlüdür — orijinal numara **matematiksel olarak geri elde edilemez.**

### Gönderim Zamanlaması

- Yalnızca **Wi-Fi bağlantısında** — mobil veri kullanılmaz
- Wi-Fi yoksa raporlar cihazda kuyrukta bekler, bağlanınca otomatik gönderilir
- Her 6 saatte bir topluluk listesi güncellenir
- Başarısız gönderimler exponential backoff ile tekrar denenir, 5 başarısız denemeden sonra düşürülür

### Oylama Sistemi

Her numara için `spam_votes` ve `not_spam_votes` ayrı tutulur. Karar kuralı:

- `spam_votes / toplam_oy > %50` → numara topluluk listesine girer
- `not_spam_votes / toplam_oy > %50` → girmez veya listeden çıkar
- Minimum oy şartı yoktur — 1 oy etkilidir
- Yanlış kayıtlar proje yöneticisi tarafından admin panelinden silinebilir/düzeltilebilir

---

## İzinler

| İzin | Neden |
|---|---|
| `RECEIVE_SMS` | Gelen SMS'leri spam analizi için almak |
| `READ_SMS` | SMS geçmişini yönetmek |
| `POST_NOTIFICATIONS` | Sessiz spam bildirimi göstermek |
| `INTERNET` | Yalnızca topluluk onayı varsa anonim veri iletimi |
| `ACCESS_NETWORK_STATE` | Wi-Fi bağlantısını kontrol etmek |

Temel spam koruması `INTERNET` izni hiç kullanılmadan çalışır.

---

## Veri Silme

**Ayarlar → Tüm Verileri Sil** ile yerel veritabanındaki tüm kayıtlar anında silinir. Uygulamanın kaldırılması da tüm yerel verileri temizler.

Topluluk paylaşımını **Ayarlar → Gizlilik** toggle'ından her zaman kapatabilirsiniz. Kapatıldıktan sonra hiçbir ağ isteği yapılmaz.

---

## Açık Kaynak ve Denetlenebilirlik

Kaynak kodun tamamı [github.com/RRasne/OpenShield](https://github.com/RRasne/OpenShield) adresinde herkese açıktır. Cloudflare Worker kodu da aynı repoda [`cloudflare/worker.js`](cloudflare/worker.js) olarak bulunmaktadır. Bu belgede yazılanları kaynak koddan bağımsız olarak doğrulayabilirsiniz.

---

## İletişim

Sorularınız veya gizlilik endişeleriniz için: [GitHub Issues](https://github.com/RRasne/OpenShield/issues)
