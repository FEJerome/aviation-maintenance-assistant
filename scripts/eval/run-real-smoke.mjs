import fs from 'node:fs/promises'
import path from 'node:path'

// Real-model release-acceptance smoke runner for the aviation reference
// implementation. Exercises the real HTTP chat endpoint (and optionally the
// read-only MCP endpoint) against a real OpenAI-compatible provider configured
// on the backend. Unlike run-golden-set.mjs there is no fake call counter, so
// model calls are counted through the backend's own quota ledger:
// EVAL_STATE_PATH/daily-llm-quota.json ({ "date": "...", "count": n }).
//
// Discipline (binding):
//   - Items run strictly sequentially, one at a time. No retries, no parallelism.
//   - Any non-2xx HTTP response (including 429) aborts the run immediately.
//   - A hard cap (default 80, EVAL_MAX_MODEL_CALLS) on total model calls consumed
//     by the run (backend quota delta + judge calls) aborts the run when reached.
//   - Credentials are read from the environment only and are never printed,
//     logged, or written into reports.
//
// Environment:
//   EVAL_BACKEND_URL     default http://127.0.0.1:18091
//   EVAL_SET             default evals/aviation/golden-set.json
//   EVAL_STATE_PATH      REQUIRED — backend AMA_STATE_PATH; the quota file inside
//                        it is the model-call counter
//   EVAL_OUTPUT_DIR      default docs/test-reports
//   EVAL_REPORT_STEM     default real-model-smoke-<YYYY-MM-DD>
//   EVAL_MCP_PASS=1      enable the optional second pass over the MCP endpoint
//   MCP_TOKEN            bearer token for the MCP pass (required with EVAL_MCP_PASS=1)
//   EVAL_MCP_PATH        default /mcp
//   EVAL_JUDGE=1         enable the LLM judge for rag items: keypoint coverage
//                        (gated) plus faithfulness (reported only, no gate)
//   EVAL_JUDGE_MODEL     judge model name (default: AMA_LLM_MODEL)
//   AMA_LLM_BASE_URL     judge endpoint — the same OpenAI-compatible triple the
//   AMA_LLM_API_KEY      backend uses; required when EVAL_JUDGE=1 (key optional
//   AMA_LLM_MODEL        only for keyless local endpoints)
//   EVAL_MAX_MODEL_CALLS default 80 — hard cap for the whole run
//   EVAL_SMOKE_LABEL     free-text label recorded in the report header

const backendUrl = process.env.EVAL_BACKEND_URL || 'http://127.0.0.1:18091'
const setPath = process.env.EVAL_SET || 'evals/aviation/golden-set.json'
const statePath = process.env.EVAL_STATE_PATH || ''
const outputDir = process.env.EVAL_OUTPUT_DIR || 'docs/test-reports'
const mcpPass = process.env.EVAL_MCP_PASS === '1'
const mcpToken = process.env.MCP_TOKEN || ''
const mcpPath = process.env.EVAL_MCP_PATH || '/mcp'
const judgeEnabled = process.env.EVAL_JUDGE === '1'
const judgeBaseUrl = (process.env.AMA_LLM_BASE_URL || '').replace(/\/+$/, '')
const judgeApiKey = process.env.AMA_LLM_API_KEY || ''
const judgeModel = process.env.EVAL_JUDGE_MODEL || process.env.AMA_LLM_MODEL || ''
const maxModelCalls = Number(process.env.EVAL_MAX_MODEL_CALLS || 80)
const smokeLabel = process.env.EVAL_SMOKE_LABEL || ''
const reportStem = process.env.EVAL_REPORT_STEM || `real-model-smoke-${new Date().toISOString().slice(0, 10)}`

if (!statePath) {
  throw new Error('EVAL_STATE_PATH is required: it must point at the backend AMA_STATE_PATH so the quota ledger can be used as the model-call counter')
}
if (!Number.isInteger(maxModelCalls) || maxModelCalls < 1) {
  throw new Error(`EVAL_MAX_MODEL_CALLS must be a positive integer, got ${process.env.EVAL_MAX_MODEL_CALLS}`)
}
const quotaFile = path.join(statePath, 'daily-llm-quota.json')

