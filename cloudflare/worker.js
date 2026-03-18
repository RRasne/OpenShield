/**
 * OpenShield Cloudflare Worker
 *
 * Public:
 *   POST /report                    → spam bildirimi al
 *   GET  /community-list?since=<ts> → delta güncelleme listesi
 *
 * Admin (/admin?key=SIFREN):
 *   GET  /admin                     → web paneli
 *   GET  /admin/stats               → ham JSON
 *   DELETE /admin/delete?hash=<h>   → kayıt sil
 *   POST /admin/add                 → manuel numara ekle
 */

const THRESHOLD = 1;  // test aşaması — yayında 5'e çıkar

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    const corsHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, X-Admin-Key",
    };

    if (request.method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders });
    }

    // ─── POST /report ────────────────────────────────────────────────────────
    if (request.method === "POST" && url.pathname === "/report") {
      try {
        const body = await request.json();

        if (!body.numberHash || typeof body.numberHash !== "string" ||
            body.numberHash.length !== 64) {
          return json({ error: "invalid numberHash" }, 400, corsHeaders);
        }

        const key = `report:${body.numberHash}`;
        const existing = await env.OPENSHIELD_KV.get(key, "json");

        const mergedTokens = existing?.tokens || {};
        const mergedRules  = existing?.rules  || {};

        if (Array.isArray(body.tokens)) {
          for (const token of body.tokens.slice(0, 20)) {
            if (typeof token === "string" && token.length <= 50)
              mergedTokens[token] = (mergedTokens[token] || 0) + 1;
          }
        }
        if (Array.isArray(body.triggeredRules)) {
          for (const rule of body.triggeredRules.slice(0, 15)) {
            if (typeof rule === "string" && rule.length <= 50)
              mergedRules[rule] = (mergedRules[rule] || 0) + 1;
          }
        }

        const updated = {
          count:    (existing?.count || 0) + 1,
          lastSeen: Date.now(),
          tokens:   mergedTokens,
          rules:    mergedRules,
          manual:   existing?.manual || false,
        };

        await env.OPENSHIELD_KV.put(key, JSON.stringify(updated), {
          expirationTtl: 60 * 60 * 24 * 90
        });

        return json({ ok: true, count: updated.count }, 200, corsHeaders);
      } catch {
        return json({ error: "bad request" }, 400, corsHeaders);
      }
    }

    // ─── GET /community-list ─────────────────────────────────────────────────
    if (request.method === "GET" && url.pathname === "/community-list") {
      const since  = parseInt(url.searchParams.get("since") || "0");
      const listed = await env.OPENSHIELD_KV.list({ prefix: "report:" });
      const result = [];

      for (const key of listed.keys) {
        const data = await env.OPENSHIELD_KV.get(key.name, "json");
        if (!data) continue;
        if (data.count >= THRESHOLD && data.lastSeen > since) {
          result.push({
            hash:      key.name.replace("report:", ""),
            count:     data.count,
            manual:    data.manual || false,
            topTokens: getTop(data.tokens, 5),
            topRules:  getTop(data.rules, 5),
          });
        }
      }

      return json(result, 200, corsHeaders);
    }

    // ─── GET /admin ──────────────────────────────────────────────────────────
    if (request.method === "GET" && url.pathname === "/admin") {
      const key = url.searchParams.get("key") || "";
      if (key !== env.ADMIN_KEY) {
        return new Response(loginPage(), {
          headers: { "Content-Type": "text/html;charset=UTF-8" }
        });
      }
      return new Response(adminPage(key), {
        headers: { "Content-Type": "text/html;charset=UTF-8" }
      });
    }

    // ─── GET /admin/stats ────────────────────────────────────────────────────
    if (request.method === "GET" && url.pathname === "/admin/stats") {
      if (!checkAdminKey(request, url, env)) return new Response("Unauthorized", { status: 401 });
      const entries = await getAllEntries(env);
      return json({
        total:          entries.length,
        aboveThreshold: entries.filter(e => e.aboveThreshold).length,
        threshold:      THRESHOLD,
        entries,
      }, 200, corsHeaders);
    }

    // ─── DELETE /admin/delete ────────────────────────────────────────────────
    if (request.method === "DELETE" && url.pathname === "/admin/delete") {
      if (!checkAdminKey(request, url, env)) return new Response("Unauthorized", { status: 401 });
      const hash = url.searchParams.get("hash");
      if (!hash) return json({ error: "hash required" }, 400, corsHeaders);
      await env.OPENSHIELD_KV.delete(`report:${hash}`);
      return json({ ok: true, deleted: hash }, 200, corsHeaders);
    }

    // ─── POST /admin/add (manuel numara ekle) ────────────────────────────────
    if (request.method === "POST" && url.pathname === "/admin/add") {
      if (!checkAdminKey(request, url, env)) return new Response("Unauthorized", { status: 401 });
      try {
        const body = await request.json();
        // number: düz numara → worker hash'ler
        // hash: zaten hash'lenmiş → direkt kullan
        let hash = body.hash;
        if (!hash && body.number) {
          hash = await sha256(body.number.replace(/\D/g, ""));
        }
        if (!hash || hash.length !== 64) {
          return json({ error: "number veya hash gerekli" }, 400, corsHeaders);
        }

        const key  = `report:${hash}`;
        const note = body.note || "Manuel eklendi";

        await env.OPENSHIELD_KV.put(key, JSON.stringify({
          count:    THRESHOLD,  // eşiği geçmiş sayılır
          lastSeen: Date.now(),
          tokens:   {},
          rules:    { [note]: 1 },
          manual:   true,
        }), { expirationTtl: 60 * 60 * 24 * 365 });  // 1 yıl

        return json({ ok: true, hash }, 200, corsHeaders);
      } catch {
        return json({ error: "bad request" }, 400, corsHeaders);
      }
    }

    return new Response("Not Found", { status: 404 });
  }
};

