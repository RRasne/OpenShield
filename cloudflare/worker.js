const MIN_UNIQUE_REPORTERS = 3
const MIN_SPAM_REPORTS = 2
const DEBUG_MIN_UNIQUE_REPORTERS = 2
const MAX_REPORTS_PER_DAY = 10
const KV_REPORTS = "REPORTS"

export default {
  async fetch(request, env) {
    const url = new URL(request.url)
    const corsHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, X-Debug-Mode",
    }

    if (request.method === "OPTIONS") return new Response(null, { headers: corsHeaders })
    if (request.method === "POST" && url.pathname === "/report") return handleReport(request, env, corsHeaders)
    if (request.method === "GET" && url.pathname === "/community.csv") return handleGetCsv(env, corsHeaders)
    if (request.method === "GET" && url.pathname === "/stats") return handleStats(env, corsHeaders)

    return new Response("Not found", { status: 404 })
  }
}

async function handleReport(request, env, corsHeaders) {
  const isDebug = request.headers.get("X-Debug-Mode") === "true"

  let body
  try {
    body = await request.json()
  } catch {
    return jsonResponse({ error: "Invalid JSON" }, 400, corsHeaders)
  }

  // Backward-compatible: old clients may send numberHash/deviceHash instead of number_hash/rules/score/category.
  const legacyNumberHash = typeof body.numberHash === "string" ? body.numberHash : null
  const legacyDeviceHash = typeof body.deviceHash === "string" ? body.deviceHash : null
  const numberHash = typeof body.number_hash === "string" ? body.number_hash : legacyNumberHash
  const rules = Array.isArray(body.rules) && body.rules.length > 0 ? body.rules : ["USER_MARK_SPAM"]
  const score = typeof body.score === "number" ? body.score : 1
  const category = typeof body.category === "string" ? body.category : "USER_REPORTED_SPAM"

  if (!numberHash || numberHash.length !== 64) {
    return jsonResponse({ error: "Invalid number_hash" }, 400, corsHeaders)
  }
  if (!Array.isArray(rules) || rules.length === 0) {
    return jsonResponse({ error: "Invalid rules" }, 400, corsHeaders)
  }
  if (typeof score !== "number" || score < 0 || score > 1) {
    return jsonResponse({ error: "Invalid score" }, 400, corsHeaders)
  }

  const validCategories = [
    "GAMBLING",
    "FRAUD",
    "PHISHING",
    "PROMOTION",
    "ROBOCALL",
    "UNKNOWN",
    "USER_REPORTED_SPAM",
    "USER_REPORTED_SUSPICIOUS",
    "USER_REPORTED_NOT_SPAM",
  ]
  const safeCategory = validCategories.includes(category) ? category : "UNKNOWN"

  const reporterHash = legacyDeviceHash || await sha256(request.headers.get("CF-Connecting-IP") || "unknown")
  const rateLimitKey = `ratelimit:${reporterHash}:${todayString()}`
  const ipCount = parseInt((await env[KV_REPORTS].get(rateLimitKey)) || "0", 10)
  if (ipCount >= MAX_REPORTS_PER_DAY) {
    return jsonResponse({ error: "Rate limit exceeded" }, 429, corsHeaders)
  }

  const reportKey = `report:${numberHash}`
  const existing = JSON.parse((await env[KV_REPORTS].get(reportKey)) || "null") || createEmptyReport()
  const previousVote = existing.reporters?.[reporterHash] || null
  const vote = verdictFrom(score, safeCategory, rules)

  const updated = {
    ...existing,
    category: pickCategory(existing.category, safeCategory),
    rule_counts: mergeRuleCounts(existing.rule_counts || {}, rules),
    first_seen: existing.first_seen || Date.now(),
    last_seen: Date.now(),
    reporters: {
      ...(existing.reporters || {}),
      [reporterHash]: vote,
    },
  }

  updated.verdict_counts = applyVote(existing.verdict_counts || createVerdictCounts(), previousVote, vote)
  updated.unique_reporters = Object.keys(updated.reporters).length
  updated.community_status = computeCommunityStatus(updated.verdict_counts, updated.unique_reporters, isDebug)
  updated.confidence = computeConfidence(updated.verdict_counts)

  await env[KV_REPORTS].put(reportKey, JSON.stringify(updated), { expirationTtl: 60 * 60 * 24 * 90 })
  await env[KV_REPORTS].put(rateLimitKey, String(ipCount + 1), { expirationTtl: 60 * 60 * 24 })

  return jsonResponse(
    {
      success: true,
      unique_reporters: updated.unique_reporters,
      verdict_counts: updated.verdict_counts,
      community_status: updated.community_status,
      confidence: updated.confidence,
      in_community_list: updated.community_status === "SPAM",
      min_unique_reporters: isDebug ? DEBUG_MIN_UNIQUE_REPORTERS : MIN_UNIQUE_REPORTERS,
    },
    200,
    corsHeaders
  )
}