const OUTCOME_BY_ROUTE = {
  rag: 'ANSWERED',
  validation_block: 'VALIDATION_BLOCKED',
  validation_unknown: 'VALIDATION_UNKNOWN',
  clarify_model: 'CLARIFICATION_REQUIRED',
  no_answer: 'NO_ANSWER',
  // out_of_scope has no native product route; it is evaluated against NO_ANSWER.
  out_of_scope: 'NO_ANSWER'
}

const FIXED_REPLY = {
  validation_block: '机型与发动机型号不匹配，请核实后重新提供。',
  validation_unknown: '机型或发动机暂未收录，请核实型号并查阅官方维修手册。',
  clarify_model: '为了提供准确的维修信息，请提供机型和发动机型号。',
  no_answer: '未在手册中找到相关内容。',
  out_of_scope: '未在手册中找到相关内容。'
}

const TAG_ENUM = new Set([
  'general-knowledge', 'engine', 'flight-controls', 'landing-gear', 'hydraulics',
  'electrical', 'structures', 'validation', 'cross-language', 'citation',
  'no-answer', 'ambiguous', 'out-of-scope'
])
const ROUTE_ENUM = new Set(Object.keys(OUTCOME_BY_ROUTE))
const ITEM_KEYS = new Set([
  'id', 'question', 'language', 'aircraft_model', 'engine_model', 'expected_route',
  'expected_behavior', 'answer_keypoints', 'source_locator', 'required_docs', 'tags', 'notes'
])

function checkItemSchema(item) {
  // Hand-rolled equivalent of evals/aviation/golden-set.schema.json (item contract),
  // identical to run-golden-set.mjs. Kept dependency-free on purpose.
  const problems = []
  if (typeof item !== 'object' || item === null || Array.isArray(item)) return ['item is not an object']
  for (const key of Object.keys(item)) {
    if (!ITEM_KEYS.has(key)) problems.push(`unknown property ${key}`)
  }
  if (typeof item.id !== 'string' || !/^AVI-[0-9]{3}$/.test(item.id)) problems.push('id must match AVI-000')
  if (typeof item.question !== 'string' || item.question.length < 1) problems.push('question required')
  if (!['en', 'zh'].includes(item.language)) problems.push('language must be en or zh')
  if (!ROUTE_ENUM.has(item.expected_route)) problems.push(`expected_route invalid: ${item.expected_route}`)
  if (!Array.isArray(item.tags) || item.tags.length < 1 || item.tags.some(tag => !TAG_ENUM.has(tag))) {
    problems.push('tags must be non-empty and use the schema enum')
  }
  if (item.expected_route === 'rag') {
    if (!Array.isArray(item.answer_keypoints) || item.answer_keypoints.length < 1) {
      problems.push('rag item requires non-empty answer_keypoints')
    }
    if (typeof item.source_locator !== 'object' || item.source_locator === null
        || typeof item.source_locator.doc_title !== 'string') {
      problems.push('rag item requires source_locator.doc_title')
    }
    if (!Array.isArray(item.required_docs) || item.required_docs.length < 1) {
      problems.push('rag item requires non-empty required_docs')
    }
  }
  return problems
}

// --- Model-call accounting -------------------------------------------------
// The backend quota ledger ({date, count}) counts every model attempt,
// including translation calls and failed attempts. A missing file means zero
// calls so far. A UTC date rollover mid-run resets the counter, in which case
// the fresh count is the delta.

async function readQuota() {
  try {
    const record = JSON.parse(await fs.readFile(quotaFile, 'utf8'))
    if (typeof record.count !== 'number' || typeof record.date !== 'string') {
      throw new Error(`quota ledger has unexpected shape: ${JSON.stringify(record).slice(0, 200)}`)
    }
    return record
  } catch (error) {
    if (error && error.code === 'ENOENT') return null
    throw error
  }
}

function quotaDelta(before, after) {
  if (!after) return 0
  if (!before) return after.count
  return after.date === before.date ? after.count - before.count : after.count
}

let runStartQuota = null
let latestQuota = null
let judgeCalls = 0

function consumedCalls() {
  return quotaDelta(runStartQuota, latestQuota) + judgeCalls
}

function enforceCallCap(aboutTo) {
  if (consumedCalls() >= maxModelCalls) {
    throw new Error(
      `model-call hard cap reached (${consumedCalls()}/${maxModelCalls}) ${aboutTo}; aborting without further calls`)
  }
}

