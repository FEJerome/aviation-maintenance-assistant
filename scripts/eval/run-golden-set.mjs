import fs from 'node:fs/promises'
import path from 'node:path'

// Golden-set evaluation harness for the aviation reference implementation.
// Loopback only: exercises the real HTTP chat endpoint (and optionally the read-only
// MCP endpoint) against the fake OpenAI-compatible server and an isolated Chroma
// collection. No real model, provider, public IP, or production service is involved.
//
// Environment:
//   EVAL_BACKEND_URL  default http://127.0.0.1:18091
//   EVAL_FAKE_URL     default http://127.0.0.1:18089
//   EVAL_SET          default evals/aviation/golden-set.json
//   EVAL_OUTPUT_DIR   default docs/test-reports
//   EVAL_REPORT_STEM  default golden-set-eval-<YYYY-MM-DD>
//   EVAL_MCP_PASS=1   enable the optional second pass over the MCP endpoint
//   MCP_TOKEN         bearer token for the MCP pass (required when EVAL_MCP_PASS=1)
//   EVAL_MCP_PATH     default /mcp

const backendUrl = process.env.EVAL_BACKEND_URL || 'http://127.0.0.1:18091'
const fakeUrl = process.env.EVAL_FAKE_URL || 'http://127.0.0.1:18089'
const setPath = process.env.EVAL_SET || 'evals/aviation/golden-set.json'
const outputDir = process.env.EVAL_OUTPUT_DIR || 'docs/test-reports'
const mcpPass = process.env.EVAL_MCP_PASS === '1'
const mcpToken = process.env.MCP_TOKEN || ''
const mcpPath = process.env.EVAL_MCP_PATH || '/mcp'
const reportStem = process.env.EVAL_REPORT_STEM || `golden-set-eval-${new Date().toISOString().slice(0, 10)}`

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
  // Hand-rolled equivalent of evals/aviation/golden-set.schema.json (item contract).
  // Kept dependency-free on purpose; if the schema changes, update both.
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

async function fakeCalls() {
  const response = await fetch(`${fakeUrl}/__calls`)
  if (!response.ok) throw new Error(`fake /__calls answered ${response.status}`)
  return (await response.json()).calls
}

async function preflight() {
  const chat = await fetch(`${backendUrl}/api/chat`).catch(() => null)
  if (!chat || chat.status !== 405) {
    throw new Error(`backend preflight failed: GET /api/chat expected 405, got ${chat ? chat.status : 'no response'}`)
  }
  await fakeCalls()
}

async function askHttp(question) {
  const response = await fetch(`${backendUrl}/api/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message: question })
  })
  if (!response.ok) throw new Error(`HTTP /api/chat answered ${response.status}`)
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
        clientInfo: { name: 'golden-set-eval', version: '1.0.0' }
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
  // Deterministic model-call expectations on the loopback fake. Short-circuit routes
  // never reach the model; cross-language items pay at most one translation call.
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

function evaluateItem(item, answer, calls) {
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

  const metrics = {
    routeAccuracy: total ? routeCorrect / total : 0,
    zeroCallViolations,
    noAnswerFalseAnswerRate: noAnswerItems.length ? noAnswerFabrications / noAnswerItems.length : 0,
    ragDocHitAt5: ragItems.length ? ragHits / ragItems.length : 0,
    citationIntegrity: results.length ? (results.length - integrityItems) / results.length : 0,
    mrr
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
  return { metrics, gates, allGatesPass: gates.every(g => g.pass) }
}

async function runChannel(name, items, ask) {
  const results = []
  for (const item of items) {
    const before = await fakeCalls()
    const answer = await ask(item.question)
    const after = await fakeCalls()
    results.push(evaluateItem(item, answer, after - before))
  }
  return { channel: name, results, ...summarize(results) }
}

function markdownReport(channels, skipped) {
  const lines = []
  lines.push('# Golden-Set Evaluation Report', '')
  lines.push(`- Date: ${new Date().toISOString()}`)
  lines.push(`- Backend: ${backendUrl} (loopback)`)
  lines.push(`- Fake LLM: ${fakeUrl} (loopback, deterministic; translation map via FAKE_TRANSLATION_MAP)`)
  lines.push(`- Set: ${setPath}`)
  lines.push('')
  lines.push('> Loopback-only deterministic evaluation. This report does not cover real models,')
  lines.push('> real providers, public networks, HTTPS, or production deployments.')
  lines.push('> `out_of_scope` items are evaluated against the NO_ANSWER outcome — a documented')
  lines.push('> mapping, not a native product route.')
  lines.push('')
  for (const channel of channels) {
    lines.push(`## Channel: ${channel.channel}`, '')
    lines.push('| Gate | Value | Result |')
    lines.push('|------|-------|--------|')
    for (const gate of channel.gates) {
      lines.push(`| ${gate.gate} | ${typeof gate.value === 'number' && gate.value % 1 !== 0 ? gate.value.toFixed(4) : gate.value} | ${gate.pass ? 'PASS' : 'FAIL'} |`)
    }
    lines.push(`| MRR (report only, no gate) | ${channel.metrics.mrr === null ? '-' : channel.metrics.mrr.toFixed(4)} | - |`)
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
  fakeUrl,
  set: setPath,
  note: 'Loopback-only deterministic evaluation; out_of_scope is mapped to NO_ANSWER (not a native route).',
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
    channel.gates.map(g => `${g.gate}=${typeof g.value === 'number' && g.value % 1 !== 0 ? g.value.toFixed(4) : g.value}`).join(', ') +
    `, MRR=${channel.metrics.mrr === null ? '-' : channel.metrics.mrr.toFixed(4)}`)
}
console.log(`Report: ${mdPath}`)
console.log(allPass ? 'GOLDEN-SET PASS' : 'GOLDEN-SET FAIL')
process.exit(allPass ? 0 : 1)
