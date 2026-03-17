/**
 * OpenShield Cloudflare Worker
 *
 * Endpoints:
 *   POST /report       → spam bildirimi al (numara hash + token'lar)
 *   GET  /community-list?since=<ts>  → delta güncelleme listesi döndür
 *
 * KV yapısı:
 *   report:<hash>  → { count, lastSeen, tokens: {token: frekans, ...}, rules: {rule: frekans} }
 *
 * Eşik: 3 farklı rapor → listeye girer (production'da 5'e çek)
 */

const THRESHOLD = 3;  // kaç rapor sonrası community listesine girer

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // CORS
    const corsHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
    };

    if (request.method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders });
    }

    // ─── POST /report ────────────────────────────────────────────────────────
    if (request.method === "POST" && url.pathname === "/report") {
      try {
        const body = await request.json();

        // Zorunlu alan kontrolü
        if (!body.numberHash || typeof body.numberHash !== "string" ||
            body.numberHash.length !== 64) {
          return new Response(JSON.stringify({ error: "invalid numberHash" }), {
            status: 400,
            headers: { ...corsHeaders, "Content-Type": "application/json" }
          });
        }

        const key = `report:${body.numberHash}`;
        const existing = await env.OPENSHIELD_KV.get(key, "json");

        // Token ve rule frekanslarını birleştir
        const mergedTokens = existing?.tokens || {};
        const mergedRules = existing?.rules || {};

        // Gelen token'ları frekansa ekle
        if (Array.isArray(body.tokens)) {
          for (const token of body.tokens.slice(0, 20)) {  // max 20 token
            if (typeof token === "string" && token.length <= 50) {
              mergedTokens[token] = (mergedTokens[token] || 0) + 1;
            }
          }
        }

        // Gelen triggered rule'ları frekansa ekle
        if (Array.isArray(body.triggeredRules)) {
          for (const rule of body.triggeredRules.slice(0, 15)) {
            if (typeof rule === "string" && rule.length <= 50) {
              mergedRules[rule] = (mergedRules[rule] || 0) + 1;
            }
          }
        }

        const updated = {
          count: (existing?.count || 0) + 1,
          lastSeen: Date.now(),
          tokens: mergedTokens,
          rules: mergedRules,
        };

        // 90 gün TTL
        await env.OPENSHIELD_KV.put(key, JSON.stringify(updated), {
          expirationTtl: 60 * 60 * 24 * 90
        });

        return new Response(JSON.stringify({ ok: true, count: updated.count }), {
          headers: { ...corsHeaders, "Content-Type": "application/json" }
        });

      } catch (e) {
        return new Response(JSON.stringify({ error: "bad request" }), {
          status: 400,
          headers: { ...corsHeaders, "Content-Type": "application/json" }
        });
      }
    }

    // ─── GET /community-list ─────────────────────────────────────────────────
    if (request.method === "GET" && url.pathname === "/community-list") {
      const since = parseInt(url.searchParams.get("since") || "0");

      // KV'deki tüm report: key'lerini listele
      const listed = await env.OPENSHIELD_KV.list({ prefix: "report:" });
      const result = [];

      for (const key of listed.keys) {
        const data = await env.OPENSHIELD_KV.get(key.name, "json");
        if (!data) continue;

        // Eşiği geçmiş + since'ten sonra güncellenenler
        if (data.count >= THRESHOLD && data.lastSeen > since) {
          const hash = key.name.replace("report:", "");
          result.push({
            hash,
            count: data.count,
            // Token ve rule özetini de gönder — RuleEngine iyileştirmesi için
            topTokens: getTop(data.tokens, 5),
            topRules: getTop(data.rules, 5),
          });
        }
      }

      return new Response(JSON.stringify(result), {
        headers: { ...corsHeaders, "Content-Type": "application/json" }
      });
    }

    // ─── GET /admin/stats ────────────────────────────────────────────────────
    // Manuel inceleme için istatistik endpoint'i
    if (request.method === "GET" && url.pathname === "/admin/stats") {
      const adminKey = request.headers.get("X-Admin-Key");
      if (adminKey !== env.ADMIN_KEY) {
        return new Response("Unauthorized", { status: 401 });
      }

      const listed = await env.OPENSHIELD_KV.list({ prefix: "report:" });
      const allEntries = [];

      for (const key of listed.keys) {
        const data = await env.OPENSHIELD_KV.get(key.name, "json");
        if (!data) continue;
        allEntries.push({
          hash: key.name.replace("report:", ""),
          count: data.count,
          lastSeen: new Date(data.lastSeen).toISOString(),
          topTokens: getTop(data.tokens, 10),
          topRules: getTop(data.rules, 10),
          aboveThreshold: data.count >= THRESHOLD,
        });
      }

      // Rapor sayısına göre sırala
      allEntries.sort((a, b) => b.count - a.count);

      return new Response(JSON.stringify({
        total: allEntries.length,
        aboveThreshold: allEntries.filter(e => e.aboveThreshold).length,
        threshold: THRESHOLD,
        entries: allEntries,
      }, null, 2), {
        headers: { ...corsHeaders, "Content-Type": "application/json" }
      });
    }

    return new Response("Not Found", { status: 404 });
  }
};

/** Bir frekans objesinden en yüksek N anahtarı döndürür */
function getTop(obj, n) {
  if (!obj) return [];
  return Object.entries(obj)
    .sort(([, a], [, b]) => b - a)
    .slice(0, n)
    .map(([key, count]) => ({ key, count }));
}