async function preflight() {
  const chat = await fetch(`${backendUrl}/api/chat`).catch(() => null)
  if (!chat || chat.status !== 405) {
    throw new Error(`backend preflight failed: GET /api/chat expected 405, got ${chat ? chat.status : 'no response'}`)
  }
  await fs.mkdir(statePath, { recursive: false }).catch(error => {
    if (!error || error.code !== 'EEXIST') throw error
  })
  runStartQuota = await readQuota()
  latestQuota = runStartQuota
  if (judgeEnabled) {
    if (!judgeBaseUrl) throw new Error('EVAL_JUDGE=1 requires AMA_LLM_BASE_URL for the judge endpoint')
    if (!judgeModel) throw new Error('EVAL_JUDGE=1 requires AMA_LLM_MODEL (or EVAL_JUDGE_MODEL) for the judge endpoint')
  }
}

async function askHttp(question) {
  const response = await fetch(`${backendUrl}/api/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message: question })
  })
  if (!response.ok) {
    throw new Error(`HTTP /api/chat answered ${response.status}; aborting (no retries on a real provider)`)
  }
  return response.json()
}

function newMcpClient() {
  const url = `${backendUrl}${mcpPath}`
  let sessionId = null
  let nextId = 1

  async function rpc(method, params, isNotification) {
    const headers = {
      'Content-Type': 'application/json',
      Accept: 'application/json, text/event-stream',
      Authorization: `Bearer ${mcpToken}`
    }
    if (sessionId) headers['Mcp-Session-Id'] = sessionId
    const body = { jsonrpc: '2.0', method, ...(params ? { params } : {}) }
    if (!isNotification) body.id = nextId++
    const response = await fetch(url, { method: 'POST', headers, body: JSON.stringify(body) })
    if (!response.ok) {
      throw new Error(`MCP endpoint answered ${response.status}; aborting (no retries on a real provider)`)
    }
    const header = response.headers.get('mcp-session-id')
    if (header) sessionId = header
    if (isNotification) return null
    const text = await response.text()
    const dataLines = text.split('\n')
      .map(line => line.replace(/^data:\s*/, ''))
      .filter(line => line.includes(`"id":${body.id},`))
    const selected = dataLines.length ? dataLines[dataLines.length - 1]
      : text.split('\n').filter(line => line.includes(`"id":${body.id},`)).pop()
    if (!selected) throw new Error(`No JSON-RPC response for id ${body.id}: ${text.slice(0, 300)}`)
    const message = JSON.parse(selected)
    if (message.error) throw new Error(`JSON-RPC error: ${JSON.stringify(message.error)}`)
    return message.result
  }

  return {
    async initialize() {
      await rpc('initialize', {
        protocolVersion: '2025-03-26',
        capabilities: {},
        clientInfo: { name: 'real-model-smoke', version: '1.0.0' }
      })
      await rpc('notifications/initialized', null, true)
    },
    async ask(question) {
      const result = await rpc('tools/call', {
        name: 'ask_maintenance_question',
        arguments: { question }
      })
      if (!result || !Array.isArray(result.content) || !result.content[0]) {
        throw new Error(`Unexpected tool result shape: ${JSON.stringify(result).slice(0, 300)}`)
      }
      return JSON.parse(result.content[0].text)
    }
  }
}

function expectedCalls(item) {
  // Same routing-level call expectations as run-golden-set.mjs. Short-circuit
  // routes never reach the model; cross-language items pay at most one
  // translation call. The quota ledger counts attempts exactly like the fake
  // counter does on loopback.
  if (['validation_block', 'validation_unknown', 'clarify_model'].includes(item.expected_route)) {
    return { min: 0, max: 0 }
  }
  if (item.expected_route === 'no_answer' || item.expected_route === 'out_of_scope') {
    return item.language === 'zh' ? { min: 0, max: 1 } : { min: 0, max: 0 }
  }
  return item.language === 'zh' ? { min: 2, max: 2 } : { min: 1, max: 1 }
}

function citationProblems(citation) {
  const problems = []
  for (const field of ['excerptId', 'documentId', 'documentTitle']) {
    if (typeof citation[field] !== 'string' || citation[field].trim() === '') {
      problems.push(`${field} blank`)
    }
  }
  if (!Number.isInteger(citation.chunkIndex) || citation.chunkIndex < 0) problems.push('chunkIndex invalid')
  if (typeof citation.score !== 'number' || Number.isNaN(citation.score)) problems.push('score missing')
  return problems
}

// --- LLM judge --------------------------------------------------------------
// One judge call per rag item per channel. Keypoint coverage is gated (every
// key point of every rag item must be covered); faithfulness is reported only.
// Faithfulness needs the cited excerpt text, which only the MCP channel
// returns, so HTTP-channel answers are judged on coverage alone.

function judgePrompt(item, answer, excerpts) {
  const lines = []
  lines.push('You are an impartial evaluator for a retrieval-augmented maintenance QA system.')
  lines.push('')
  lines.push('Question:')
  lines.push(item.question)
  lines.push('')
  lines.push('System answer:')
  lines.push(answer.reply)
  lines.push('')
  if (excerpts.length) {
    lines.push('Source excerpts the answer cites:')
    excerpts.forEach((excerpt, index) => {
      lines.push(`[S${index + 1}] (${excerpt.documentTitle}, chunk ${excerpt.chunkIndex})`)
      lines.push(excerpt.text)
      lines.push('')
    })
  } else {
    lines.push('No source excerpt text is available for this answer.')
    lines.push('')
  }
  lines.push('Key points an acceptable answer must cover:')
  item.answer_keypoints.forEach((keypoint, index) => lines.push(`${index + 1}. ${keypoint}`))
  lines.push('')
  lines.push('Evaluate strictly:')
  lines.push('1. For each key point, decide whether the answer explicitly covers it. Paraphrase counts; vague allusion does not.')
  if (excerpts.length) {
    lines.push('2. Faithfulness: decide whether every factual claim in the answer is supported by the cited source excerpts.')
  } else {
    lines.push('2. Faithfulness cannot be evaluated without excerpt text; set supported to null.')
  }
  lines.push('Respond with JSON only, no prose, in exactly this shape:')
  lines.push('{"keypoint_coverage":[{"keypoint":"<verbatim key point>","covered":true}],"faithfulness":{"supported":true,"notes":"<one sentence>"}}')
  return lines.join('\n')
}

function parseJudgeVerdict(raw, item) {
  const start = raw.indexOf('{')
  const end = raw.lastIndexOf('}')
  if (start < 0 || end <= start) throw new Error('judge response contains no JSON object')
  const verdict = JSON.parse(raw.slice(start, end + 1))
  if (!Array.isArray(verdict.keypoint_coverage)
      || verdict.keypoint_coverage.length !== item.answer_keypoints.length
      || verdict.keypoint_coverage.some(entry => typeof entry.covered !== 'boolean')) {
    throw new Error('judge verdict has malformed keypoint_coverage')
  }
  const faithfulness = verdict.faithfulness && typeof verdict.faithfulness === 'object'
    ? {
        supported: typeof verdict.faithfulness.supported === 'boolean' ? verdict.faithfulness.supported : null,
        notes: typeof verdict.faithfulness.notes === 'string' ? verdict.faithfulness.notes : ''
      }
    : { supported: null, notes: '' }
  return {
    keypointCoverage: verdict.keypoint_coverage.map(entry => ({
      keypoint: typeof entry.keypoint === 'string' ? entry.keypoint : '',
      covered: entry.covered
    })),
    faithfulness
  }
}

async function judgeItem(item, answer) {
  enforceCallCap(`before judging ${item.id}`)
  const excerpts = (Array.isArray(answer.citations) ? answer.citations : [])
    .filter(citation => typeof citation.text === 'string' && citation.text.trim() !== '')
  const response = await fetch(`${judgeBaseUrl}/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(judgeApiKey ? { Authorization: `Bearer ${judgeApiKey}` } : {})
    },
    body: JSON.stringify({
      model: judgeModel,
      stream: false,
      temperature: 0,
      messages: [{ role: 'user', content: judgePrompt(item, answer, excerpts) }]
    })
  })
  judgeCalls += 1
  if (!response.ok) {
    throw new Error(`judge endpoint answered ${response.status}; aborting (no retries on a real provider)`)
  }
  const payload = await response.json()
  const content = payload && payload.choices && payload.choices[0]
    && payload.choices[0].message && payload.choices[0].message.content
  if (typeof content !== 'string') throw new Error('judge response has no message content')
  try {
    const verdict = parseJudgeVerdict(content, item)
    return {
      ...verdict,
      faithfulnessEvaluable: excerpts.length > 0,
      allKeypointsCovered: verdict.keypointCoverage.every(entry => entry.covered)
    }
  } catch (error) {
    // The judge call was consumed but its verdict is unusable: fail closed on the
    // coverage gate without aborting the run.
    return { error: `judge verdict unusable: ${error.message}` }
  }
}