// ─── Yardımcılar ──────────────────────────────────────────────────────────────

function json(data, status, headers = {}) {
  return new Response(JSON.stringify(data, null, 2), {
    status,
    headers: { ...headers, "Content-Type": "application/json" }
  });
}

function checkAdminKey(request, url, env) {
  const fromHeader = request.headers.get("X-Admin-Key");
  const fromQuery  = url.searchParams.get("key");
  return (fromHeader || fromQuery) === env.ADMIN_KEY;
}

async function sha256(text) {
  const buf    = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(buf)).map(b => b.toString(16).padStart(2, "0")).join("");
}

async function getAllEntries(env) {
  const listed  = await env.OPENSHIELD_KV.list({ prefix: "report:" });
  const entries = [];
  for (const key of listed.keys) {
    const data = await env.OPENSHIELD_KV.get(key.name, "json");
    if (!data) continue;
    entries.push({
      hash:           key.name.replace("report:", ""),
      count:          data.count,
      lastSeen:       new Date(data.lastSeen).toISOString(),
      topTokens:      getTop(data.tokens, 10),
      topRules:       getTop(data.rules, 10),
      manual:         data.manual || false,
      aboveThreshold: data.count >= THRESHOLD,
    });
  }
  return entries.sort((a, b) => b.count - a.count);
}

function getTop(obj, n) {
  if (!obj) return [];
  return Object.entries(obj)
    .sort(([, a], [, b]) => b - a)
    .slice(0, n)
    .map(([key, count]) => ({ key, count }));
}

// ─── Admin HTML ───────────────────────────────────────────────────────────────

function loginPage() {
  return `<!DOCTYPE html><html lang="tr">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>OpenShield Admin</title>
<style>
  *{box-sizing:border-box;margin:0;padding:0}
  body{background:#080D18;color:#F8FAFC;font-family:system-ui,sans-serif;
       display:flex;align-items:center;justify-content:center;min-height:100vh}
  .card{background:#0F1623;border:1px solid #1C2840;border-radius:16px;padding:40px;width:360px}
  h2{color:#3B82F6;margin-bottom:8px}p{color:#94A3B8;font-size:14px;margin-bottom:24px}
  input{width:100%;background:#1C2840;border:1px solid #334155;border-radius:10px;
        padding:12px 16px;color:#F8FAFC;font-size:15px;outline:none;margin-bottom:16px}
  input:focus{border-color:#3B82F6}
  button{width:100%;background:#3B82F6;color:#fff;border:none;border-radius:10px;
         padding:13px;font-size:15px;font-weight:600;cursor:pointer}
  button:hover{background:#2563EB}
</style></head>
<body>
<div class="card">
  <h2>🛡 OpenShield</h2>
  <p>Admin paneline erişmek için anahtar girin.</p>
  <input type="password" id="k" placeholder="Admin anahtarı"
         onkeydown="if(event.key==='Enter')login()">
  <button onclick="login()">Giriş</button>
</div>
<script>
function login(){
  const k=document.getElementById('k').value.trim();
  if(k) window.location.href='/admin?key='+encodeURIComponent(k);
}
</script>
</body></html>`;
}