async function handleGetCsv(env, corsHeaders) {
  const list = await env[KV_REPORTS].list({ prefix: "report:" })
  const lines = [
    "# OpenShield Community Spam List",
    `# Updated: ${new Date().toISOString()}`,
    "# Format: number_hash,category,report_count",
  ]

  for (const key of list.keys) {
    const data = JSON.parse((await env[KV_REPORTS].get(key.name)) || "null")
    if (!data || data.community_status !== "SPAM") continue
    lines.push(`${key.name.replace("report:", "")},${data.category},${data.unique_reporters}`)
  }

  return new Response(lines.join("\n"), {
    headers: {
      ...corsHeaders,
      "Content-Type": "text/csv",
      "Cache-Control": "public, max-age=3600",
    },
  })
}

async function handleStats(env, corsHeaders) {
  const list = await env[KV_REPORTS].list({ prefix: "report:" })
  let totalUniqueNumbers = 0
  let spamCount = 0
  let suspiciousCount = 0
  let totalReports = 0

  for (const key of list.keys) {
    const data = JSON.parse((await env[KV_REPORTS].get(key.name)) || "null")
    if (!data) continue
    totalUniqueNumbers += 1
    totalReports += data.unique_reporters || 0
    if (data.community_status === "SPAM") spamCount += 1
    if (data.community_status === "SUSPICIOUS") suspiciousCount += 1
  }

  return jsonResponse(
    {
      total_unique_numbers: totalUniqueNumbers,
      spam_entries: spamCount,
      suspicious_entries: suspiciousCount,
      total_unique_reports: totalReports,
      min_unique_reporters: MIN_UNIQUE_REPORTERS,
      debug_min_unique_reporters: DEBUG_MIN_UNIQUE_REPORTERS,
      min_spam_reports: MIN_SPAM_REPORTS,
    },
    200,
    corsHeaders
  )
}

function createEmptyReport() {
  return {
    category: "UNKNOWN",
    rule_counts: {},
    first_seen: null,
    last_seen: null,
    reporters: {},
    verdict_counts: createVerdictCounts(),
    unique_reporters: 0,
    community_status: "UNKNOWN",
    confidence: 0,
  }
}

function createVerdictCounts() {
  return {
    spam: 0,
    suspicious: 0,
    not_spam: 0,
  }
}

function verdictFrom(score, category, rules) {
  if (category === "USER_REPORTED_NOT_SPAM" || rules.includes("USER_MARK_NOT_SPAM")) return "not_spam"
  if (category === "USER_REPORTED_SUSPICIOUS" || rules.includes("USER_MARK_SUSPICIOUS")) return "suspicious"
  if (score >= 0.6 || category === "USER_REPORTED_SPAM" || rules.includes("USER_MARK_SPAM")) return "spam"
  if (score >= 0.35) return "suspicious"
  return "not_spam"
}

function applyVote(counts, previousVote, nextVote) {
  const updated = { ...counts }
  if (previousVote && updated[previousVote] > 0) updated[previousVote] -= 1
  updated[nextVote] = (updated[nextVote] || 0) + 1
  return updated
}

function computeCommunityStatus(counts, uniqueReporters, isDebug = false) {
  const spamVotes = counts.spam || 0
  const suspiciousVotes = counts.suspicious || 0
  const notSpamVotes = counts.not_spam || 0
  const totalVotes = spamVotes + suspiciousVotes + notSpamVotes
  if (totalVotes === 0) return "UNKNOWN"

  const weightedSpamRatio = (spamVotes + suspiciousVotes * 0.5) / totalVotes
  const minUniqueReporters = isDebug ? DEBUG_MIN_UNIQUE_REPORTERS : MIN_UNIQUE_REPORTERS

  if (uniqueReporters >= minUniqueReporters && spamVotes >= MIN_SPAM_REPORTS && weightedSpamRatio >= 0.75) {
    return "SPAM"
  }
  if (uniqueReporters >= 2 && weightedSpamRatio >= 0.45) {
    return "SUSPICIOUS"
  }
  return "UNKNOWN"
}

function computeConfidence(counts) {
  const spamVotes = counts.spam || 0
  const suspiciousVotes = counts.suspicious || 0
  const notSpamVotes = counts.not_spam || 0
  const totalVotes = spamVotes + suspiciousVotes + notSpamVotes
  if (totalVotes === 0) return 0
  return Number(((spamVotes + suspiciousVotes * 0.5) / totalVotes).toFixed(2))
}

function pickCategory(currentCategory, nextCategory) {
  if (!currentCategory || currentCategory === "UNKNOWN") return nextCategory
  if (nextCategory.startsWith("USER_REPORTED_")) return currentCategory
  return nextCategory
}

function mergeRuleCounts(existing, newRules) {
  const merged = { ...existing }
  for (const rule of newRules) {
    merged[rule] = (merged[rule] || 0) + 1
  }
  return merged
}

async function sha256(text) {
  const buffer = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text))
  return Array.from(new Uint8Array(buffer)).map((byte) => byte.toString(16).padStart(2, "0")).join("")
}

function todayString() {
  return new Date().toISOString().split("T")[0]
}

function jsonResponse(data, status, corsHeaders) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
    },
  })
}