function evaluateItem(item, answer, calls, judge) {
  const failures = []
  const expectedOutcome = OUTCOME_BY_ROUTE[item.expected_route]
  const callExpectation = expectedCalls(item)
  const citations = Array.isArray(answer.citations) ? answer.citations : []

  const routeOk = answer.outcome === expectedOutcome
  if (!routeOk) failures.push(`route: expected ${expectedOutcome}, got ${answer.outcome}`)

  const zeroCallViolation = calls < callExpectation.min || calls > callExpectation.max
  if (zeroCallViolation) {
    failures.push(`calls: expected ${callExpectation.min}..${callExpectation.max}, got ${calls}`)
  }

  let hitAt5 = null
  let reciprocalRank = null
  if (item.expected_route === 'rag') {
    if (!Array.isArray(answer.reply) && typeof answer.reply === 'string' && answer.reply.trim() !== ''
        && answer.reply.includes('[S')) {
      // reply structure ok
    } else {
      failures.push('reply: must be non-empty and contain a [S citation marker')
    }
    if (citations.length === 0) failures.push('citations: must be non-empty for rag items')
    if (answer.degraded !== false) failures.push('degraded: must be false')
    const integrity = citations.flatMap(citationProblems)
    if (integrity.length) failures.push(`citation integrity: ${integrity.join('; ')}`)
    const docs = citations.map(citation => citation.documentTitle)
    const firstHit = docs.findIndex(doc => item.required_docs.includes(doc))
    hitAt5 = firstHit >= 0 && firstHit < 5
    reciprocalRank = hitAt5 ? 1 / (firstHit + 1) : 0
    if (!hitAt5) {
      failures.push(`retrieval: no required doc (${item.required_docs.join(', ')}) in top-5 citations`)
    }
    if (judge && judge.error) {
      failures.push(`judge: ${judge.error}`)
    } else if (judge && !judge.allKeypointsCovered) {
      const missed = judge.keypointCoverage.filter(entry => !entry.covered).map(entry => entry.keypoint)
      failures.push(`judge: key points not covered: ${missed.join(' | ')}`)
    }
  } else {
    if (citations.length !== 0) failures.push('citations: must be empty on this route')
    if (answer.reply !== FIXED_REPLY[item.expected_route]) {
      failures.push(`reply: expected fixed notice ${JSON.stringify(FIXED_REPLY[item.expected_route])}`)
    }
  }

  return {
    id: item.id,
    route: item.expected_route,
    language: item.language,
    expectedOutcome,
    actualOutcome: answer.outcome,
    calls,
    callExpectation: `${callExpectation.min}..${callExpectation.max}`,
    routeOk,
    zeroCallViolation,
    hitAt5,
    reciprocalRank,
    citationIntegrityOk: item.expected_route !== 'rag' || citations.flatMap(citationProblems).length === 0,
    noAnswerFabricated: ['no_answer', 'out_of_scope'].includes(item.expected_route)
      ? (answer.outcome !== 'NO_ANSWER' || answer.reply !== FIXED_REPLY.no_answer)
      : null,
    judge: judge || null,
    pass: failures.length === 0,
    failures
  }
}

