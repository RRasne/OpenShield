const REPORT_THRESHOLD = 5
const MAX_REPORTS_PER_DAY = 3
const KV_REPORTS = "REPORTS"

export default {
  async fetch(request, env) {
    const url = new URL(request.url)
    const corsHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
    }

    if (request.method === "OPTIONS") return new Response(null, { headers: corsHeaders })
    if (request.method === "POST" && url.pathname === "/report") return handleReport(request, env, corsHeaders)
    if (request.method === "GET" && url.pathname === "/community.csv") return handleGetCsv(env, corsHeaders)
    if (request.method === "GET" && url.pathname === "/stats") return handleStats(env, corsHeaders)

    return new Response("Not found", { status: 404 })
  }
}

async function handleReport(request, env, corsHeaders) {
  let body
  try { body = await request.json() } catch { return jsonResponse({ error: "Invalid JSON" }, 400, corsHeaders) }

  const { number_hash, rules, score, category } = body
  if (!number_hash || typeof number_hash !== "string" || number_hash.length !== 64)
    return jsonResponse({ error: "Invalid number_hash" }, 400, corsHeaders)
  if (!Array.isArray(rules) || rules.length === 0)
    return jsonResponse({ error: "Invalid rules" }, 400, corsHeaders)
  if (typeof score !== "number" || score < 0 || score > 1)
    return jsonResponse({ error: "Invalid score" }, 400, corsHeaders)

  const validCategories = ["GAMBLING", "FRAUD", "PHISHING", "PROMOTION", "ROBOCALL", "UNKNOWN"]
  const safeCategory = validCategories.includes(category) ? category : "UNKNOWN"

  const ipHash = await sha256(request.headers.get("CF-Connecting-IP") || "unknown")
  const rateLimitKey = `ratelimit:${ipHash}:${todayString()}`
  const ipCount = parseInt(await env[KV_REPORTS].get(rateLimitKey) || "0")
  if (ipCount >= MAX_REPORTS_PER_DAY) return jsonResponse({ error: "Rate limit exceeded" }, 429, corsHeaders)

  const reportKey = `report:${number_hash}`
  const existing = JSON.parse(await env[KV_REPORTS].get(reportKey) || "null")
  const updated = {
    count: (existing?.count || 0) + 1,
    category: safeCategory,
    rule_counts: mergeRuleCounts(existing?.rule_counts || {}, rules),
    first_seen: existing?.first_seen || Date.now(),
    last_seen: Date.now(),
  }

  await env[KV_REPORTS].put(reportKey, JSON.stringify(updated), { expirationTtl: 60 * 60 * 24 * 90 })
  await env[KV_REPORTS].put(rateLimitKey, String(ipCount + 1), { expirationTtl: 60 * 60 * 24 })

  return jsonResponse({ success: true, count: updated.count, threshold: REPORT_THRESHOLD, in_community_list: updated.count >= REPORT_THRESHOLD }, 200, corsHeaders)
}

async function handleGetCsv(env, corsHeaders) {
  const list = await env[KV_REPORTS].list({ prefix: "report:" })
  const lines = [
    "# OpenShield Community Spam List",
    `# GÃ¼ncelleme: ${new Date().toISOString()}`,
    "# Format: number_hash,category,report_count",
  ]
  for (const key of list.keys) {
    const data = JSON.parse(await env[KV_REPORTS].get(key.name) || "null")
    if (!data || data.count < REPORT_THRESHOLD) continue
    lines.push(`${key.name.replace("report:", "")},${data.category},${data.count}`)
  }
  return new Response(lines.join("\n"), { headers: { ...corsHeaders, "Content-Type": "text/csv", "Cache-Control": "public, max-age=3600" } })
}

async function handleStats(env, corsHeaders) {
  const list = await env[KV_REPORTS].list({ prefix: "report:" })
  let totalReports = 0, inCommunityList = 0
  const categoryCount = {}
  for (const key of list.keys) {
    const data = JSON.parse(await env[KV_REPORTS].get(key.name) || "null")
    if (!data) continue
    totalReports += data.count
    if (data.count >= REPORT_THRESHOLD) {
      inCommunityList++
      categoryCount[data.category] = (categoryCount[data.category] || 0) + 1
    }
  }
  return jsonResponse({ total_unique_numbers: list.keys.length, in_community_list: inCommunityList, total_reports: totalReports, threshold: REPORT_THRESHOLD, categories: categoryCount }, 200, corsHeaders)
}

function mergeRuleCounts(existing, newRules) {
  const merged = { ...existing }
  for (const rule of newRules) merged[rule] = (merged[rule] || 0) + 1
  return merged
}

async function sha256(text) {
  const buffer = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text))
  return Array.from(new Uint8Array(buffer)).map(b => b.toString(16).padStart(2, "0")).join("")
}

function todayString() { return new Date().toISOString().split("T")[0] }

function jsonResponse(data, status, corsHeaders) {
  return new Response(JSON.stringify(data), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } })
}
