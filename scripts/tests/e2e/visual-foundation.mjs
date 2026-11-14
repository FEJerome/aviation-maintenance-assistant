import fs from 'node:fs/promises'
import path from 'node:path'

const scenario = process.env.VISUAL_SCENARIO || 'citations'
const appUrl = process.env.APP_URL || 'http://127.0.0.1:5173'
const debugUrl = process.env.DEBUG_URL || 'http://127.0.0.1:19222'
const fakeUrl = process.env.FAKE_URL || 'http://127.0.0.1:18089'
const outputDir = process.env.VISUAL_OUTPUT_DIR || 'docs/assets/screenshots'
const mobile = scenario === 'mobile'

const definitions = {
  clarification: {
    file: 'foundation-01-clarification.png',
    question: 'CTLS engine oil pressure',
    ready: "document.body.innerText.includes('机型和发动机型号')"
  },
  citations: {
    file: 'foundation-02-citations.png',
    question: 'CTLS Rotax 912 engine oil pressure',
    ready: "document.querySelectorAll('.citation-item').length > 0"
  },
  mismatch: {
    file: 'foundation-03-new-model-mismatch.png',
    question: 'CTLS Rotax 912 engine oil pressure',
    followUp: 'B737-800 PW1100G engine oil pressure',
    ready: "document.body.innerText.includes('不匹配')"
  },
  degraded: {
    file: 'foundation-04-blocking-fallback.png',
    question: 'CTLS Rotax 912 engine oil pressure',
    ready: "document.querySelector('.degraded-notice') !== null"
  },
  'stream-error': {
    file: 'foundation-05-stream-error.png',
    question: 'CTLS Rotax 912 engine oil pressure',
    ready: "document.querySelector('.error-notice') !== null"
  },
  mobile: {
    file: 'foundation-06-mobile-citations.png',
    question: 'CTLS Rotax 912 engine oil pressure',
    ready: "document.querySelectorAll('.citation-item').length > 0"
  }
}

const definition = definitions[scenario]
if (!definition) throw new Error(`Unknown scenario: ${scenario}`)
await fs.mkdir(outputDir, { recursive: true })

const target = await fetch(`${debugUrl}/json/new?${encodeURIComponent(appUrl)}`, { method: 'PUT' })
  .then(response => response.json())
const socket = new WebSocket(target.webSocketDebuggerUrl)
await new Promise((resolve, reject) => {
  socket.addEventListener('open', resolve, { once: true })
  socket.addEventListener('error', reject, { once: true })
})

let nextId = 1
const pending = new Map()
const eventWaiters = new Map()
const exceptions = []
const networkFailures = []

socket.addEventListener('message', ({ data }) => {
  const message = JSON.parse(data)
  if (message.id) {
    const waiter = pending.get(message.id)
    if (waiter) {
      pending.delete(message.id)
      if (message.error) waiter.reject(new Error(message.error.message))
      else waiter.resolve(message.result)
    }
    return
  }
  if (message.method === 'Runtime.exceptionThrown') exceptions.push(message.params.exceptionDetails.text)
  if (message.method === 'Network.loadingFailed') networkFailures.push(message.params.errorText)
  const waiter = eventWaiters.get(message.method)
  if (waiter) {
    eventWaiters.delete(message.method)
    waiter(message.params)
  }
})

function send(method, params = {}) {
  const id = nextId++
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject })
    socket.send(JSON.stringify({ id, method, params }))
  })
}

function waitEvent(method, timeout = 20000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`Timed out waiting for ${method}`)), timeout)
    eventWaiters.set(method, params => {
      clearTimeout(timer)
      resolve(params)
    })
  })
}

async function evaluate(expression) {
  const result = await send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true
  })
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.text)
  return result.result.value
}

async function waitFor(expression, timeout = 30000) {
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) {
    if (await evaluate(expression)) return
    await new Promise(resolve => setTimeout(resolve, 150))
  }
  throw new Error(`Timed out waiting for: ${expression}`)
}

async function ask(question) {
  await evaluate(`(() => {
    const input = document.querySelector('.chat-input-area input');
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    setter.call(input, ${JSON.stringify(question)});
    input.dispatchEvent(new Event('input', { bubbles: true }));
    return true;
  })()`)
  // Wait for Vue to flush the disabled state before clicking send.
  await new Promise(resolve => setTimeout(resolve, 200))
  await evaluate(`(() => {
    const button = document.querySelector('.chat-input-area button');
    if (button.disabled) throw new Error('Send button is still disabled after input flush');
    button.click();
    return true;
  })()`)
}

try {
  await send('Page.enable')
  await send('Runtime.enable')
  await send('Network.enable')
  await send('Emulation.setDeviceMetricsOverride', {
    width: mobile ? 390 : 1440,
    height: mobile ? 844 : 900,
    deviceScaleFactor: 1,
    mobile
  })
  const loaded = waitEvent('Page.loadEventFired')
  await send('Page.navigate', { url: appUrl })
  await loaded
  await evaluate("document.querySelector('.guide-start-btn')?.click(); document.querySelector('.guide-close')?.click(); true")
  await waitFor("document.querySelector('.chat-input-area input') !== null")

  await ask(definition.question)
  if (definition.followUp) {
    await waitFor("document.querySelectorAll('.citation-item').length > 0")
    await ask(definition.followUp)
  }
  await waitFor(definition.ready)
  await waitFor("document.querySelector('.loading-indicator') === null")

  const state = await evaluate(`(() => ({
    citations: document.querySelectorAll('.citation-item').length,
    degraded: document.querySelector('.degraded-notice') !== null,
    error: document.querySelector('.error-notice')?.innerText || '',
    systemText: [...document.querySelectorAll('.message-row.system')].map(node => node.innerText),
    horizontalOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth
  }))()`)
  const fake = await fetch(`${fakeUrl}/__calls`).then(response => response.json())
  const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  await fs.writeFile(path.join(outputDir, definition.file), Buffer.from(screenshot.data, 'base64'))

  const result = {
    scenario,
    viewport: mobile ? '390x844' : '1440x900',
    screenshot: definition.file,
    fakeAttempts: fake.calls,
    state,
    exceptions,
    networkFailures
  }
  await fs.writeFile(
    path.join(outputDir, `foundation-${scenario}-result.json`),
    JSON.stringify(result, null, 2)
  )
  console.log(JSON.stringify(result, null, 2))
} finally {
  socket.close()
  await fetch(`${debugUrl}/json/close/${target.id}`).catch(() => {})
}