function summarize(results) {
  const total = results.length
  const routeCorrect = results.filter(r => r.routeOk).length
  const zeroCallViolations = results.filter(r => r.zeroCallViolation).length
  const noAnswerItems = results.filter(r => r.noAnswerFabricated !== null)
  const noAnswerFabrications = noAnswerItems.filter(r => r.noAnswerFabricated).length
  const ragItems = results.filter(r => r.hitAt5 !== null)
  const ragHits = ragItems.filter(r => r.hitAt5).length
  const integrityItems = results.filter(r => r.citationIntegrityOk === false).length
  const mrr = ragItems.length
    ? ragItems.reduce((sum, r) => sum + r.reciprocalRank, 0) / ragItems.length
    : null
  const judged = ragItems.filter(r => r.judge)
  const coverageFailures = judged.filter(r => r.judge.error || !r.judge.allKeypointsCovered).length
  const faithful = judged.filter(r => r.judge.faithfulnessEvaluable && r.judge.faithfulness.supported === true).length
  const unfaithful = judged.filter(r => r.judge.faithfulnessEvaluable && r.judge.faithfulness.supported === false).length

  const metrics = {
    routeAccuracy: total ? routeCorrect / total : 0,
    zeroCallViolations,
    noAnswerFalseAnswerRate: noAnswerItems.length ? noAnswerFabrications / noAnswerItems.length : 0,
    ragDocHitAt5: ragItems.length ? ragHits / ragItems.length : 0,
    citationIntegrity: results.length ? (results.length - integrityItems) / results.length : 0,
    mrr,
    judgeKeypointCoverage: judged.length ? (judged.length - coverageFailures) / judged.length : null,
    judgeFaithfulness: { evaluable: faithful + unfaithful, faithful, unfaithful }
  }
  const gates = [
    { gate: 'route accuracy = 100%', value: metrics.routeAccuracy, pass: metrics.routeAccuracy === 1 },
    { gate: 'zero-call violations = 0', value: zeroCallViolations, pass: zeroCallViolations === 0 },
    {
      gate: 'no_answer false-answer rate = 0',
      value: metrics.noAnswerFalseAnswerRate,
      pass: metrics.noAnswerFalseAnswerRate === 0
    },
    { gate: 'rag doc-level hit@5 = 100%', value: metrics.ragDocHitAt5, pass: metrics.ragDocHitAt5 === 1 },
    { gate: 'citations integrity = 100%', value: metrics.citationIntegrity, pass: metrics.citationIntegrity === 1 }
  ]
  if (judged.length) {
    gates.push({
      gate: 'judge keypoint coverage = 100%',
      value: metrics.judgeKeypointCoverage,
      pass: metrics.judgeKeypointCoverage === 1
    })
  }
  return { metrics, gates, allGatesPass: gates.every(g => g.pass) }
}

