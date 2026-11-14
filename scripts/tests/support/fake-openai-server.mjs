import http from 'node:http'

const port = Number(process.env.PORT || 18089)
let calls = 0
let mode = 'ok'
let lastRequest = null

function json(response, status, body) {
  response.writeHead(status, { 'Content-Type': 'application/json' })
  response.end(JSON.stringify(body))
}

function completion(content) {
  return {
    id: 'synthetic-completion',
    object: 'chat.completion',
    choices: [{ index: 0, message: { role: 'assistant', content }, finish_reason: 'stop' }]
  }
}

const server = http.createServer((request, response) => {
  if (request.url === '/__calls' && request.method === 'GET') {
    json(response, 200, { calls, lastRequest })
    return
  }
  if (request.url === '/__reset' && request.method === 'POST') {
    calls = 0
    lastRequest = null
    mode = 'ok'
    json(response, 200, { calls })
    return
  }
  if (request.url === '/__mode' && request.method === 'POST') {
    let body = ''
    request.on('data', chunk => { body += chunk })
    request.on('end', () => {
      const payload = JSON.parse(body || '{}')
      mode = payload.mode || 'ok'
      json(response, 200, { mode })
    })
    return
  }
  if (request.method !== 'POST') {
    json(response, 404, { error: 'not found' })
    return
  }

  let body = ''
  request.on('data', chunk => { body += chunk })
  request.on('end', () => {
    calls += 1
    const payload = JSON.parse(body || '{}')
    lastRequest = {
      path: request.url,
      model: payload.model,
      stream: payload.stream === true,
      hasTemperature: Object.hasOwn(payload, 'temperature')
    }

    if (mode === 'http400') {
      json(response, 400, { error: { message: 'synthetic provider detail' } })
      return
    }

    const answer = 'Synthetic grounded answer [S1].'
    if (payload.stream === true) {
      response.writeHead(200, { 'Content-Type': 'text/event-stream' })
      response.write(`data: ${JSON.stringify({ choices: [{ index: 0, delta: { content: 'Synthetic grounded ' } }] })}\n\n`)
      if (mode === 'streamError') {
        setTimeout(() => response.socket.destroy(), 25)
        return
      }
      response.write(`data: ${JSON.stringify({ choices: [{ index: 0, delta: { content: 'answer [S1].' } }] })}\n\n`)
      response.end('data: [DONE]\n\n')
      return
    }
    json(response, 200, completion(answer))
  })
})

server.listen(port, '127.0.0.1', () => {
  console.log(`Synthetic OpenAI-compatible server listening on 127.0.0.1:${port}`)
})
