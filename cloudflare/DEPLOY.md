# OpenShield Cloudflare Worker — Deploy Talimatları

## 1. Dosyaları Güncelle

worker.js ve wrangler.toml dosyalarını şuraya kopyala:
  C:\Users\Ensar\Desktop\OpenShield\cloudflare\

## 2. Node.js Kurulu Değilse
  https://nodejs.org adresinden LTS sürümü indir ve kur.

## 3. Wrangler Kur (ilk seferinde)
  PowerShell'de:
  npm install -g wrangler

## 4. Cloudflare'e Giriş Yap (ilk seferinde)
  wrangler login
  → Tarayıcı açılır, Cloudflare hesabınla giriş yap, izin ver.

## 5. Admin Şifreni Belirle
  wrangler secret put ADMIN_KEY
  → Enter a secret value: yazıya istediğin şifreyi yaz (örn: gizli123)
  → Bu şifre KV'de saklanmaz, Cloudflare'de şifreli tutulur.

## 6. Deploy Et
  cd C:\Users\Ensar\Desktop\OpenShield\cloudflare
  wrangler deploy

  Başarılıysa şunu görürsün:
  ✅ Deployed to: https://openshield-api.<senin-hesabın>.workers.dev

## 7. Admin Paneline Gir
  https://openshield-api.<senin-hesabın>.workers.dev/admin
  → Şifreni gir → topluluk verilerini gör / sil

## 8. Android Uygulamasındaki URL'yi Güncelle
  Worker URL'yi kopyala ve uygulamadaki API base URL'yi güncelle.
  (Bir sonraki adımda halledelim)

## Notlar
- KV namespace ID mevcut olanı kullanıyor (1974878e1d7943b7a6372a57f73c097d)
- Binding adı REPORTS → OPENSHIELD_KV olarak düzeltildi (worker.js ile uyumlu)
- THRESHOLD = 3 (production'da 5'e çekmek için worker.js'de değiştir)