async function runChannel(name, items, ask) {
  const results = []
  for (const item of items) {
    enforceCallCap(`before ${name} item ${item.id}`)
    const before = await readQuota()
    const answer = await ask(item.question)
    const after = await readQuota()
    latestQuota = after || before || latestQuota
    let judge = null
    if (judgeEnabled && item.expected_route === 'rag') {
      enforceCallCap(`before judging ${item.id}`)
      // Transport/HTTP errors from the judge abort the run; unusable verdicts
      // come back as { error } and fail the item's coverage gate.
      judge = await judgeItem(item, answer)
    }
    results.push(evaluateItem(item, answer, quotaDelta(before, after), judge))
  }
  return { channel: name, results, ...summarize(results) }
}

function formatValue(value) {
  return typeof value === 'number' && value % 1 !== 0 ? value.toFixed(4) : value
}

function markdownReport(channels, skipped) {
  const lines = []
  lines.push('# Real-Model Smoke Report', '')
  lines.push(`- Date: ${new Date().toISOString()}`)
  lines.push(`- Backend: ${backendUrl}`)
  if (smokeLabel) lines.push(`- Label: ${smokeLabel}`)
  lines.push(`- Judge: ${judgeEnabled ? `enabled (model ${judgeModel})` : 'disabled'}`)
  lines.push(`- Model calls consumed by this run: ${consumedCalls()} (hard cap ${maxModelCalls})`)
  lines.push(`- Set: ${setPath}`)
  lines.push('')
  lines.push('> Real-model smoke against the configured provider and model on the synthetic')
  lines.push('> quickstart corpus, at one point in time. It covers exactly that one')
  lines.push('> provider/model/corpus combination and nothing else: not other providers, not')
  lines.push('> other models, not real manuals, and not production deployments. Credentials')
  lines.push('> are supplied through server-side environment variables only and never appear')
  lines.push('> in this report. `out_of_scope` items are evaluated against the NO_ANSWER')
  lines.push('> outcome — a documented mapping, not a native product route.')
  lines.push('')
  for (const channel of channels) {
    lines.push(`## Channel: ${channel.channel}`, '')
    lines.push('| Gate | Value | Result |')
    lines.push('|------|-------|--------|')
    for (const gate of channel.gates) {
      lines.push(`| ${gate.gate} | ${formatValue(gate.value)} | ${gate.pass ? 'PASS' : 'FAIL'} |`)
    }
    lines.push(`| MRR (report only, no gate) | ${channel.metrics.mrr === null ? '-' : channel.metrics.mrr.toFixed(4)} | - |`)
    const faith = channel.metrics.judgeFaithfulness
    if (faith.evaluable > 0) {
      lines.push(`| Judge faithfulness (report only, no gate) | ${faith.faithful}/${faith.evaluable} faithful | - |`)
    }
    lines.push('')
    lines.push('| Item | Route | Expected | Actual | Calls | Hit@5 | Result |')
    lines.push('|------|-------|----------|--------|-------|-------|--------|')
    for (const r of channel.results) {
      const hit = r.hitAt5 === null ? '-' : (r.hitAt5 ? 'yes' : 'no')
      const detail = r.pass ? 'PASS' : `FAIL (${r.failures.join('; ')})`
      lines.push(`| ${r.id} | ${r.route} | ${r.expectedOutcome} | ${r.actualOutcome} | ${r.calls} (${r.callExpectation}) | ${hit} | ${detail} |`)
    }
    lines.push('')
  }
  if (skipped.length) {
    lines.push('## Skipped channels', '')
    for (const note of skipped) lines.push(`- ${note}`)
    lines.push('')
  }
  return lines.join('\n')
}

