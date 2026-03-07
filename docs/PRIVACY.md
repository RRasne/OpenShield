# Gizlilik Politikası — OpenShield

*Son güncelleme: Mart 2026*

## Özet

OpenShield'in temel felsefesi: **Verileriniz cihazınızda kalır.**

Tek istisna: **Kullanıcı onayıyla** anonim spam bildirimleri.

---

## Toplanan Veriler

### Her zaman — hiçbir şey toplanmaz

OpenShield:
- SMS içeriklerinizi okur, **asla kaydetmez**
- Telefon numaralarını analiz eder, **düz metin olarak saklamaz**
- İnternet bağlantısı **kullanmaz** (spam tespiti için)

### İsteğe bağlı — topluluk katkısı (onay gerekir)

İlk kurulumda "Evet, anonim olarak paylaş" seçeneğini seçerseniz:

| Gönderilen | Format | Amaç |
|---|---|---|
| Spam numaranın hash'i | SHA-256 (tek yönlü) | Topluluk listesi |
| Spam kategorisi | Metin (GAMBLING, PHISHING vb.) | Sınıflandırma |
| Uygulama versiyonu | Metin (1.0) | Uyumluluk |
| Dil kodu | 2 harf (tr, en) | Bölgesel analiz |

**Asla gönderilmez:**
- Gerçek telefon numarası
- SMS içeriği
- Konum bilgisi
- Kullanıcı kimliği veya cihaz tanımlayıcısı

Veriler **GitHub üzerinden herkese açık** olarak toplanır.
Kaynak kod ve toplanan veriler denetlenebilir: [github.com/RRasne/OpenShield](https://github.com/RRasne/OpenShield)

---

## İzinler

| İzin | Neden |
|---|---|
| `RECEIVE_SMS` | Gelen SMS'leri analiz etmek |
| `READ_SMS` | Filtre listesi yönetimi |
| `POST_NOTIFICATIONS` | Spam uyarıları |
| `INTERNET` | **Sadece** kullanıcı onayı varsa anonim bildirim göndermek için |
| `ACCESS_NETWORK_STATE` | İnternet bağlantısı kontrolü |

---

## Veri Silme

**Ayarlar → Tüm Verileri Sil** ile uygulama veritabanındaki tüm kayıtlar anında silinir.
Uygulamanın kaldırılması da tüm yerel verileri siler.

Topluluk bildirimi tercihini **Ayarlar → Gizlilik** menüsünden her zaman değiştirebilirsiniz.

---

## İletişim

[GitHub Issues](https://github.com/RRasne/OpenShield/issues)