function adminPage(adminKey) {
  return `<!DOCTYPE html><html lang="tr">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>OpenShield Admin</title>
<style>
  *{box-sizing:border-box;margin:0;padding:0}
  body{background:#080D18;color:#F8FAFC;font-family:system-ui,sans-serif;padding:24px}
  h1{color:#3B82F6;font-size:22px;margin-bottom:4px}
  .sub{color:#94A3B8;font-size:13px;margin-bottom:24px}
  .stats{display:flex;gap:12px;margin-bottom:24px;flex-wrap:wrap}
  .stat{background:#0F1623;border:1px solid #1C2840;border-radius:12px;padding:16px 24px;min-width:120px}
  .stat-val{font-size:28px;font-weight:700;color:#3B82F6}
  .stat-lbl{font-size:12px;color:#94A3B8;margin-top:4px}
  .toolbar{display:flex;gap:10px;margin-bottom:16px;flex-wrap:wrap;align-items:center}
  .btn{border:none;border-radius:8px;padding:8px 18px;font-size:13px;cursor:pointer;font-weight:600}
  .btn-blue{background:#3B82F6;color:#fff}.btn-blue:hover{background:#2563EB}
  .btn-green{background:#22C55E;color:#000}.btn-green:hover{background:#16A34A}
  .add-box{background:#0F1623;border:1px solid #1C2840;border-radius:12px;
           padding:16px;margin-bottom:20px;display:none}
  .add-box.open{display:block}
  .add-box h3{color:#22C55E;font-size:14px;margin-bottom:12px}
  .row{display:flex;gap:10px;flex-wrap:wrap}
  input[type=text]{background:#1C2840;border:1px solid #334155;border-radius:8px;
                   padding:8px 12px;color:#F8FAFC;font-size:13px;outline:none;flex:1;min-width:180px}
  input[type=text]:focus{border-color:#22C55E}
  table{width:100%;border-collapse:collapse;background:#0F1623;border-radius:12px;overflow:hidden}
  th{background:#1C2840;color:#94A3B8;font-size:12px;text-align:left;padding:10px 14px;font-weight:600}
  td{padding:10px 14px;border-top:1px solid #1C2840;font-size:13px;vertical-align:top}
  .badge{display:inline-block;padding:2px 8px;border-radius:6px;font-size:11px;font-weight:600}
  .spam{background:#EF444420;color:#EF4444}
  .ok{background:#22C55E20;color:#22C55E}
  .manual{background:#F59E0B20;color:#F59E0B}
  .hash{font-family:monospace;font-size:11px;color:#475569;max-width:160px;
        overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
  .rules{font-size:11px;color:#94A3B8;max-width:200px}
  .del{background:#EF444415;color:#EF4444;border:1px solid #EF444430;
       border-radius:6px;padding:4px 10px;font-size:11px;cursor:pointer}
  .del:hover{background:#EF444430}
  .empty{text-align:center;padding:48px;color:#475569}
  #status{font-size:13px;margin-left:8px}
  .ok-msg{color:#22C55E}.err-msg{color:#EF4444}
</style></head>
<body>
<h1>🛡 OpenShield — Topluluk Verileri</h1>
<div class="sub">Cloudflare KV spam raporları</div>

<div class="stats">
  <div class="stat"><div class="stat-val" id="s-total">—</div><div class="stat-lbl">Toplam Hash</div></div>
  <div class="stat"><div class="stat-val" id="s-above">—</div><div class="stat-lbl">Listede</div></div>
  <div class="stat"><div class="stat-val" id="s-manual" style="color:#F59E0B">—</div><div class="stat-lbl">Manuel</div></div>
  <div class="stat"><div class="stat-val" id="s-threshold" style="color:#F59E0B">—</div><div class="stat-lbl">Eşik</div></div>
</div>

<div class="toolbar">
  <button class="btn btn-blue" onclick="load()">🔄 Yenile</button>
  <button class="btn btn-green" onclick="toggleAdd()">➕ Manuel Numara Ekle</button>
  <span id="status"></span>
</div>

<!-- Manuel ekleme formu -->
<div class="add-box" id="addBox">
  <h3>➕ Manuel Numara Ekle</h3>
  <div class="row">
    <input type="text" id="addNum" placeholder="Telefon numarası (örn: 905551234567)">
    <input type="text" id="addNote" placeholder="Not (örn: Bahis spam)">
    <button class="btn btn-green" onclick="addNumber()">Ekle</button>
  </div>
</div>

<table id="tbl">
  <thead><tr>
    <th>Hash</th><th>Rapor</th><th>Son Görülme</th><th>Top Kurallar</th><th>Durum</th><th></th>
  </tr></thead>
  <tbody id="tbody"><tr><td colspan="6" class="empty">Yükleniyor...</td></tr></tbody>
</table>

<script>
const KEY = ${JSON.stringify(adminKey)};

function setStatus(msg, ok) {
  const el = document.getElementById('status');
  el.textContent = msg;
  el.className = ok ? 'ok-msg' : 'err-msg';
  setTimeout(() => el.textContent = '', 3000);
}

function toggleAdd() {
  const box = document.getElementById('addBox');
  box.classList.toggle('open');
}

async function addNumber() {
  const num  = document.getElementById('addNum').value.trim();
  const note = document.getElementById('addNote').value.trim() || 'Manuel eklendi';
  if (!num) { setStatus('Numara girin', false); return; }

  const res = await fetch('/admin/add', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Admin-Key': KEY },
    body: JSON.stringify({ number: num, note })
  });
  const d = await res.json();
  if (res.ok) {
    setStatus('✓ Eklendi: ' + d.hash.substring(0, 12) + '...', true);
    document.getElementById('addNum').value  = '';
    document.getElementById('addNote').value = '';
    load();
  } else {
    setStatus('Hata: ' + (d.error || res.status), false);
  }
}

async function load() {
  const res = await fetch('/admin/stats', { headers: { 'X-Admin-Key': KEY } });
  if (!res.ok) { setStatus('Yetki hatası: ' + res.status, false); return; }
  const d = await res.json();

  document.getElementById('s-total').textContent     = d.total;
  document.getElementById('s-above').textContent     = d.aboveThreshold;
  document.getElementById('s-manual').textContent    = d.entries.filter(e => e.manual).length;
  document.getElementById('s-threshold').textContent = d.threshold;

  const tbody = document.getElementById('tbody');
  if (!d.entries.length) {
    tbody.innerHTML = '<tr><td colspan="6" class="empty">Henüz veri yok.</td></tr>';
    return;
  }

  tbody.innerHTML = d.entries.map(e => {
    const rules = e.topRules.map(r => r.key + ' ×' + r.count).join(', ') || '—';
    const date  = new Date(e.lastSeen).toLocaleString('tr-TR');
    const badge = e.manual
      ? '<span class="badge manual">Manuel</span>'
      : e.aboveThreshold
        ? '<span class="badge spam">Listede</span>'
        : '<span class="badge ok">Bekliyor</span>';
    return \`<tr>
      <td><span class="hash" title="\${e.hash}">\${e.hash}</span></td>
      <td style="color:#3B82F6;font-weight:700">\${e.count}</td>
      <td style="color:#94A3B8">\${date}</td>
      <td><span class="rules">\${rules}</span></td>
      <td>\${badge}</td>
      <td><button class="del" onclick="del('\${e.hash}')">Sil</button></td>
    </tr>\`;
  }).join('');
}

async function del(hash) {
  if (!confirm('Silmek istediğine emin misin?')) return;
  const res = await fetch('/admin/delete?hash=' + hash,
    { method: 'DELETE', headers: { 'X-Admin-Key': KEY } });
  if (res.ok) { setStatus('✓ Silindi', true); load(); }
}

load();
</script>
</body></html>`;
}