const dataset = JSON.parse(await fs.readFile(setPath, 'utf8'))
const items = dataset.items
if (!Array.isArray(items) || items.length === 0) throw new Error('golden set has no items')
const schemaProblems = items.flatMap(item => checkItemSchema(item).map(p => `${item.id || '?'}: ${p}`))
if (schemaProblems.length) {
  throw new Error(`golden-set schema violations:\n${schemaProblems.join('\n')}`)
}

await preflight()

const channels = []
channels.push(await runChannel('http', items, askHttp))

const skipped = []
if (mcpPass) {
  if (!mcpToken) throw new Error('EVAL_MCP_PASS=1 requires MCP_TOKEN')
  const client = newMcpClient()
  await client.initialize()
  channels.push(await runChannel('mcp', items, question => client.ask(question)))
} else {
  skipped.push('mcp: optional second pass not enabled (set EVAL_MCP_PASS=1 and MCP_TOKEN to run it)')
}

const allPass = channels.every(channel => channel.allGatesPass)
const report = {
  generatedAt: new Date().toISOString(),
  backendUrl,
  label: smokeLabel || null,
  judge: judgeEnabled ? { model: judgeModel, calls: judgeCalls } : null,
  modelCallsConsumed: consumedCalls(),
  maxModelCalls,
  set: setPath,
  note: 'Real-model smoke on the synthetic corpus; covers exactly one provider/model at one point in time. out_of_scope is mapped to NO_ANSWER (not a native route).',
  channels: channels.map(channel => ({
    channel: channel.channel,
    metrics: channel.metrics,
    gates: channel.gates,
    allGatesPass: channel.allGatesPass,
    results: channel.results
  })),
  skipped,
  allGatesPass: allPass
}

await fs.mkdir(outputDir, { recursive: true })
const jsonPath = path.join(outputDir, `${reportStem}.json`)
const mdPath = path.join(outputDir, `${reportStem}.md`)
await fs.writeFile(jsonPath, JSON.stringify(report, null, 2) + '\n')
await fs.writeFile(mdPath, markdownReport(channels, skipped))

for (const channel of channels) {
  const status = channel.allGatesPass ? 'PASS' : 'FAIL'
  console.log(`[${channel.channel}] gates ${status}: ` +
    channel.gates.map(g => `${g.gate}=${formatValue(g.value)}`).join(', ') +
    `, MRR=${channel.metrics.mrr === null ? '-' : channel.metrics.mrr.toFixed(4)}`)
}
console.log(`Model calls consumed: ${consumedCalls()} (hard cap ${maxModelCalls})`)
console.log(`Report: ${mdPath}`)
console.log(allPass ? 'REAL-MODEL SMOKE PASS' : 'REAL-MODEL SMOKE FAIL')
process.exit(allPass ? 0 : 1)
